package piuk.blockchain.android.ui.backup.start

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.fragment_backup_start.*
import piuk.blockchain.android.R
import com.blockchain.koin.scopedInject
import com.blockchain.koin.scopedInjectActivity
import com.blockchain.ui.password.SecondPasswordHandler
import piuk.blockchain.android.ui.backup.wordlist.BackupWalletWordListFragment
import piuk.blockchain.androidcoreui.ui.base.BaseFragment
import piuk.blockchain.android.util.inflate

class BackupWalletStartingFragment :
    BaseFragment<BackupWalletStartingView, BackupWalletStartingPresenter>(),
    BackupWalletStartingView {

    private val backupWalletStartingPresenter: BackupWalletStartingPresenter by scopedInject()

    private val secondPasswordHandler: SecondPasswordHandler by scopedInjectActivity()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = container!!.inflate(R.layout.fragment_backup_start)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        button_start.setOnClickListener {
            secondPasswordHandler.validate(
                requireContext(),
                object : SecondPasswordHandler.ResultListener {
                    override fun onNoSecondPassword() {
                        loadFragmentWordListFragment()
                    }

                    override fun onSecondPasswordValidated(validatedSecondPassword: String) {
                        loadFragmentWordListFragment(validatedSecondPassword)
                    }
                }
            )
        }
    }

    override fun createPresenter() = backupWalletStartingPresenter

    override fun getMvpView() = this

    private fun loadFragmentWordListFragment(secondPassword: String? = null) {
        val fragment = BackupWalletWordListFragment().apply {
            secondPassword?.let {
                arguments = Bundle().apply {
                    putString(
                        BackupWalletWordListFragment.ARGUMENT_SECOND_PASSWORD,
                        it
                    )
                }
            }
        }
        activity?.run {
            supportFragmentManager.beginTransaction()
                .replace(R.id.content_frame, fragment)
                .addToBackStack(null)
                .commit()
        }
    }

    companion object {
        const val TAG = "BackupWalletStartingFragment"
    }
}