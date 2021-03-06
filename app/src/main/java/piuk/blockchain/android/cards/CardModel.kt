package piuk.blockchain.android.cards

import com.blockchain.preferences.CurrencyPrefs
import com.blockchain.preferences.SimpleBuyPrefs
import com.blockchain.nabu.datamanagers.Partner
import com.blockchain.nabu.datamanagers.custodialwalletimpl.CardStatus
import com.google.gson.Gson
import io.reactivex.Scheduler
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.subscribeBy
import piuk.blockchain.android.cards.partners.CardActivator
import piuk.blockchain.android.cards.partners.CompleteCardActivation
import piuk.blockchain.android.simplebuy.SimpleBuyInteractor
import piuk.blockchain.android.ui.base.mvi.MviModel
import java.lang.IllegalStateException

class CardModel(
    scheduler: Scheduler,
    currencyPrefs: CurrencyPrefs,
    private val interactor: SimpleBuyInteractor,
    private val prefs: SimpleBuyPrefs,
    private val cardActivators: List<CardActivator>,
    private val gson: Gson
) : MviModel<CardState, CardIntent>(gson.fromJson(prefs.cardState(), CardState::class.java)
    ?: CardState(currencyPrefs.selectedFiatCurrency), scheduler) {

    override fun performAction(previousState: CardState, intent: CardIntent): Disposable? =
        when (intent) {
            is CardIntent.AddNewCard -> interactor.addNewCard(previousState.fiatCurrency,
                previousState.billingAddress
                    ?: throw IllegalStateException("No billing address was provided")
            ).doOnSubscribe {
                process(CardIntent.UpdateRequestState(CardRequestStatus.Loading))
            }.subscribeBy(onSuccess = {
                if (it.partner == Partner.EVERYPAY) {
                    process(CardIntent.ActivateEveryPayCard(cardId = it.cardId,
                        card = intent.cardData))
                }
                process(CardIntent.UpdateCardId(it.cardId))
            }, onError = {
                process(CardIntent.UpdateRequestState(CardRequestStatus.Error(CardError.CREATION_FAILED)))
            })
            is CardIntent.ActivateEveryPayCard -> cardActivators.first { it.partner == Partner.EVERYPAY }.activateCard(
                intent.card,
                intent.cardId
            ).doOnSubscribe {
                process(CardIntent.UpdateRequestState(CardRequestStatus.Loading))
            }.subscribeBy(onError = {
                process(CardIntent.UpdateRequestState(CardRequestStatus.Error(CardError.ACTIVATION_FAIL)))
            }, onSuccess = {
                if (it is CompleteCardActivation.EverypayCompleteCardActivationDetails)
                    process(CardIntent.AuthoriseEverypayCard(it.paymentLink, it.exitLink))
            })
            is CardIntent.CheckCardStatus -> interactor.pollForCardStatus(previousState.cardId
                ?: throw IllegalStateException("No billing address was provided"))
                .doOnSubscribe {
                    process(CardIntent.UpdateRequestState(CardRequestStatus.Loading))
                }
                .subscribeBy(onError = {
                    process(CardIntent.UpdateRequestState(CardRequestStatus.Error(CardError.PENDING_AFTER_POLL)))
                }, onSuccess = {
                    process(it)
                    if (it.cardDetails.status == CardStatus.ACTIVE)
                        process(CardIntent.UpdateRequestState(CardRequestStatus.Success(
                            it.cardDetails
                        )))
                    else
                        process(CardIntent.UpdateRequestState(CardRequestStatus.Error(
                            if (it.cardDetails.status == CardStatus.PENDING) CardError.PENDING_AFTER_POLL
                            else CardError.LINK_FAILED)
                        ))
                })
            else -> null
        }

    override fun onStateUpdate(s: CardState) {
        prefs.updateCardState(gson.toJson(s))
    }
}
