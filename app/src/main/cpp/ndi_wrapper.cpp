#include <jni.h>
#include <string>
#include <vector>
#include <mutex>
#include <cstring>
#include <android/log.h>
#include <Processing.NDI.Lib.h>

#define LOG_TAG "NDI_Wrapper"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Structure to hold NDI sender and its associated frame buffer
struct NdiSenderContext {
    NDIlib_send_instance_t p_send;
    uint8_t* p_buffer;
    size_t buffer_size;
    std::mutex send_mutex;

    NdiSenderContext() : p_send(nullptr), p_buffer(nullptr), buffer_size(0) {}
};

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_server_NdiNativeSender_nativeInitialize(JNIEnv* env, jobject /* this */) {
    if (!NDIlib_initialize()) {
        LOGE("NDIlib_initialize failed.");
        return JNI_FALSE;
    }
    LOGI("NDIlib initialized successfully.");
    return JNI_TRUE;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_server_NdiNativeSender_nativeCreateSender(JNIEnv* env, jobject /* this */, jstring name) {
    const char* native_name = env->GetStringUTFChars(name, nullptr);

    NDIlib_send_create_t create_settings;
    create_settings.p_ndi_name = native_name;
    create_settings.p_groups = nullptr;
    create_settings.clock_video = true;
    create_settings.clock_audio = false;

    NDIlib_send_instance_t p_send = NDIlib_send_create(&create_settings);
    env->ReleaseStringUTFChars(name, native_name);

    if (!p_send) {
        LOGE("NDIlib_send_create failed.");
        return 0;
    }

    NdiSenderContext* context = new NdiSenderContext();
    context->p_send = p_send;

    LOGI("NDI sender created and wrapped in context.");
    return reinterpret_cast<jlong>(context);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_server_NdiNativeSender_nativeDestroySender(JNIEnv* env, jobject /* this */, jlong ptr) {
    if (ptr) {
        NdiSenderContext* context = reinterpret_cast<NdiSenderContext*>(ptr);
        if (context->p_send) {
            NDIlib_send_destroy(context->p_send);
        }
        if (context->p_buffer) {
            free(context->p_buffer);
        }
        delete context;
        LOGI("NDI sender context destroyed.");
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_server_NdiNativeSender_nativeSendVideo(JNIEnv* env, jobject /* this */, jlong ptr, jintArray pixels, jint width, jint height) {
    if (!ptr || !pixels) return JNI_FALSE;

    NdiSenderContext* context = reinterpret_cast<NdiSenderContext*>(ptr);

    jsize len = env->GetArrayLength(pixels);
    if (len < width * height) {
        LOGE("Pixel array too small: %d < %d", len, width * height);
        return JNI_FALSE;
    }

    jint* p_pixels = env->GetIntArrayElements(pixels, nullptr);
    if (!p_pixels) return JNI_FALSE;

    {
        // Use a per-sender mutex to prevent concurrent buffer writes
        std::lock_guard<std::mutex> lock(context->send_mutex);

        size_t required_size = width * height * 4;
        if (!context->p_buffer || context->buffer_size < required_size) {
            if (context->p_buffer) free(context->p_buffer);
            context->p_buffer = (uint8_t*)malloc(required_size);
            context->buffer_size = required_size;
        }

        // On Android (little-endian), IntArray pixels (ARGB_8888) are already in BGRA byte order in memory.
        // NDIlib_FourCC_type_BGRA expects Blue, Green, Red, Alpha bytes.
        // Since we need to keep the buffer valid until the NEXT frame, we must copy it.
        memcpy(context->p_buffer, p_pixels, required_size);

        NDIlib_video_frame_v2_t video_frame;
        video_frame.xres = width;
        video_frame.yres = height;
        video_frame.FourCC = NDIlib_FourCC_type_BGRA;
        video_frame.frame_rate_N = 30000;
        video_frame.frame_rate_D = 1001;
        video_frame.picture_aspect_ratio = (float)width / (float)height;
        video_frame.frame_format_type = NDIlib_frame_format_type_progressive;
        video_frame.timecode = NDIlib_send_timecode_synthesize;
        video_frame.p_data = context->p_buffer;
        video_frame.line_stride_in_bytes = width * 4;

        NDIlib_send_send_video_v2(context->p_send, &video_frame);
    }

    env->ReleaseIntArrayElements(pixels, p_pixels, JNI_ABORT);
    return JNI_TRUE;
}
