package piuk.blockchain.android.ui.kyc.navhost

import com.blockchain.android.testutils.rxInit
import com.blockchain.exceptions.MetadataNotFoundException
import com.blockchain.nabu.NabuToken
import com.blockchain.nabu.datamanagers.NabuDataManager
import com.blockchain.nabu.models.responses.nabu.Address
import com.blockchain.nabu.models.responses.nabu.KycState
import com.blockchain.nabu.models.responses.nabu.NabuUser
import com.blockchain.nabu.models.responses.nabu.TierLevels
import com.blockchain.nabu.models.responses.nabu.UserState
import com.blockchain.nabu.service.TierUpdater
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.never
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import io.reactivex.Completable
import io.reactivex.Single
import org.amshove.kluent.mock
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import piuk.blockchain.android.KycNavXmlDirections
import piuk.blockchain.android.campaign.CampaignType
import piuk.blockchain.android.campaign.SunriverCampaignRegistration
import piuk.blockchain.android.ui.getBlankNabuUser
import piuk.blockchain.android.ui.kyc.reentry.ReentryDecision
import piuk.blockchain.android.ui.kyc.reentry.ReentryDecisionKycNavigator
import piuk.blockchain.android.ui.kyc.reentry.ReentryPoint
import piuk.blockchain.android.ui.validOfflineToken

class KycNavHostPresenterTest {

    private lateinit var subject: KycNavHostPresenter
    private val view: KycNavHostView = mock()
    private val sunriverCampaign: SunriverCampaignRegistration = mock()
    private val nabuDataManager: NabuDataManager = mock()
    private val nabuToken: NabuToken = mock()
    private val reentryDecision: ReentryDecision = mock()
    private val tierUpdater: TierUpdater = mock()

    @Suppress("unused")
    @get:Rule
    val initSchedulers = rxInit {
        mainTrampoline()
        ioTrampoline()
    }

    @Before
    fun setUp() {
        subject = KycNavHostPresenter(
            nabuToken,
            nabuDataManager,
            sunriverCampaign,
            reentryDecision,
            ReentryDecisionKycNavigator(mock(), mock(), mock()),
            tierUpdater
        )
        subject.initView(view)
    }

    @Test
    fun `onViewReady exception thrown`() {
        // Arrange
        whenever(nabuToken.fetchNabuToken()).thenReturn(Single.error { Throwable() })
        // Act
        subject.onViewReady()
        // Assert
        verify(view).displayLoading(true)
        verify(view).showErrorToastAndFinish(any())
    }

    @Test
    fun `onViewReady no metadata found`() {
        // Arrange
        whenever(nabuToken.fetchNabuToken()).thenReturn(Single.error { MetadataNotFoundException("") })

        // Act
        subject.onViewReady()

        // Assert
        verify(view).displayLoading(true)
        verify(view).displayLoading(false)
    }

    @Test
    fun `onViewReady metadata found, empty user object`() {
        // Arrange
        whenever(nabuToken.fetchNabuToken()).thenReturn(Single.just(validOfflineToken))
        whenever(nabuDataManager.getUser(validOfflineToken)).thenReturn(Single.just(getBlankNabuUser()))

        // Act
        subject.onViewReady()
        // Assert
        verify(view).displayLoading(true)
        verify(view).displayLoading(false)
    }

    @Test
    fun `onViewReady, should redirect to country selection`() {
        // Arrange
        givenReentryDecision(ReentryPoint.CountrySelection)
        whenever(view.campaignType).thenReturn(CampaignType.Swap)
        whenever(view.showTiersLimitsSplash).thenReturn(true).thenReturn(false)
        whenever(nabuToken.fetchNabuToken()).thenReturn(Single.just(validOfflineToken))
        whenever(nabuDataManager.getUser(validOfflineToken))
            .thenReturn(
                Single.just(
                    NabuUser(
                        firstName = "FIRST_NAME",
                        lastName = "LAST_NAME",
                        email = "",
                        emailVerified = false,
                        dob = null,
                        mobile = null,
                        mobileVerified = false,
                        address = null,
                        state = UserState.Created,
                        kycState = KycState.None,
                        insertedAt = null,
                        updatedAt = null
                    )
                )
            )
        // Act
        subject.onViewReady()
        // Assert
        verify(view).displayLoading(true)
        verify(view).navigate(KycNavXmlDirections.actionStartCountrySelection())
        verify(view).displayLoading(false)
    }

    @Test
    fun `register for sunriver campaign if campaign type is sunriver but not registered yet`() {
        // Arrange
        whenever(view.campaignType).thenReturn(CampaignType.Sunriver)
        whenever(sunriverCampaign.registerCampaign()).thenReturn(Completable.complete())
        whenever(nabuToken.fetchNabuToken()).thenReturn(Single.just(validOfflineToken))
        whenever(tierUpdater.setUserTier(2)).thenReturn(Completable.complete())
        whenever(sunriverCampaign.userIsInCampaign()).thenReturn(Single.just(false))
        whenever(nabuDataManager.getUser(validOfflineToken))
            .thenReturn(
                Single.just(
                    NabuUser(
                        firstName = "FIRST_NAME",
                        lastName = "LAST_NAME",
                        email = "",
                        emailVerified = false,
                        dob = null,
                        mobile = null,
                        mobileVerified = false,
                        address = null,
                        state = UserState.None,
                        kycState = KycState.None,
                        insertedAt = null,
                        updatedAt = null
                    )
                )
            )
        // Act
        subject.onViewReady()
        // Assert
        verify(view).displayLoading(true)
        verify(sunriverCampaign).registerCampaign()
        verify(view).displayLoading(false)
    }

    @Test
    fun `not register for sunriver campaign if campaign type is sunriver and user is registered`() {
        // Arrange
        whenever(view.campaignType).thenReturn(CampaignType.Sunriver)
        whenever(nabuToken.fetchNabuToken()).thenReturn(Single.just(validOfflineToken))
        whenever(tierUpdater.setUserTier(2)).thenReturn(Completable.complete())
        whenever(sunriverCampaign.userIsInCampaign()).thenReturn(Single.just(true))
        whenever(nabuDataManager.getUser(validOfflineToken))
            .thenReturn(
                Single.just(
                    NabuUser(
                        firstName = "FIRST_NAME",
                        lastName = "LAST_NAME",
                        email = "",
                        emailVerified = false,
                        dob = null,
                        mobile = null,
                        mobileVerified = false,
                        address = null,
                        state = UserState.None,
                        kycState = KycState.None,
                        insertedAt = null,
                        updatedAt = null
                    )
                )
            )
        // Act
        subject.onViewReady()
        // Assert
        verify(view).displayLoading(true)
        verify(sunriverCampaign, never()).registerCampaign()
        verify(view).displayLoading(false)
    }

    @Test
    fun `onViewReady sunriver, should redirect to splash`() {
        // Arrange
        givenReentryDecision(ReentryPoint.CountrySelection)
        whenever(view.campaignType).thenReturn(CampaignType.Sunriver)
        whenever(nabuToken.fetchNabuToken()).thenReturn(Single.just(validOfflineToken))
        whenever(tierUpdater.setUserTier(2)).thenReturn(Completable.complete())
        whenever(sunriverCampaign.userIsInCampaign()).thenReturn(Single.just(true))
        whenever(nabuDataManager.getUser(validOfflineToken))
            .thenReturn(
                Single.just(
                    NabuUser(
                        firstName = "FIRST_NAME",
                        lastName = "LAST_NAME",
                        email = "",
                        emailVerified = false,
                        dob = null,
                        mobile = null,
                        mobileVerified = false,
                        address = null,
                        state = UserState.None,
                        kycState = KycState.None,
                        insertedAt = null,
                        updatedAt = null
                    )
                )
            )
        // Act
        subject.onViewReady()
        // Assert
        verify(view).displayLoading(true)
        verify(view).navigateToKycSplash()
        verify(view).displayLoading(false)
    }

    @Test
    fun `onViewReady resubmission campaign, should redirect to splash`() {
        // Arrange
        givenReentryDecision(ReentryPoint.CountrySelection)
        whenever(view.campaignType).thenReturn(CampaignType.Resubmission)
        whenever(nabuToken.fetchNabuToken()).thenReturn(Single.just(validOfflineToken))
        whenever(nabuDataManager.getUser(validOfflineToken))
            .thenReturn(
                Single.just(
                    NabuUser(
                        firstName = "FIRST_NAME",
                        lastName = "LAST_NAME",
                        email = "",
                        emailVerified = true,
                        dob = null,
                        mobile = null,
                        mobileVerified = false,
                        address = null,
                        state = UserState.Created,
                        kycState = KycState.UnderReview,
                        insertedAt = null,
                        updatedAt = null
                    )
                )
            )
        // Act
        subject.onViewReady()
        // Assert
        verify(view).displayLoading(true)
        verify(view).navigateToResubmissionSplash()
        verify(view).displayLoading(false)
    }

    @Test
    fun `onViewReady resubmission user, should redirect to splash`() {
        // Arrange
        givenReentryDecision(ReentryPoint.CountrySelection)
        whenever(view.campaignType).thenReturn(CampaignType.Swap)
        whenever(nabuToken.fetchNabuToken()).thenReturn(Single.just(validOfflineToken))
        whenever(nabuDataManager.getUser(validOfflineToken))
            .thenReturn(
                Single.just(
                    NabuUser(
                        firstName = "FIRST_NAME",
                        lastName = "LAST_NAME",
                        email = "",
                        emailVerified = true,
                        dob = null,
                        mobile = null,
                        mobileVerified = false,
                        address = null,
                        state = UserState.Created,
                        kycState = KycState.UnderReview,
                        insertedAt = null,
                        updatedAt = null,
                        resubmission = Any()
                    )
                )
            )
        // Act
        subject.onViewReady()
        // Assert
        verify(view).displayLoading(true)
        verify(view).navigateToResubmissionSplash()
        verify(view).displayLoading(false)
    }

    @Test
    fun `onViewReady, should redirect to address`() {
        // Arrange
        givenReentryDecision(ReentryPoint.Address)
        whenever(nabuToken.fetchNabuToken()).thenReturn(Single.just(validOfflineToken))

        val nabuUser = NabuUser(
            firstName = "firstName",
            lastName = "lastName",
            email = "",
            emailVerified = false,
            dob = null,
            mobile = null,
            mobileVerified = false,
            address = getCompletedAddress(),
            state = UserState.Created,
            kycState = KycState.None,
            insertedAt = null,
            updatedAt = null
        )
        whenever(nabuDataManager.getUser(validOfflineToken)).thenReturn(Single.just(nabuUser))

        // Act
        subject.onViewReady()

        // Assert
        verify(view).displayLoading(true)
        verify(view).navigate(
            KycNavXmlDirections.actionStartAddressEntry(nabuUser.toProfileModel())
        )
        verify(view).displayLoading(false)
    }

    @Test
    fun `onViewReady, should redirect to phone entry`() {
        // Arrange
        givenReentryDecision(ReentryPoint.MobileEntry)
        whenever(nabuToken.fetchNabuToken()).thenReturn(Single.just(validOfflineToken))

        val nabuUser = NabuUser(
            firstName = "firstName",
            lastName = "lastName",
            email = "",
            emailVerified = false,
            dob = null,
            mobile = "mobile",
            mobileVerified = false,
            address = getCompletedAddress(),
            state = UserState.Created,
            kycState = KycState.None,
            insertedAt = null,
            updatedAt = null
        )
        whenever(nabuDataManager.getUser(validOfflineToken))
            .thenReturn(Single.just(nabuUser))
        // Act
        subject.onViewReady()
        // Assert
        verify(view).displayLoading(true)
        verify(view).navigate(KycNavXmlDirections.actionStartMobileVerification("regionCode"))
        verify(view).displayLoading(false)
    }

    @Test
    fun `onViewReady, when user is a tier 1, should not redirect to phone entry`() {
        // Arrange
        givenReentryDecision(ReentryPoint.MobileEntry)
        whenever(nabuToken.fetchNabuToken()).thenReturn(Single.just(validOfflineToken))

        val nabuUser = NabuUser(
            firstName = "firstName",
            lastName = "lastName",
            email = "",
            emailVerified = false,
            dob = null,
            mobile = "mobile",
            mobileVerified = false,
            address = getCompletedAddress(),
            state = UserState.Created,
            kycState = KycState.None,
            insertedAt = null,
            updatedAt = null,
            tiers = TierLevels(current = 1, next = 2, selected = 2)
        )
        whenever(nabuDataManager.getUser(validOfflineToken)).thenReturn(Single.just(nabuUser))
        // Act
        subject.onViewReady()
        // Assert
        verify(view).displayLoading(true)
        verify(view, never()).navigate(any())
        verify(view).displayLoading(false)
    }

    @Test
    fun `onViewReady, should redirect to Onfido`() {
        // Arrange
        givenReentryDecision(ReentryPoint.Veriff)
        whenever(nabuToken.fetchNabuToken()).thenReturn(Single.just(validOfflineToken))

        val nabuUser = NabuUser(
            firstName = "firstName",
            lastName = "lastName",
            email = "",
            emailVerified = false,
            mobile = "mobile",
            dob = null,
            mobileVerified = true,
            address = getCompletedAddress(),
            state = UserState.Active,
            kycState = KycState.None,
            insertedAt = null,
            updatedAt = null
        )
        whenever(nabuDataManager.getUser(validOfflineToken))
            .thenReturn(Single.just(nabuUser))
        // Act
        subject.onViewReady()
        // Assert
        verify(view).displayLoading(true)
        verify(view).navigate(KycNavXmlDirections.actionStartVeriff("regionCode"))
        verify(view).displayLoading(false)
    }

    @Test
    fun `onViewReady, should redirect to KYC status page`() {
        // Arrange
        whenever(nabuToken.fetchNabuToken()).thenReturn(Single.just(validOfflineToken))
        val nabuUser = NabuUser(
            firstName = "firstName",
            lastName = "lastName",
            email = "",
            emailVerified = false,
            mobile = "mobile",
            dob = null,
            mobileVerified = true,
            address = getCompletedAddress(),
            state = UserState.Active,
            kycState = KycState.Pending,
            insertedAt = null,
            updatedAt = null
        )
        whenever(nabuDataManager.getUser(validOfflineToken)).thenReturn(Single.just(nabuUser))

        // Act
        subject.onViewReady()
        // Assert
        verify(view).displayLoading(true)
        verify(view).displayLoading(false)
    }

    private fun getCompletedAddress(): Address = Address(
        city = "city",
        line1 = "line1",
        line2 = "line2",
        state = "state",
        countryCode = "regionCode",
        postCode = "postCode"
    )

    private fun givenReentryDecision(reentryPoint: ReentryPoint) {
        whenever(reentryDecision.findReentryPoint(any())).thenReturn(reentryPoint)
    }
}
