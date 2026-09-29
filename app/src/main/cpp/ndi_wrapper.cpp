#include <jni.h>
#include <string>
#include <vector>
#include <mutex>
#include <cstring>
#include <android/log.h>
#include <android/bitmap.h>
#include <Processing.NDI.Lib.h>

#define LOG_TAG "NDI_Wrapper"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Structure to hold NDI sender and its associated frame buffer
struct NdiSenderContext {
    NDIlib_send_instance_t p_send;
    uint8_t* p_buffer;
    size_t buffer_size;
    int fps_n;
    int fps_d;
    std::mutex send_mutex;

    NdiSenderContext() : p_send(nullptr), p_buffer(nullptr), buffer_size(0), fps_n(30), fps_d(1) {}
};

extern "C" JNIEXPORT jboolean JNICALL
Java_com_arabicchristianmedia_server_NdiNativeSender_nativeInitialize(JNIEnv* env, jobject /* this */) {
    if (!NDIlib_initialize()) {
        LOGE("NDIlib_initialize failed.");
        return JNI_FALSE;
    }
    LOGI("NDIlib initialized successfully.");
    return JNI_TRUE;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_arabicchristianmedia_server_NdiNativeSender_nativeCreateSender(JNIEnv* env, jobject /* this */, jstring name, jint fps) {
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
    // User-selected frame rate (from the app's dropdown). Receivers use this as
    // the stream's nominal rate; actual pushes are dirty-frame driven.
    context->fps_n = fps > 0 ? fps : 30;
    context->fps_d = 1;

    LOGI("NDI sender created and wrapped in context (fps=%d).", context->fps_n);
    return reinterpret_cast<jlong>(context);
}

extern "C" JNIEXPORT void JNICALL
Java_com_arabicchristianmedia_server_NdiNativeSender_nativeDestroySender(JNIEnv* env, jobject /* this */, jlong ptr) {
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
Java_com_arabicchristianmedia_server_NdiNativeSender_nativeSendVideoBitmap(JNIEnv* env, jobject /* this */, jlong ptr, jobject bitmap, jint width, jint height) {
    if (!ptr || !bitmap) return JNI_FALSE;

    NdiSenderContext* context = reinterpret_cast<NdiSenderContext*>(ptr);

    // Zero-copy: lock the Bitmap's pixel memory directly instead of round-tripping
    // through a Java IntArray (which cost an extra 8.3 MB copy per 1080p frame).
    AndroidBitmapInfo info;
    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) {
        LOGE("AndroidBitmap_getInfo failed.");
        return JNI_FALSE;
    }
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        LOGE("Unsupported bitmap format: %d (need RGBA_8888).", info.format);
        return JNI_FALSE;
    }
    if ((int)info.width < width || (int)info.height < height) {
        LOGE("Bitmap too small: %ux%u < %dx%d.", info.width, info.height, width, height);
        return JNI_FALSE;
    }

    void* pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0 || !pixels) {
        LOGE("AndroidBitmap_lockPixels failed.");
        return JNI_FALSE;
    }

    jboolean result = JNI_FALSE;
    {
        // Use a per-sender mutex to prevent concurrent buffer writes
        std::lock_guard<std::mutex> lock(context->send_mutex);

        size_t required_size = (size_t)width * (size_t)height * 4;
        if (!context->p_buffer || context->buffer_size < required_size) {
            if (context->p_buffer) free(context->p_buffer);
            context->p_buffer = (uint8_t*)malloc(required_size);
            context->buffer_size = required_size;
        }

        if (context->p_buffer) {
            // On Android (little-endian), ARGB_8888 bitmap memory is already in BGRA
            // byte order, which is exactly what NDIlib_FourCC_type_BGRA expects.
            // The buffer must stay valid until the NEXT frame, hence the copy into
            // the sender-owned buffer (NDI sends asynchronously).
            const size_t row_bytes = (size_t)width * 4;
            if (info.stride == row_bytes) {
                memcpy(context->p_buffer, pixels, required_size);
            } else {
                // Stride can exceed width*4; copy row by row to stay correct.
                const uint8_t* src = static_cast<const uint8_t*>(pixels);
                for (int y = 0; y < height; y++) {
                    memcpy(context->p_buffer + (size_t)y * row_bytes, src + (size_t)y * info.stride, row_bytes);
                }
            }

            NDIlib_video_frame_v2_t video_frame;
            video_frame.xres = width;
            video_frame.yres = height;
            video_frame.FourCC = NDIlib_FourCC_type_BGRA;
            video_frame.frame_rate_N = context->fps_n;
            video_frame.frame_rate_D = context->fps_d;
            video_frame.picture_aspect_ratio = (float)width / (float)height;
            video_frame.frame_format_type = NDIlib_frame_format_type_progressive;
            video_frame.timecode = NDIlib_send_timecode_synthesize;
            video_frame.p_data = context->p_buffer;
            video_frame.line_stride_in_bytes = width * 4;

            NDIlib_send_send_video_v2(context->p_send, &video_frame);
            result = JNI_TRUE;
        }
    }

    AndroidBitmap_unlockPixels(env, bitmap);
    return result;
}
