package piuk.blockchain.android.cards.partners

import com.blockchain.nabu.datamanagers.CustodialWalletManager
import com.blockchain.nabu.datamanagers.Partner
import com.blockchain.nabu.models.responses.simplebuy.CardPartnerAttributes
import com.blockchain.nabu.models.responses.simplebuy.EveryPayAttrs
import io.reactivex.Single
import piuk.blockchain.android.cards.CardData
import piuk.blockchain.android.everypay.models.CardDetailRequest
import piuk.blockchain.android.everypay.models.CardDetailResponse
import piuk.blockchain.android.everypay.models.CcDetails
import piuk.blockchain.android.everypay.service.EveryPayService
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Random
import java.util.Date

interface CardActivator {
    val partner: Partner
    fun activateCard(cardData: CardData, cardId: String): Single<out CompleteCardActivation>
    fun paymentAttributes(): CardPartnerAttributes
}

class EverypayCardActivator(
    private val submitCardService: EveryPayService,
    private val custodialWalletManager: CustodialWalletManager
) : CardActivator {

    override val partner: Partner = Partner.EVERYPAY

    override fun activateCard(cardData: CardData, cardId: String):
            Single<CompleteCardActivation.EverypayCompleteCardActivationDetails> =
        custodialWalletManager.activateCard(cardId, CardPartnerAttributes(
            EveryPayAttrs(redirectUrl)
        )).flatMap { credentials ->
            credentials.everypay?.let { everyPay ->
                submitCard(cardData, everyPay.apiUsername, everyPay.mobileToken).map {
                    CompleteCardActivation.EverypayCompleteCardActivationDetails(
                        paymentLink = credentials.everypay?.paymentLink ?: "",
                        exitLink = redirectUrl
                    )
                }
            }
                ?: Single.error<CompleteCardActivation.EverypayCompleteCardActivationDetails>(
                    Throwable("Card partner not supported")
                )
        }

    override fun paymentAttributes(): CardPartnerAttributes =
        CardPartnerAttributes(EveryPayAttrs(redirectUrl))

    private fun submitCard(cardData: CardData, apiUserName: String, mobileToken: String): Single<CardDetailResponse> =
        submitCardService.getCardDetail(
            CardDetailRequest(
                apiUsername = apiUserName,
                mobileToken = mobileToken,
                nonce = nonce(),
                timestamp = timestamp(),
                ccDetails = CcDetails(
                    number = cardData.number,
                    month = cardData.month.toString(),
                    year = cardData.year.toString(),
                    cvc = cardData.cvv,
                    holderName = cardData.fullName
                )
            ), "Bearer $mobileToken"
        )

    private fun timestamp(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.getDefault()).format(Date())

    private fun nonce(): String {
        val source = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
        val len = 30
        val sb = StringBuilder(len)
        val random = Random()

        for (i in 0 until len) {
            sb.append(source[random.nextInt(source.length)])
        }
        return sb.toString()
    }

    companion object {
        const val redirectUrl = "https://google.com"
    }
}

sealed class CompleteCardActivation {
    data class EverypayCompleteCardActivationDetails(val paymentLink: String, val exitLink: String) :
        CompleteCardActivation()
}
