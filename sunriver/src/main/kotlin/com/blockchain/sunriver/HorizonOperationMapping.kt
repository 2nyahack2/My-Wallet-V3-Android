package com.blockchain.sunriver

import com.blockchain.sunriver.models.XlmTransaction
import info.blockchain.balance.CryptoCurrency
import info.blockchain.balance.CryptoValue
import org.stellar.sdk.KeyPair
import org.stellar.sdk.Memo
import org.stellar.sdk.MemoHash
import org.stellar.sdk.MemoId
import org.stellar.sdk.MemoReturnHash
import org.stellar.sdk.MemoText
import org.stellar.sdk.responses.operations.CreateAccountOperationResponse
import org.stellar.sdk.responses.operations.OperationResponse
import org.stellar.sdk.responses.operations.PaymentOperationResponse

internal fun List<OperationResponse>.map(accountId: String, horizonProxy: HorizonProxy): List<XlmTransaction> =
    filter { it is CreateAccountOperationResponse || it is PaymentOperationResponse }
        .map {
            mapOperationResponse(
                it,
                accountId,
                horizonProxy
            )
        }

internal fun mapOperationResponse(
    operationResponse: OperationResponse,
    usersAccountId: String,
    horizonProxy: HorizonProxy
): XlmTransaction =
    when (operationResponse) {
        is CreateAccountOperationResponse -> operationResponse.mapCreate(usersAccountId, horizonProxy)
        is PaymentOperationResponse -> operationResponse.mapPayment(usersAccountId, horizonProxy)
        else -> throw IllegalArgumentException("Unsupported operation type ${operationResponse.javaClass.simpleName}")
    }

private fun CreateAccountOperationResponse.mapCreate(
    usersAccountId: String,
    horizonProxy: HorizonProxy
): XlmTransaction {
    return try {
        val transactionResponse = horizonProxy.getTransaction(transactionHash)
        val memo = transactionResponse.memo ?: Memo.none()
        val fee = CryptoValue.fromMinor(CryptoCurrency.XLM, transactionResponse.feeCharged.toBigInteger())
        toXlmTransaction(usersAccountId, startingBalance, memo, account, funder, fee)
    } catch (e: Throwable) {
        // There's a bug in the xlm sdk (horizonProxy.getTransaction()) which throws a
        // NoSuchMethodError when parsing a int memo on pre jdk 1.8 devices
        // In this case, we can't know the fee but everything else is known, so:
        toXlmTransaction(usersAccountId, startingBalance, Memo.none(), account, funder, CryptoValue.ZeroXlm)
    }
}

private fun PaymentOperationResponse.mapPayment(
    usersAccountId: String,
    horizonProxy: HorizonProxy
): XlmTransaction {
    return try {
        val transactionResponse = horizonProxy.getTransaction(transactionHash)
        val memo = transactionResponse.memo ?: Memo.none()
        val fee = CryptoValue.fromMinor(CryptoCurrency.XLM, transactionResponse.feeCharged.toBigInteger())
        toXlmTransaction(usersAccountId, amount, memo, to, from, fee)
    } catch (e: Throwable) {
        // There's a bug in the xlm sdk (horizonProxy.getTransaction()) which throws a
        // NoSuchMethodError when parsing a int memo on pre jdk 1.8 devices
        // In this case, we can't know the fee but everything else is known, so:
        toXlmTransaction(usersAccountId, amount, Memo.none(), to, from, CryptoValue.ZeroXlm)
    }
}

private fun OperationResponse.toXlmTransaction(
    usersAccountId: String,
    amount: String,
    memo: Memo,
    to: String,
    from: String,
    fee: CryptoValue
) = XlmTransaction(
        timeStamp = createdAt,
        value = deltaValueForAccount(usersAccountId, KeyPair.fromAccountId(from), amount),
        fee = fee,
        hash = transactionHash,
        memo = mapMemo(memo),
        to = KeyPair.fromAccountId(to).toHorizonKeyPair().neuter(),
        from = KeyPair.fromAccountId(from).toHorizonKeyPair().neuter()
    )

private fun deltaValueForAccount(
    usersAccountId: String,
    from: KeyPair,
    value: String
): CryptoValue {
    val valueAsBigDecimal = value.toBigDecimal()
    val deltaForThisAccount =
        if (from.accountId == usersAccountId) {
            valueAsBigDecimal.negate()
        } else {
            valueAsBigDecimal
        }
    return CryptoValue.fromMajor(CryptoCurrency.XLM, deltaForThisAccount)
}

private fun mapMemo(memo: Memo): com.blockchain.sunriver.Memo =
    when (memo) {
        is MemoId -> Memo(memo.id.toString(), "id")
        is MemoHash -> Memo(memo.hexValue, "hash")
        is MemoReturnHash -> Memo(memo.hexValue, "return")
        is MemoText -> Memo(memo.text, "text")
        else -> com.blockchain.sunriver.Memo.None
    }
