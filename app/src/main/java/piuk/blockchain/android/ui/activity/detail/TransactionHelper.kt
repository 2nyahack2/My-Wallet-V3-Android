package piuk.blockchain.android.ui.activity.detail

import info.blockchain.balance.CryptoCurrency
import info.blockchain.balance.CryptoValue
import info.blockchain.balance.Money
import info.blockchain.wallet.multiaddress.TransactionSummary
import info.blockchain.wallet.payment.Payment
import org.apache.commons.lang3.tuple.Pair
import piuk.blockchain.android.coincore.NonCustodialActivitySummaryItem
import piuk.blockchain.androidcore.data.bitcoincash.BchDataManager
import piuk.blockchain.androidcore.data.payload.PayloadDataManager
import java.util.ArrayList
import java.util.HashMap
import java.util.TreeMap

@Deprecated("Switch to coincore")
class TransactionHelper(
    private val payloadDataManager: PayloadDataManager,
    private val bchDataManager: BchDataManager
) {
    /**
     * Return a Pair of maps that correspond to the inputs and outputs of a transaction, whilst
     * filtering out Change addresses.
     *
     * @param tx A [TransactionSummary] object
     * @return A Pair of Maps representing the input and output of the transaction
     */
    fun filterNonChangeBtcAddresses(
        tx: NonCustodialActivitySummaryItem
    ): Pair<Map<String, Money>, Map<String, Money>> {
        val inputMap = HashMap<String, Money>()
        val outputMap = HashMap<String, Money>()
        val inputXpubList = ArrayList<String>()

        // Inputs / From field
        if (tx.transactionType == TransactionSummary.TransactionType.RECEIVED && tx.inputsMap.isNotEmpty()) {
            // Only 1 addr for receive
            val treeMap = TreeMap(tx.inputsMap)
            inputMap[treeMap.lastKey()] = treeMap.lastEntry().value
        } else {
            for (inputAddress in tx.inputsMap.keys) {
                val inputValue = tx.inputsMap[inputAddress] ?: CryptoValue.zero(CryptoCurrency.BTC)
                // Move or Send
                // The address belongs to us
                val xpub = payloadDataManager.getXpubFromAddress(inputAddress)
                // Address belongs to xpub we own
                if (xpub != null) { // Only add xpub once
                    if (!inputXpubList.contains(xpub)) {
                        inputMap[inputAddress] = inputValue
                        inputXpubList.add(xpub)
                    }
                } else { // Legacy Address or someone else's address
                    inputMap[inputAddress] = inputValue
                }
            }
        }

        // Outputs / To field
        for (outputAddress in tx.outputsMap.keys) {
            val outputValue = tx.outputsMap[outputAddress] ?: CryptoValue.zero(CryptoCurrency.BTC)
            if (payloadDataManager.isOwnHDAddress(outputAddress)) {
                // If output address belongs to an xpub we own - we have to check if it's change
                val xpub = payloadDataManager.getXpubFromAddress(outputAddress)
                if (inputXpubList.contains(xpub)) {
                    continue // change back to same xpub
                }
                // Receiving to same address multiple times?
                if (outputMap.containsKey(outputAddress)) {
                    val prevAmount = outputMap[outputAddress]!! + outputValue
                    outputMap[outputAddress] = prevAmount
                } else {
                    outputMap[outputAddress] = outputValue
                }
            } else if (
                payloadDataManager.wallet!!.legacyAddressStringList.contains(outputAddress)
            ) { // If output address belongs to a legacy address we own - we have to check if it's change
                // If it goes back to same address AND if it's not the total amount sent
                // (inputs x and y could send to output y in which case y is not receiving change,
                // but rather the total amount)
                if (inputMap.containsKey(outputAddress) &&
                    outputValue.toBigInteger().abs().compareTo(tx.value.toBigInteger()) != 0
                ) {
                    continue // change back to same input address
                }
                // Output more than tx amount - change
                if (outputValue.toBigInteger().abs() > tx.value.toBigInteger()) {
                    continue
                }
                outputMap[outputAddress] = outputValue
            } else {
                if (tx.transactionType != TransactionSummary.TransactionType.RECEIVED) {
                    outputMap[outputAddress] = outputValue
                }
            }
        }
        return Pair.of(inputMap, outputMap)
    }

    fun filterNonChangeBchAddresses(
        tx: NonCustodialActivitySummaryItem
    ): Pair<Map<String, Money>, Map<String, Money>> {
        val inputMap = HashMap<String, Money>()
        val outputMap = HashMap<String, Money>()
        val inputXpubList = ArrayList<String>()
        // Inputs / From field
        if (tx.transactionType == TransactionSummary.TransactionType.RECEIVED && tx.inputsMap.isNotEmpty()) {
            for ((address, value) in tx.inputsMap) {
                if (value.toBigInteger() == Payment.DUST)
                    continue
                inputMap[address] = value
            }
        } else {
            for (inputAddress in tx.inputsMap.keys) {
                val inputValue = tx.inputsMap[inputAddress] ?: CryptoValue.zero(CryptoCurrency.BCH)
                // Move or Send
                // The address belongs to us
                val xpub = bchDataManager.getXpubFromAddress(inputAddress)
                // Skip dust input
                if (inputValue.toBigInteger() == Payment.DUST)
                    continue
                // Address belongs to xpub we own
                if (xpub != null) { // Only add xpub once
                    if (!inputXpubList.contains(xpub)) {
                        inputMap[inputAddress] = inputValue
                        inputXpubList.add(xpub)
                    }
                } else { // Legacy Address or someone else's address
                    inputMap[inputAddress] = inputValue
                }
            }
        }
        // Outputs / To field
        for (outputAddress in tx.outputsMap.keys) {
            val outputValue = tx.outputsMap[outputAddress] ?: CryptoValue.zero(CryptoCurrency.BCH)
            // Skip dust output
            if (outputValue.toBigInteger() == Payment.DUST)
                continue

            if (bchDataManager.isOwnAddress(outputAddress)) {
                // If output address belongs to an xpub we own - we have to check if it's change
                val xpub = bchDataManager.getXpubFromAddress(outputAddress)
                if (inputXpubList.contains(xpub)) {
                    continue // change back to same xpub
                }
                // Receiving to same address multiple times?
                if (outputMap.containsKey(outputAddress)) {
                    val prevAmount = outputMap[outputAddress]!! + outputValue
                    outputMap[outputAddress] = prevAmount
                } else {
                    outputMap[outputAddress] = outputValue
                }
            } else if (
                bchDataManager.getLegacyAddressStringList().contains(outputAddress)
            ) { // If output address belongs to a legacy address we own - we have to check if it's
                // change
                // If it goes back to same address AND if it's not the total amount sent
                // (inputs x and y could send to output y in which case y is not receiving change,
                // but rather the total amount)
                if (inputMap.containsKey(outputAddress) &&
                    outputValue.toBigInteger().abs().compareTo(tx.value.toBigInteger()) != 0
                ) {
                    continue // change back to same input address
                }
                // Output more than tx amount - change
                if (outputValue.toBigInteger().abs() > tx.value.toBigInteger()) {
                    continue
                }
                outputMap[outputAddress] = outputValue
            } else {
                if (tx.transactionType != TransactionSummary.TransactionType.RECEIVED) {
                    outputMap[outputAddress] = outputValue
                }
            }
        }
        return Pair.of(
            inputMap,
            outputMap
        )
    }
}