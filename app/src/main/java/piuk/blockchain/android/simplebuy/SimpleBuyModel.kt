package piuk.blockchain.android.simplebuy

import com.blockchain.preferences.RatingPrefs
import com.blockchain.preferences.SimpleBuyPrefs
import com.blockchain.nabu.datamanagers.BuySellOrder
import com.blockchain.nabu.datamanagers.OrderState
import com.blockchain.nabu.datamanagers.PaymentMethod
import com.blockchain.nabu.datamanagers.custodialwalletimpl.PaymentMethodType
import com.blockchain.nabu.models.data.LinkedBankErrorState
import com.blockchain.nabu.models.data.LinkedBankState
import com.blockchain.nabu.models.responses.simplebuy.EverypayPaymentAttrs
import com.google.gson.Gson
import io.reactivex.Completable
import io.reactivex.Scheduler
import io.reactivex.Single
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.subscribeBy
import piuk.blockchain.android.cards.partners.CardActivator
import piuk.blockchain.android.cards.partners.EverypayCardActivator
import piuk.blockchain.android.ui.base.mvi.MviModel
import piuk.blockchain.androidcore.utils.extensions.thenSingle

class SimpleBuyModel(
    private val prefs: SimpleBuyPrefs,
    private val ratingPrefs: RatingPrefs,
    initialState: SimpleBuyState,
    scheduler: Scheduler,
    private val gson: Gson,
    private val cardActivators: List<CardActivator>,
    private val interactor: SimpleBuyInteractor
) : MviModel<SimpleBuyState, SimpleBuyIntent>(
    gson.fromJson(prefs.simpleBuyState(), SimpleBuyState::class.java) ?: initialState,
    scheduler
) {

    override fun performAction(previousState: SimpleBuyState, intent: SimpleBuyIntent): Disposable? =
        when (intent) {
            is SimpleBuyIntent.FetchBuyLimits ->
                interactor.fetchBuyLimitsAndSupportedCryptoCurrencies(intent.fiatCurrency)
                    .subscribeBy(
                        onSuccess = { pairs ->
                            process(
                                SimpleBuyIntent.UpdatedBuyLimitsAndSupportedCryptoCurrencies(
                                    pairs,
                                    intent.cryptoCurrency
                                )
                            )
                            process(SimpleBuyIntent.NewCryptoCurrencySelected(intent.cryptoCurrency))
                        },
                        onError = { process(SimpleBuyIntent.ErrorIntent()) }
                    )
            is SimpleBuyIntent.FetchSupportedFiatCurrencies ->
                interactor.fetchSupportedFiatCurrencies()
                    .subscribeBy(
                        onSuccess = { process(it) },
                        onError = { process(SimpleBuyIntent.ErrorIntent()) }
                    )
            is SimpleBuyIntent.CancelOrder -> (previousState.id?.let {
                interactor.cancelOrder(it)
            } ?: Completable.complete())
                .subscribeBy(
                    onComplete = { process(SimpleBuyIntent.OrderCanceled) },
                    onError = { process(SimpleBuyIntent.ErrorIntent()) }
                )
            is SimpleBuyIntent.CancelOrderIfAnyAndCreatePendingOne -> (previousState.id?.let {
                interactor.cancelOrder(it)
            } ?: Completable.complete()).thenSingle {
                interactor.createOrder(
                    previousState.selectedCryptoCurrency
                        ?: throw IllegalStateException("Missing Cryptocurrency "),
                    previousState.order.amount ?: throw IllegalStateException("Missing amount"),
                    previousState.selectedPaymentMethod?.concreteId(),
                    previousState.selectedPaymentMethod?.paymentMethodType
                        ?: throw IllegalStateException("Missing Payment Method"),
                    true
                )
            }.subscribeBy(
                onSuccess = {
                    process(it)
                },
                onError = {
                    process(SimpleBuyIntent.ErrorIntent())
                }
            )

            is SimpleBuyIntent.FetchKycState -> interactor.pollForKycState()
                .subscribeBy(
                    onSuccess = { process(it) },
                    onError = { /*never fails. will return SimpleBuyIntent.KycStateUpdated(KycState.FAILED)*/ }
                )

            is SimpleBuyIntent.FetchQuote -> interactor.fetchQuote(
                previousState.selectedCryptoCurrency,
                previousState.order.amount
            ).subscribeBy(
                onSuccess = { process(it) },
                onError = { process(SimpleBuyIntent.ErrorIntent()) }
            )

            is SimpleBuyIntent.BuyButtonClicked -> interactor.checkTierLevel()
                .subscribeBy(
                    onSuccess = { process(it) },
                    onError = { process(SimpleBuyIntent.ErrorIntent()) }
                )

            is SimpleBuyIntent.LinkBankTransferRequested -> {
                interactor.userIsEligibleToLinkABank(previousState.fiatCurrency).flatMap {
                    if (it) {
                        interactor.linkNewBank(previousState.fiatCurrency)
                    } else {
                        Single.just(SimpleBuyIntent.SelectedPaymentMethodUpdate(
                            previousState.paymentOptions.availablePaymentMethods.first { paymentMethod ->
                                paymentMethod.id == PaymentMethod.UNDEFINED_BANK_TRANSFER_PAYMENT_ID
                            }
                        ))
                    }
                }
                    .subscribeBy(
                        onSuccess = { process(it) },
                        onError = { process(SimpleBuyIntent.ErrorIntent(ErrorState.LinkedBankNotSupported)) }
                    )
            }

            is SimpleBuyIntent.TryToLinkABankTransfer -> {
                interactor.fetchPaymentMethods(previousState.fiatCurrency).map {
                    it.any { paymentMethod -> paymentMethod is PaymentMethod.UndefinedBankTransfer }
                }.subscribeBy(
                    onSuccess = { isEligibleToLinkABank ->
                        if (isEligibleToLinkABank) {
                            process(SimpleBuyIntent.LinkBankTransferRequested)
                        } else {
                            process(SimpleBuyIntent.ErrorIntent(ErrorState.LinkedBankNotSupported))
                        }
                    },
                    onError = {
                        process(SimpleBuyIntent.ErrorIntent(ErrorState.LinkedBankNotSupported))
                    }
                )
            }

            is SimpleBuyIntent.UpdateAccountProvider -> {
                interactor.updateAccountProviderId(
                    linkingId = intent.linkingBankId,
                    providerAccountId = intent.accountProviderId,
                    accountId = intent.accountId
                ).subscribeBy(
                    onComplete = {
                        process(SimpleBuyIntent.StartPollingForLinkStatus(intent.linkingBankId))
                    },
                    onError = {
                        process(SimpleBuyIntent.ProviderAccountIdUpdateError)
                    }
                )
            }

            is SimpleBuyIntent.StartPollingForLinkStatus -> {
                interactor.pollForLinkedBankState(intent.bankId).subscribeBy(
                    onSuccess = {
                        when (it.state) {
                            LinkedBankState.ACTIVE -> {
                                process(SimpleBuyIntent.LinkedBankStateSuccess(it))
                            }
                            LinkedBankState.BLOCKED,
                            LinkedBankState.UNKNOWN -> {
                                when (it.errorStatus) {
                                    LinkedBankErrorState.ACCOUNT_ALREADY_LINKED -> {
                                        process(SimpleBuyIntent.LinkedBankStateAlreadyLinked)
                                    }
                                    LinkedBankErrorState.UNKNOWN -> {
                                        process(SimpleBuyIntent.LinkedBankStateError)
                                    }
                                    LinkedBankErrorState.ACCOUNT_TYPE_UNSUPPORTED -> {
                                        process(SimpleBuyIntent.LinkedBankStateUnsupportedAccount)
                                    }
                                    LinkedBankErrorState.NAMES_MISS_MATCHED -> {
                                        process(SimpleBuyIntent.LinkedBankStateNamesMissMatch)
                                    }
                                    LinkedBankErrorState.NONE -> {
                                        // do nothing
                                    }
                                }
                            }
                            LinkedBankState.PENDING -> {
                                process(SimpleBuyIntent.LinkedBankStateTimeout)
                            }
                        }
                    },
                    onError = {
                        process(SimpleBuyIntent.LinkedBankStateError)
                    }
                )
            }

            is SimpleBuyIntent.UpdateExchangeRate -> interactor.exchangeRate(intent.currency)
                .subscribeBy(
                    onSuccess = { process(it) },
                    onError = { }
                )
            is SimpleBuyIntent.NewCryptoCurrencySelected -> interactor.exchangeRate(intent.currency)
                .subscribeBy(
                    onSuccess = { process(it) },
                    onError = { }
                )
            is SimpleBuyIntent.FetchWithdrawLockTime -> {
                require(previousState.selectedPaymentMethod != null)
                interactor.fetchWithdrawLockTime(
                    previousState.selectedPaymentMethod.paymentMethodType
                )
                    .subscribeBy(
                        onSuccess = { process(it) },
                        onError = { }
                    )
            }

            is SimpleBuyIntent.FetchSuggestedPaymentMethod -> interactor.fetchPaymentMethods(
                intent.fiatCurrency,
                intent.selectedPaymentMethodId
            )
                .subscribeBy(
                    onSuccess = {
                        process(it)
                    },
                    onError = {
                        process(SimpleBuyIntent.ErrorIntent())
                    }
                )
            is SimpleBuyIntent.LinkBankSelected,
            is SimpleBuyIntent.DepositFundsRequested -> interactor.checkTierLevel()
                .subscribeBy(
                    onSuccess = { process(it) },
                    onError = { process(SimpleBuyIntent.ErrorIntent()) }
                )
            is SimpleBuyIntent.MakePayment ->
                interactor.fetchOrder(intent.orderId)
                    .subscribeBy({
                        process(SimpleBuyIntent.ErrorIntent())
                    }, {
                        process(SimpleBuyIntent.OrderPriceUpdated(it.price))
                        if (it.paymentMethodType == PaymentMethodType.PAYMENT_CARD) {
                            handleCardPayment(it)
                        } else {
                            pollForOrderStatus()
                        }
                    })
            is SimpleBuyIntent.ConfirmOrder -> interactor.confirmOrder(
                previousState.id ?: throw IllegalStateException("Order Id not available"),
                previousState.selectedPaymentMethod?.takeIf { it.isBank() }?.concreteId(),
                cardActivators.firstOrNull {
                    previousState.selectedPaymentMethod?.partner == it.partner
                }?.paymentAttributes()
            )
                .subscribeBy(
                    onSuccess = {
                        val orderCreatedSuccessfully = it.state == OrderState.FINISHED
                        if (orderCreatedSuccessfully) updatePreRatingCompletedActionsCounter()
                        process(SimpleBuyIntent.OrderCreated(it, shouldShowAppRating(orderCreatedSuccessfully)))
                    },
                    onError = { process(SimpleBuyIntent.ErrorIntent()) }
                )
            is SimpleBuyIntent.CheckOrderStatus -> interactor.pollForOrderStatus(
                previousState.id ?: throw IllegalStateException("Order Id not available")
            ).subscribeBy(
                onSuccess = {
                    if (it.state == OrderState.FINISHED)
                        process(SimpleBuyIntent.CardPaymentSucceeded)
                    else if (it.state == OrderState.AWAITING_FUNDS || it.state == OrderState.PENDING_EXECUTION) {
                        process(SimpleBuyIntent.CardPaymentPending)
                    } else process(SimpleBuyIntent.ErrorIntent())
                },
                onError = {
                    process(SimpleBuyIntent.ErrorIntent())
                }
            )
            is SimpleBuyIntent.AppRatingShown -> {
                ratingPrefs.hasSeenRatingDialog = true
                null
            }
            else -> null
        }

    private fun updatePreRatingCompletedActionsCounter() {
        ratingPrefs.preRatingActionCompletedTimes = ratingPrefs.preRatingActionCompletedTimes + 1
    }

    private fun shouldShowAppRating(orderCreatedSuccessFully: Boolean): Boolean =
        ratingPrefs.preRatingActionCompletedTimes >= COMPLETED_ORDERS_BEFORE_SHOWING_APP_RATING &&
            !ratingPrefs.hasSeenRatingDialog && orderCreatedSuccessFully

    private fun pollForOrderStatus() {
        process(SimpleBuyIntent.CheckOrderStatus)
    }

    private fun handleCardPayment(order: BuySellOrder) {
        order.attributes?.everypay?.let { attrs ->
            if (attrs.paymentState == EverypayPaymentAttrs.WAITING_3DS &&
                order.state == OrderState.AWAITING_FUNDS
            ) {
                process(
                    SimpleBuyIntent.Open3dsAuth(
                        attrs.paymentLink,
                        EverypayCardActivator.redirectUrl
                    )
                )
                process(SimpleBuyIntent.ResetEveryPayAuth)
            } else {
                process(SimpleBuyIntent.CheckOrderStatus)
            }
        } ?: kotlin.run {
            process(SimpleBuyIntent.ErrorIntent()) // todo handle case of partner not supported
        }
    }

    override fun onStateUpdate(s: SimpleBuyState) {
        prefs.updateSimpleBuyState(gson.toJson(s))
    }

    companion object {
        private const val COMPLETED_ORDERS_BEFORE_SHOWING_APP_RATING = 1
    }
}