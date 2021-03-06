package piuk.blockchain.android.campaign

import com.blockchain.android.testutils.rxInit
import com.blockchain.nabu.datamanagers.NabuDataManager
import com.blockchain.nabu.models.responses.nabu.CampaignData
import com.blockchain.nabu.models.responses.nabu.KycState
import com.blockchain.nabu.models.responses.nabu.RegisterCampaignRequest
import com.blockchain.nabu.models.responses.nabu.UserState
import com.blockchain.sunriver.XlmDataManager
import com.blockchain.nabu.NabuToken
import com.blockchain.nabu.models.responses.tokenresponse.NabuOfflineTokenResponse
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.verifyNoMoreInteractions
import com.nhaarman.mockito_kotlin.whenever
import info.blockchain.balance.AccountReference
import io.reactivex.Completable
import io.reactivex.Single
import org.amshove.kluent.`it returns`
import org.amshove.kluent.`should be`
import org.amshove.kluent.`should equal`
import org.amshove.kluent.mock
import org.junit.Rule
import org.junit.Test

class SunriverCampaignRegistrationTest {

    @get:Rule
    val initSchedulers = rxInit {
        ioTrampoline()
    }

    @Test
    fun `get card type none as campaign disabled`() {
        SunriverCampaignRegistration(
            mock {
                on { enabled } `it returns` Single.just(false)
            },
            mock(),
            mock(),
            mock(),
            mock()
        ).getCampaignCardType()
            .test()
            .values()
            .first()
            .apply {
                this `should equal` SunriverCardType.None
            }
    }

    @Test
    fun `get card type complete`() {
        val offlineToken = NabuOfflineTokenResponse("userId", "token")
        SunriverCampaignRegistration(
            mock {
                on { enabled } `it returns` Single.just(true)
            },
            mock {
                on { getCampaignList(offlineToken) } `it returns` Single.just(listOf("SUNRIVER"))
            },
            givenToken(offlineToken),
            mock {
                on { getUserState() } `it returns` Single.just<UserState>(UserState.Active)
                on { getKycStatus() } `it returns` Single.just<KycState>(KycState.Verified)
            },
            mock()
        ).getCampaignCardType()
            .test()
            .values()
            .first()
            .apply {
                this `should equal` SunriverCardType.Complete
            }
    }

    @Test
    fun `get card type join wait list`() {
        val offlineToken = NabuOfflineTokenResponse("userId", "token")
        SunriverCampaignRegistration(
            mock {
                on { enabled } `it returns` Single.just(true)
            },
            mock {
                on { getCampaignList(offlineToken) } `it returns` Single.just(emptyList())
            },
            givenToken(offlineToken),
            mock {
                on { getUserState() } `it returns` Single.just<UserState>(UserState.Active)
                on { getKycStatus() } `it returns` Single.just<KycState>(KycState.None)
            },
            mock()
        ).getCampaignCardType()
            .test()
            .values()
            .first()
            .apply {
                this `should equal` SunriverCardType.JoinWaitList
            }
    }

    @Test
    fun `get card type finish sign up`() {
        val offlineToken = NabuOfflineTokenResponse("userId", "token")
        SunriverCampaignRegistration(
            mock {
                on { enabled } `it returns` Single.just(true)
            },
            mock {
                on { getCampaignList(offlineToken) } `it returns` Single.just(listOf("SUNRIVER"))
            },
            givenToken(offlineToken),
            mock {
                on { getUserState() } `it returns` Single.just<UserState>(UserState.Created)
                on { getKycStatus() } `it returns` Single.just<KycState>(KycState.None)
            },
            mock()
        ).getCampaignCardType()
            .test()
            .values()
            .first()
            .apply {
                this `should equal` SunriverCardType.FinishSignUp
            }
    }

    @Test
    fun `register as user already has an account`() {
        val offlineToken = NabuOfflineTokenResponse("userId", "token")
        val accountRef = AccountReference.Xlm("", "GABCDEFHI")
        val campaignData = CampaignData("name", false)
        val nabuDataManager = mock<NabuDataManager> {
            on { registerCampaign(any(), any(), any()) } `it returns` Completable.complete()
        }
        val xlmDataManager: XlmDataManager = mock()
        whenever(xlmDataManager.defaultAccount()).thenReturn(Single.just(accountRef))

        SunriverCampaignRegistration(
            featureFlag = mock(),
            nabuDataManager = nabuDataManager,
            nabuToken = givenToken(offlineToken),
            kycStatusHelper = mock(),
            xlmDataManager = xlmDataManager
        ).registerCampaign(campaignData)
            .test()
            .assertNoErrors()
            .assertComplete()
        verify(nabuDataManager).registerCampaign(
            offlineToken,
            RegisterCampaignRequest.registerSunriver(
                accountRef.accountId,
                campaignData.newUser
            ),
            campaignData.campaignName
        )
        verifyNoMoreInteractions(nabuDataManager)
    }

    @Test
    fun `register as user has no account`() {
        val offlineToken = NabuOfflineTokenResponse("userId", "token")
        val accountRef = AccountReference.Xlm("", "GABCDEFHIJ")
        val campaignData = CampaignData("name", false)
        val nabuDataManager = mock<NabuDataManager> {
            on { registerCampaign(any(), any(), any()) } `it returns` Completable.complete()
            on { requestJwt() } `it returns` Single.just("jwt")
            on { getAuthToken("jwt") } `it returns` Single.just(offlineToken)
        }
        val xlmDataManager: XlmDataManager = mock()
        whenever(xlmDataManager.defaultAccount()).thenReturn(Single.just(accountRef))

        SunriverCampaignRegistration(
            featureFlag = mock(),
            nabuDataManager = nabuDataManager,
            nabuToken = givenToken(offlineToken),
            kycStatusHelper = mock(),
            xlmDataManager = xlmDataManager
        ).registerCampaign(campaignData)
            .test()
            .assertNoErrors()
            .assertComplete()
        verify(nabuDataManager).registerCampaign(
            offlineToken,
            RegisterCampaignRequest.registerSunriver(
                accountRef.accountId,
                campaignData.newUser
            ),
            campaignData.campaignName
        )
        verifyNoMoreInteractions(nabuDataManager)
    }

//    @Test
//    fun `register as user already has an account`() {
//        val offlineToken = NabuOfflineTokenResponse("userId", "token")
//        val accountRef = AccountReference.Xlm("", "GABCDEFHI")
//        val campaignData = CampaignData("name", false)
//        val nabuDataManager = mock<NabuDataManager> {
//            on { registerCampaign(any(), any(), any()) } `it returns` Completable.complete()
//        }
//        SunriverCampaignRegistration(
//            mock(),
//            nabuDataManager,
//            givenToken(offlineToken),
//            mock(),
//            mock()
//        ).registerCampaignAndSignUpIfNeeded(campaignData)
//            .test()
//            .assertNoErrors()
//            .assertComplete()
//        verify(nabuDataManager).registerCampaign(
//            offlineToken,
//            RegisterCampaignRequest.registerSunriver(
//                accountRef.accountId,
//                campaignData.newUser
//            ),
//            campaignData.campaignName
//        )
//        verifyNoMoreInteractions(nabuDataManager)
//    }
//
//    @Test
//    fun `register as user has no account`() {
//        val offlineToken = NabuOfflineTokenResponse("userId", "token")
//        val accountRef = AccountReference.Xlm("", "GABCDEFHIJ")
//        val campaignData = CampaignData("name", false)
//        val nabuDataManager = mock<NabuDataManager> {
//            on { registerCampaign(any(), any(), any()) } `it returns` Completable.complete()
//            on { requestJwt() } `it returns` Single.just("jwt")
//            on { getAuthToken("jwt") } `it returns` Single.just(offlineToken)
//        }
//        SunriverCampaignRegistration(
//            mock(),
//            nabuDataManager,
//            givenToken(offlineToken),
//            mock(),
//            mock()
//        ).registerCampaignAndSignUpIfNeeded(accountRef, campaignData)
//            .test()
//            .assertNoErrors()
//            .assertComplete()
//        verify(nabuDataManager).registerCampaign(
//            offlineToken,
//            RegisterCampaignRequest.registerSunriver(
//                accountRef.accountId,
//                campaignData.newUser
//            ),
//            campaignData.campaignName
//        )
//        verifyNoMoreInteractions(nabuDataManager)
//    }

    @Test
    fun `register sunriver campaign`() {
        val offlineToken = NabuOfflineTokenResponse("userId", "token")
        val accountRef = AccountReference.Xlm("", "GABCDEFHJIK")
        val nabuDataManager = mock<NabuDataManager> {
            on { registerCampaign(any(), any(), any()) } `it returns` Completable.complete()
        }
        SunriverCampaignRegistration(
            mock(),
            nabuDataManager,
            givenToken(offlineToken),
            mock(),
            mock {
                on { defaultAccount() } `it returns` Single.just(accountRef)
            }
        ).registerCampaign()
            .test()
            .assertNoErrors()
            .assertComplete()
        verify(nabuDataManager).registerCampaign(
            offlineToken,
            RegisterCampaignRequest.registerSunriver(
                "GABCDEFHJIK",
                false
            ),
            "SUNRIVER"
        )
        verifyNoMoreInteractions(nabuDataManager)
    }

    @Test
    fun `user is in sunriver campaign`() {
        givenUserInCampaigns(listOf("SUNRIVER"))
            .userIsInCampaign()
            .test()
            .values()
            .single() `should be` true
    }

    @Test
    fun `user is not in any campaign`() {
        givenUserInCampaigns(emptyList())
            .userIsInCampaign()
            .test()
            .values()
            .single() `should be` false
    }

    @Test
    fun `user is in other campaign`() {
        givenUserInCampaigns(listOf("CAMPAIGN2"))
            .userIsInCampaign()
            .test()
            .values()
            .single() `should be` false
    }

    @Test
    fun `user is in multiple campaigns`() {
        givenUserInCampaigns(listOf("CAMPAIGN2", "SUNRIVER"))
            .userIsInCampaign()
            .test()
            .values()
            .single() `should be` true
    }
}

private fun givenUserInCampaigns(campaigns: List<String>): SunriverCampaignRegistration {
    val offlineToken = NabuOfflineTokenResponse("userId", "token")
    return SunriverCampaignRegistration(
        mock(),
        mock {
            on { getCampaignList(offlineToken) } `it returns` Single.just(campaigns)
        },
        givenToken(offlineToken),
        mock(),
        mock()
    )
}

private fun givenToken(offlineToken: NabuOfflineTokenResponse): NabuToken =
    mock {
        on { fetchNabuToken() } `it returns` Single.just(offlineToken)
    }
