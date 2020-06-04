/*
 * Copyright (C) 2018 CyberAgent, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jp.co.cyberagent.android.gpuimage.sample.activity

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import jp.co.cyberagent.android.gpuimage.GPUImageView
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilter
import jp.co.cyberagent.android.gpuimage.sample.GPUImageFilterTools
import jp.co.cyberagent.android.gpuimage.sample.GPUImageFilterTools.FilterAdjuster
import jp.co.cyberagent.android.gpuimage.sample.R
import jp.co.cyberagent.android.gpuimage.sample.utils.Camera1Loader
import jp.co.cyberagent.android.gpuimage.sample.utils.Camera2Loader
import jp.co.cyberagent.android.gpuimage.sample.utils.CameraLoader
import jp.co.cyberagent.android.gpuimage.sample.utils.doOnLayout
import jp.co.cyberagent.android.gpuimage.util.Rotation

class CameraActivity : AppCompatActivity() {

    private val gpuImageView: GPUImageView by lazy { findViewById<GPUImageView>(R.id.surfaceView) }
    private val smallGPUImageView: GPUImageView by lazy { findViewById<GPUImageView>(R.id.surfaceView_small) }

    private var currentGPUImageView: GPUImageView? = null
    private var smallView: Boolean = true

    private val seekBar: SeekBar by lazy { findViewById<SeekBar>(R.id.seekBar) }
    private val cameraLoader: CameraLoader by lazy {
        if (Build.VERSION.SDK_INT < 21) {
            Camera1Loader(this)
        } else {
            Camera2Loader(this)
        }
    }
    private var filterAdjuster: FilterAdjuster? = null

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        println("onCreate")
        setContentView(R.layout.activity_camera)

        seekBar.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                filterAdjuster?.adjust(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
        findViewById<View>(R.id.button_choose_filter).setOnClickListener {
            GPUImageFilterTools.showDialog(this) { filter -> switchFilterTo(filter) }
        }
        findViewById<View>(R.id.button_capture).setOnClickListener {
            saveSnapshot()
        }
        findViewById<View>(R.id.img_switch_orientation).setOnClickListener(View.OnClickListener {
            setOrientation()
        })

        findViewById<View>(R.id.img_switch_camera).run {
            if (!cameraLoader.hasMultipleCamera()) {
                visibility = View.GONE
            }
            setOnClickListener {
                cameraLoader.switchCamera()
                setRenderRotation()
            }
        }

        cameraLoader.setOnPreviewFrameListener { data, width, height ->
            currentGPUImageView?.updatePreviewFrame(data, width, height)
        }

//        gpuImageView.setRotation(getRotation(cameraLoader.getCameraOrientation()))
        gpuImageView.setRenderMode(GPUImageView.RENDERMODE_CONTINUOUSLY)

//        smallGPUImageView.setRotation(getRotation(cameraLoader.getCameraOrientation()))
        smallGPUImageView.setRenderMode(GPUImageView.RENDERMODE_CONTINUOUSLY)

        smallGPUImageView.setOnClickListener {
            switchView()
        }

        gpuImageView.setOnClickListener {
            switchView()
        }

        switchView()
    }


    fun switchView() {
        var filter = currentGPUImageView?.filter

        if (smallView) {
            smallView = false
            currentGPUImageView = gpuImageView
            gpuImageView.visibility = View.VISIBLE
            smallGPUImageView.visibility = View.INVISIBLE
        } else {
            smallView = true
            currentGPUImageView = smallGPUImageView
            smallGPUImageView.visibility = View.VISIBLE
            gpuImageView.visibility = View.INVISIBLE
        }
        if (filter != null) {
            currentGPUImageView?.filter = filter
        }

        setRenderRotation()
    }

    private fun setRenderRotation() {
        currentGPUImageView?.setRotation(getRotation(cameraLoader.getCameraOrientation()), cameraLoader.isFrontCamera(), false)
    }

    override fun onResume() {
        super.onResume()
        currentGPUImageView?.doOnLayout {
            cameraLoader.onResume(it.width, it.height)
        }
    }

    override fun onPause() {
        cameraLoader.onPause()
        super.onPause()
    }

    private fun saveSnapshot() {
        val folderName = "GPUImage"
        val fileName = System.currentTimeMillis().toString() + ".jpg"
        currentGPUImageView?.saveToPictures(folderName, fileName) {
            Toast.makeText(this, "$folderName/$fileName saved", Toast.LENGTH_SHORT).show()
        }
    }

    @SuppressLint("SourceLockedOrientationActivity")
    private fun setOrientation() {
        when (resources.configuration.orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
            Configuration.ORIENTATION_PORTRAIT -> {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
            else -> {
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        println("onConfigurationChanged $newConfig")
        setRenderRotation()
    }

    private fun getRotation(orientation: Int): Rotation {
        return when (orientation) {
            90 -> Rotation.ROTATION_90
            180 -> Rotation.ROTATION_180
            270 -> Rotation.ROTATION_270
            else -> Rotation.NORMAL
        }
    }

    private fun switchFilterTo(filter: GPUImageFilter) {
        if (currentGPUImageView?.filter == null || currentGPUImageView?.filter!!.javaClass != filter.javaClass) {
            currentGPUImageView?.filter = filter
            filterAdjuster = FilterAdjuster(filter)
            filterAdjuster?.adjust(seekBar.progress)
        }
    }
}
