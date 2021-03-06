package piuk.blockchain.android.simplebuy

import com.blockchain.nabu.datamanagers.BankAccount
import com.blockchain.nabu.datamanagers.BankDetail
import com.blockchain.nabu.datamanagers.custodialwalletimpl.PaymentAccountMapper
import com.blockchain.nabu.models.responses.simplebuy.BankAccountResponse
import piuk.blockchain.android.R
import piuk.blockchain.android.util.StringUtils

class GBPPaymentAccountMapper(private val stringUtils: StringUtils) : PaymentAccountMapper {

    override fun map(bankAccountResponse: BankAccountResponse): BankAccount? {
        if (bankAccountResponse.currency != "GBP") return null
        return BankAccount(
            listOf(
                BankDetail(stringUtils.getString(R.string.account_number),
                    bankAccountResponse.agent.account ?: return null,
                    true),
                BankDetail(stringUtils.getString(R.string.sort_code),
                    bankAccountResponse.agent.code ?: return null,
                    true),
                BankDetail(stringUtils.getString(R.string.recipient_name),
                    bankAccountResponse.agent.recipient ?: return null)
            )
        )
    }
}

class EURPaymentAccountMapper(private val stringUtils: StringUtils) : PaymentAccountMapper {

    override fun map(bankAccountResponse: BankAccountResponse): BankAccount? {
        if (bankAccountResponse.currency != "EUR") return null
        return BankAccount(
            listOf(

                BankDetail(stringUtils.getString(R.string.bank_code_swift_bic),
                    bankAccountResponse.agent.account ?: "LHVBEE22",
                    true),

                BankDetail(stringUtils.getString(R.string.bank_name),
                    bankAccountResponse.agent.name ?: return null,
                    true),

                BankDetail(stringUtils.getString(R.string.bank_country),
                    bankAccountResponse.agent.country ?: stringUtils.getString(R.string.estonia)),

                BankDetail(stringUtils.getString(R.string.iban),
                    bankAccountResponse.address ?: return null, true),

                BankDetail(stringUtils.getString(R.string.recipient_name),
                    bankAccountResponse.agent.recipient ?: "")
            )
        )
    }
}