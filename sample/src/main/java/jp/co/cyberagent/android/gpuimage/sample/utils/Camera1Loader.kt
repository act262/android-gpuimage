@file:Suppress("DEPRECATION")

package jp.co.cyberagent.android.gpuimage.sample.utils

import android.app.Activity
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.opengl.GLES20
import android.util.Log
import android.view.Surface
import jp.co.cyberagent.android.gpuimage.sample.activity.CameraActivity

class Camera1Loader(private val activity: Activity) : CameraLoader() {

    private var cameraInstance: Camera? = null
    private var cameraFacing: Int = Camera.CameraInfo.CAMERA_FACING_BACK

    private var viewWidth: Int = 0
    private var viewHeight: Int = 0

    override fun onResume(width: Int, height: Int) {
        viewWidth = width
        viewHeight = height
        setUpCamera()
    }

    override fun onPause() {
        releaseCamera()
    }

    override fun switchCamera() {
        cameraFacing = when (cameraFacing) {
            Camera.CameraInfo.CAMERA_FACING_FRONT -> Camera.CameraInfo.CAMERA_FACING_BACK
            Camera.CameraInfo.CAMERA_FACING_BACK -> Camera.CameraInfo.CAMERA_FACING_FRONT
            else -> return
        }
        releaseCamera()
        setUpCamera()
    }

    override fun getCameraOrientation(): Int {
        val degrees = when (activity.windowManager.defaultDisplay.rotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
        val cameraInfo = Camera.CameraInfo()
        for (id in 0 until Camera.getNumberOfCameras()) {
            Camera.getCameraInfo(id, cameraInfo)
            if (cameraInfo.facing == cameraFacing) {
                break
            }
        }
        val orientation = cameraInfo.orientation
        return if (cameraFacing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            (360 - (orientation + degrees) % 360) % 360
        } else { // back-facing
            (orientation - degrees + 360) % 360
        }
    }

    override fun hasMultipleCamera(): Boolean {
        return Camera.getNumberOfCameras() > 1
    }

    private fun setUpCamera() {
        val id = getCurrentCameraId()
        try {
            cameraInstance = getCameraInstance(id)
        } catch (e: IllegalAccessError) {
            Log.e(TAG, "Camera not found")
            return
        }
        val parameters = cameraInstance!!.parameters

        if (parameters.supportedFocusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
            parameters.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE
        }
        val size = chooseOptimalSize()
        parameters.setPreviewSize(size.width, size.height)
        cameraInstance!!.parameters = parameters

        cameraInstance!!.setPreviewCallback { data, camera ->
            if (data == null || camera == null) {
                return@setPreviewCallback
            }
            val size = camera.parameters.previewSize
            onPreviewFrame?.invoke(data, size.width, size.height)
        }

        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        val surfaceTexture = SurfaceTexture(textures[0])
        cameraInstance!!.setPreviewTexture(surfaceTexture)
        (activity as CameraActivity).currentGPUImageView?.setupSurfaceTexture(surfaceTexture)
        cameraInstance!!.startPreview()
    }

    private fun getCurrentCameraId(): Int {
        val cameraInfo = Camera.CameraInfo()
        for (id in 0 until Camera.getNumberOfCameras()) {
            Camera.getCameraInfo(id, cameraInfo)
            if (cameraInfo.facing == cameraFacing) {
                return id
            }
        }
        return 0
    }

    override fun isFrontCamera(): Boolean {
        return cameraFacing == Camera.CameraInfo.CAMERA_FACING_FRONT
    }

    private fun getCameraInstance(id: Int): Camera {
        return try {
            Camera.open(id)
        } catch (e: Exception) {
            throw IllegalAccessError("Camera not found")
        }
    }

    private fun releaseCamera() {
        cameraInstance!!.setPreviewCallback(null)
        cameraInstance!!.release()
        cameraInstance = null
    }

    private fun chooseOptimalSize(): Camera.Size {
        if (viewWidth == 0 || viewHeight == 0) {
            return cameraInstance!!.Size(0, 0)
        }
        val outputSizes = cameraInstance?.parameters?.supportedPreviewSizes as List<Camera.Size>

        val orientation = getCameraOrientation()
        val maxPreviewWidth = if (orientation == 90 or 270) viewHeight else viewWidth
        val maxPreviewHeight = if (orientation == 90 or 270) viewWidth else viewHeight

        return outputSizes.filter {
            it.width < maxPreviewWidth / 2 && it.height < maxPreviewHeight / 2
        }.maxBy {
            it.width * it.height
        } ?: cameraInstance!!.Size(Camera1Loader.PREVIEW_WIDTH, Camera1Loader.PREVIEW_HEIGHT)
    }

    companion object {
        private const val TAG = "Camera1Loader"
        private const val PREVIEW_WIDTH = 480
        private const val PREVIEW_HEIGHT = 640
    }
}