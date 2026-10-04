package com.arabicchristianmedia.server

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * v1.7: decodes a looping local video offscreen and serves its frames as the
 * NDI background layer.
 *
 * Pipeline: MediaPlayer -> SurfaceTexture -> EGL pbuffer -> glReadPixels into
 * a reused ARGB_8888 Bitmap. All GL and player work happens on a dedicated
 * decoder thread; the NDI render thread only draws the latest completed frame.
 *
 * Frame cadence: [requestFrame] is called on the feed's motion tick (up to
 * 30fps). When motion is off, no new frames are requested and the last frame
 * stays frozen — the "Motion OFF freezes video" behavior falls out naturally.
 * Decodes at a moderate fixed resolution (default 960x540); the NDI canvas
 * upscales with filtering, which is plenty for a background layer and keeps
 * CPU/battery sane.
 *
 * Fully defensive: if EGL or the codec fails, [drawOnto] simply draws nothing
 * and the template falls back to its normal background.
 */
class VideoBackgroundRenderer(
    private val decodeWidth: Int = 960,
    private val decodeHeight: Int = 540
) {
    companion object {
        private const val TAG = "VideoBackground"
    }

    private val thread = HandlerThread("VideoBgDecoder").apply { start() }
    private val handler = Handler(thread.looper)
    private val lock = Any()

    @Volatile private var alpha: Float = 1f

    // Decoder-thread state only (except where noted):
    private var eglDisplay: EGLDisplay? = null
    private var eglContext: EGLContext? = null
    private var eglSurface: EGLSurface? = null
    private var surfaceTexture: SurfaceTexture? = null
    private var player: MediaPlayer? = null
    private var currentFile: File? = null
    private var frameAvailable = false
    private var glReady = false
    private var program = 0
    private var aPositionLoc = 0
    private var aTexCoordLoc = 0
    private var uSTMatrixLoc = 0
    private var textureId = 0
    private var pixelBuffer: ByteBuffer? = null

    /** Latest decoded frame. Written on the decoder thread, drawn on the NDI thread. */
    private var frameBitmap: Bitmap? = null

    private val drawPaint = Paint().apply { isFilterBitmap = true }

    init {
        handler.post { initGl() }
    }

    fun setAlpha(value: Float) {
        alpha = value.coerceIn(0f, 1f)
    }

    /**
     * v1.8: Set muted state. When muted, volume is 0; otherwise full volume.
     */
    fun setMuted(muted: Boolean) {
        handler.post {
            try {
                val vol = if (muted) 0f else 1f
                player?.setVolume(vol, vol)
            } catch (e: Exception) {
                // Player not ready; ignore.
            }
        }
    }

    /**
     * Points the renderer at a video file. No-op if unchanged. Pauses
     * playback when null.
     */
    fun setSource(file: File?) {
        handler.post {
            if (file == null) {
                pausePlayer()
                currentFile = null
                return@post
            }
            if (file.absolutePath == currentFile?.absolutePath && player != null) {
                resumePlayer()
                return@post
            }
            currentFile = file
            startPlayer(file)
        }
    }

    /** Pauses decoding (keeps the last frame for the frozen-video state). */
    fun pause() {
        handler.post { pausePlayer() }
    }

    /**
     * Decodes one pending frame, if the codec has produced one. Called on the
     * feed's motion tick; cheap no-op when no new frame arrived.
     */
    fun requestFrame() {
        handler.post { renderGlFrame() }
    }

    /** Draws the latest frame into [dst] with the configured alpha. Thread-safe. */
    fun drawOnto(canvas: Canvas, dst: Rect) {
        val a = (alpha.coerceIn(0f, 1f) * 255).toInt()
        if (a <= 0) return
        synchronized(lock) {
            val bmp = frameBitmap
            if (bmp == null || bmp.isRecycled) return
            drawPaint.alpha = a
            canvas.drawBitmap(bmp, null, dst, drawPaint)
        }
    }

    /** True once at least one frame has been decoded. */
    fun hasFrame(): Boolean = synchronized(lock) {
        frameBitmap?.let { !it.isRecycled } == true
    }

    fun release() {
        handler.post {
            try { player?.stop() } catch (e: Exception) {}
            try { player?.release() } catch (e: Exception) {}
            player = null
            try { surfaceTexture?.release() } catch (e: Exception) {}
            surfaceTexture = null
            eglDisplay?.let { display ->
                EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                eglSurface?.let { EGL14.eglDestroySurface(display, it) }
                eglContext?.let { EGL14.eglDestroyContext(display, it) }
                EGL14.eglTerminate(display)
            }
            eglDisplay = null
            eglContext = null
            eglSurface = null
            glReady = false
            thread.quitSafely()
        }
        synchronized(lock) {
            try { frameBitmap?.recycle() } catch (e: Exception) {}
            frameBitmap = null
            pixelBuffer = null
        }
    }

    // ---- Decoder thread internals ----

    private fun startPlayer(file: File) {
        stopPlayer()
        if (!glReady || surfaceTexture == null) {
            Log.w(TAG, "GL not ready; cannot play ${file.name}")
            return
        }
        try {
            val surface = Surface(surfaceTexture)
            player = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setSurface(surface)
                isLooping = true
                setOnPreparedListener { it.start() }
                setOnErrorListener { _, what, extra ->
                    Log.w(TAG, "Player error what=$what extra=$extra file=${file.name}")
                    true
                }
                prepareAsync()
            }
            surface.release() // MediaPlayer holds its own reference
            Log.i(TAG, "Playing ${file.name}")
        } catch (e: Exception) {
            Log.w(TAG, "Cannot play video ${file.name}: ${e.message}")
            player = null
        }
    }

    private fun pausePlayer() {
        try {
            player?.let { if (it.isPlaying) it.pause() }
        } catch (e: Exception) {}
    }

    private fun resumePlayer() {
        try {
            player?.let { if (!it.isPlaying) it.start() }
        } catch (e: Exception) {}
    }

    private fun stopPlayer() {
        try { player?.stop() } catch (e: Exception) {}
        try { player?.release() } catch (e: Exception) {}
        player = null
    }

    private fun renderGlFrame() {
        if (!glReady || !frameAvailable) return
        frameAvailable = false
        val st = surfaceTexture ?: return
        try {
            st.updateTexImage()
        } catch (e: Exception) {
            return
        }
        val transform = FloatArray(16)
        st.getTransformMatrix(transform)

        GLES20.glViewport(0, 0, decodeWidth, decodeHeight)
        GLES20.glClearColor(0f, 0f, 0f, 0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUseProgram(program)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "sTexture"), 0)
        GLES20.glUniformMatrix4fv(uSTMatrixLoc, 1, false, transform, 0)

        GLES20.glEnableVertexAttribArray(aPositionLoc)
        GLES20.glVertexAttribPointer(aPositionLoc, 2, GLES20.GL_FLOAT, false, 0, quadBuffer())
        GLES20.glEnableVertexAttribArray(aTexCoordLoc)
        GLES20.glVertexAttribPointer(aTexCoordLoc, 2, GLES20.GL_FLOAT, false, 0, texBuffer())
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(aPositionLoc)
        GLES20.glDisableVertexAttribArray(aTexCoordLoc)

        var buf = pixelBuffer
        if (buf == null) {
            buf = ByteBuffer.allocateDirect(decodeWidth * decodeHeight * 4).order(ByteOrder.nativeOrder())
            pixelBuffer = buf
        }
        buf.rewind()
        GLES20.glReadPixels(0, 0, decodeWidth, decodeHeight, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf)
        buf.rewind()
        synchronized(lock) {
            var bmp = frameBitmap
            if (bmp == null || bmp.isRecycled || bmp.width != decodeWidth || bmp.height != decodeHeight) {
                try { bmp?.recycle() } catch (e: Exception) {}
                bmp = Bitmap.createBitmap(decodeWidth, decodeHeight, Bitmap.Config.ARGB_8888)
                frameBitmap = bmp
            }
            // GL_RGBA bytes match ARGB_8888 memory layout on little-endian.
            bmp.copyPixelsFromBuffer(buf)
        }
    }

    private var quadBuf: FloatBuffer? = null
    private var texBuf: FloatBuffer? = null
    private fun quadBuffer(): FloatBuffer {
        var b = quadBuf
        if (b == null) {
            b = ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
            b.put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
            b.position(0)
            quadBuf = b
        }
        b.position(0)
        return b
    }
    private fun texBuffer(): FloatBuffer {
        var b = texBuf
        if (b == null) {
            b = ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
            b.put(floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f))
            b.position(0)
            texBuf = b
        }
        b.position(0)
        return b
    }

    private fun initGl() {
        try {
            val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (display == EGL14.EGL_NO_DISPLAY) return
            val version = IntArray(2)
            if (!EGL14.eglInitialize(display, version, 0, version, 1)) return
            val attribList = intArrayOf(
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            if (!EGL14.eglChooseConfig(display, attribList, 0, configs, 0, 1, numConfigs, 0)) return
            val ctxAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
            val context = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT, ctxAttribs, 0)
            if (context == EGL14.EGL_NO_CONTEXT) return
            val surfAttribs = intArrayOf(EGL14.EGL_WIDTH, decodeWidth, EGL14.EGL_HEIGHT, decodeHeight, EGL14.EGL_NONE)
            val surface = EGL14.eglCreatePbufferSurface(display, configs[0], surfAttribs, 0)
            if (surface == EGL14.EGL_NO_SURFACE) return
            if (!EGL14.eglMakeCurrent(display, surface, surface, context)) return

            val textures = IntArray(1)
            GLES20.glGenTextures(1, textures, 0)
            textureId = textures[0]
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

            program = buildProgram()
            if (program == 0) return
            aPositionLoc = GLES20.glGetAttribLocation(program, "aPosition")
            aTexCoordLoc = GLES20.glGetAttribLocation(program, "aTexCoord")
            uSTMatrixLoc = GLES20.glGetUniformLocation(program, "uSTMatrix")

            surfaceTexture = SurfaceTexture(textureId).apply {
                setDefaultBufferSize(decodeWidth, decodeHeight)
                setOnFrameAvailableListener({ frameAvailable = true }, handler)
            }

            eglDisplay = display
            eglContext = context
            eglSurface = surface
            glReady = true
            Log.i(TAG, "GL decoder ready (${decodeWidth}x$decodeHeight)")
        } catch (e: Exception) {
            Log.w(TAG, "GL init failed: ${e.message}")
        }
    }

    private fun buildProgram(): Int {
        // Note: gl_Position.y is negated so the rendered image is pre-flipped
        // vertically — glReadPixels returns bottom-up rows, and this cancels
        // that flip so the Bitmap lands top-down correct.
        val vertexSrc = """
            attribute vec4 aPosition;
            attribute vec2 aTexCoord;
            uniform mat4 uSTMatrix;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = vec4(aPosition.x, -aPosition.y, 0.0, 1.0);
                vTexCoord = (uSTMatrix * vec4(aTexCoord, 0.0, 1.0)).xy;
            }
        """.trimIndent()
        val fragmentSrc = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 vTexCoord;
            uniform samplerExternalOES sTexture;
            void main() {
                gl_FragColor = texture2D(sTexture, vTexCoord);
            }
        """.trimIndent()
        val vs = compileShader(GLES20.GL_VERTEX_SHADER, vertexSrc)
        val fs = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSrc)
        if (vs == 0 || fs == 0) return 0
        val prog = GLES20.glCreateProgram()
        GLES20.glAttachShader(prog, vs)
        GLES20.glAttachShader(prog, fs)
        GLES20.glLinkProgram(prog)
        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(prog, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            Log.w(TAG, "GL link failed: ${GLES20.glGetProgramInfoLog(prog)}")
            GLES20.glDeleteProgram(prog)
            return 0
        }
        return prog
    }

    private fun compileShader(type: Int, src: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, src)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            Log.w(TAG, "GL compile failed: ${GLES20.glGetShaderInfoLog(shader)}")
            GLES20.glDeleteShader(shader)
            return 0
        }
        return shader
    }
}
