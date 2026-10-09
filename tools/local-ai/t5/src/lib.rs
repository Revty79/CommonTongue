//! Isolated CPU research port of the locked Pass 4 quantized T5 worker.
//! No network client, download path, production adapter, or semantic verifier.
use anyhow::{anyhow, Result};
use candle::{Device, Tensor};
use candle_transformers::{generation::LogitsProcessor, models::quantized_t5 as t5};
use serde_json::{json, Value};
use std::{ffi::{CStr, CString, c_void}, os::raw::c_char, path::Path, time::Instant,
          sync::{Mutex, OnceLock}};
use tokenizers::Tokenizer;
mod handles;

static MODELS: OnceLock<Mutex<handles::Handles<Model>>> = OnceLock::new();
fn models() -> &'static Mutex<handles::Handles<Model>> {
    MODELS.get_or_init(|| Mutex::new(handles::Handles::new()))
}

// Synchronous callbacks on the JNI caller's thread. No pointer/ID/token/text is sent.
type Progress = Option<unsafe extern "C" fn(u32, *mut c_void) -> i32>;
struct Checkpoints { callback: Progress, context: *mut c_void }
impl Checkpoints {
    fn emit(&mut self, stage: u32) -> Result<()> {
        if let Some(callback) = self.callback {
            if unsafe { callback(stage, self.context) } == 0 {
                return Err(anyhow!("Translation checkpoint failed"));
            }
        }
        Ok(())
    }
}

struct Model {
    network: t5::T5ForConditionalGeneration,
    tokenizer: Tokenizer,
    config: t5::Config,
}

fn load(directory: &Path) -> Result<Model> {
    // Set both pools before any tensor work, exactly as the corrected Pass 4 worker.
    std::env::set_var("RAYON_NUM_THREADS", "4");
    std::env::set_var("CANDLE_NUM_THREADS", "4");
    let mut config: t5::Config = serde_json::from_str(&std::fs::read_to_string(directory.join("config.json"))?)?;
    config.use_cache = true;
    let mut tokenizer = Tokenizer::from_file(directory.join("tokenizer.json")).map_err(|e| anyhow!(e.to_string()))?;
    tokenizer.with_padding(None);
    tokenizer.with_truncation(None).map_err(|e| anyhow!(e.to_string()))?;
    let variables = t5::VarBuilder::from_gguf(directory.join("model-q4k.gguf"), &Device::Cpu)?;
    let network = t5::T5ForConditionalGeneration::load(variables, &config)?;
    Ok(Model { network, tokenizer, config })
}

fn generate(model: &mut Model, request: &Value, checkpoints: &mut Checkpoints) -> Result<Value> {
    let text = request["text"].as_str().ok_or_else(|| anyhow!("Missing text"))?;
    if text.trim().is_empty() { return Err(anyhow!("Empty source")); }
    let prefix = match request["direction"].as_str() {
        Some("en-es") => "<2es>", Some("es-en") => "<2en>", _ => return Err(anyhow!("Unsupported direction")),
    };
    let prompt = format!("{prefix} {text}");
    checkpoints.emit(1)?; // TOKENIZE_START
    let encoded = model.tokenizer.encode(prompt, true).map_err(|e| anyhow!(e.to_string()))?;
    if encoded.len() > 512 { return Err(anyhow!("Input exceeds 512 tokens; no truncation")); }
    checkpoints.emit(2)?; // TOKENIZE_COMPLETE
    model.network.clear_kv_cache();
    let start = Instant::now();
    let ids = Tensor::new(encoded.get_ids(), &Device::Cpu)?.unsqueeze(0)?;
    checkpoints.emit(3)?; // ENCODER_START
    let encoder = model.network.encode(&ids)?;
    let prefill_ms = start.elapsed().as_secs_f64() * 1000.0;
    checkpoints.emit(4)?; // ENCODER_COMPLETE
    let mut output = vec![model.config.decoder_start_token_id.unwrap_or(model.config.pad_token_id) as u32];
    let mut sampler = LogitsProcessor::new(404, None, None);
    let generation_start = Instant::now();
    let mut completed = false;
    checkpoints.emit(5)?; // DECODER_START
    for index in 0..128 {
        let decoder_ids = Tensor::new(&[*output.last().unwrap()], &Device::Cpu)?.unsqueeze(0)?;
        let logits = model.network.decode(&decoder_ids, &encoder)?.squeeze(0)?;
        let next = sampler.sample(&logits)?;
        if index == 0 { checkpoints.emit(6)?; } // FIRST_TOKEN (metadata only, even if EOS)
        if next as usize == model.config.eos_token_id { completed = true; break; }
        output.push(next);
    }
    let translated = model.tokenizer.decode(&output[1..], true).map_err(|e| anyhow!(e.to_string()))?;
    let generation_ms = generation_start.elapsed().as_secs_f64() * 1000.0;
    Ok(json!({"text": translated, "completed": completed, "tokens": output.len()-1,
        "prompt_tokens": encoded.len(), "prefill_ms": prefill_ms, "generation_ms": generation_ms,
        "ms": start.elapsed().as_secs_f64()*1000.0, "threads": 4,
        "execution_abi": if cfg!(target_arch="aarch64") { "arm64-v8a" } else { "desktop-control" }}))
}

fn string(value: Value) -> *mut c_char {
    CString::new(value.to_string()).expect("JSON contains no raw NUL").into_raw()
}

// Callers serialize model access. Cancellation terminates the private Android
// inference process; this API does not pretend blocking tensor kernels cooperate.
#[no_mangle]
pub unsafe extern "C" fn trial_t5_load(path: *const c_char) -> *mut c_char {
    let result = std::panic::catch_unwind(|| -> Result<Value> {
        if path.is_null() { return Err(anyhow!("Null path")); }
        let path = CStr::from_ptr(path).to_str()?;
        let begin = Instant::now();
        let model = load(Path::new(path))?;
        let handle = models().lock().map_err(|_| anyhow!("Model registry unavailable"))?
            .insert(model).ok_or_else(|| anyhow!("Model handle limit reached"))?;
        Ok(json!({"handle":handle, "handle_kind":"opaque-id-v1",
                  "load_ms":begin.elapsed().as_secs_f64()*1000.0}))
    });
    string(match result { Ok(Ok(value))=>value, Ok(Err(error))=>json!({"error":error.to_string()}), Err(_)=>json!({"error":"Native load panic"}) })
}

#[no_mangle]
pub unsafe extern "C" fn trial_t5_run(handle: u64, request: *const c_char,
                                    callback: Progress, context: *mut c_void) -> *mut c_char {
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| -> Result<Value> {
        if handle == 0 || handle > handles::MAX_HANDLE || request.is_null() { return Err(anyhow!("Model/request missing")); }
        let request: Value = serde_json::from_str(CStr::from_ptr(request).to_str()?)?;
        let mut registry = models().lock().map_err(|_| anyhow!("Model registry unavailable"))?;
        let model = registry.get_mut(handle).ok_or_else(|| anyhow!("Unknown or closed model handle"))?;
        generate(model, &request, &mut Checkpoints { callback, context })
    }));
    string(match result { Ok(Ok(value))=>value, Ok(Err(error))=>json!({"error":error.to_string()}), Err(_)=>json!({"error":"Native inference panic"}) })
}

#[no_mangle]
pub extern "C" fn trial_t5_close(handle: u64) {
    if let Ok(mut registry) = models().lock() { registry.remove(handle); }
}

#[no_mangle]
pub unsafe extern "C" fn trial_t5_string_free(value: *mut c_char) {
    if !value.is_null() { drop(CString::from_raw(value)); }
}
