package piuk.blockchain.android.withdraw.mvi

import com.blockchain.nabu.datamanagers.Beneficiary
import info.blockchain.balance.FiatValue
import info.blockchain.balance.Money
import piuk.blockchain.android.simplebuy.ErrorState
import piuk.blockchain.android.ui.base.mvi.MviState
import piuk.blockchain.androidcore.utils.helperfunctions.unsafeLazy

data class WithdrawState(
    val currency: String? = null,
    private val balance: FiatValue? = null,
    val amount: FiatValue? = null,
    val isLoading: Boolean = false,
    val errorState: ErrorState? = null,
    val selectedBank: Beneficiary? = null,
    val fee: FiatValue? = null,
    val withdrawSucceeded: Boolean = false,
    val withdrawRequested: Boolean = false,
    val beneficiaries: List<Beneficiary>? = null
) : MviState {
    fun amountIsValid(): Boolean =
        amount?.let { amount ->
            availableForWithdraw?.let { balance ->
                amount <= balance && amount.isZero.not()
            } ?: false
        } ?: false

    val availableForWithdraw: FiatValue? by unsafeLazy {
        if (balance != null && fee != null && currency != null) {
            FiatValue.fromMinor(currency, 0L.coerceAtLeast((balance.toBigInteger() - fee.toBigInteger()).toLong()))
        } else
            null
    }

    val amountError: AmountError? by unsafeLazy {
        availableForWithdraw?.let { available ->
            amount?.let { amount ->
                if (amount > available) AmountError.ABOVE_MAX else null
            }
        }
    }

    val total: Money? by unsafeLazy {
        amount?.let { amount ->
            fee?.let { fee ->
                amount + fee
            }
        }
    }
}

enum class AmountError {
    ABOVE_MAX
}