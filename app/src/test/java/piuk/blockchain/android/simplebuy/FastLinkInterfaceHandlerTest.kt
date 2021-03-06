package piuk.blockchain.android.simplebuy

import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.verifyNoMoreInteractions
import com.nhaarman.mockito_kotlin.verifyZeroInteractions
import org.amshove.kluent.mock
import org.junit.Before
import org.junit.Test
import piuk.blockchain.android.ui.linkbank.yodlee.FastLinkInterfaceHandler

class FastLinkInterfaceHandlerTest {

    private lateinit var fastlinkHandler: FastLinkInterfaceHandler
    private val listener: FastLinkInterfaceHandler.FastLinkListener = mock()

    @Before
    fun setUp() {
        fastlinkHandler = FastLinkInterfaceHandler(listener)
    }

    @Test
    fun internalServerErrorShouldNotTriggerTheListener() {
        // When
        fastlinkHandler.postMessage(
            "{\n" +
                "  \"type\": \"POST_MESSAGE\",\n" +
                "  \"data\": {\n" +
                "    \"fnToCall\": \"errorHandler\",\n" +
                "    \"code\": \"100\",\n" +
                "    \"title \": \"TECH_ERROR\",\n" +
                "    \"message\": \"Internal Server Error.\"\n" +
                "  }\n" +
                "}"
        )

        // Then
        verifyZeroInteractions(listener)
    }

    @Test
    fun statusFailedWithoutExitActionShouldNotTriggerTheListener() {
        // When
        fastlinkHandler.postMessage(
            "{\n" +
                "  \"type\": \"POST_MESSAGE\",\n" +
                "  \"data\": {\n" +
                "    \"providerId\": 2852,\n" +
                "    \"providerName\": \"Bank of America\",\n" +
                "    \"requestId\": \"NWLsQ+Ixn6yB2TL3018GBFd4yil=\",\n" +
                "    \"isMFAError\": true,\n" +
                "    \"reason\": \"The information you provided is incorrect. " +
                "Please try again or visit Bank of America to verify your details.\",\n" +
                "    \"status\": \"FAILED\",\n" +
                "    \"additionalStatus\": \"INVALID_ADDL_INFO_PROVIDED\",\n" +
                "    \"providerAccountId\": 10722673,\n" +
                "    \"fnToCall\": \"accountStatus\"\n" +
                "  }\n" +
                "}"
        )

        // Then
        verifyZeroInteractions(listener)
    }

    @Test
    fun postMessageWithActionExitAndSitesAvailableShouldTriggerListenerWithTheRightArgs() {
        // When
        fastlinkHandler.postMessage(
            "{\n" +
                "  \"type\": \"POST_MESSAGE\",\n" +
                "  \"data\": {\n" +
                "    \"action\": \"exit\",\n" +
                "    \"fnToCall\": \"accountStatus\",\n" +
                "    \"sites\": [\n" +
                "      {\n" +
                "        \"providerld\": 2852,\n" +
                "        \"providerName\": \"Bank of America\",\n" +
                "        \"requestId\": \"hD/00dCz8rkOduCc/ NFHIT02ZS8=\",\n" +
                "        \"status\": \"SUCCESS\",\n" +
                "        \"additionalSta tus\": \"AVAILABLE_DATA_RETRIEVED\",\n" +
                "        \"providerAccountId\": 10722878,\n" +
                "        \"accountId\": \"11172738\"\n" +
                "      }\n" +
                "    ]\n" +
                "  }\n" +
                "}"
        )

        // Then
        verify(listener).flowSuccess(providerAccountId = "10722878", accountId = "11172738")
        verifyNoMoreInteractions(listener)
    }

    @Test
    fun postMessageWithSuccessButNoExitActionShouldNotTriggerTheListener() {
        // When
        fastlinkHandler.postMessage(
            "{\n" +
                "  \"type\": \"POST_MESSAGE\",\n" +
                "  \"data\": {\n" +
                "    \"providerld\": 21,\n" +
                "    \"providerName\": \"Charles Schwab\",\n" +
                "    \"requestid\": \"mxSAXehcFepD TTHY3QKDVTmul2k=\",\n" +
                "    \"status\": \"SUCCESS\",\n" +
                "    \"additionalStatus\": \"ACCT_SUM MARY_RECEIVED\",\n" +
                "    \"providerAccountid\": 10729375,\n" +
                "    \"fnToCall\": \"accountStatus\"\n" +
                "  }\n" +
                "}"
        )

        // Then
        verifyZeroInteractions(listener)
    }

    @Test
    fun postMessageWithFailureButNoExitActionShouldNotTriggerTheListener() {
        // When
        fastlinkHandler.postMessage(
            "{\n" +
                "  \"type\": \"POST_MESSAGE\",\n" +
                "  \"data\": {\n" +
                "    \"providerId\": 2852,\n" +
                "    \"providerName\": \"Bank of America\",\n" +
                "    \"requestId\": \"PwBex9DXXKdUchtkdHbEhE1LVuU=\",\n" +
                "    \"isMFAError\": true,\n" +
                "    \"reason\": \"Check that your credentials are the same that you use for this institution.\",\n" +
                "    \"status\": \"FAILED\",\n" +
                "    \"additionalStatus\": \"INCORRECT_CREDENTIALS\",\n" +
                "    \"providerAccountId\": 10070628,\n" +
                "    \"fnToCall\": \"accountStatus\"\n" +
                "  }\n" +
                "}"
        )

        // Then
        verifyZeroInteractions(listener)
    }

    @Test
    fun postMessageForUserCloseActionWithActionExitShouldTriggerError() {
        // When
        fastlinkHandler.postMessage(
            "{\n" +
                "  \"type\": \"POST_MESSAGE\",\n" +
                "  \"data\": {\n" +
                "    \"action\": \"exit\",\n" +
                "    \"fnToCall\": \"accountStatus\",\n" +
                "    \"sites\": [],\n" +
                "    \"status\": \"USER_CLOSE_ACTION\"\n" +
                "  }\n" +
                "}"
        )

        // Then
        verify(listener).flowError(FastLinkInterfaceHandler.FastLinkFlowError.FLOW_QUIT_BY_USER, null)
        verifyNoMoreInteractions(listener)
    }

    @Test
    fun postMessageForStatusFailedWithActionExitShouldTriggerError() {
        // When
        fastlinkHandler.postMessage(
            "{\n" +
                "  \"type\": \"POST_MESSAGE\",\n" +
                "  \"data\": {\n" +
                "    \"action\": \"exit\",\n" +
                "    \"fnToCall\": \"accountStatus\",\n" +
                "    \"sites\": [],\n" +
                "    \"status\": \"FAILED\"\n" +
                "  }\n" +
                "}"
        )

        // Then
        verify(listener).flowError(FastLinkInterfaceHandler.FastLinkFlowError.OTHER, null)
        verifyNoMoreInteractions(listener)
    }

    @Test
    fun postMessageForStatusFailedAndReasonShouldTriggerErrorIfExitActionIsPresented() {
        // When
        fastlinkHandler.postMessage(
            "{\n" +
                "  \"type\": \"POST_MESSAGE\",\n" +
                "  \"data\": {\n" +
                "    \"action\": \"exit\",\n" +
                "    \"fnToCall\": \"accountStatus\",\n" +
                "    \"sites\": [],\n" +
                "    \"status\": \"USER_CLOSE_ACTION\",\n" +
                "    \"reason\": \"Invalid State\"\n" +
                "  }\n" +
                "}"
        )

        // Then
        verify(listener).flowError(FastLinkInterfaceHandler.FastLinkFlowError.FLOW_QUIT_BY_USER, "Invalid State")
        verifyNoMoreInteractions(listener)
    }

    @Test
    fun postMessageForStatusActionAbandonedWithActionExitShouldTriggerError() {
        // When
        fastlinkHandler.postMessage(
            "{\n" +
                "  \"type\": \"POST_MESSAGE\",\n" +
                "  \"data\": {\n" +
                "    \"action\": \"exit\",\n" +
                "    \"fnToCall\": \"accountStatus\",\n" +
                "    \"sites\": [],\n" +
                "    \"status\": \"ACTION_ABANDONED\"\n" +
                "  }\n" +
                "}"
        )

        // Then
        verify(listener).flowError(FastLinkInterfaceHandler.FastLinkFlowError.FLOW_QUIT_BY_USER, null)
        verifyNoMoreInteractions(listener)
    }

    @Test
    fun postMessageForOpeningExternalUrlShouldTriggerTheCorrespondingActionOnTheListener() {
        // When
        fastlinkHandler.postMessage(
            "\n" +
                "{\n" +
                "  \"type\": \"OPEN_EXTERNAL_URL\",\n" +
                "  \"data\": {\n" +
                "    \"url\": \"https://www.yodlee.com/financial-products\"\n" +
                "  }\n" +
                "}"
        )

        // Then
        verify(listener).openExternalUrl("https://www.yodlee.com/financial-products")
        verifyNoMoreInteractions(listener)
    }

    @Test
    fun postMessageForSuccessButWithNoExitActionShouldTriggerNothing() {
        // When
        fastlinkHandler.postMessage(
            "{\n" +
                "  \"type\": \"POST_MESSAGE\",\n" +
                "  \"data\": {\n" +
                "    \"providerId\": 16441,\n" +
                "    \"providerName\": \"Dag Site\",\n" +
                "    \"requestId\": \"I9yNQ9mxU69JZpxaN/SEFR/Yra8=\",\n" +
                "    \"status\": \"SUCCESS\",\n" +
                "    \"additionalStatus\": \"ACCT_SUMMARY_RECEIVED\",\n" +
                "    \"providerAccountId\": 10070629,\n" +
                "    \"fnToCall\": \"accountStatus\"\n" +
                "  }\n" +
                "}"
        )

        // Then
        verifyZeroInteractions(listener)
    }

    @Test
    fun postMessageWithActionExitButNoSitesOrStatusShouldTriggerError() {
        // When
        fastlinkHandler.postMessage(
            "{\n" +
                "  \"type\": \"POST_MESSAGE\",\n" +
                "  \"data\": {\n" +
                "    \"action\": \"exit\",\n" +
                "    \"fnToCall\": \"accountStatus\"\n" +
                "  }\n" +
                "}\n"
        )
        // Then
        verify(listener).flowError(FastLinkInterfaceHandler.FastLinkFlowError.OTHER)
        verifyNoMoreInteractions(listener)
    }

    @Test
    fun messageWithNorDataEitherTypeShouldTriggerNothing() {
        // When
        fastlinkHandler.postMessage(
            "{\n" +
                "  \"rtet\": 213\n" +
                "}"
        )
        // Then
        verifyZeroInteractions(listener)
    }
}