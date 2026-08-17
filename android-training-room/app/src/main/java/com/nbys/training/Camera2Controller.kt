package com.nbys.training

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Range
import android.util.Size
import android.view.Surface
import android.view.TextureView
import kotlin.math.roundToInt

class Camera2Controller(
    context: Context,
    private val texture: TextureView,
    private val onFrame: (Image, Rect, Int, Float) -> Unit,
    private val onStatus: (String) -> Unit
) {
    private val manager=context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val thread=HandlerThread("camera2-analysis").apply { start() }
    private val handler=Handler(thread.looper)
    private var device:CameraDevice?=null; private var session:CameraCaptureSession?=null; private var reader:ImageReader?=null; private var previewSurface:Surface?=null
    private var cameraId=""; private var characteristics:CameraCharacteristics?=null; private var requestedFps=60
    private var sensorRect:Rect?=null; private var zoom=1f; private var frameCount=0; private var frameWindowAt=0L; private var measuredFps=0f
    private var trainingExposure=false

    @SuppressLint("MissingPermission")
    fun start() {
        cameraId=manager.cameraIdList.first { manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING)==CameraCharacteristics.LENS_FACING_BACK }
        characteristics=manager.getCameraCharacteristics(cameraId); sensorRect=characteristics?.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
        requestedFps=if(supportsYuvFps(120)) 120 else 60
        if(!texture.isAvailable) texture.surfaceTextureListener=object:TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface:SurfaceTexture,w:Int,h:Int){ open() }
            override fun onSurfaceTextureSizeChanged(surface:SurfaceTexture,w:Int,h:Int){}
            override fun onSurfaceTextureDestroyed(surface:SurfaceTexture)=true
            override fun onSurfaceTextureUpdated(surface:SurfaceTexture){}
        } else open()
    }

    private fun supportsYuvFps(fps:Int):Boolean {
        val c=characteristics ?: return false
        val ranges=c.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES) ?: emptyArray()
        val map=c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: return false
        val duration=map.getOutputMinFrameDuration(ImageFormat.YUV_420_888,Size(1280,720))
        return ranges.any { it.lower<=fps && it.upper>=fps } && duration in 1..(1_000_000_000L/fps)
    }

    @SuppressLint("MissingPermission") private fun open(){ manager.openCamera(cameraId,object:CameraDevice.StateCallback(){
        override fun onOpened(value:CameraDevice){device=value;createSession(requestedFps)}
        override fun onDisconnected(value:CameraDevice){value.close()}
        override fun onError(value:CameraDevice,error:Int){value.close();onStatus("Camera2 打开失败：$error")}
    },handler)}

    private fun createSession(fps:Int) {
        session?.close(); reader?.close(); requestedFps=fps; frameCount=0; frameWindowAt=System.nanoTime()
        val st=texture.surfaceTexture ?: return; st.setDefaultBufferSize(1280,720)
        previewSurface?.release(); val preview=Surface(st); previewSurface=preview
        reader=ImageReader.newInstance(1280,720,ImageFormat.YUV_420_888,4).also { source ->
            source.setOnImageAvailableListener({ r ->
                val image=r.acquireLatestImage() ?: return@setOnImageAvailableListener
                frameCount++; val now=System.nanoTime(); val elapsed=(now-frameWindowAt)/1_000_000_000f
                if(elapsed>=2f){ measuredFps=frameCount/elapsed; frameCount=0; frameWindowAt=now
                    if(requestedFps==120 && measuredFps<90f){ image.close(); onStatus("120fps 分析流不可用，自动降为 60fps"); handler.post { createSession(60) }; return@setOnImageAvailableListener }
                    onStatus("Camera2 请求 ${requestedFps}fps · 实际 ${measuredFps.roundToInt()}fps")
                }
                onFrame(image,Rect(0,0,image.width,image.height),90,measuredFps)
            },handler)
        }
        val outputs=listOf(preview,reader!!.surface)
        device?.createCaptureSession(outputs,object:CameraCaptureSession.StateCallback(){
            override fun onConfigured(value:CameraCaptureSession){ session=value; updateRepeatingRequest() }
            override fun onConfigureFailed(value:CameraCaptureSession){ if(fps==120) createSession(60) else onStatus("Camera2 采集配置失败") }
        },handler)
    }

    fun setZoom(value:Float):Float { val max=(characteristics?.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1f); zoom=value.coerceIn(1f,max); updateRepeatingRequest(); return zoom }
    fun setTrainingExposure(enabled:Boolean) { if(trainingExposure==enabled) return; trainingExposure=enabled; updateRepeatingRequest() }
    private fun updateRepeatingRequest() {
        val current=session ?: return; val preview=previewSurface ?: return; val source=reader?.surface ?: return; val camera=device ?: return
        val request=camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
            addTarget(preview); addTarget(source)
            set(CaptureRequest.CONTROL_AF_MODE,CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
            set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,Range(requestedFps,requestedFps))
            applyZoom(this); applyExposure(this)
        }.build()
        current.setRepeatingRequest(request,null,handler)
    }
    private fun applyExposure(builder:CaptureRequest.Builder) {
        if(!trainingExposure) { builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION,0); return }
        val c=characteristics ?: return
        val range=c.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE) ?: return
        val step=c.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP)?.toFloat() ?: return
        if(step<=0f || range.lower==range.upper) return
        val compensation=(-2f/step).roundToInt().coerceIn(range.lower,range.upper)
        builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION,compensation)
        onStatus("训练曝光 ${String.format("%.1f",compensation*step)} EV")
    }
    private fun applyZoom(builder:CaptureRequest.Builder){ val sensor=sensorRect ?: return; val ratio=zoom; val w=(sensor.width()/ratio).toInt();val h=(sensor.height()/ratio).toInt();val left=sensor.centerX()-w/2;val top=sensor.centerY()-h/2;builder.set(CaptureRequest.SCALER_CROP_REGION,Rect(left,top,left+w,top+h)) }
    fun close(){session?.close();device?.close();reader?.close();previewSurface?.release();thread.quitSafely()}
}
