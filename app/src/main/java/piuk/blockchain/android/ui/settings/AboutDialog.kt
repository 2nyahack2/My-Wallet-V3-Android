package piuk.blockchain.android.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager.NameNotFoundException
import android.net.Uri
import android.view.View
import android.webkit.WebView
import androidx.appcompat.app.AlertDialog
import kotlinx.android.synthetic.main.activity_about.view.*
import piuk.blockchain.android.BuildConfig
import piuk.blockchain.android.R
import piuk.blockchain.android.ui.base.SlidingModalBottomDialog
import piuk.blockchain.android.util.copyHashOnLongClick
import piuk.blockchain.android.util.gone
import timber.log.Timber
import java.util.Calendar

class AboutDialog : SlidingModalBottomDialog() {
    override val layoutResource: Int
        get() = R.layout.activity_about

    override fun initControls(view: View) {
        with(view) {
            about.text = getString(
                R.string.about,
                "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) ${BuildConfig.COMMIT_HASH}",
                Calendar.getInstance().get(Calendar.YEAR).toString()
            )

            about.copyHashOnLongClick(requireContext())
            licenses.setOnClickListener {
                val layout = View.inflate(activity, R.layout.dialog_licenses, null)

                layout.findViewById<WebView>(R.id.webview).apply {
                    loadUrl("file:///android_asset/licenses.html")
                }

                AlertDialog.Builder(requireActivity(), R.style.AlertDialogStyle)
                    .setView(layout)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }

            if (hasWallet()) {
                free_wallet.gone()
            } else {
                free_wallet.setOnClickListener {
                    try {
                        val marketIntent =
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("market://details?id=$STR_MERCHANT_PACKAGE")
                            )
                        startActivity(marketIntent)
                    } catch (e: ActivityNotFoundException) {
                        Timber.e(e, "Google Play Store not found")
                    }
                }
            }
            rate_us.setOnClickListener {
                (parentFragment as? ReviewHost)?.showReviewDialog()
                dismiss()
            }
        }
    }

    private fun hasWallet(): Boolean {
        val pm = requireActivity().packageManager
        return try {
            pm.getPackageInfo(STR_MERCHANT_PACKAGE, 0)
            true
        } catch (e: NameNotFoundException) {
            false
        }
    }

    companion object {
        private const val STR_MERCHANT_PACKAGE = "info.blockchain.merchant"
    }
}
