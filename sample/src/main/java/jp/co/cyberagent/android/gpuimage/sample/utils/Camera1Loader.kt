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

    override fun onResume(width: Int, height: Int) {
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

    companion object {
        private const val TAG = "Camera1Loader"
    }
}