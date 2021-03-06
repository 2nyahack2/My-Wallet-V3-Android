package piuk.blockchain.android.cards

import com.blockchain.nabu.datamanagers.BillingAddress
import com.blockchain.nabu.datamanagers.PaymentMethod
import piuk.blockchain.android.ui.base.mvi.MviIntent

sealed class CardIntent : MviIntent<CardState> {

    class UpdateBillingAddress(private val billingAddress: BillingAddress) : CardIntent() {
        override fun reduce(oldState: CardState): CardState =
            oldState.copy(billingAddress = billingAddress)
    }

    class CardUpdated(val cardDetails: PaymentMethod.Card) : CardIntent() {
        override fun reduce(oldState: CardState): CardState = oldState.copy(cardStatus = cardDetails.status)
    }

    class ActivateEveryPayCard(val card: CardData, val cardId: String) : CardIntent() {
        override fun reduce(oldState: CardState): CardState =
            oldState
    }

    class AuthoriseEverypayCard(private val paymentLink: String, private val exitLink: String) : CardIntent() {
        override fun reduce(oldState: CardState): CardState =
            oldState.copy(authoriseEverypayCard = EverypayAuthOptions(paymentLink, exitLink))
    }

    class UpdateCardId(private val cardId: String) : CardIntent() {
        override fun reduce(oldState: CardState): CardState =
            oldState.copy(cardId = cardId)
    }

    class AddNewCard(val cardData: CardData) : CardIntent() {
        override fun reduce(oldState: CardState): CardState =
            oldState

        override fun isValidFor(oldState: CardState): Boolean {
            return oldState.billingAddress != null
        }
    }

    class UpdateRequestState(private val status: CardRequestStatus) : CardIntent() {
        override fun reduce(oldState: CardState): CardState =
            oldState.copy(cardRequestStatus = status)
    }

    object ResetEveryPayAuth : CardIntent() {
        override fun reduce(oldState: CardState): CardState =
            oldState.copy(authoriseEverypayCard = null)
    }

    object ReadyToAddNewCard : CardIntent() {
        override fun reduce(oldState: CardState): CardState =
            oldState.copy(addCard = true)
    }

    object CardAddRequested : CardIntent() {
        override fun reduce(oldState: CardState): CardState =
            oldState.copy(addCard = false)
    }

    object CheckCardStatus : CardIntent() {
        override fun reduce(oldState: CardState): CardState =
            oldState
    }
}