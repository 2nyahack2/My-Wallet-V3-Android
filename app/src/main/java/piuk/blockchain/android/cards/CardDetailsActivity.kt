package piuk.blockchain.android.cards

import android.content.Intent
import android.os.Bundle
import com.blockchain.preferences.SimpleBuyPrefs
import com.blockchain.nabu.datamanagers.PaymentMethod
import kotlinx.android.synthetic.main.activity_card_details.*
import kotlinx.android.synthetic.main.toolbar_general.toolbar_general
import org.koin.android.ext.android.inject
import piuk.blockchain.android.R
import piuk.blockchain.android.ui.base.BlockchainActivity
import piuk.blockchain.android.util.visible
import piuk.blockchain.android.util.gone
import piuk.blockchain.androidcore.utils.helperfunctions.consume

class CardDetailsActivity : BlockchainActivity(), AddCardNavigator, CardDetailsPersistence {
    override val alwaysDisableScreenshots: Boolean
        get() = false

    private var cardData: CardData? = null
    private val simpleBuyPrefs: SimpleBuyPrefs by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_card_details)
        setSupportActionBar(toolbar_general)

        if (savedInstanceState == null) {
            simpleBuyPrefs.clearCardState()
            supportFragmentManager.beginTransaction()
                .replace(R.id.content_frame, AddNewCardFragment(), AddNewCardFragment::class.simpleName)
                .commitAllowingStateLoss()
        }
    }

    override fun showLoading() {
        progress.visible()
    }

    override fun hideLoading() {
        progress.gone()
    }

    override fun onSupportNavigateUp(): Boolean = consume {
        onBackPressed()
    }

    override fun navigateToBillingDetails() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.content_frame, BillingAddressFragment(), BillingAddressFragment::class.simpleName)
            .addToBackStack(BillingAddressFragment::class.simpleName)
            .commitAllowingStateLoss()
    }

    override fun navigateToCardVerification() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.content_frame, CardVerificationFragment(), CardVerificationFragment::class.simpleName)
            .addToBackStack(CardVerificationFragment::class.simpleName)
            .commitAllowingStateLoss()
    }

    override fun exitWithSuccess(card: PaymentMethod.Card) {
        val data = Intent().apply {
            putExtras(Bundle().apply {
                putSerializable(CARD_KEY, card)
            })
        }
        setResult(RESULT_OK, data)
        finish()
    }

    override fun exitWithError() {
        finish()
    }

    override fun setCardData(cardData: CardData) {
        this.cardData = cardData
    }

    override fun getCardData(): CardData? = cardData

    companion object {
        const val CARD_KEY = "card_key"
        const val ADD_CARD_REQUEST_CODE = 3245
    }
}

data class CardData(val fullName: String, val number: String, val month: Int, val year: Int, val cvv: String)
