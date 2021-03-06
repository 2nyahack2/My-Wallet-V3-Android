/*
 * Copyright (C) 2008 ZXing authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package piuk.blockchain.android.ui.scan.camera

import android.content.Context
import android.content.res.Configuration
import android.graphics.Point
import android.graphics.Rect
import android.hardware.Camera
import android.os.Handler
import android.view.Surface
import android.view.SurfaceHolder
import androidx.appcompat.app.AppCompatActivity
import com.google.zxing.PlanarYUVLuminanceSource
import piuk.blockchain.android.ui.scan.camera.open.OpenCameraManager
import timber.log.Timber
import java.io.IOException

/**
 * This object wraps the Camera service object and expects to be the only one talking to it. The
 * implementation encapsulates the steps needed to take preview-sized images, which are used for
 * both preview and decoding.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */
class CameraManager(private val context: Context) {
    private val configManager = CameraConfigurationManager(context)
    private var camera: Camera? = null
    private var autoFocusManager: AutoFocusManager? = null

    /**
     * Calculates the framing rect which the UI should draw to show the user where to place the
     * barcode. This target helps with alignment as well as forces the user to hold the device
     * far enough away to ensure the image will be in focus.
     *
     * @return The rectangle to draw on screen in window coordinates.
     */
    var targetRect: Rect? = null
    var framingViewSize = Point()
    private var framingRectInPreview: Rect? = null
    private var initialized = false
    private var previewing = false

    /**
     * Preview frames are delivered here, which we pass on to the registered handler. Make sure to
     * clear the handler so it will only receive one message.
     */
    private val previewCallback: PreviewCallback

    init {
        previewCallback = PreviewCallback(configManager)
    }

    /**
     * Opens the camera driver and initializes the hardware parameters.
     *
     * @param holder The surface object which the camera will draw preview frames into.
     * @throws IOException Indicates the camera driver failed to open.
     */
    @Synchronized
    @Throws(IOException::class)
    fun openDriver(activity: AppCompatActivity, holder: SurfaceHolder) {
        var theCamera = camera
        if (theCamera == null) {
            theCamera = OpenCameraManager().build().open()
            setCameraDisplayOrientation(activity, 0, theCamera)
            if (theCamera == null) {
                throw IOException()
            }
            camera = theCamera
        }
        theCamera.setPreviewDisplay(holder)
        if (!initialized) {
            initialized = true
            configManager.initFromCameraParameters(theCamera)
        }
        var parameters = theCamera.parameters
        val parametersFlattened = parameters?.flatten() // Save these, temporarily
        try {
            configManager.setDesiredCameraParameters(theCamera, false)
        } catch (re: RuntimeException) {
            // Driver failed
            Timber.w("Camera rejected parameters. Setting only minimal safe-mode parameters")
            Timber.i("Resetting to saved camera params: %s", parametersFlattened)
            // Reset:
            if (parametersFlattened != null) {
                parameters = theCamera.parameters
                parameters.unflatten(parametersFlattened)
                try {
                    theCamera.parameters = parameters
                    configManager.setDesiredCameraParameters(theCamera, true)
                } catch (e: RuntimeException) {
                    // Well, darn. Give up
                    Timber.w("Camera rejected even safe-mode parameters! No configuration")
                }
            }
        }
    }

    @get:Synchronized
    val isOpen: Boolean
        get() = camera != null

    /**
     * Closes the camera driver if still in use.
     */
    @Synchronized
    fun closeDriver() {
        camera?.release()
        camera = null
        // Make sure to clear these each time we close the camera, so that any scanning rect
        // requested by intent is forgotten.
        targetRect = null
        framingRectInPreview = null
    }

    /**
     * Asks the camera hardware to begin drawing preview frames to the screen.
     */
    @Synchronized
    fun startPreview() {
        val theCamera = camera
        if (theCamera != null && !previewing) {
            theCamera.startPreview()
            previewing = true
            autoFocusManager = AutoFocusManager(context, camera)
        }
    }

    /**
     * Tells the camera to stop drawing preview frames.
     */
    @Synchronized
    fun stopPreview() {
        autoFocusManager?.stop()
        autoFocusManager = null

        if (camera != null && previewing) {
            camera!!.stopPreview()
            previewCallback.setHandler(null, 0)
            previewing = false
        }
    }

    @Synchronized
    fun setTorch(newSetting: Boolean) {
        if (camera != null) {
            autoFocusManager?.stop()
            configManager.setTorch(camera, newSetting)
            autoFocusManager?.start()
        }
    }

    /**
     * A single preview frame will be returned to the handler supplied. The data will arrive as byte[]
     * in the message.obj field, with width and height encoded as message.arg1 and message.arg2,
     * respectively.
     *
     * @param handler The handler to send the message to.
     * @param message The what field of the message to be sent.
     */
    @Synchronized
    fun requestPreviewFrame(handler: Handler?, message: Int) {
        val theCamera = camera
        if (theCamera != null && previewing) {
            previewCallback.setHandler(handler, message)
            theCamera.setOneShotPreviewCallback(previewCallback)
        }
    } // Called early, before init even finished

    /**
     * Like [.getTargetRect] but coordinates are in terms of the preview frame,
     * not UI / screen.
     */
    @get:Synchronized
    private val targetRectInPreview: Rect?
        get() {
            if (framingRectInPreview == null) {
                val framingRect = targetRect
                if (framingRect == null || framingRect.isEmpty) {
                    return null
                }
                val rect = Rect(framingRect)
                val cameraResolution = configManager.cameraResolution
                val screenResolution = configManager.screenResolution
                if (cameraResolution == null || screenResolution == null) {
                    // Called early, before init even finished
                    return null
                }
                if (context.resources.configuration.orientation != Configuration.ORIENTATION_PORTRAIT) {
                    rect.left = rect.left * cameraResolution.x / screenResolution.x
                    rect.right = rect.right * cameraResolution.x / screenResolution.x
                    rect.top = rect.top * cameraResolution.y / screenResolution.y
                    rect.bottom = rect.bottom * cameraResolution.y / screenResolution.y
                } else {
                    rect.left = rect.left * cameraResolution.y / framingViewSize.x
                    rect.right = rect.right * cameraResolution.y / framingViewSize.x
                    rect.top = rect.top * cameraResolution.x / framingViewSize.y
                    rect.bottom = rect.bottom * cameraResolution.x / framingViewSize.y
                }
                framingRectInPreview = rect
            }
            return framingRectInPreview
        }

    /**
     * A factory method to build the appropriate LuminanceSource object based on the format
     * of the preview buffers, as described by Camera.Parameters.
     *
     * @param data A preview frame.
     * @param width The width of the image.
     * @param height The height of the image.
     * @return A PlanarYUVLuminanceSource instance.
     */
    fun buildLuminanceSource(
        data: ByteArray?,
        width: Int,
        height: Int
    ): PlanarYUVLuminanceSource? {
        val rect = targetRectInPreview ?: return null
        // Go ahead and assume it's YUV rather than die.
        return PlanarYUVLuminanceSource(
            data, width, height, rect.left, rect.top,
            rect.width(), rect.height(), false
        )
    }

    companion object {
        fun setCameraDisplayOrientation(
            activity: AppCompatActivity,
            cameraId: Int,
            camera: Camera?
        ) {
            val info = Camera.CameraInfo()
            Camera.getCameraInfo(cameraId, info)
            val rotation = activity.windowManager.defaultDisplay
                .rotation
            var degrees = 0
            when (rotation) {
                Surface.ROTATION_0 -> degrees = 0
                Surface.ROTATION_90 -> degrees = 90
                Surface.ROTATION_180 -> degrees = 180
                Surface.ROTATION_270 -> degrees = 270
            }
            var result: Int
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                result = (info.orientation + degrees) % 360
                result = (360 - result) % 360 // compensate the mirror
            } else { // back-facing
                result = (info.orientation - degrees + 360) % 360
            }
            camera!!.setDisplayOrientation(result)
        }
    }
}