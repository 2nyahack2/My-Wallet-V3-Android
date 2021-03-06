package piuk.blockchain.android.util

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Resources
import android.widget.ImageView
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import com.blockchain.wallet.DefaultLabels
import info.blockchain.balance.CryptoCurrency
import piuk.blockchain.android.R

@ColorRes
fun CryptoCurrency.colorRes(): Int =
    when (this) {
        CryptoCurrency.BTC -> R.color.color_bitcoin_logo
        CryptoCurrency.ETHER -> R.color.color_ether_logo
        CryptoCurrency.BCH -> R.color.color_bitcoin_cash_logo
        CryptoCurrency.XLM -> R.color.color_stellar_logo
        CryptoCurrency.PAX -> R.color.color_pax_logo
        CryptoCurrency.STX -> TODO("STUB: STX NOT IMPLEMENTED")
        CryptoCurrency.ALGO -> R.color.color_algo_logo
        CryptoCurrency.USDT -> R.color.color_usdt_logo
        CryptoCurrency.DGLD -> R.color.color_dgld_logo
    }

@ColorInt
fun CryptoCurrency.chartLineColour(context: Context) =
    ContextCompat.getColor(context,
        when (this) {
            CryptoCurrency.BTC -> R.color.color_bitcoin_logo
            CryptoCurrency.ETHER -> R.color.color_ether_logo
            CryptoCurrency.BCH -> R.color.color_bitcoin_cash_logo
            CryptoCurrency.XLM -> R.color.color_stellar_logo
            CryptoCurrency.PAX -> R.color.color_pax_logo
            CryptoCurrency.STX -> TODO("STUB: STX NOT IMPLEMENTED")
            CryptoCurrency.ALGO -> R.color.color_algo_logo
            CryptoCurrency.USDT -> R.color.color_usdt_logo
            CryptoCurrency.DGLD -> R.color.dgld_chart
        })

@DrawableRes
fun CryptoCurrency.drawableResFilled(): Int =
    when (this) {
        CryptoCurrency.BTC -> R.drawable.vector_bitcoin_colored
        CryptoCurrency.ETHER -> R.drawable.vector_eth_colored
        CryptoCurrency.BCH -> R.drawable.vector_bitcoin_cash_colored
        CryptoCurrency.XLM -> R.drawable.vector_xlm_colored
        CryptoCurrency.PAX -> R.drawable.vector_pax_colored
        CryptoCurrency.STX -> R.drawable.ic_logo_stx
        CryptoCurrency.ALGO -> R.drawable.vector_algo_colored
        CryptoCurrency.USDT -> R.drawable.vector_usdt_colored
        CryptoCurrency.DGLD -> R.drawable.vector_dgld_colored
    }

@DrawableRes
fun CryptoCurrency.coinIconWhite(): Int =
    when (this) {
        CryptoCurrency.BTC -> R.drawable.vector_bitcoin_white
        CryptoCurrency.ETHER -> R.drawable.vector_eth_white
        CryptoCurrency.BCH -> R.drawable.vector_bitcoin_cash_white
        CryptoCurrency.XLM -> R.drawable.vector_xlm_white
        CryptoCurrency.PAX -> R.drawable.vector_pax_white
        CryptoCurrency.STX -> TODO("STUB: STX NOT IMPLEMENTED")
        CryptoCurrency.ALGO -> R.drawable.vector_algo_white
        CryptoCurrency.USDT -> R.drawable.vector_usdt_white
        CryptoCurrency.DGLD -> R.drawable.vector_dgld_white
    }

@DrawableRes
fun CryptoCurrency.maskedAsset(): Int =
    when (this) {
        CryptoCurrency.BTC -> R.drawable.ic_btc_circled_mask
        CryptoCurrency.XLM -> R.drawable.ic_xlm_circled_mask
        CryptoCurrency.ETHER -> R.drawable.ic_eth_circled_mask
        CryptoCurrency.PAX -> R.drawable.ic_usdd_circled_mask
        CryptoCurrency.BCH -> R.drawable.ic_bch_circled_mask
        CryptoCurrency.STX -> TODO("STUB: STX NOT IMPLEMENTED")
        CryptoCurrency.ALGO -> R.drawable.ic_algo_circled_mask
        CryptoCurrency.USDT -> R.drawable.ic_usdt_circled_mask
        CryptoCurrency.DGLD -> R.drawable.ic_dgld_circled_mask
    }

fun ImageView.setImageDrawable(@DrawableRes res: Int) {
    setImageDrawable(AppCompatResources.getDrawable(context, res))
}

fun ImageView.setCoinIcon(currency: CryptoCurrency) {
    setImageDrawable(currency.drawableResFilled())
}

@DrawableRes
fun CryptoCurrency.errorIcon(): Int =
    when (this) {
        CryptoCurrency.BTC -> R.drawable.vector_btc_error
        CryptoCurrency.BCH -> R.drawable.vector_bch_error
        CryptoCurrency.ETHER -> R.drawable.vector_eth_error
        CryptoCurrency.XLM -> R.drawable.vector_xlm_error
        CryptoCurrency.PAX -> R.drawable.vector_pax_error
        CryptoCurrency.STX -> TODO("STUB: STX NOT IMPLEMENTED")
        CryptoCurrency.ALGO -> R.drawable.vector_algo_error
        CryptoCurrency.USDT -> R.drawable.vector_usdt_error
        CryptoCurrency.DGLD -> R.drawable.vector_dgld_error
    }

@StringRes
fun CryptoCurrency.assetName() =
    when (this) {
        CryptoCurrency.BTC -> R.string.bitcoin
        CryptoCurrency.ETHER -> R.string.ethereum
        CryptoCurrency.BCH -> R.string.bitcoin_cash
        CryptoCurrency.XLM -> R.string.lumens
        CryptoCurrency.PAX -> R.string.usd_pax_1
        CryptoCurrency.STX -> R.string.stacks_1
        CryptoCurrency.ALGO -> R.string.algorand
        CryptoCurrency.USDT -> R.string.usdt
        CryptoCurrency.DGLD -> R.string.dgld
    }

@ColorRes
fun CryptoCurrency.assetTint() =
    when (this) {
        CryptoCurrency.BTC -> R.color.btc_bkgd
        CryptoCurrency.BCH -> R.color.bch_bkgd
        CryptoCurrency.ETHER -> R.color.ether_bkgd
        CryptoCurrency.PAX -> R.color.pax_bkgd
        CryptoCurrency.XLM -> R.color.xlm_bkgd
        CryptoCurrency.ALGO -> R.color.algo_bkgd
        CryptoCurrency.USDT -> R.color.usdt_bkgd
        CryptoCurrency.DGLD -> R.color.dgld_bkgd
        else -> {
            android.R.color.transparent // STX left, do nothing
        }
    }

@ColorRes
fun CryptoCurrency.assetFilter() =
    when (this) {
        CryptoCurrency.BTC -> R.color.btc
        CryptoCurrency.BCH -> R.color.bch
        CryptoCurrency.ETHER -> R.color.eth
        CryptoCurrency.PAX -> R.color.pax
        CryptoCurrency.XLM -> R.color.xlm
        CryptoCurrency.ALGO -> R.color.algo
        CryptoCurrency.USDT -> R.color.usdt
        CryptoCurrency.DGLD -> R.color.black
        else -> {
            android.R.color.transparent // STX left, do nothing
        }
    }

fun CryptoCurrency.makeBlockExplorerUrl(
    transactionHash: String
) = when (this) {
    CryptoCurrency.BTC -> "https://www.blockchain.com/btc/tx/"
    CryptoCurrency.BCH -> "https://www.blockchain.com/bch/tx/"
    CryptoCurrency.XLM -> "https://stellarchain.io/tx/"
    CryptoCurrency.ETHER,
    CryptoCurrency.PAX,
    CryptoCurrency.USDT,
    CryptoCurrency.DGLD -> "https://www.blockchain.com/eth/tx/"
    CryptoCurrency.ALGO -> "https://algoexplorer.io/tx/"
    CryptoCurrency.STX -> TODO("STUB: STX NOT IMPLEMENTED")
} + transactionHash

fun CryptoCurrency.getDecimalPlaces(): Int =
    when (this) {
        CryptoCurrency.BTC,
        CryptoCurrency.ETHER,
        CryptoCurrency.BCH,
        CryptoCurrency.PAX,
        CryptoCurrency.ALGO,
        CryptoCurrency.USDT,
        CryptoCurrency.DGLD -> 2
        CryptoCurrency.XLM -> 4
        CryptoCurrency.STX -> TODO("STUB: STX NOT IMPLEMENTED")
    }

fun ImageView.setAssetIconColours(cryptoCurrency: CryptoCurrency, context: Context) {
    setBackgroundResource(R.drawable.bkgd_tx_circle)
    ViewCompat.setBackgroundTintList(this,
        ColorStateList.valueOf(ContextCompat.getColor(context, cryptoCurrency.assetTint())))
    setColorFilter(ContextCompat.getColor(context, cryptoCurrency.assetFilter()))
}

internal class ResourceDefaultLabels(
    private val resources: Resources
) : DefaultLabels {

    override fun getDefaultNonCustodialWalletLabel(cryptoCurrency: CryptoCurrency): String =
        resources.getString(
            when (cryptoCurrency) {
                CryptoCurrency.BTC -> R.string.btc_default_wallet_name
                CryptoCurrency.ETHER -> R.string.eth_default_account_label
                CryptoCurrency.BCH -> R.string.bch_default_account_label
                CryptoCurrency.XLM -> R.string.xlm_default_account_label
                CryptoCurrency.PAX -> R.string.pax_default_account_label_1
                CryptoCurrency.STX -> TODO("STUB: STX NOT IMPLEMENTED")
                CryptoCurrency.ALGO -> R.string.algo_default_account_label
                CryptoCurrency.USDT -> R.string.usdt_default_account_label
                CryptoCurrency.DGLD -> R.string.dgld_default_account_label
            }
        )

    override fun getDefaultCustodialWalletLabel(cryptoCurrency: CryptoCurrency): String {
        val asset = resources.getString(cryptoCurrency.assetName())
        return resources.getString(R.string.custodial_wallet_default_label, asset)
    }

    override fun getAssetMasterWalletLabel(cryptoCurrency: CryptoCurrency): String =
        resources.getString(cryptoCurrency.assetName())

    override fun getAllWalletLabel(): String =
        resources.getString(R.string.default_label_all_wallets)

    override fun getDefaultInterestWalletLabel(cryptoCurrency: CryptoCurrency): String =
        resources.getString(R.string.default_label_interest_wallet)

    override fun getDefaultExchangeWalletLabel(cryptoCurrency: CryptoCurrency): String =
        resources.getString(R.string.exchange_default_account_label, cryptoCurrency.displayTicker)

    override fun getDefaultCustodialFiatWalletLabel(fiatCurrency: String): String =
        resources.getString(R.string.currency_wallet, fiatCurrency)
}

class AssetResourceFactory(
    private val resources: Resources
) {
    fun assetName(asset: CryptoCurrency): String =
        resources.getString(asset.assetName())
}