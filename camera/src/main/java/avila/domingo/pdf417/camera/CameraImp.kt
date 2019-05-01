@file:Suppress("DEPRECATION")

package avila.domingo.pdf417.camera

import android.graphics.Point
import android.hardware.Camera
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import avila.domingo.pdf417.domain.ICamera
import avila.domingo.pdf417.domain.model.Image
import io.reactivex.Observable
import java.lang.Math.abs
import java.util.*

class CameraImp(
    private val windowManager: WindowManager,
    private val imagePeriodMs: Long,
    surfaceView: SurfaceView
) : ICamera {

    private var rxImage: (ByteArray, Int) -> Unit = { _, _ -> }

    private var camera: Camera? = null
    private val cameraInfo = Camera.CameraInfo()

    private val cameraTimer = Timer()
    private val cameraTimerTask = object : TimerTask() {
        override fun run() {
            camera?.run {
                autoFocus { b, camera ->
                    if (b) camera.setOneShotPreviewCallback { data, _ ->
                        rxImage.invoke(data, rotationDegrees())
                    }
                }
            }
        }
    }

    private val screenSize = screenSize()

    init {
        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                try {
                    camera = Camera.open(0)
                    camera?.setPreviewDisplay(holder)
                } catch (e: Exception) {
                }
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                try {
                    camera?.run {
                        val customParameters = parameters
                        customParameters.supportedFocusModes.run {
                            when {
                                this.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE) -> customParameters.focusMode =
                                    Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE
                                this.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO) -> customParameters.focusMode =
                                    Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO
                                this.contains(Camera.Parameters.FOCUS_MODE_AUTO) -> customParameters.focusMode =
                                    Camera.Parameters.FOCUS_MODE_AUTO
                            }
                        }

                        val screenRatio = screenSize.witdh / screenSize.height.toFloat()

                        var diff = Float.MAX_VALUE
                        var previewWidth = 0
                        var previewHeight = 0

                        customParameters.supportedPreviewSizes
                            .sortedByDescending { it.width }
                            .apply {
                                this.forEach {
                                    val previewDiff = abs((it.width / it.height.toFloat()) - screenRatio)
                                    if (previewDiff < diff) {
                                        diff = previewDiff
                                        previewWidth = it.width
                                        previewHeight = it.height
                                    }
                                }
                            }
                            .filter { screenRatio == (it.width / it.height.toFloat()) }
                            .run {
                                if (size > 0) {
                                    get(0).run { customParameters.setPreviewSize(width, height) }
                                } else {
                                    customParameters.setPreviewSize(previewWidth, previewHeight)
                                }
                            }

                        diff = Float.MAX_VALUE

                        customParameters.supportedPictureSizes
                            .sortedByDescending { it.width }
                            .apply {
                                this.forEach {
                                    val previewDiff = abs((it.width / it.height.toFloat()) - screenRatio)
                                    if (previewDiff < diff) {
                                        diff = previewDiff
                                        previewWidth = it.width
                                        previewHeight = it.height
                                    }
                                }
                            }
                            .filter { screenRatio == (it.width / it.height.toFloat()) }
                            .run {
                                if (size > 0) {
                                    get(0).run { customParameters.setPictureSize(width, height) }
                                } else {
                                    customParameters.setPictureSize(previewWidth, previewHeight)
                                }
                            }

                        parameters = customParameters
                        setDisplayOrientation(rotationDegrees())
                        startPreview()
                        cameraTimer.schedule(cameraTimerTask, 100, imagePeriodMs)
                    }
                } catch (e: Exception) {
                }
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                cameraTimer.cancel()
                camera?.stopPreview()
                camera?.release()
                camera = null
            }
        })
    }

    private fun screenSize(): Size =
        Point().apply { windowManager.defaultDisplay.getSize(this) }.let { point ->
            if (point.x > point.y) {
                Size(point.x, point.y)
            } else {
                Size(point.y, point.x)
            }
        }


    private fun rotationDegrees(): Int {
        var rotationDegrees = when (windowManager.defaultDisplay.rotation) {
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            Surface.ROTATION_0 -> 0
            else -> 0
        }

        if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
            rotationDegrees = 360 - rotationDegrees
        }

        return (cameraInfo.orientation + rotationDegrees) % 360
    }

    override fun images(): Observable<Image> = Observable.create {
        rxImage = { data, degree ->
            it.onNext(Image(data, degree))
        }
    }

    data class Size(val witdh: Int, val height: Int)
}