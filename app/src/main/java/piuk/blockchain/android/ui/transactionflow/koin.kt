package piuk.blockchain.android.ui.transactionflow

import android.content.Context
import com.blockchain.koin.payloadScope
import io.reactivex.android.schedulers.AndroidSchedulers
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module
import piuk.blockchain.android.ui.transactionflow.analytics.TxFlowAnalytics
import piuk.blockchain.android.ui.transactionflow.engine.TransactionInteractor
import piuk.blockchain.android.ui.transactionflow.engine.TransactionModel
import piuk.blockchain.android.ui.transactionflow.engine.TransactionState
import piuk.blockchain.android.ui.transactionflow.engine.TxFlowErrorReporting
import piuk.blockchain.android.ui.transactionflow.flow.ActiveTransactionFlow
import piuk.blockchain.android.ui.transactionflow.flow.ExchangePriceFormatter
import piuk.blockchain.android.ui.transactionflow.flow.FeedTotalFormatter
import piuk.blockchain.android.ui.transactionflow.flow.FromPropertyFormatter
import piuk.blockchain.android.ui.transactionflow.flow.SwapDestinationPropertyFormatter
import piuk.blockchain.android.ui.transactionflow.flow.SwapExchangeRateFormatter
import piuk.blockchain.android.ui.transactionflow.flow.SwapReceiveFormatter
import piuk.blockchain.android.ui.transactionflow.flow.SwapSourcePropertyFormatter
import piuk.blockchain.android.ui.transactionflow.flow.ToPropertyFormatter
import piuk.blockchain.android.ui.transactionflow.flow.TotalFormatter
import piuk.blockchain.android.ui.transactionflow.flow.TransactionFlowCustomiser
import piuk.blockchain.android.ui.transactionflow.flow.TransactionFlowCustomiserImpl
import piuk.blockchain.android.ui.transactionflow.flow.TxConfirmReadOnlyMapper
import piuk.blockchain.android.ui.transactionflow.flow.TxOptionsFormatter

val transactionFlowScope = named("TransactionScope")

val transactionModule = module {

    factory {
        TransactionFlowCustomiserImpl(
            resources = get<Context>().resources
        )
    }.bind(TransactionFlowCustomiser::class)

    factory {
        ExchangePriceFormatter(
            resources = get<Context>().resources
        )
    }.bind(TxOptionsFormatter::class)

    factory {
        TotalFormatter(
            resources = get<Context>().resources
        )
    }.bind(TxOptionsFormatter::class)

    factory {
        SwapExchangeRateFormatter(
            resources = get<Context>().resources
        )
    }.bind(TxOptionsFormatter::class)

    factory {
        SwapReceiveFormatter(
            resources = get<Context>().resources
        )
    }.bind(TxOptionsFormatter::class)

    factory {
        FromPropertyFormatter(
            resources = get<Context>().resources
        )
    }.bind(TxOptionsFormatter::class)

    factory {
        FeedTotalFormatter(
            resources = get<Context>().resources
        )
    }.bind(TxOptionsFormatter::class)

    factory {
        ToPropertyFormatter(
            resources = get<Context>().resources
        )
    }.bind(TxOptionsFormatter::class)

    factory {
        SwapSourcePropertyFormatter(
            resources = get<Context>().resources
        )
    }.bind(TxOptionsFormatter::class)

    factory {
        SwapDestinationPropertyFormatter(
            resources = get<Context>().resources
        )
    }.bind(TxOptionsFormatter::class)

    factory {
        TxConfirmReadOnlyMapper(
            formatters = getAll()
        )
    }

    factory {
        TxFlowAnalytics(
            analytics = get()
        )
    }

    factory {
        TxFlowErrorReporting(
            crashLogger = get()
        )
    }

    scope(transactionFlowScope) {

        scoped {
            TransactionInteractor(
                coincore = payloadScope.get(),
                addressFactory = payloadScope.get(),
                custodialRepository = payloadScope.get(),
                custodialWalletManager = payloadScope.get(),
                currencyPrefs = get(),
                eligibilityProvider = payloadScope.get(),
                accountsSorting = get()
            )
        }

        // hack. find a better way to handle flow navigation (Rx Activity result)
        scoped {
            ActiveTransactionFlow()
        }

        scoped {
            TransactionModel(
                initialState = TransactionState(),
                mainScheduler = AndroidSchedulers.mainThread(),
                interactor = get(),
                errorLogger = get()
            )
        }
    }
}
