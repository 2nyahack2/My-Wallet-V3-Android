package com.blockchain.nabu.models.responses.nabu

import android.annotation.SuppressLint
import com.squareup.moshi.FromJson
import com.squareup.moshi.Json
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.ToJson
import java.text.SimpleDateFormat
import java.util.Date

data class AirdropStatusList(
    private val userCampaignsInfoResponseList: List<AirdropStatus> = emptyList()
) {
    operator fun get(campaignName: String): AirdropStatus? {
        return userCampaignsInfoResponseList.firstOrNull { it.campaignName == campaignName }
    }

    val airdropList
        get() = userCampaignsInfoResponseList
}

data class AirdropStatus(
    val campaignName: String,
    val campaignEndDate: Date?, // NOT USED!
    val campaignState: CampaignState,
    @field:Json(name = "userCampaignState")
    val userState: UserCampaignState,
    val attributes: CampaignAttributes,
    val updatedAt: Date,
    @field:Json(name = "userCampaignTransactionResponseList")
    val txResponseList: List<CampaignTransaction>
)

data class CampaignAttributes(
    @field:Json(name = "x-campaign-address")
    val campaignAddress: String = "",
    @field:Json(name = "x-campaign-code")
    val campaignCode: String = "",
    @field:Json(name = "x-campaign-email")
    val campaignEmail: String = "",
    @field:Json(name = "x-campaign-reject-reason")
    val rejectReason: String = ""
)

data class CampaignTransaction(
    val fiatValue: Long,
    val fiatCurrency: String,
    val withdrawalQuantity: Long,
    val withdrawalCurrency: String,
    val withdrawalAt: Date,
    @field:Json(name = "userCampaignTransactionState")
    val transactionState: CampaignTransactionState
)

sealed class CampaignState {
    object None : CampaignState()
    object Started : CampaignState()
    object Ended : CampaignState()
}

sealed class UserCampaignState {
    object None : UserCampaignState()
    object Registered : UserCampaignState()
    object TaskFinished : UserCampaignState()
    object RewardSend : UserCampaignState()
    object RewardReceived : UserCampaignState()
    object Failed : UserCampaignState()
}

sealed class CampaignTransactionState {
    object None : CampaignTransactionState()
    object PendingDeposit : CampaignTransactionState()
    object FinishedDeposit : CampaignTransactionState()
    object PendingWithdrawal : CampaignTransactionState()
    object FinishedWithdrawal : CampaignTransactionState()
    object Failed : CampaignTransactionState()
}

// -------------------------------------------------------------------------------------------------------
// Moshi JSON adapters

class IsoDateMoshiAdapter {

    @SuppressLint("SimpleDateFormat")
    private val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss") // ISO-8601 date format

    @FromJson
    fun fromJson(input: String): Date = format.parse(input)

    @ToJson
    fun toJson(date: Date): String = format.format(date)
}

class UserCampaignStateMoshiAdapter {
    @FromJson
    fun fromJson(input: String): UserCampaignState =
        when (input) {
            NONE -> UserCampaignState.None
            REGISTERED -> UserCampaignState.Registered
            TASK_FINISHED -> UserCampaignState.TaskFinished
            REWARD_SEND -> UserCampaignState.RewardSend
            REWARD_RECEIVED -> UserCampaignState.RewardReceived
            FAILED -> UserCampaignState.Failed
            else -> throw JsonDataException("Unknown UserCampaignState: $input")
        }

    @ToJson
    fun toJson(state: UserCampaignState): String =
        when (state) {
            UserCampaignState.None -> NONE
            UserCampaignState.Registered -> REGISTERED
            UserCampaignState.TaskFinished -> TASK_FINISHED
            UserCampaignState.RewardSend -> REWARD_SEND
            UserCampaignState.RewardReceived -> REWARD_RECEIVED
            UserCampaignState.Failed -> FAILED
        }

    companion object {
        private const val NONE = "NONE"
        private const val REGISTERED = "REGISTERED"
        private const val TASK_FINISHED = "TASK_FINISHED"
        private const val REWARD_SEND = "REWARD_SEND"
        private const val REWARD_RECEIVED = "REWARD_RECEIVED"
        private const val FAILED = "FAILED"
    }
}

class CampaignStateMoshiAdapter {
    @FromJson
    fun fromJson(input: String): CampaignState =
        when (input) {
            NONE -> CampaignState.None
            STARTED -> CampaignState.Started
            ENDED -> CampaignState.Ended
            else -> throw JsonDataException("Unknown CampaignState: $input")
        }

    @ToJson
    fun toJson(state: CampaignState): String =
        when (state) {
            CampaignState.None -> NONE
            CampaignState.Started -> STARTED
            CampaignState.Ended -> ENDED
        }

    companion object {
        private const val NONE = "NONE"
        private const val STARTED = "STARTED"
        private const val ENDED = "ENDED"
    }
}

class CampaignTransactionStateMoshiAdapter {

    @FromJson
    fun fromJson(input: String): CampaignTransactionState =
        when (input) {
            NONE -> CampaignTransactionState.None
            PENDING_DEPOSIT -> CampaignTransactionState.PendingDeposit
            FINISHED_DEPOSIT -> CampaignTransactionState.FinishedDeposit
            PENDING_WITHDRAWAL -> CampaignTransactionState.PendingWithdrawal
            FINISHED_WITHDRAWAL -> CampaignTransactionState.FinishedWithdrawal
            FAILED -> CampaignTransactionState.Failed
            else -> throw JsonDataException("Unknown CampaignTransactionState: $input")
        }

    @ToJson
    fun toJson(state: CampaignTransactionState): String =
        when (state) {
            CampaignTransactionState.None -> NONE
            CampaignTransactionState.PendingDeposit -> PENDING_DEPOSIT
            CampaignTransactionState.FinishedDeposit -> FINISHED_DEPOSIT
            CampaignTransactionState.PendingWithdrawal -> PENDING_WITHDRAWAL
            CampaignTransactionState.FinishedWithdrawal -> FINISHED_WITHDRAWAL
            CampaignTransactionState.Failed -> FAILED
        }

    companion object {
        private const val NONE = "NONE"
        private const val PENDING_DEPOSIT = "PENDING_DEPOSIT"
        private const val FINISHED_DEPOSIT = "FINISHED_DEPOSIT"
        private const val PENDING_WITHDRAWAL = "PENDING_WITHDRAWAL"
        private const val FINISHED_WITHDRAWAL = "FINISHED_WITHDRAWAL"
        private const val FAILED = "FAILED"
    }
}
