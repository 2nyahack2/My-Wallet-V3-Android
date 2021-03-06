package piuk.blockchain.android.ui.transactionflow.engine

import com.blockchain.logging.CrashLogger

class TxFlowErrorReporting(private val crashLogger: CrashLogger) {
    fun log(error: TxFlowLogError) {
        crashLogger.logException(error)
    }
}

sealed class TxFlowLogError(msg: String, cause: Throwable) : Exception(msg, cause) {
    class LoopFail(e: Throwable) : TxFlowLogError("LOOP FAILED", e)
    class ResetFail(e: Throwable) : TxFlowLogError("RESET FAILED", e)
    class AddressFail(e: Throwable) : TxFlowLogError("VALIDATE ADDRESS FAILED", e)
    class TargetFail(e: Throwable) : TxFlowLogError("SELECT TARGET FAIL", e)
    class BalanceFail(e: Throwable) : TxFlowLogError("BALANCE FAIL", e)
    class ExecuteFail(e: Throwable) : TxFlowLogError("EXECUTE FAIL", e)
    class ValidateFail(e: Throwable) : TxFlowLogError("VALIDATE ALL FAILED", e)
}
