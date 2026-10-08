// Research stdio worker using the MIT OR Apache-2.0 Candle quantized-T5 API.
// No hub client, cloud fallback, server, downloader or production dependency.
use anyhow::{anyhow, Result};
use candle::{Device, Tensor};
use candle_transformers::generation::LogitsProcessor;
use candle_transformers::models::quantized_t5 as t5;
use serde_json::{json, Value};
use std::io::{self, BufRead, Write};
use std::path::Path;
use std::time::Instant;
use tokenizers::Tokenizer;

fn generate(model: &mut t5::T5ForConditionalGeneration, tokenizer: &Tokenizer,
            config: &t5::Config, request: &Value) -> Result<Value> {
    let prompt = request["prompt"].as_str().ok_or_else(|| anyhow!("Missing prompt"))?;
    let limit = request["max_tokens"].as_u64().unwrap_or(128) as usize;
    if limit == 0 || limit > 256 { return Err(anyhow!("Invalid generation limit")); }
    let encoded = tokenizer.encode(prompt, true).map_err(|e| anyhow!(e.to_string()))?;
    if encoded.len() > 512 { return Err(anyhow!("Input too long; no truncation")); }
    model.clear_kv_cache();
    let device = &Device::Cpu;
    let ids = Tensor::new(encoded.get_ids(), device)?.unsqueeze(0)?;
    let start = Instant::now();
    let encoder = model.encode(&ids)?;
    let prefill_ms = start.elapsed().as_secs_f64() * 1000.0;
    let mut output = vec![config.decoder_start_token_id.unwrap_or(config.pad_token_id) as u32];
    let mut sampler = LogitsProcessor::new(404, None, None); // Greedy, no repetition penalty.
    let mut completed = false;
    let generation_start = Instant::now();
    for _ in 0..limit {
        let last = *output.last().unwrap();
        let decoder_ids = Tensor::new(&[last], device)?.unsqueeze(0)?;
        let logits = model.decode(&decoder_ids, &encoder)?.squeeze(0)?;
        let next = sampler.sample(&logits)?;
        if next as usize == config.eos_token_id { completed = true; break; }
        output.push(next);
    }
    let text = tokenizer.decode(&output[1..], true).map_err(|e| anyhow!(e.to_string()))?;
    let generation_ms = generation_start.elapsed().as_secs_f64() * 1000.0;
    let tokens = output.len() - 1;
    Ok(json!({"text": text, "completed": completed, "tokens": tokens,
              "prompt_tokens": encoded.len(), "prefill_ms": prefill_ms,
              "generation_ms": generation_ms, "ms": start.elapsed().as_secs_f64()*1000.0,
              "tokens_per_second": tokens as f64 * 1000.0 / generation_ms.max(0.001)}))
}

fn main() -> Result<()> {
    let args: Vec<String> = std::env::args().collect();
    if args.len() != 3 { return Err(anyhow!("Usage: worker LOCAL_MODEL_DIRECTORY THREADS")); }
    std::env::set_var("RAYON_NUM_THREADS", &args[2]);
    std::env::set_var("CANDLE_NUM_THREADS", &args[2]);
    let directory = Path::new(&args[1]);
    let mut config: t5::Config = serde_json::from_str(&std::fs::read_to_string(directory.join("config.json"))?)?;
    config.use_cache = true;
    let mut tokenizer = Tokenizer::from_file(directory.join("tokenizer.json")).map_err(|e| anyhow!(e.to_string()))?;
    tokenizer.with_padding(None);
    tokenizer.with_truncation(None).map_err(|e| anyhow!(e.to_string()))?;
    let start = Instant::now();
    let variables = t5::VarBuilder::from_gguf(directory.join("model-q4k.gguf"), &Device::Cpu)?;
    let mut model = t5::T5ForConditionalGeneration::load(variables, &config)?;
    println!("{}", json!({"ready": true, "load_ms": start.elapsed().as_secs_f64()*1000.0,
                          "context_tokens": 512, "threads": args[2], "barrier_threads": args[2], "quantization": "Q4_K"}));
    io::stdout().flush()?;
    for line in io::stdin().lock().lines() {
        let request: Value = serde_json::from_str(&line?)?;
        if request.get("shutdown").is_some() { break; }
        let result = match generate(&mut model, &tokenizer, &config, &request) {
            Ok(value) => value, Err(error) => json!({"error": error.to_string()}),
        };
        println!("{}", result);
        io::stdout().flush()?;
    }
    Ok(())
}
