package piuk.blockchain.android.withdraw.mvi

class WithdrawStatePersistence {
    private var _state = WithdrawState()

    val state: WithdrawState
        get() = _state

    fun updateState(state: WithdrawState) {
        _state = state
    }

    fun clearState() {
        _state = WithdrawState()
    }
}