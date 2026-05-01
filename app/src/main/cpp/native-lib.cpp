#include <android/log.h>
#include <jni.h>

#include <algorithm>
#include <atomic>
#include <chrono>
#include <cstdint>
#include <cstring>
#include <memory>
#include <mutex>
#include <string>
#include <vector>

#include "llama.h"

#define LOG_TAG "AiTunesLlama"

namespace {

std::once_flag g_backend_once;

struct LlamaSession {
    llama_model *model = nullptr;
    llama_context *ctx = nullptr;
    std::atomic<bool> cancel{false};
};

void ensure_backend() {
    std::call_once(g_backend_once, [] { llama_backend_init(); });
}

bool abort_cb(void *userdata) {
    auto *cancel = static_cast<std::atomic<bool> *>(userdata);
    return cancel && cancel->load(std::memory_order_relaxed);
}

std::string build_chat_prompt(llama_model *model, const char *system_text, const char *user_text) {
    const char *tmpl = llama_model_chat_template(model, nullptr);
    llama_chat_message msgs[2];
    msgs[0] = {"system", system_text};
    msgs[1] = {"user", user_text};

    const int32_t needed =
        llama_chat_apply_template(tmpl, msgs, 2, true, nullptr, 0);
    if (needed < 0) {
        return std::string(system_text) + "\n\n" + user_text + "\n";
    }
    std::vector<char> buf(static_cast<size_t>(needed) + 256);
    const int32_t w =
        llama_chat_apply_template(tmpl, msgs, 2, true, buf.data(), static_cast<int32_t>(buf.size()));
    if (w < 0) {
        return std::string(system_text) + "\n\n" + user_text + "\n";
    }
    return std::string(buf.data(), static_cast<size_t>(w));
}

bool decode_prompt_chunks(
    llama_context *ctx,
    const std::vector<llama_token> &tokens,
    int32_t n_batch,
    std::atomic<bool> *cancel
) {
    size_t i = 0;
    while (i < tokens.size()) {
        if (cancel && cancel->load(std::memory_order_relaxed)) {
            return false;
        }
        const size_t chunk = std::min(static_cast<size_t>(n_batch), tokens.size() - i);
        llama_batch batch =
            llama_batch_get_one(const_cast<llama_token *>(tokens.data() + i), static_cast<int32_t>(chunk));
        const int res = llama_decode(ctx, batch);
        if (res == 2) {
            return false;
        }
        if (res != 0) {
            __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "llama_decode (prompt) rc=%d", res);
            return false;
        }
        i += chunk;
    }
    return true;
}

void throw_illegal_state(JNIEnv *env, const char *msg) {
    jclass ex = env->FindClass("java/lang/IllegalStateException");
    if (ex) {
        env->ThrowNew(ex, msg);
    }
}

} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_aitunes_app_engine_LlamaNativeBridge_nativeLoadModel(
    JNIEnv *env,
    jclass /* clazz */,
    jstring jpath,
    jint n_ctx,
    jint n_batch,
    jint n_threads,
    jboolean j_use_mmap,
    jboolean j_low_memory
) {
    ensure_backend();

    const char *path = env->GetStringUTFChars(jpath, nullptr);
    if (!path) {
        return 0;
    }

    std::unique_ptr<LlamaSession> session = std::make_unique<LlamaSession>();

    llama_model_params mparams = llama_model_default_params();
    const bool low = j_low_memory == JNI_TRUE;
    if (low) {
        mparams.use_mmap = false;
        mparams.use_mlock = false;
    } else {
        mparams.use_mmap = j_use_mmap == JNI_TRUE;
        mparams.use_mlock = false;
    }

    session->model = llama_model_load_from_file(path, mparams);
    env->ReleaseStringUTFChars(jpath, path);

    if (!session->model) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "llama_model_load_from_file falló");
        return 0;
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = static_cast<uint32_t>(std::max(512, static_cast<int>(n_ctx)));
    cparams.n_batch = static_cast<uint32_t>(std::max(32, static_cast<int>(n_batch)));
    cparams.n_threads = std::max(1, static_cast<int>(n_threads));
    cparams.n_threads_batch = cparams.n_threads;
    cparams.abort_callback = abort_cb;
    cparams.abort_callback_data = &session->cancel;

    session->ctx = llama_init_from_model(session->model, cparams);
    if (!session->ctx) {
        llama_model_free(session->model);
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "llama_init_from_model falló");
        return 0;
    }

    llama_set_n_threads(session->ctx, cparams.n_threads, cparams.n_threads_batch);

    __android_log_print(
        ANDROID_LOG_INFO,
        LOG_TAG,
        "Modelo listo n_ctx=%u n_batch=%u threads=%d mmap=%d mlock=%d",
        cparams.n_ctx,
        cparams.n_batch,
        cparams.n_threads,
        low ? 0 : (j_use_mmap == JNI_TRUE ? 1 : 0),
        mparams.use_mlock ? 1 : 0
    );

    return reinterpret_cast<jlong>(session.release());
}

extern "C" JNIEXPORT void JNICALL
Java_com_aitunes_app_engine_LlamaNativeBridge_nativeReleaseModel(JNIEnv * /* env */, jclass /* clazz */, jlong handle) {
    if (handle == 0) {
        return;
    }
    auto *session = reinterpret_cast<LlamaSession *>(handle);
    if (session->ctx) {
        llama_free(session->ctx);
        session->ctx = nullptr;
    }
    if (session->model) {
        llama_model_free(session->model);
        session->model = nullptr;
    }
    delete session;
}

extern "C" JNIEXPORT void JNICALL
Java_com_aitunes_app_engine_LlamaNativeBridge_nativeCancel(JNIEnv * /* env */, jclass /* clazz */, jlong handle) {
    if (handle == 0) {
        return;
    }
    auto *session = reinterpret_cast<LlamaSession *>(handle);
    session->cancel.store(true, std::memory_order_relaxed);
}

extern "C" JNIEXPORT void JNICALL
Java_com_aitunes_app_engine_LlamaNativeBridge_nativeStreamCompletion(
    JNIEnv *env,
    jclass /* clazz */,
    jlong handle,
    jstring j_system,
    jstring j_user,
    jobject j_callback
) {
    if (handle == 0 || !j_callback) {
        return;
    }

    auto *session = reinterpret_cast<LlamaSession *>(handle);
    if (!session->ctx || !session->model) {
        throw_illegal_state(env, "Sesión nativa no inicializada");
        return;
    }

    jclass cb_class = env->GetObjectClass(j_callback);
    if (!cb_class) {
        return;
    }
    const jmethodID on_token =
        env->GetMethodID(cb_class, "onToken", "(Ljava/lang/String;)V");
    if (!on_token) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "Callback sin método onToken");
        return;
    }

    const char *sys_c = env->GetStringUTFChars(j_system, nullptr);
    const char *usr_c = env->GetStringUTFChars(j_user, nullptr);
    if (!sys_c || !usr_c) {
        if (sys_c) {
            env->ReleaseStringUTFChars(j_system, sys_c);
        }
        if (usr_c) {
            env->ReleaseStringUTFChars(j_user, usr_c);
        }
        return;
    }

    session->cancel.store(false, std::memory_order_relaxed);

    const std::string prompt = build_chat_prompt(session->model, sys_c, usr_c);
    env->ReleaseStringUTFChars(j_system, sys_c);
    env->ReleaseStringUTFChars(j_user, usr_c);

    const llama_vocab *vocab = llama_model_get_vocab(session->model);

    std::vector<llama_token> tokens(8192);
    const int32_t n_tok = llama_tokenize(
        vocab,
        prompt.c_str(),
        static_cast<int32_t>(prompt.size()),
        tokens.data(),
        static_cast<int32_t>(tokens.size()),
        true,
        true
    );
    if (n_tok < 0) {
        tokens.resize(static_cast<size_t>(-n_tok));
        const int32_t n2 = llama_tokenize(
            vocab,
            prompt.c_str(),
            static_cast<int32_t>(prompt.size()),
            tokens.data(),
            static_cast<int32_t>(tokens.size()),
            true,
            true
        );
        if (n2 < 0) {
            __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "tokenize falló");
            return;
        }
        tokens.resize(static_cast<size_t>(n2));
    } else {
        tokens.resize(static_cast<size_t>(n_tok));
    }

    const int32_t n_ctx = static_cast<int32_t>(llama_n_ctx(session->ctx));
    const int32_t max_prompt_tokens = std::max(64, n_ctx - 64);
    if (static_cast<int32_t>(tokens.size()) > max_prompt_tokens) {
        __android_log_print(
            ANDROID_LOG_WARN,
            LOG_TAG,
            "Prompt demasiado largo (%d tok) para n_ctx=%d; truncando a %d",
            static_cast<int>(tokens.size()),
            n_ctx,
            max_prompt_tokens
        );
        tokens.resize(static_cast<size_t>(max_prompt_tokens));
    }

    const int32_t n_batch = static_cast<int32_t>(llama_n_batch(session->ctx));
    if (!decode_prompt_chunks(session->ctx, tokens, n_batch, &session->cancel)) {
        __android_log_print(ANDROID_LOG_WARN, LOG_TAG, "Prompt truncado o cancelado");
        return;
    }

    llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
    llama_sampler *smpl = llama_sampler_chain_init(sparams);
    llama_sampler_chain_add(smpl, llama_sampler_init_top_p(0.9f, 1));
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(0.7f));
    llama_sampler_chain_add(
        smpl,
        llama_sampler_init_dist(static_cast<uint32_t>(llama_time_us() & 0xffffffffu))
    );

    const uint32_t n_ctx_u32 = llama_n_ctx(session->ctx);
    const int max_gen =
        static_cast<int>(n_ctx_u32) - static_cast<int>(tokens.size()) - 32;
    const int gen_budget = std::max(0, std::min(max_gen, 2048));

    using clock = std::chrono::steady_clock;
    const clock::time_point t0 = clock::now();
    int n_gen = 0;

    for (int step = 0; step < gen_budget; ++step) {
        if (session->cancel.load(std::memory_order_relaxed)) {
            break;
        }

        llama_token next = llama_sampler_sample(smpl, session->ctx, -1);
        if (llama_vocab_is_eog(vocab, next)) {
            break;
        }

        llama_sampler_accept(smpl, next);

        int32_t piece_len = llama_token_to_piece(vocab, next, nullptr, 0, 0, false);
        if (piece_len < 0) {
            piece_len = -piece_len;
        }
        std::vector<char> piece(static_cast<size_t>(piece_len) + 4);
        int32_t w = llama_token_to_piece(vocab, next, piece.data(), static_cast<int32_t>(piece.size()), 0, false);
        if (w < 0) {
            w = -w;
            piece.resize(static_cast<size_t>(w) + 4);
            w = llama_token_to_piece(vocab, next, piece.data(), static_cast<int32_t>(piece.size()), 0, false);
        }
        if (w > 0) {
            jstring j_piece = env->NewStringUTF(std::string(piece.data(), static_cast<size_t>(w)).c_str());
            if (j_piece) {
                env->CallVoidMethod(j_callback, on_token, j_piece);
                env->DeleteLocalRef(j_piece);
            }
        }

        llama_batch one = llama_batch_get_one(&next, 1);
        const int dres = llama_decode(session->ctx, one);
        if (dres == 2) {
            break;
        }
        if (dres != 0) {
            __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "llama_decode (gen) rc=%d", dres);
            break;
        }
        n_gen++;
    }

    llama_sampler_free(smpl);

    const double elapsed =
        std::chrono::duration<double>(clock::now() - t0).count();
    if (elapsed > 0.0 && n_gen > 0) {
        __android_log_print(
            ANDROID_LOG_INFO,
            LOG_TAG,
            "generación %.2f tok/s (%d tokens, %.2f s)",
            static_cast<double>(n_gen) / elapsed,
            n_gen,
            elapsed
        );
    }
}
