package piuk.blockchain.android.ui.linkbank.yodlee

import android.net.Uri
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.blockchain.koin.scopedInject
import com.blockchain.ui.urllinks.URL_YODLEE_SUPPORT_LEARN_MORE
import kotlinx.android.synthetic.main.fragment_link_a_bank.*
import org.koin.android.ext.android.inject
import piuk.blockchain.android.R
import piuk.blockchain.android.simplebuy.BankPartnerTypes
import piuk.blockchain.android.simplebuy.ErrorState
import piuk.blockchain.android.simplebuy.SimpleBuyIntent
import piuk.blockchain.android.simplebuy.SimpleBuyModel
import piuk.blockchain.android.simplebuy.SimpleBuyState
import piuk.blockchain.android.simplebuy.accountMismatchError
import piuk.blockchain.android.simplebuy.accountMismatchErrorCtaCancel
import piuk.blockchain.android.simplebuy.accountMismatchErrorCtaRetry
import piuk.blockchain.android.simplebuy.bankLinkingAlreadyCtaCancel
import piuk.blockchain.android.simplebuy.bankLinkingAlreadyCtaRetry
import piuk.blockchain.android.simplebuy.bankLinkingAlreadyLinked
import piuk.blockchain.android.simplebuy.bankLinkingGenericError
import piuk.blockchain.android.simplebuy.bankLinkingGenericErrorCtaCancel
import piuk.blockchain.android.simplebuy.bankLinkingGenericErrorCtaRetry
import piuk.blockchain.android.simplebuy.bankLinkingIncorrectAccount
import piuk.blockchain.android.simplebuy.bankLinkingIncorrectCtaCancel
import piuk.blockchain.android.simplebuy.bankLinkingIncorrectCtaRetry
import piuk.blockchain.android.simplebuy.bankLinkingSuccess
import piuk.blockchain.android.ui.base.mvi.MviFragment
import piuk.blockchain.android.ui.base.setupToolbar
import piuk.blockchain.android.util.StringUtils
import piuk.blockchain.android.util.gone
import piuk.blockchain.android.util.inflate
import piuk.blockchain.android.util.visible
import piuk.blockchain.androidcore.utils.helperfunctions.unsafeLazy

class LinkBankFragment : MviFragment<SimpleBuyModel, SimpleBuyIntent, SimpleBuyState>() {

    override val model: SimpleBuyModel by scopedInject()
    private val stringUtils: StringUtils by inject()

    private val accountProviderId: String by lazy {
        arguments?.getString(ACCOUNT_PROVIDER_ID) ?: ""
    }

    private val accountId: String by lazy {
        arguments?.getString(ACCOUNT_ID) ?: ""
    }

    private val linkingBankId: String by lazy {
        arguments?.getString(LINKING_BANK_ID) ?: ""
    }

    private val errorState: ErrorState? by unsafeLazy {
        arguments?.getSerializable(ERROR_STATE) as? ErrorState
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = container?.inflate(R.layout.fragment_link_a_bank)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (savedInstanceState == null && accountProviderId.isNotEmpty() && accountId.isNotEmpty()) {
            model.process(SimpleBuyIntent.UpdateAccountProvider(accountProviderId, accountId, linkingBankId))
        }
        activity.setupToolbar(R.string.link_a_bank, false)
    }

    override fun render(newState: SimpleBuyState) {

        if (newState.isLoading) {
            showLinkingInProgress()
        }

        val error = newState.errorState ?: errorState
        error?.let {
            showErrorState(it)
        }

        if (newState.selectedPaymentMethod?.isActive() == true && newState.selectedPaymentMethod.isBank()) {
            showLinkingSuccess(
                label = newState.selectedPaymentMethod.label ?: "",
                id = newState.selectedPaymentMethod.id
            )
        }
    }

    private fun showErrorState(state: ErrorState) {
        when (state) {
            ErrorState.BankLinkingTimeout -> {
                analytics.logEvent(bankLinkingGenericError(BankPartnerTypes.ACH.name))
                link_bank_btn.text = getString(R.string.common_try_again)
                link_bank_title.text = getString(R.string.yodlee_linking_generic_error_title)
                link_bank_subtitle.text = getString(R.string.yodlee_linking_timeout_error_subtitle)
            }
            ErrorState.LinkedBankAlreadyLinked -> {
                analytics.logEvent(
                    bankLinkingAlreadyLinked(BankPartnerTypes.ACH.name)
                )
                link_bank_btn.text = getString(R.string.yodlee_linking_try_different_account)
                link_bank_title.text = getString(R.string.yodlee_linking_generic_error_title)
                link_bank_subtitle.text = getString(R.string.yodlee_linking_already_linked_error_subtitle)
            }
            ErrorState.LinkedBankAccountUnsupported -> {
                analytics.logEvent(
                    bankLinkingIncorrectAccount(BankPartnerTypes.ACH.name)
                )
                link_bank_btn.text = getString(R.string.yodlee_linking_try_different_bank)
                link_bank_title.text = getString(R.string.yodlee_linking_checking_error_title)
                link_bank_subtitle.text = getString(R.string.yodlee_linking_checking_error_subtitle)
            }
            ErrorState.LinkedBankNamesMismatched -> {
                analytics.logEvent(
                    accountMismatchError(BankPartnerTypes.ACH.name)
                )

                link_bank_btn.text = getString(R.string.yodlee_linking_try_different_bank)
                link_bank_title.text = getString(R.string.yodlee_linking_is_this_your_bank)

                val linksMap = mapOf<String, Uri>(
                    "yodlee_names_dont_match_learn_more" to Uri.parse(URL_YODLEE_SUPPORT_LEARN_MORE)
                )

                val text = stringUtils.getStringWithMappedAnnotations(
                    R.string.yodlee_linking_already_linked_error_subtitle,
                    linksMap,
                    activity
                )

                link_bank_subtitle.text = text
                link_bank_subtitle.movementMethod = LinkMovementMethod.getInstance()
            }
            else -> {
                analytics.logEvent(bankLinkingGenericError(BankPartnerTypes.ACH.name))
                link_bank_btn.text = getString(R.string.common_try_again)
                link_bank_title.text = getString(R.string.yodlee_linking_generic_error_title)
                link_bank_subtitle.text = getString(R.string.yodlee_linking_generic_error_subtitle)
            }
        }

        link_bank_icon.setImageResource(
            if (state == ErrorState.LinkedBankNamesMismatched) {
                R.drawable.ic_bank_user
            } else {
                R.drawable.ic_bank_details_big
            }
        )
        link_bank_progress.gone()
        link_bank_state_indicator.setImageResource(R.drawable.ic_alert_white_bkgd)
        link_bank_state_indicator.visible()
        link_bank_btn.visible()
        link_bank_btn.setOnClickListener {
            logRetryAnalytics(state)
            navigator().retry()
        }
        link_bank_cancel.visible()
        link_bank_cancel.setOnClickListener {
            logCancelAnalytics(state)
            navigator().bankLinkingCancelled()
        }
    }

    private fun logRetryAnalytics(state: ErrorState) {
        when (state) {
            ErrorState.LinkedBankAlreadyLinked -> analytics.logEvent(
                bankLinkingAlreadyCtaRetry(BankPartnerTypes.ACH.name)
            )
            ErrorState.LinkedBankAccountUnsupported -> analytics.logEvent(
                bankLinkingIncorrectCtaRetry(BankPartnerTypes.ACH.name)
            )
            ErrorState.LinkedBankNamesMismatched -> analytics.logEvent(
                accountMismatchErrorCtaRetry(BankPartnerTypes.ACH.name)
            )
            else -> analytics.logEvent(bankLinkingGenericErrorCtaRetry(BankPartnerTypes.ACH.name))
        }
    }

    private fun logCancelAnalytics(state: ErrorState) {
        when (state) {
            ErrorState.LinkedBankAlreadyLinked -> analytics.logEvent(
                bankLinkingAlreadyCtaCancel(BankPartnerTypes.ACH.name)
            )
            ErrorState.LinkedBankAccountUnsupported -> analytics.logEvent(
                bankLinkingIncorrectCtaCancel(BankPartnerTypes.ACH.name)
            )
            ErrorState.LinkedBankNamesMismatched -> analytics.logEvent(
                accountMismatchErrorCtaCancel(BankPartnerTypes.ACH.name)
            )
            else -> analytics.logEvent(bankLinkingGenericErrorCtaCancel(BankPartnerTypes.ACH.name))
        }
    }

    private fun showLinkingInProgress() {
        link_bank_icon.setImageResource(R.drawable.ic_blockchain_blue_circle)
        link_bank_progress.visible()
        link_bank_state_indicator.gone()
        link_bank_btn.gone()
        link_bank_title.text = getString(R.string.yodlee_linking_title)
        link_bank_subtitle.text = getString(R.string.yodlee_linking_subtitle)
    }

    private fun showLinkingSuccess(label: String, id: String) {
        analytics.logEvent(bankLinkingSuccess(BankPartnerTypes.ACH.name))

        link_bank_icon.setImageResource(R.drawable.ic_bank_details_big)
        link_bank_progress.gone()
        link_bank_state_indicator.setImageResource(R.drawable.ic_check_circle)
        link_bank_state_indicator.visible()
        link_bank_btn.visible()
        link_bank_btn.setOnClickListener {
            navigator().bankLinkingFinished(id)
        }
        link_bank_title.text = getString(R.string.yodlee_linking_success_title)
        link_bank_subtitle.text = getString(R.string.yodlee_linking_success_subtitle, label)
    }

    private fun navigator(): YodleeLinkingFlowNavigator =
        (activity as? YodleeLinkingFlowNavigator)
            ?: throw IllegalStateException("Parent must implement YodleeLinkingFlowNavigator")

    companion object {
        private const val ACCOUNT_PROVIDER_ID = "ACCOUNT_PROVIDER_ID"
        private const val ACCOUNT_ID = "ACCOUNT_ID"
        private const val LINKING_BANK_ID = "LINKING_BANK_ID"
        private const val ERROR_STATE = "ERROR_STATE"

        fun newInstance(accountProviderId: String, accountId: String, linkingBankId: String) =
            LinkBankFragment().apply {
                arguments = Bundle().apply {
                    putString(ACCOUNT_PROVIDER_ID, accountProviderId)
                    putString(ACCOUNT_ID, accountId)
                    putString(LINKING_BANK_ID, linkingBankId)
                }
            }

        fun newInstance(errorState: ErrorState) = LinkBankFragment().apply {
            arguments = Bundle().apply {
                putSerializable(ERROR_STATE, errorState)
            }
        }
    }
}