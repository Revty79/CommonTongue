#include <jni.h>
#include <string>
#include <vector>
#include <codecvt>
#include <locale>
#include <cstdint>
#include <android/log.h>
#include "whisper.h"
#include "sentencepiece_processor.h"

static std::string utf(JNIEnv *env, jstring value) {
    const jchar *chars = env->GetStringChars(value, nullptr);
    std::u16string text(reinterpret_cast<const char16_t *>(chars), env->GetStringLength(value));
    std::wstring_convert<std::codecvt_utf8_utf16<char16_t>, char16_t> converter;
    std::string result = converter.to_bytes(text);
    env->ReleaseStringChars(value, chars);
    return result;
}

static jstring java_string(JNIEnv *env, const std::string &text) {
    std::wstring_convert<std::codecvt_utf8_utf16<char16_t>, char16_t> converter;
    std::u16string result = converter.from_bytes(text);
    return env->NewString(reinterpret_cast<const jchar *>(result.data()), result.size());
}

static void fail(JNIEnv *env, const std::string &message) {
    env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), message.c_str());
}

static void native_log(ggml_log_level level, const char *text, void *) {
    __android_log_write(level == GGML_LOG_LEVEL_ERROR ? ANDROID_LOG_ERROR : ANDROID_LOG_INFO, "LocalInference", text);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_commontongue_spike_device_Native_buildInfo(JNIEnv *env, jobject) {
#if defined(__aarch64__)
    const char *abi = "arm64-v8a";
#else
    const char *abi = "x86_64";
#endif
#ifdef __OPTIMIZE__
    const char *optimized = "optimized";
#else
    const char *optimized = "unoptimized";
#endif
    std::string result = std::string(abi) + "; " + optimized + "; CPU only; GGML_NATIVE=OFF";
    return java_string(env, result);
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_commontongue_spike_device_Native_asrCreate(JNIEnv *env, jobject, jstring path) {
    whisper_log_set(native_log, nullptr);
    auto params = whisper_context_default_params();
    params.use_gpu = false;
    auto *ctx = whisper_init_from_file_with_params(utf(env, path).c_str(), params);
    if (!ctx) fail(env, "Whisper model could not load");
    return reinterpret_cast<jlong>(ctx);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_commontongue_spike_device_Native_asrRun(JNIEnv *env, jobject, jlong handle, jfloatArray audio, jstring language) {
    auto *ctx = reinterpret_cast<whisper_context *>(handle);
    std::string lang = utf(env, language);
    auto params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.n_threads = 4;
    params.language = lang.c_str();
    params.translate = false;
    params.no_context = true;
    params.print_progress = false;
    params.print_realtime = false;
    params.print_timestamps = false;
    params.print_special = false;
    params.greedy.best_of = 1;
    params.temperature_inc = 0;
    const auto size = env->GetArrayLength(audio);
    std::vector<float> pcm(size);
    env->GetFloatArrayRegion(audio, 0, size, pcm.data());
    if (whisper_full(ctx, params, pcm.data(), size) != 0) {
        fail(env, "Whisper inference failed");
        return nullptr;
    }
    std::string text;
    for (int index = 0; index < whisper_full_n_segments(ctx); ++index) text += whisper_full_get_segment_text(ctx, index);
    return java_string(env, text);
}

extern "C" JNIEXPORT void JNICALL
Java_com_commontongue_spike_device_Native_asrClose(JNIEnv *, jobject, jlong handle) {
    whisper_free(reinterpret_cast<whisper_context *>(handle));
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_commontongue_spike_device_Native_tokenizerCreate(JNIEnv *env, jobject, jstring path) {
    auto *processor = new sentencepiece::SentencePieceProcessor();
    auto status = processor->Load(utf(env, path));
    if (!status.ok()) {
        delete processor;
        fail(env, status.ToString());
        return 0;
    }
    return reinterpret_cast<jlong>(processor);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_commontongue_spike_device_Native_encode(JNIEnv *env, jobject, jlong handle, jstring text) {
    auto *processor = reinterpret_cast<sentencepiece::SentencePieceProcessor *>(handle);
    std::vector<std::string> pieces;
    auto status = processor->Encode(utf(env, text), &pieces);
    if (!status.ok()) { fail(env, status.ToString()); return nullptr; }
    auto result = env->NewObjectArray(pieces.size(), env->FindClass("java/lang/String"), nullptr);
    for (size_t index = 0; index < pieces.size(); ++index) {
        jstring piece = java_string(env, pieces[index]);
        env->SetObjectArrayElement(result, index, piece);
        env->DeleteLocalRef(piece);
    }
    return result;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_commontongue_spike_device_Native_decode(JNIEnv *env, jobject, jlong handle, jobjectArray input) {
    auto *processor = reinterpret_cast<sentencepiece::SentencePieceProcessor *>(handle);
    std::vector<std::string> pieces;
    for (int index = 0; index < env->GetArrayLength(input); ++index) {
        auto piece = static_cast<jstring>(env->GetObjectArrayElement(input, index));
        pieces.push_back(utf(env, piece));
        env->DeleteLocalRef(piece);
    }
    std::string text;
    auto status = processor->Decode(pieces, &text);
    if (!status.ok()) { fail(env, status.ToString()); return nullptr; }
    return java_string(env, text);
}

extern "C" JNIEXPORT void JNICALL
Java_com_commontongue_spike_device_Native_tokenizerClose(JNIEnv *, jobject, jlong handle) {
    delete reinterpret_cast<sentencepiece::SentencePieceProcessor *>(handle);
}

extern "C" char *trial_t5_load(const char *);
using t5_progress = int32_t (*)(uint32_t, void *);
extern "C" char *trial_t5_run(uint64_t, const char *, t5_progress, void *);
extern "C" void trial_t5_close(uint64_t);
extern "C" void trial_t5_string_free(char *);

struct TranslationCallback {
    JNIEnv *env;
    jobject listener;
    jmethodID method;
};

static int32_t translation_progress(uint32_t stage, void *opaque) {
    // Rust invokes this synchronously on this JNI thread only. The JNI local
    // listener reference and the stack context remain live for the entire call.
    auto *callback = static_cast<TranslationCallback *>(opaque);
    callback->env->CallVoidMethod(callback->listener, callback->method, static_cast<jint>(stage));
    return callback->env->ExceptionCheck() ? 0 : 1;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_commontongue_spike_device_Native_t5Load(JNIEnv *env, jobject, jstring path) {
    char *result = trial_t5_load(utf(env, path).c_str());
    jstring reply = java_string(env, result);
    trial_t5_string_free(result);
    return reply;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_commontongue_spike_device_Native_t5Run(JNIEnv *env, jobject, jlong handle, jstring request, jobject listener) {
    if (handle <= 0 || static_cast<uint64_t>(handle) > UINT32_MAX || !listener) {
        fail(env, "Invalid translation model ID or progress callback");
        return nullptr;
    }
    jclass type = env->GetObjectClass(listener);
    jmethodID method = env->GetMethodID(type, "onStage", "(I)V");
    env->DeleteLocalRef(type);
    if (!method || env->ExceptionCheck()) return nullptr;
    TranslationCallback callback{env, listener, method};
    const std::string input = utf(env, request);
    char *result = trial_t5_run(static_cast<uint64_t>(handle), input.c_str(), translation_progress, &callback);
    // A failed journal/callback aborts generation in Rust. Preserve the pending
    // Java exception and still release Rust's independently owned output string.
    if (env->ExceptionCheck()) {
        trial_t5_string_free(result);
        return nullptr;
    }
    jstring reply = java_string(env, result);
    trial_t5_string_free(result);
    return reply;
}

extern "C" JNIEXPORT void JNICALL
Java_com_commontongue_spike_device_Native_t5Close(JNIEnv *, jobject, jlong handle) {
    if (handle > 0 && static_cast<uint64_t>(handle) <= UINT32_MAX)
        trial_t5_close(static_cast<uint64_t>(handle));
}
