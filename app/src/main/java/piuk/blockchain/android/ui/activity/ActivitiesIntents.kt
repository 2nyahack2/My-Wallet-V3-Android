package piuk.blockchain.android.ui.activity

import info.blockchain.balance.CryptoCurrency
import piuk.blockchain.android.coincore.ActivitySummaryList
import piuk.blockchain.android.coincore.BlockchainAccount
import piuk.blockchain.android.ui.base.mvi.MviIntent

sealed class ActivitiesIntent : MviIntent<ActivitiesState>

class AccountSelectedIntent(
    val account: BlockchainAccount,
    val isRefreshRequested: Boolean
) : ActivitiesIntent() {
    override fun reduce(oldState: ActivitiesState): ActivitiesState {
        val activitiesList = if (oldState.account == account) {
            oldState.activityList // Is a refresh, keep the list
        } else {
            emptyList()
        }
        return oldState.copy(
            account = account,
            isLoading = true,
            activityList = activitiesList
        )
    }
}

object SelectDefaultAccountIntent : ActivitiesIntent() {
    override fun reduce(oldState: ActivitiesState): ActivitiesState {
        return oldState.copy(
            account = null,
            isLoading = true,
            activityList = emptyList()
        )
    }
}

class ActivityListUpdatedIntent(
    private val activityList: ActivitySummaryList
) : ActivitiesIntent() {
    override fun reduce(oldState: ActivitiesState): ActivitiesState {
        return oldState.copy(
            isError = false,
            isLoading = false,
            activityList = activityList
        )
    }
}

object ActivityListUpdatedErrorIntent : ActivitiesIntent() {
    override fun reduce(oldState: ActivitiesState): ActivitiesState {
        return oldState.copy(
            isLoading = false,
            activityList = emptyList(),
            isError = true
        )
    }
}

object ShowAccountSelectionIntent : ActivitiesIntent() {
    override fun reduce(oldState: ActivitiesState): ActivitiesState {
        return oldState.copy(bottomSheet = ActivitiesSheet.ACCOUNT_SELECTOR)
    }
}

class CancelSimpleBuyOrderIntent(
    val orderId: String
) : ActivitiesIntent() {
    override fun reduce(oldState: ActivitiesState): ActivitiesState = oldState
}

class ShowActivityDetailsIntent(
    private val cryptoCurrency: CryptoCurrency,
    private val txHash: String,
    private val type: CryptoActivityType
) : ActivitiesIntent() {
    override fun reduce(oldState: ActivitiesState): ActivitiesState {
        return oldState.copy(
            bottomSheet = ActivitiesSheet.CRYPTO_ACTIVITY_DETAILS,
            selectedCryptoCurrency = cryptoCurrency,
            selectedTxId = txHash,
            activityType = type
        )
    }
}

class ShowFiatActivityDetailsIntent(
    val currency: String,
    val txHash: String
) : ActivitiesIntent() {
    override fun reduce(oldState: ActivitiesState): ActivitiesState {
        return oldState.copy(
            bottomSheet = ActivitiesSheet.FIAT_ACTIVITY_DETAILS,
            selectedFiatCurrency = currency,
            selectedTxId = txHash
        )
    }
}

object ClearBottomSheetIntent : ActivitiesIntent() {
    override fun reduce(oldState: ActivitiesState): ActivitiesState =
        oldState.copy(bottomSheet = null,
            selectedCryptoCurrency = null,
            selectedTxId = "",
            activityType = CryptoActivityType.UNKNOWN
        )
}
