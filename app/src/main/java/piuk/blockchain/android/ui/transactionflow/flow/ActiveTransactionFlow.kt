package piuk.blockchain.android.ui.transactionflow.flow

import piuk.blockchain.android.ui.transactionflow.DialogFlow

class ActiveTransactionFlow {
    private lateinit var flow: DialogFlow

    fun getFlow(): DialogFlow = if (::flow.isInitialized) flow else throw IllegalStateException("Flow not initialised")

    fun setFlow(flow: DialogFlow) {
        this.flow = flow
    }
}