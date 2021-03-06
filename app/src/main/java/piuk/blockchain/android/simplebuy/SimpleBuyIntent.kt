package piuk.blockchain.android.simplebuy

import com.blockchain.nabu.datamanagers.BuySellOrder
import com.blockchain.nabu.datamanagers.BuySellPairs
import com.blockchain.nabu.datamanagers.CustodialQuote
import com.blockchain.nabu.datamanagers.OrderState
import com.blockchain.nabu.datamanagers.Partner
import com.blockchain.nabu.datamanagers.PaymentMethod
import com.blockchain.nabu.datamanagers.custodialwalletimpl.PaymentMethodType
import com.blockchain.nabu.models.data.LinkBankTransfer
import com.blockchain.nabu.models.data.LinkedBank
import info.blockchain.balance.CryptoCurrency
import info.blockchain.balance.CryptoValue
import info.blockchain.balance.FiatValue
import piuk.blockchain.android.cards.EverypayAuthOptions
import piuk.blockchain.android.ui.base.mvi.MviIntent
import java.math.BigInteger

sealed class SimpleBuyIntent : MviIntent<SimpleBuyState> {

    override fun isValidFor(oldState: SimpleBuyState): Boolean {
        return oldState.errorState == null
    }

    override fun reduce(oldState: SimpleBuyState): SimpleBuyState =
        oldState

    class NewCryptoCurrencySelected(val currency: CryptoCurrency) : SimpleBuyIntent() {
        override fun reduce(oldState: SimpleBuyState): SimpleBuyState =
            if (oldState.selectedCryptoCurrency == currency) oldState else
                oldState.copy(selectedCryptoCurrency = currency, amount = null, exchangePrice = null)
    }

    class AmountUpdated(private val amount: FiatValue) : SimpleBuyIntent() {
        override fun reduce(oldState: SimpleBuyState): SimpleBuyState =
            oldState.copy(amount = amount)
    }

    object ResetLinkBankTransfer : SimpleBuyIntent() {
        override fun reduce(oldState: SimpleBuyState): SimpleBuyState =
            oldState.copy(linkBankTransfer = null, linkBankRequested = false)
    }

    class OrderPriceUpdated(private val price: FiatValue?) : SimpleBuyIntent() {
        override fun reduce(oldState: SimpleBuyState): SimpleBuyState =
            oldState.copy(orderExchangePrice = price, isLoading = false)
    }

    class Open3dsAuth(private val paymentLink: String, private val exitLink: String) : SimpleBuyIntent() {
        override fun reduce(oldState: SimpleBuyState): SimpleBuyState =
            oldState.copy(everypayAuthOptions = EverypayAuthOptions(paymentLink, exitLink))
    }

    class ExchangeRateUpdated(private val price: FiatValue) : SimpleBuyIntent() {
        override fun reduce(oldState: SimpleBuyState): SimpleBuyState =
            oldState.copy(exchangePrice = price)
    }

    class PaymentMethodsUpdated(
        private val availablePaymentMethods: List<PaymentMethod>,
        private val canAddCard: Boolean,
        private val canLinkFunds: Boolean,
        private val canLinkBank: Boolean,
        private val preselectedId: String? = null // pass this value if you want to preselect one
    ) : SimpleBuyIntent() {
        override fun reduce(oldState: SimpleBuyState): SimpleBuyState {
            val selectedPaymentMethodId =
                selectedMethodId(oldState.selectedPaymentMethod?.id) ?: availablePaymentMethods[0].id
            val selectedPaymentMethod = availablePaymentMethods.firstOrNull {
                it.id == selectedPaymentMethodId
            }

            val type = when (selectedPaymentMethod) {
                is PaymentMethod.Card -> PaymentMethodType.PAYMENT_CARD
                is PaymentMethod.Funds -> PaymentMethodType.FUNDS
                is PaymentMethod.Bank -> PaymentMethodType.BANK_TRANSFER
                else -> PaymentMethodType.UNKNOWN
            }

            return oldState.copy(
                selectedPaymentMethod = SelectedPaymentMethod(
                    selectedPaymentMethodId,
                    (selectedPaymentMethod as? PaymentMethod.Card)?.partner,
                    selectedPaymentMethod?.detailedLabel() ?: "",
                    type
                ),
                paymentOptions = PaymentOptions(
                    availablePaymentMethods = availablePaymentMethods,
                    canAddCard = canAddCard,
                    canLinkFunds = canLinkFunds,
                    canLinkBank = canLinkBank
                )
            )
        }

        private fun selectedMethodId(oldStateId: String?): String? =
            when {
                preselectedId != null -> availablePaymentMethods.firstOrNull { it.id == preselectedId }?.id
                oldStateId != null -> availablePaymentMethods.firstOrNull { it.id == oldStateId }?.id
                else -> availablePaymentMethods[0].id
            }
    }

    class SelectedPaymentMethodUpdate(
        private val paymentMethod: PaymentMethod
    ) : SimpleBuyIntent() {
        override fun reduce(oldState: SimpleBuyState): SimpleBuyState =
            oldState.copy(
                selectedPaymentMethod = SelectedPaymentMethod(
                    paymentMethod.id,
                    // no partner for bank transfer or ui label. Ui label for bank transfer is coming from resources
                    (paymentMethod as? PaymentMethod.Card)?.partner,
                    paymentMethod.detailedLabel(),
                    when (paymentMethod) {
                        is PaymentMethod.UndefinedBankTransfer -> PaymentMethodType.BANK_TRANSFER
                        is PaymentMethod.Bank -> PaymentMethodType.BANK_TRANSFER
                        is PaymentMethod.Funds -> PaymentMethodType.FUNDS
                        is PaymentMethod.UndefinedFunds -> PaymentMethodType.FUNDS
                        else -> PaymentMethodType.PAYMENT_CARD
                    }
                )
            )
    }

    class UpdateExchangeRate(val currency: CryptoCurrency) : SimpleBuyIntent() {
        override fun reduce(oldState: SimpleBuyState): SimpleBuyState =
            oldState.copy(exchangePrice = null)
    }

    object BuyButtonClicked : SimpleBuyIntent() {
        override fun reduce(oldState: SimpleBuyState): SimpleBuyState =
            oldState.copy(confirmationActionRequested = true, orderState = OrderState.INITIALISED)
    }

    object LinkBankTransferRequested : SimpleBuyIntent() {
        override fun reduce(oldState: SimpleBuyState): SimpleBuyState = oldState.copy(
            linkBankRequested = false
        )
    }

    object TryToLinkABankTransfer : SimpleBuyIntent() {
        override fun reduce(oldState: SimpleBuyState): SimpleBuyState {
            return oldState.copy(isLoading = true)
        }
    }

    object ProviderAccountIdUpdateError : SimpleBuyIntent() {
        override fun reduce(oldState: SimpleBuyState): SimpleBuyState = oldState.copy(
            errorState = ErrorState.BankLinkingUpdateFailed,
            isLoading = false
        )
    }

    class LinkedBankStateSuccess(private val linkedBank: LinkedBank) : SimpleBuyIntent() {
        override fun reduce(oldState: SimpleBuyState): SimpleBuyState =
            oldState.copy(
                isLoading = false,
                selectedPaymentMethod = SelectedPaymentMethod(
                    id = linkedBank.id,
                    paymentMethodType = PaymentMethodType.BANK_TRANSFER,
                    label = linkedBank.name
                )
            )
    }

    object LinkedBankStateError : SimpleBuyIntent() {
        override fun reduce(oldState: SimpleBuyState): SimpleBuyState = oldState.copy(
            errorState = ErrorState.BankLinkingFailed,
            isLoading = false
        )
    }

    object LinkedBankStateAlreadyLinked : SimpleBuyIntent() {
        override fun reduce(oldState: SimpleBuyState): SimpleBuyState = oldState.copy(
            errorState = ErrorState.LinkedBankAlreadyLinked,
            isLoading = false
        )
    }

    object LinkedBankStateNamesMissMatch : SimpleBuyIntent() {
        override fun reduce(oldState: SimpleBuyState): SimpleBuyState = oldState.copy(
            errorState = ErrorState.LinkedBankNamesMismatched,
            isLoading = false
        )
    }

    object LinkedBankStateUnsupportedAccount : SimpleBuyIntent() {
        override fun reduce(oldState: SimpleBuyState): SimpleBuyState = oldState.copy(
            errorState = ErrorState.LinkedBankAccountUnsupported,
            isLoading = false
        )
    }

    object LinkedBankStateTimeout : SimpleBuyIntent() {
        override fun reduce(oldState: SimpleBuyState): SimpleBuyState = oldState.copy(
            errorState = ErrorState.BankLinkingTimeout,
            isLoading = false
        )
    }

    class UpdateAccountProvider(
        val accountProviderId: String,
        val accountId: String,
        val linkingBankId: String
    ) : SimpleBuyIntent() {
        override fun reduce(oldState: SimpleBuyState): SimpleBuyState = oldState.copy(
            isLoading = true
        )
    }

    data class StartPollingForLinkStatus(val bankId: String) : SimpleBuyIntent() {
        override fun reduce(oldState: SimpleBuyState): SimpleBuyState =
            oldState.copy(isLoading = true)
    }

    object ClearAnySelectedPaymentMethods : SimpleBuyIntent() {
        override fun reduce(oldState: SimpleBuyState): SimpleBuyState =
            oldState.copy(
                selectedPaymentMethod = null
            )
    }

    data class FiatCurrencyUpdated(private val fiatCurrency: String) : SimpleBuyIntent() {
        override fun reduce(oldState: SimpleBuyState): SimpleBuyState =
            oldState.copy(fiatCurrency = fiatCurrency, amount = null)
    }

    data class UpdatedBuyLimitsAndSupportedCryptoCurrencies(
        val buySellPairs: BuySellPairs,
        private val selectedCryptoCurrency: CryptoCurrency?
    ) : SimpleBuyIntent() {

        override fun reduce(oldState: SimpleBuyState): SimpleBuyState {
            val supportedPairsAndLimits = buySellPairs.pairs.filter { it.fiatCurrency == oldState.fiatCurrency }

            if (supportedPairsAndLimits.isEmpty()) {
                return oldState.copy(errorState = ErrorState.NoAvailableCurrenciesToTrade)
            }

            val minValueForSelectedPair = supportedPairsAndLimits.firstOrNull { pairs ->
                pairs.fiatCurrency == oldState.fiatCurrency &&
                    pairs.cryptoCurrency == selectedCryptoCurrency
            }?.buyLimits?.minLimit(oldState.fiatCurrency)?.valueMinor

            val maxValueForSelectedPair = supportedPairsAndLimits.firstOrNull { pairs ->
                pairs.fiatCurrency == oldState.fiatCurrency &&
                    pairs.cryptoCurrency == selectedCryptoCurrency
            }?.buyLimits?.maxLimit(oldState.fiatCurrency)?.valueMinor

            return oldState.copy(
                supportedPairsAndLimits = supportedPairsAndLimits,
                selectedCryptoCurrency = selectedCryptoCurrency,
                predefinedAmounts = oldState.predefinedAmounts.filter {
                    it.valueMinor >= (minValueForSelectedPair ?: 0) && it.valueMinor <= (maxValueForSelectedPair ?: 0)
                }
            )
        }
    }

    data class SupportedCurrenciesUpdated(private val currencies: List<String>) : SimpleBuyIntent() {
        override fun reduce(oldState: SimpleBuyState): SimpleBuyState =
            oldState.copy(supportedFiatCurrencies = currencies)
    }

    data class UpdatedPredefinedAmounts(private val amounts: List<FiatValue>) : SimpleBuyIntent() {
        override fun reduce(oldState: SimpleBuyState): SimpleBuyState {
            return oldState.copy(predefinedAmounts = amounts.filter {
                val isBiggerThanMin = it.valueMinor >= oldState.minFiatAmount.valueMinor
                val isSmallerThanMax = it.valueMinor <= oldState.maxFiatAmount.valueMinor
                isBiggerThanMin && isSmallerThanMax
            })
        }
    }

    data class WithdrawLocksTimeUpdated(private val time: BigInteger = BigInteger.ZERO) : SimpleBuyIntent() {
        override fun reduce(oldState: SimpleBuyState): SimpleBuyState {
            return oldState.copy(withdrawalLockPeriod = time)
        }
    }

    data class QuoteUpdated(private val custodialQuote: CustodialQuote) : SimpleBuyIntent() {
        override fun reduce(oldState: SimpleBuyState): SimpleBuyState {
            return oldState.copy(custodialQuote = custodialQuote)
        }
    }

    data class FetchBuyLimits(val fiatCurrency: String, val cryptoCurrency: CryptoCurrency) : SimpleBuyIntent() {
        override fun reduce(oldState: SimpleBuyState): SimpleBuyState =
            oldState.copy(fiatCurrency = fiatCurrency, selectedCryptoCurrency = cryptoCurrency)
    }

    data class FlowCurrentScreen(val flowScreen: FlowScreen) : SimpleBuyIntent() {
        override fun reduce(oldState: SimpleBuyState): SimpleBuyState = oldState.copy(currentScreen = flowScreen)
    }

    data class FetchSuggestedPaymentMethod(val fiatCurrency: String, val selectedPaymentMethodId: String? = null) :
        SimpleBuyIntent() {
        override fun reduce(oldState: SimpleBuyState): SimpleBuyState =
            oldState.copy(paymentOptions = PaymentOptions())
    }

    object FetchSupportedFiatCurrencies : SimpleBuyIntent() {
        override fun reduce(oldState: SimpleBuyState): SimpleBuyState =
            oldState.copy(supportedFiatCurrencies = emptyList())
    }

    object CancelOrder : SimpleBuyIntent() {
        override fun isValidFor(oldState: SimpleBuyState) = true
    }

    object ClearState : SimpleBuyIntent() {
        override fun reduce(oldState: SimpleBuyState): SimpleBuyState =
            SimpleBuyState()

        override fun isValidFor(oldState: SimpleBuyState): Boolean {
            return oldState.orderState < OrderState.PENDING_CONFIRMATION ||
                oldState.orderState > OrderState.PENDING_EXECUTION
        }
    }

    object ConfirmOrder : SimpleBuyIntent() {
        override fun reduce(oldState: SimpleBuyState): SimpleBuyState =
            oldState.copy(confirmationActionRequested = true, isLoading = true)
    }

    object FetchWithdrawLockTime : SimpleBuyIntent()

    object NavigationHandled : SimpleBuyIntent() {
        override fun reduce(oldState: SimpleBuyState): SimpleBuyState =
            oldState.copy(confirmationActionRequested = false, depositFundsRequested = false)
    }

    object KycStarted : SimpleBuyIntent() {
        override fun reduce(oldState: SimpleBuyState): SimpleBuyState =
            oldState.copy(
                kycStartedButNotCompleted = true,
                currentScreen = FlowScreen.KYC,
                kycVerificationState = null
            )
    }

    class ErrorIntent(private val error: ErrorState = ErrorState.GenericError) : SimpleBuyIntent() {
        override fun reduce(oldState: SimpleBuyState): SimpleBuyState =
            oldState.copy(errorState = error, isLoading = false, confirmationActionRequested = false)

        override fun isValidFor(oldState: SimpleBuyState): Boolean {
            return true
        }
    }

    object KycCompleted : SimpleBuyIntent() {
        override fun reduce(oldState: SimpleBuyState): SimpleBuyState =
            oldState.copy(kycStartedButNotCompleted = false)
    }

    object FetchKycState : SimpleBuyIntent() {
        override fun reduce(oldState: SimpleBuyState): SimpleBuyState =
            oldState.copy(kycVerificationState = KycState.PENDING)
    }

    object FetchQuote : SimpleBuyIntent()

    class KycStateUpdated(val kycState: KycState) : SimpleBuyIntent() {
        override fun reduce(oldState: SimpleBuyState): SimpleBuyState =
            oldState.copy(kycVerificationState = kycState)
    }

    class BankLinkProcessStarted(private val bankTransfer: LinkBankTransfer) : SimpleBuyIntent() {
        override fun reduce(oldState: SimpleBuyState): SimpleBuyState {
            return oldState.copy(
                linkBankTransfer = bankTransfer,
                confirmationActionRequested = false,
                linkBankRequested = false,
                isLoading = false
            )
        }
    }

    object OrderCanceled : SimpleBuyIntent() {
        override fun reduce(oldState: SimpleBuyState): SimpleBuyState =
            SimpleBuyState(orderState = OrderState.CANCELED)
    }

    class OrderCreated(
        private val buyOrder: BuySellOrder,
        private val showInAppRating: Boolean = false
    ) : SimpleBuyIntent() {
        override fun reduce(oldState: SimpleBuyState): SimpleBuyState =
            oldState.copy(
                orderState = buyOrder.state,
                expirationDate = buyOrder.expires,
                id = buyOrder.id,
                fee = buyOrder.fee,
                orderValue = buyOrder.orderValue as CryptoValue,
                orderExchangePrice = buyOrder.price,
                paymentSucceeded = buyOrder.state == OrderState.FINISHED,
                isLoading = false,
                showRating = showInAppRating
            )
    }

    object AppRatingShown : SimpleBuyIntent() {
        override fun reduce(oldState: SimpleBuyState): SimpleBuyState =
            oldState.copy(showRating = false)
    }

    class UpdateSelectedPaymentMethod(
        private val id: String,
        private val label: String?,
        private val partner: Partner?,
        private val type: PaymentMethodType
    ) : SimpleBuyIntent() {
        override fun reduce(oldState: SimpleBuyState): SimpleBuyState =
            oldState.copy(selectedPaymentMethod = SelectedPaymentMethod(id, partner, label, type))
    }

    object ClearError : SimpleBuyIntent() {
        override fun reduce(oldState: SimpleBuyState): SimpleBuyState =
            oldState.copy(errorState = null)

        override fun isValidFor(oldState: SimpleBuyState): Boolean {
            return oldState.errorState != null
        }
    }

    object ResetEveryPayAuth : SimpleBuyIntent() {
        override fun reduce(oldState: SimpleBuyState): SimpleBuyState =
            oldState.copy(everypayAuthOptions = null)
    }

    object CancelOrderIfAnyAndCreatePendingOne : SimpleBuyIntent() {
        override fun reduce(oldState: SimpleBuyState): SimpleBuyState =
            oldState.copy(isLoading = true)

        override fun isValidFor(oldState: SimpleBuyState): Boolean {
            return oldState.selectedCryptoCurrency != null &&
                oldState.order.amount != null &&
                oldState.orderState != OrderState.AWAITING_FUNDS &&
                oldState.orderState != OrderState.PENDING_EXECUTION
        }
    }

    class MakePayment(val orderId: String) : SimpleBuyIntent() {
        override fun reduce(oldState: SimpleBuyState): SimpleBuyState =
            oldState.copy(isLoading = true)
    }

    object CheckOrderStatus : SimpleBuyIntent() {
        override fun reduce(oldState: SimpleBuyState): SimpleBuyState =
            oldState.copy(isLoading = true)
    }

    object CardPaymentSucceeded : SimpleBuyIntent() {
        override fun reduce(oldState: SimpleBuyState): SimpleBuyState =
            oldState.copy(paymentSucceeded = true, isLoading = false)
    }

    object CardPaymentPending : SimpleBuyIntent() {
        override fun reduce(oldState: SimpleBuyState): SimpleBuyState =
            oldState.copy(paymentPending = true, isLoading = false)
    }

    object DepositFundsRequested : SimpleBuyIntent() {
        override fun reduce(oldState: SimpleBuyState): SimpleBuyState =
            oldState.copy(depositFundsRequested = true)
    }

    object LinkBankSelected : SimpleBuyIntent() {
        override fun reduce(oldState: SimpleBuyState): SimpleBuyState =
            oldState.copy(linkBankRequested = true)
    }

    object LinkBankActionHandled : SimpleBuyIntent() {
        override fun reduce(oldState: SimpleBuyState): SimpleBuyState =
            oldState.copy(linkBankRequested = false)
    }

    object DepositFundsHandled : SimpleBuyIntent() {
        override fun reduce(oldState: SimpleBuyState): SimpleBuyState =
            oldState.copy(depositFundsRequested = false)
    }
}
