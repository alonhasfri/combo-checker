// Small bridge between the Kotlin app and whisper.cpp.
#include <jni.h>
#include <android/log.h>
#include <atomic>
#include <string>
#include <thread>
#include <algorithm>
#include "whisper.h"

#define TAG "VoiceBubble"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

static std::atomic<bool> g_abort{false};

static bool abort_cb(void *) { return g_abort.load(); }

extern "C" JNIEXPORT jlong JNICALL
Java_com_voicebubble_Whisper_nativeLoad(JNIEnv *env, jobject, jstring jpath) {
    const char *path = env->GetStringUTFChars(jpath, nullptr);
    whisper_context_params cp = whisper_context_default_params();
    cp.use_gpu = false;
    whisper_context *ctx = whisper_init_from_file_with_params(path, cp);
    env->ReleaseStringUTFChars(jpath, path);
    return reinterpret_cast<jlong>(ctx);
}

extern "C" JNIEXPORT void JNICALL
Java_com_voicebubble_Whisper_nativeFree(JNIEnv *, jobject, jlong ptr) {
    if (ptr) whisper_free(reinterpret_cast<whisper_context *>(ptr));
}

extern "C" JNIEXPORT void JNICALL
Java_com_voicebubble_Whisper_nativeAbort(JNIEnv *, jobject) { g_abort = true; }

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_voicebubble_Whisper_nativeTranscribe(JNIEnv *env, jobject, jlong ptr, jfloatArray jaudio,
                                              jstring jlang, jstring jprompt, jint threads) {
    auto *ctx = reinterpret_cast<whisper_context *>(ptr);
    if (!ctx) return nullptr;
    g_abort = false;

    const char *lang = env->GetStringUTFChars(jlang, nullptr);
    const char *prompt = env->GetStringUTFChars(jprompt, nullptr);
    jsize n = env->GetArrayLength(jaudio);
    jfloat *audio = env->GetFloatArrayElements(jaudio, nullptr);

    whisper_full_params p = whisper_full_default_params(WHISPER_SAMPLING_BEAM_SEARCH);
    p.beam_search.beam_size = 3;   // a little slower than greedy, noticeably fewer mistakes
    p.n_threads = threads;
    p.print_realtime = false;
    p.print_progress = false;
    p.print_timestamps = false;
    p.print_special = false;
    p.translate = false;
    p.no_timestamps = true;
    p.no_context = true;
    p.suppress_blank = true;
    p.suppress_nst = true;
    p.language = lang;             // "auto" lets Whisper detect the language
    p.initial_prompt = prompt[0] ? prompt : nullptr;
    p.abort_callback = abort_cb;
    p.abort_callback_user_data = nullptr;

    std::string out;
    int rc = whisper_full(ctx, p, audio, n);
    if (rc == 0 && !g_abort) {
        int segs = whisper_full_n_segments(ctx);
        for (int i = 0; i < segs; i++) out += whisper_full_get_segment_text(ctx, i);
    }
    LOGI("whisper_full rc=%d samples=%d chars=%zu", rc, (int) n, out.size());

    env->ReleaseFloatArrayElements(jaudio, audio, JNI_ABORT);
    env->ReleaseStringUTFChars(jlang, lang);
    env->ReleaseStringUTFChars(jprompt, prompt);
    if (rc != 0 || g_abort) return nullptr;
    // Raw UTF-8 bytes; Kotlin decodes them (NewStringUTF chokes on emoji and odd bytes).
    jbyteArray arr = env->NewByteArray((jsize) out.size());
    env->SetByteArrayRegion(arr, 0, (jsize) out.size(), reinterpret_cast<const jbyte *>(out.data()));
    return arr;
}
