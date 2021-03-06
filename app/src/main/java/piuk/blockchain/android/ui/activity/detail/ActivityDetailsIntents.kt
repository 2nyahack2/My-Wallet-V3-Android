package piuk.blockchain.android.ui.activity.detail

import com.blockchain.nabu.datamanagers.OrderState
import com.blockchain.nabu.datamanagers.custodialwalletimpl.OrderType
import info.blockchain.balance.CryptoCurrency
import info.blockchain.wallet.multiaddress.TransactionSummary
import piuk.blockchain.android.coincore.CustodialInterestActivitySummaryItem
import piuk.blockchain.android.coincore.CustodialTradingActivitySummaryItem
import piuk.blockchain.android.coincore.NonCustodialActivitySummaryItem
import piuk.blockchain.android.coincore.TradeActivitySummaryItem
import piuk.blockchain.android.ui.activity.CryptoActivityType
import piuk.blockchain.android.ui.base.mvi.MviIntent
import java.util.Date

sealed class ActivityDetailsIntents : MviIntent<ActivityDetailState>

class LoadActivityDetailsIntent(
    val cryptoCurrency: CryptoCurrency,
    val txHash: String,
    val activityType: CryptoActivityType
) : ActivityDetailsIntents() {
    override fun reduce(oldState: ActivityDetailState): ActivityDetailState {
        return oldState
    }
}

class LoadNonCustodialCreationDateIntent(
    val summaryItem: NonCustodialActivitySummaryItem
) : ActivityDetailsIntents() {
    override fun reduce(oldState: ActivityDetailState): ActivityDetailState {
        return oldState
    }
}

object ActivityDetailsLoadFailedIntent : ActivityDetailsIntents() {
    override fun reduce(oldState: ActivityDetailState): ActivityDetailState {
        return oldState.copy(
            isError = true
        )
    }
}

class LoadNonCustodialHeaderDataIntent(
    private val summaryItem: NonCustodialActivitySummaryItem
) : ActivityDetailsIntents() {
    override fun reduce(oldState: ActivityDetailState): ActivityDetailState {
        return oldState.copy(
            transactionType = summaryItem.transactionType,
            amount = summaryItem.value,
            isPending = !summaryItem.isConfirmed,
            isFeeTransaction = summaryItem.isFeeTransaction,
            confirmations = summaryItem.confirmations,
            totalConfirmations = summaryItem.cryptoCurrency.requiredConfirmations
        )
    }
}

class LoadCustodialTradingHeaderDataIntent(
    private val summaryItem: CustodialTradingActivitySummaryItem
) : ActivityDetailsIntents() {
    override fun reduce(oldState: ActivityDetailState): ActivityDetailState {
        return oldState.copy(
            transactionType = if (summaryItem.type == OrderType.BUY) TransactionSummary.TransactionType.BUY else
                TransactionSummary.TransactionType.SELL,
            amount = summaryItem.value,
            isPending = summaryItem.status == OrderState.AWAITING_FUNDS,
            isPendingExecution = summaryItem.status == OrderState.PENDING_EXECUTION,
            isFeeTransaction = false,
            confirmations = 0,
            totalConfirmations = 0
        )
    }
}

class LoadCustodialInterestHeaderDataIntent(
    private val summaryItem: CustodialInterestActivitySummaryItem
) : ActivityDetailsIntents() {
    override fun reduce(oldState: ActivityDetailState): ActivityDetailState {
        return oldState.copy(
            transactionType = summaryItem.type,
            interestState = summaryItem.status,
            amount = summaryItem.value,
            isPending = summaryItem.isPending(),
            isFeeTransaction = false,
            confirmations = summaryItem.confirmations,
            totalConfirmations = summaryItem.account.asset.requiredConfirmations
        )
    }
}

class LoadSwapHeaderDataIntent(
    private val summaryItem: TradeActivitySummaryItem
) : ActivityDetailsIntents() {
    override fun reduce(oldState: ActivityDetailState): ActivityDetailState {
        return oldState.copy(
            transactionType = TransactionSummary.TransactionType.SWAP,
            amount = summaryItem.value,
            isPending = summaryItem.state.isPending,
            isFeeTransaction = false
        )
    }
}

class LoadSellHeaderDataIntent(
    private val summaryItem: TradeActivitySummaryItem
) : ActivityDetailsIntents() {
    override fun reduce(oldState: ActivityDetailState): ActivityDetailState {
        return oldState.copy(
            transactionType = TransactionSummary.TransactionType.SELL,
            amount = summaryItem.receivingValue,
            isPending = summaryItem.state.isPending,
            isFeeTransaction = false
        )
    }
}

class ListItemsLoadedIntent(
    private val list: List<ActivityDetailsType>
) : ActivityDetailsIntents() {
    override fun reduce(oldState: ActivityDetailState): ActivityDetailState {
        val currentList = oldState.listOfItems.toMutableSet()
        currentList.addAll(list.toSet())
        return oldState.copy(
            listOfItems = currentList,
            descriptionState = DescriptionState.NOT_SET
        )
    }
}

object ListItemsFailedToLoadIntent : ActivityDetailsIntents() {
    override fun reduce(oldState: ActivityDetailState): ActivityDetailState {
        return oldState.copy(
            isError = true
        )
    }
}

object CreationDateLoadFailedIntent : ActivityDetailsIntents() {
    override fun reduce(oldState: ActivityDetailState): ActivityDetailState {
        return oldState.copy(
            isError = true
        )
    }
}

object DescriptionUpdatedIntent : ActivityDetailsIntents() {
    override fun reduce(oldState: ActivityDetailState): ActivityDetailState {
        return oldState.copy(
            descriptionState = DescriptionState.UPDATE_SUCCESS
        )
    }
}

object DescriptionUpdateFailedIntent : ActivityDetailsIntents() {
    override fun reduce(oldState: ActivityDetailState): ActivityDetailState {
        return oldState.copy(
            descriptionState = DescriptionState.UPDATE_ERROR
        )
    }
}

class CreationDateLoadedIntent(private val createdDate: Date) : ActivityDetailsIntents() {
    override fun reduce(oldState: ActivityDetailState): ActivityDetailState {
        val list = oldState.listOfItems.toMutableSet()
        list.add(Created(createdDate))
        return oldState.copy(
            listOfItems = list
        )
    }
}

class UpdateDescriptionIntent(
    val txId: String,
    val cryptoCurrency: CryptoCurrency,
    val description: String
) :
    ActivityDetailsIntents() {
    override fun reduce(oldState: ActivityDetailState): ActivityDetailState {
        return oldState
    }
}
