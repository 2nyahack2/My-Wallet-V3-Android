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
package piuk.blockchain.android.ui.scan

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Point
import android.hardware.Camera
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Parcelable
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.Surface
import android.view.SurfaceHolder
import android.view.View
import android.view.WindowManager
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import com.google.zxing.BarcodeFormat
import com.google.zxing.DecodeHintType
import com.google.zxing.Result
import com.karumi.dexter.Dexter
import info.blockchain.balance.CryptoCurrency
import io.reactivex.Single
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.subscribeBy
import kotlinx.android.parcel.Parcelize
import kotlinx.android.synthetic.main.activity_scan.*
import piuk.blockchain.android.R
import piuk.blockchain.android.ui.base.BlockchainActivity
import piuk.blockchain.android.ui.scan.camera.CameraManager
import piuk.blockchain.android.util.visibleIf
import piuk.blockchain.android.util.windowRect
import piuk.blockchain.androidcore.utils.helperfunctions.unsafeLazy
import piuk.blockchain.androidcoreui.ui.customviews.ToastCustom
import piuk.blockchain.androidcoreui.ui.customviews.toast
import timber.log.Timber
import java.io.IOException
import java.util.EnumSet
import java.util.concurrent.TimeUnit

/**
 * This activity opens the camera and does the actual scanning on a background thread. It draws a viewfinder to help the
 * user place the barcode correctly, shows feedback as the image processing is happening, and then overlays the results
 * when a scan is successful.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 * @author Sean Owen
 */

sealed class QrExpected : Parcelable {
    @Parcelize
    object AnyAssetAddressQr : QrExpected()
    @Parcelize
    data class AssetAddressQr(val asset: CryptoCurrency) : QrExpected()
    @Parcelize
    object BitPayQr : QrExpected()
    @Parcelize
    object ImportWalletKeysQr : QrExpected() // Import a wallet.
    @Parcelize
    object LegacyPairingQr : QrExpected()
    @Parcelize
    object WebLoginQr : QrExpected() // New auth

    companion object {
        val IMPORT_KEYS_QR = setOf(ImportWalletKeysQr)
        val LEGACY_PAIRING_QR = setOf(LegacyPairingQr)
        val MAIN_ACTIVITY_QR = setOf(AnyAssetAddressQr /*, WebLoginQr */, BitPayQr)

        @Suppress("FunctionName")
        fun ASSET_ADDRESS_QR(asset: CryptoCurrency) = setOf(AssetAddressQr(asset))
    }
}

class QrScanActivity : BlockchainActivity(), SurfaceHolder.Callback {

    val cameraManager: CameraManager by unsafeLazy {
        CameraManager(this)
    }

    var handler: Handler? = null
        private set

    private var hasSurface = false
    private val decodeFormats: Collection<BarcodeFormat>? by unsafeLazy {
        intent?.let { DecodeFormatManager.parseDecodeFormats(it) }
    }

    private val decodeHints: Map<DecodeHintType, *>? by unsafeLazy {
        intent?.let { DecodeHintManager.parseDecodeHints(it) }
    }

    private val characterSet: String? by unsafeLazy {
        intent?.getStringExtra(Intents.Scan.CHARACTER_SET)
    }

    private val inactivityTimer = InactivityTimer()

    private var flashStatus = false
    private val hasFlashLight: Boolean by unsafeLazy {
        packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)
    }

    private val expectedSet: Set<QrExpected>
        get() = intent?.getParcelableArrayExtra(PARAM_EXPECTED_QR)
                    ?.filterIsInstance<QrExpected>()
                    ?.toSet() ?: emptySet()

    override val alwaysDisableScreenshots: Boolean = true

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = currentOrientation

        val window = window
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_scan)

        val toolbar = findViewById<Toolbar>(R.id.toolbar_general)
        setupToolbar(toolbar, R.string.scan_qr)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        supportActionBar!!.setHomeButtonEnabled(true)
    }

    // handle reverse-mounted cameras on devices like the Nexus 5X
    private val currentOrientation: Int
        get() = when (windowManager.defaultDisplay.rotation) {
            Surface.ROTATION_0,
            Surface.ROTATION_270 -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            else -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
        }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    override fun onResume() {
        super.onResume()

        // CameraManager must be initialized here, not in onCreate()
        // because possible bugs with setting view when not yet measured

        handler = null
        resetStatusView()
        val surfaceView = preview_view
        val surfaceHolder = surfaceView.holder
        if (hasSurface) {
            // The activity was paused but not stopped, so the surface still exists. Therefore
            // surfaceCreated() won't be called, so init the camera here.
            initCamera(surfaceHolder)
        } else {
            // Install the callback and wait for surfaceCreated() to init the camera.
            surfaceHolder.addCallback(this)
        }
        inactivityTimer.onActivityResumed()

        doConfigureUiElements()
    }

    private fun doConfigureUiElements() {
        require(expectedSet.isNotEmpty())

        feedback_block.visibleIf { QrExpected.WebLoginQr in expectedSet }
        instructions.text = if (expectedSet.size > 1) {
            ""
        } else {
            when (val expect = expectedSet.first()) {
                is QrExpected.AnyAssetAddressQr -> getString(R.string.qr_activity_hint_any_asset)
                is QrExpected.AssetAddressQr -> getString(R.string.qr_activity_hint_asset_address, expect.asset)
                is QrExpected.BitPayQr -> getString(R.string.qr_activity_hint_bitpay)
                is QrExpected.ImportWalletKeysQr -> getString(R.string.qr_activity_hint_import_wallet)
                is QrExpected.LegacyPairingQr -> getString(R.string.qr_activity_hint_pairing_code)
                is QrExpected.WebLoginQr -> getString(R.string.qr_activity_hint_new_web_login)
            }
        }
    }

    override fun onPause() {
        (handler as? CaptureActivityHandler)?.quitSynchronously()
        handler = null

        inactivityTimer.onActivityPaused()
        cameraManager.closeDriver()
        if (!hasSurface) {
            val surfaceView = preview_view
            val surfaceHolder = surfaceView.holder
            surfaceHolder.removeCallback(this)
        }

        // Close scanner when going to background
        finish()
        super.onPause()
    }

    override fun onDestroy() {
        inactivityTimer.stop()
        super.onDestroy()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_BACK -> {
                setResult(Activity.RESULT_CANCELED)
                finish()
            }
            KeyEvent.KEYCODE_FOCUS,
            KeyEvent.KEYCODE_CAMERA ->
                // Handle these events so they don't launch the Camera app
                return true
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                cameraManager.setTorch(false)
                return true
            }
            KeyEvent.KEYCODE_VOLUME_UP -> {
                cameraManager.setTorch(true)
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        if (holder == null) {
            Timber.e("*** WARNING *** surfaceCreated() gave us a null surface!")
        }
        if (!hasSurface) {
            hasSurface = true
            initCamera(holder)
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        hasSurface = false
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) { }

    fun handleDecode(rawResult: Result) {
        inactivityTimer.onActivityEvent()
        handleDecodeExternally(rawResult)
    }

    private fun handleDecodeExternally(rawResult: Result) {
        setResult(
            Activity.RESULT_OK,
            Intent(intent.action).apply {
                putExtra(EXTRA_SCAN_RESULT, rawResult.toString())
            }
        )
        finish()
    }

    private fun initCamera(surfaceHolder: SurfaceHolder?) {
        checkNotNull(surfaceHolder) { "No SurfaceHolder provided" }
        require(!cameraManager.isOpen)

        try {
            cameraManager.openDriver(this, surfaceHolder)
            // Creating the handler starts the preview, which can also throw a RuntimeException.
            if (handler == null) {
                handler = CaptureActivityHandler(
                    this,
                    decodeFormats,
                    decodeHints,
                    characterSet,
                    cameraManager
                )
            }

            val targetRect = viewfinder_guide.windowRect
            viewfinder_view.let {
                cameraManager.framingViewSize = Point(it.width, it.height)
                cameraManager.targetRect = targetRect
                it.setTargetRect(targetRect)
            }
        } catch (e: IOException) {
            Timber.e(e)
            finish(RESULT_CAMERA_ERROR)
        } catch (e: RuntimeException) {
            // Barcode Scanner has seen crashes in the wild of this variety:
            // java.?lang.?RuntimeException: Fail to connect to camera service
            Timber.e("Unexpected error initializing camera: $e")
            finish(RESULT_CAMERA_ERROR)
        }
    }

    private fun finish(error: Int) {
        setResult(error)
        finish()
    }

    private fun resetStatusView() {
        viewfinder_view.visibility = View.VISIBLE
    }

    fun drawViewfinder() {
        viewfinder_view.invalidate()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            R.id.action_flash_light -> { toggleTorch(); true }
            else -> super.onOptionsItemSelected(item)
        }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu items for use in the action bar
        if (hasFlashLight) {
            menuInflater.inflate(R.menu.menu_scan, menu)
        }
        return super.onCreateOptionsMenu(menu)
    }

    private fun toggleTorch() {
        try {
            flashStatus = !flashStatus
            cameraManager.setTorch(flashStatus)
        } catch (e: Exception) {
        }
    }

    companion object {
        private const val EXTRA_SCAN_RESULT = "EXTRA_SCAN_RESULT"
        private const val TIMEOUT_MILLIS = 5 * 60 * 1000L

        const val RESULT_CAMERA_ERROR = 10

        private const val PARAM_EXPECTED_QR = "PARAM_EXPECTED_QR"

        const val SCAN_URI_RESULT = 12007

        fun start(ctx: Activity, expect: Set<QrExpected>) {
            requestScanPermissions(
                ctx,
                { doStart(ctx, expect) },
                { onPermissionDenied(ctx) }
            )
        }

        fun start(fragment: Fragment, expect: Set<QrExpected>) {
            val ctx = fragment.requireContext()
            requestScanPermissions(
                ctx,
                { doStart(fragment, expect) },
                { onPermissionDenied(ctx) }
            )
        }

        private fun doStart(fragment: Fragment, expect: Set<QrExpected>) =
            if (canOpenScan()) {
                fragment.startActivityForResult(
                    prepIntent(fragment.requireContext(), expect),
                    SCAN_URI_RESULT
                )
            } else {
                fragment.toast(R.string.camera_unavailable, ToastCustom.TYPE_ERROR)
            }

        private fun doStart(activity: Activity, expect: Set<QrExpected>) =
            if (canOpenScan()) {
                activity.startActivityForResult(
                    prepIntent(activity, expect),
                    SCAN_URI_RESULT
                )
            } else {
                activity.toast(R.string.camera_unavailable, ToastCustom.TYPE_ERROR)
            }

        private fun onPermissionDenied(ctx: Context) {
            ctx.toast(R.string.request_camera_permission, ToastCustom.TYPE_ERROR)
        }

        private fun prepIntent(ctx: Context, expect: Set<QrExpected>) =
            Intent(ctx, QrScanActivity::class.java).apply {
                action = Intents.Scan.ACTION
                putExtra(Intents.Scan.FORMATS, EnumSet.allOf(BarcodeFormat::class.java))
                putExtra(Intents.Scan.MODE, Intents.Scan.QR_CODE_MODE)
                putExtra(PARAM_EXPECTED_QR, expect.toTypedArray())
            }

        private fun canOpenScan(): Boolean {
            var camera: Camera? = null

            return try {
                camera = Camera.open()
                true
            } catch (e: RuntimeException) {
                false
            } finally {
                camera?.release()
            }
        }

        private fun requestScanPermissions(
            ctx: Context,
            onSuccess: () -> Unit,
            onDenied: () -> Unit
        ) {
            val permissionListener = CameraPermissionListener(
                granted = onSuccess,
                denied = onDenied
            )

            Dexter.withContext(ctx)
                .withPermission(Manifest.permission.CAMERA)
                .withListener(permissionListener)
                .onSameThread()
                .withErrorListener { error -> Timber.wtf("Dexter permissions error $error") }
                .check()
        }

        fun Intent?.getRawScanData(): String? =
            this?.getStringExtra(EXTRA_SCAN_RESULT)
    }

    private inner class InactivityTimer {

        private var disposable: Disposable? = null

        fun onActivityEvent() {
            stop()
            start()
        }

        fun onActivityPaused() {
            stop()
            unregisterReceiver(powerStatusReceiver)
        }

        fun onActivityResumed() {
            registerReceiver(powerStatusReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            onActivityEvent()
        }

        fun stop() {
            disposable?.dispose()
            disposable = null
        }

        private fun start() {
            require(disposable == null)

            disposable = Single.timer(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
                .subscribeBy(
                    onSuccess = { finish() }
                )
        }

        private val powerStatusReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (Intent.ACTION_BATTERY_CHANGED == intent?.action) {
                    // 0 indicates that we're on battery
                    if (intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) <= 0) {
                        onActivityEvent()
                    } else {
                        stop()
                    }
                }
            }
        }
    }
}
