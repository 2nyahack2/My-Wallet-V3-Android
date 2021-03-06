package piuk.blockchain.android.coincore.fiat

import com.blockchain.android.testutils.rxInit
import com.blockchain.preferences.CurrencyPrefs
import com.blockchain.nabu.datamanagers.CustodialWalletManager
import com.blockchain.nabu.datamanagers.repositories.AssetBalancesRepository
import com.blockchain.nabu.models.responses.nabu.KycTierLevel
import com.blockchain.nabu.models.responses.nabu.KycTiers
import com.blockchain.nabu.service.TierService
import com.blockchain.wallet.DefaultLabels
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import io.reactivex.Single
import org.junit.Test
import org.junit.Rule
import piuk.blockchain.android.coincore.impl.CryptoInterestAccount
import piuk.blockchain.android.coincore.impl.CryptoNonCustodialAccount
import piuk.blockchain.android.coincore.impl.CustodialTradingAccount
import piuk.blockchain.androidcore.data.exchangerate.ExchangeRateDataManager

class FiatAssetTransferTest {

    @get:Rule
    val rxSchedulers = rxInit {
        mainTrampoline()
        ioTrampoline()
        computationTrampoline()
    }

    private val labels: DefaultLabels = mock()
    private val assetBalancesRepository: AssetBalancesRepository = mock()
    private val exchangeRateDataManager: ExchangeRateDataManager = mock()
    private val custodialWalletManager: CustodialWalletManager = mock()
    private val currencyPrefs: CurrencyPrefs = mock()
    private val tier: KycTiers = mock()

    private val tierService: TierService = mock {
        on { tiers() }.then { Single.just(tier) }
    }

    private val subject = FiatAsset(
        labels,
        assetBalancesRepository,
        exchangeRateDataManager,
        custodialWalletManager,
        tierService,
        currencyPrefs
    )

    @Test
    fun transferListForCustodialSource() {
        whenever(tier.isApprovedFor(KycTierLevel.GOLD)).thenReturn(true)
        whenever(currencyPrefs.selectedFiatCurrency).thenReturn(SELECTED_FIAT)
        whenever(custodialWalletManager.getSupportedFundsFiats(any(), any()))
            .thenReturn(Single.just(FIAT_ACCOUNT_LIST))

        whenever(labels.getDefaultCustodialFiatWalletLabel(any())).thenReturn(DEFAULT_LABEL)

        val sourceAccount: CustodialTradingAccount = mock()

        subject.transactionTargets(sourceAccount)
            .test()
            .assertNoErrors()
            .assertComplete()
            .assertValue { list ->
                list.isEmpty()
            }
    }

    @Test
    fun transferListForInterestSource() {
        val sourceAccount: CryptoInterestAccount = mock()

        subject.transactionTargets(sourceAccount)
            .test()
            .assertNoErrors()
            .assertResult(emptyList())
    }

    @Test
    fun transferListForNonCustodialSource() {
        val sourceAccount: CryptoNonCustodialAccount = mock()

        subject.transactionTargets(sourceAccount)
            .test()
            .assertNoErrors()
            .assertResult(emptyList())
    }

    @Test
    fun transferListForFiatSource() {
        val sourceAccount: FiatCustodialAccount = mock()

        subject.transactionTargets(sourceAccount)
            .test()
            .assertNoErrors()
            .assertResult(emptyList())
    }

    companion object {
        private const val DEFAULT_LABEL = "label"
        private const val SELECTED_FIAT = "USD"
        private val FIAT_ACCOUNT_LIST = listOf("USD", "GBP")
    }
}