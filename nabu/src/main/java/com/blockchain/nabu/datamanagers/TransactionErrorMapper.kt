package com.blockchain.nabu.datamanagers

import com.blockchain.nabu.models.responses.nabu.NabuApiException
import com.blockchain.nabu.models.responses.nabu.NabuErrorCodes
import com.blockchain.nabu.models.responses.nabu.NabuErrorStatusCodes

class TransactionErrorMapper {
    fun mapToTransactionError(exception: Throwable): TransactionError {
        return if (exception is NabuApiException) {
            when (exception.getErrorStatusCode()) {
                NabuErrorStatusCodes.InternalServerError -> {
                    when (exception.getErrorCode()) {
                        NabuErrorCodes.InternalServerError -> TransactionError.InternalServerError
                        NabuErrorCodes.AlbertExecutionError -> TransactionError.AlbertExecutionError
                        else -> TransactionError.UnexpectedError
                    }
                }
                NabuErrorStatusCodes.Conflict -> {
                    when (exception.getErrorCode()) {
                        NabuErrorCodes.TradingTemporarilyDisabled -> TransactionError.TradingTemporarilyDisabled
                        NabuErrorCodes.InsufficientBalance -> TransactionError.InsufficientBalance
                        NabuErrorCodes.IneligibleForSwap -> TransactionError.IneligibleForSwap
                        else -> TransactionError.OrderLimitReached
                    }
                }
                NabuErrorStatusCodes.BadRequest -> {
                    when (exception.getErrorCode()) {
                        NabuErrorCodes.OrderBelowMinLimit -> TransactionError.OrderBelowMin
                        NabuErrorCodes.OrderAboveMaxLimit -> TransactionError.OrderAboveMax
                        NabuErrorCodes.DailyLimitExceeded -> TransactionError.SwapDailyLimitExceeded
                        NabuErrorCodes.WeeklyLimitExceeded -> TransactionError.SwapWeeklyLimitExceeded
                        NabuErrorCodes.AnnualLimitExceeded -> TransactionError.SwapYearlyLimitExceeded
                        NabuErrorCodes.PendingOrdersLimitReached -> TransactionError.OrderLimitReached
                        NabuErrorCodes.InvalidCryptoAddress -> TransactionError.InvalidCryptoAddress
                        NabuErrorCodes.InvalidCryptoCurrency -> TransactionError.InvalidCryptoCurrency
                        NabuErrorCodes.InvalidFiatCurrency -> TransactionError.InvalidFiatCurrency
                        NabuErrorCodes.OrderDirectionDisabled -> TransactionError.OrderDirectionDisabled
                        NabuErrorCodes.InvalidOrExpiredQuote -> TransactionError.InvalidOrExpiredQuote
                        NabuErrorCodes.InvalidDestinationAmount -> TransactionError.InvalidDestinationAmount
                        else -> TransactionError.UnexpectedError
                    }
                }
                NabuErrorStatusCodes.Forbidden -> TransactionError.WithdrawalAlreadyPending
                else -> TransactionError.UnexpectedError
            }
        } else {
            TransactionError.UnexpectedError
        }
    }
}
