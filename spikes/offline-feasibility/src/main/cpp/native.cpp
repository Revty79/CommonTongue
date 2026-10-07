#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include "whisper.h"
#include "sentencepiece_processor.h"

static std::string utf(JNIEnv *env, jstring value) {
    const char *chars = env->GetStringUTFChars(value, nullptr);
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

static void fail(JNIEnv *env, const std::string &message) {
    env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), message.c_str());
}

static void native_log(ggml_log_level level, const char *text, void *) {
    __android_log_write(level == GGML_LOG_LEVEL_ERROR ? ANDROID_LOG_ERROR : ANDROID_LOG_INFO, "LocalInference", text);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_commontongue_spike_offline_Native_buildInfo(JNIEnv *env, jobject) {
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
    return env->NewStringUTF(result.c_str());
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_commontongue_spike_offline_Native_asrCreate(JNIEnv *env, jobject, jstring path) {
    whisper_log_set(native_log, nullptr);
    auto params = whisper_context_default_params();
    params.use_gpu = false;
    auto *ctx = whisper_init_from_file_with_params(utf(env, path).c_str(), params);
    if (!ctx) fail(env, "Whisper model could not load");
    return reinterpret_cast<jlong>(ctx);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_commontongue_spike_offline_Native_asrRun(JNIEnv *env, jobject, jlong handle, jfloatArray audio, jstring language) {
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
    return env->NewStringUTF(text.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_commontongue_spike_offline_Native_asrClose(JNIEnv *, jobject, jlong handle) {
    whisper_free(reinterpret_cast<whisper_context *>(handle));
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_commontongue_spike_offline_Native_tokenizerCreate(JNIEnv *env, jobject, jstring path) {
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
Java_com_commontongue_spike_offline_Native_encode(JNIEnv *env, jobject, jlong handle, jstring text) {
    auto *processor = reinterpret_cast<sentencepiece::SentencePieceProcessor *>(handle);
    std::vector<std::string> pieces;
    auto status = processor->Encode(utf(env, text), &pieces);
    if (!status.ok()) { fail(env, status.ToString()); return nullptr; }
    auto result = env->NewObjectArray(pieces.size(), env->FindClass("java/lang/String"), nullptr);
    for (size_t index = 0; index < pieces.size(); ++index) {
        jstring piece = env->NewStringUTF(pieces[index].c_str());
        env->SetObjectArrayElement(result, index, piece);
        env->DeleteLocalRef(piece);
    }
    return result;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_commontongue_spike_offline_Native_decode(JNIEnv *env, jobject, jlong handle, jobjectArray input) {
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
    return env->NewStringUTF(text.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_commontongue_spike_offline_Native_tokenizerClose(JNIEnv *, jobject, jlong handle) {
    delete reinterpret_cast<sentencepiece::SentencePieceProcessor *>(handle);
}
