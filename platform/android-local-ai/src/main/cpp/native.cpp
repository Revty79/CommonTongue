#include <jni.h>
#include <cstdint>
#include <codecvt>
#include <locale>
#include <memory>
#include <mutex>
#include <stdexcept>
#include <string>
#include <unordered_map>
#include <vector>
#include "whisper.h"

// JNI values are never addresses. IDs are monotonic and never recycled.
struct RecognitionOwner {
    whisper_context *context;
    std::mutex operation;
    explicit RecognitionOwner(whisper_context *value) : context(value) {}
    ~RecognitionOwner() { whisper_free(context); }
};
static std::mutex registry_lock;
static uint64_t next_id = 1;
static std::unordered_map<uint32_t, std::shared_ptr<RecognitionOwner>> recognizers;
static void quiet_log(ggml_log_level, const char *, void *) {} // Native logs can contain private paths/text.
static void fail(JNIEnv *env) {
    if (!env->ExceptionCheck()) env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), "NATIVE_FAILED");
}
static std::string utf(JNIEnv *env, jstring value) {
    if (!value) throw std::invalid_argument("INPUT_INVALID");
    auto *chars = env->GetStringChars(value, nullptr);
    if (!chars) throw std::runtime_error("NATIVE_FAILED");
    std::u16string text(reinterpret_cast<const char16_t *>(chars), env->GetStringLength(value));
    env->ReleaseStringChars(value, chars);
    return std::wstring_convert<std::codecvt_utf8_utf16<char16_t>, char16_t>().to_bytes(text);
}
static jstring java_string(JNIEnv *env, const std::string &text) {
    auto value = std::wstring_convert<std::codecvt_utf8_utf16<char16_t>, char16_t>().from_bytes(text);
    return env->NewString(reinterpret_cast<const jchar *>(value.data()), value.size());
}
static std::shared_ptr<RecognitionOwner> owner(jlong id) {
    if (id <= 0 || static_cast<uint64_t>(id) > UINT32_MAX) throw std::invalid_argument("NATIVE_FAILED");
    std::lock_guard<std::mutex> guard(registry_lock);
    auto found = recognizers.find(static_cast<uint32_t>(id));
    if (found == recognizers.end()) throw std::invalid_argument("NATIVE_FAILED");
    return found->second; // Keeps backing storage alive even if close removes its ID.
}
extern "C" JNIEXPORT jlong JNICALL
Java_com_commontongue_local_android_Native_asrCreate(JNIEnv *env, jobject, jstring path) {
    try {
        whisper_log_set(quiet_log, nullptr);
        ggml_log_set(quiet_log, nullptr);
        auto params = whisper_context_default_params();
        params.use_gpu = false;
        auto *ctx = whisper_init_from_file_with_params(utf(env, path).c_str(), params);
        if (!ctx) throw std::runtime_error("LOAD_FAILED");
        auto value = std::make_shared<RecognitionOwner>(ctx);
        std::lock_guard<std::mutex> guard(registry_lock);
        if (next_id > UINT32_MAX) throw std::runtime_error("RESOURCE_LIMIT");
        uint32_t id = next_id++;
        recognizers.emplace(id, value);
        return id;
    } catch (...) { fail(env); return 0; }
}
extern "C" JNIEXPORT jstring JNICALL
Java_com_commontongue_local_android_Native_asrRun(JNIEnv *env, jobject, jlong id, jfloatArray audio, jstring language) {
    try {
        auto value = owner(id);
        std::lock_guard<std::mutex> guard(value->operation);
        auto lang = utf(env, language);
        if ((lang != "en" && lang != "es") || !audio) throw std::invalid_argument("INPUT_INVALID");
        const auto size = env->GetArrayLength(audio);
        if (size <= 0 || size > 16000 * 31) throw std::invalid_argument("INPUT_INVALID");
        std::vector<float> pcm(size);
        env->GetFloatArrayRegion(audio, 0, size, pcm.data());
        if (env->ExceptionCheck()) return nullptr;
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
        if (whisper_full(value->context, params, pcm.data(), size)) throw std::runtime_error("NATIVE_FAILED");
        std::string text;
        for (int i = 0; i < whisper_full_n_segments(value->context); ++i)
            text += whisper_full_get_segment_text(value->context, i);
        return java_string(env, text);
    } catch (...) { fail(env); return nullptr; }
}
extern "C" JNIEXPORT void JNICALL
Java_com_commontongue_local_android_Native_asrClose(JNIEnv *, jobject, jlong id) {
    if (id <= 0 || static_cast<uint64_t>(id) > UINT32_MAX) return;
    std::lock_guard<std::mutex> guard(registry_lock);
    recognizers.erase(static_cast<uint32_t>(id));
}

extern "C" char *trial_t5_load(const char *);
extern "C" void local_t5_privacy_init();
using Progress = int32_t (*)(uint32_t, void *);
extern "C" char *trial_t5_run(uint64_t, const char *, Progress, void *);
extern "C" void trial_t5_close(uint64_t);
extern "C" void trial_t5_string_free(char *);
using RustString = std::unique_ptr<char, decltype(&trial_t5_string_free)>;
struct Callback { JNIEnv *env; jobject listener; jmethodID method; };
static int32_t checkpoint(uint32_t stage, void *opaque) {
    auto *callback = static_cast<Callback *>(opaque);
    callback->env->CallVoidMethod(callback->listener, callback->method, static_cast<jint>(stage));
    return callback->env->ExceptionCheck() ? 0 : 1;
}
extern "C" JNIEXPORT jstring JNICALL
Java_com_commontongue_local_android_Native_t5Load(JNIEnv *env, jobject, jstring path) {
    try {
        local_t5_privacy_init();
        RustString output(trial_t5_load(utf(env, path).c_str()), trial_t5_string_free);
        if (!output) throw std::runtime_error("LOAD_FAILED");
        return java_string(env, output.get());
    } catch (...) { fail(env); return nullptr; }
}
extern "C" JNIEXPORT jstring JNICALL
Java_com_commontongue_local_android_Native_t5Run(JNIEnv *env, jobject, jlong id, jstring request, jobject listener) {
    try {
        if (id <= 0 || static_cast<uint64_t>(id) > UINT32_MAX || !listener) throw std::invalid_argument("NATIVE_FAILED");
        jclass type = env->GetObjectClass(listener);
        auto method = env->GetMethodID(type, "onStage", "(I)V");
        env->DeleteLocalRef(type);
        if (!method || env->ExceptionCheck()) return nullptr;
        Callback callback{env, listener, method}; // Synchronous; refs live until Rust returns.
        RustString output(trial_t5_run(id, utf(env, request).c_str(), checkpoint, &callback), trial_t5_string_free);
        if (env->ExceptionCheck()) return nullptr;
        if (!output) throw std::runtime_error("NATIVE_FAILED");
        return java_string(env, output.get());
    } catch (...) { fail(env); return nullptr; }
}
extern "C" JNIEXPORT void JNICALL
Java_com_commontongue_local_android_Native_t5Close(JNIEnv *, jobject, jlong id) {
    if (id > 0 && static_cast<uint64_t>(id) <= UINT32_MAX) trial_t5_close(id);
}
