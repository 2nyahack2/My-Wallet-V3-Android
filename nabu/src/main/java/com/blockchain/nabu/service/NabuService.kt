package com.blockchain.nabu.service

import com.blockchain.nabu.api.nabu.Nabu
import com.blockchain.nabu.datamanagers.TransactionError
import com.blockchain.nabu.datamanagers.custodialwalletimpl.PaymentMethodType
import com.blockchain.nabu.extensions.wrapErrorMessage
import com.blockchain.nabu.models.responses.banktransfer.CreateLinkBankRequestBody
import com.blockchain.nabu.models.responses.banktransfer.UpdateProviderAccountBody
import com.blockchain.nabu.models.responses.interest.InterestAccountDetailsResponse
import com.blockchain.nabu.models.responses.nabu.AddAddressRequest
import com.blockchain.nabu.models.responses.nabu.AirdropStatusList
import com.blockchain.nabu.models.responses.nabu.ApplicantIdRequest
import com.blockchain.nabu.models.responses.nabu.NabuBasicUser
import com.blockchain.nabu.models.responses.nabu.NabuCountryResponse
import com.blockchain.nabu.models.responses.nabu.NabuJwt
import com.blockchain.nabu.models.responses.nabu.NabuStateResponse
import com.blockchain.nabu.models.responses.nabu.NabuUser
import com.blockchain.nabu.models.responses.nabu.RecordCountryRequest
import com.blockchain.nabu.models.responses.nabu.RegisterCampaignRequest
import com.blockchain.nabu.models.responses.nabu.Scope
import com.blockchain.nabu.models.responses.nabu.SendToMercuryAddressRequest
import com.blockchain.nabu.models.responses.nabu.SendToMercuryAddressResponse
import com.blockchain.nabu.models.responses.nabu.SendWithdrawalAddressesRequest
import com.blockchain.nabu.models.responses.nabu.SupportedDocuments
import com.blockchain.nabu.models.responses.nabu.WalletMercuryLink
import com.blockchain.nabu.models.responses.simplebuy.AddNewCardBodyRequest
import com.blockchain.nabu.models.responses.simplebuy.BankAccountResponse
import com.blockchain.nabu.models.responses.simplebuy.CardPartnerAttributes
import com.blockchain.nabu.models.responses.simplebuy.ConfirmOrderRequestBody
import com.blockchain.nabu.models.responses.simplebuy.CustodialWalletOrder
import com.blockchain.nabu.models.responses.simplebuy.DepositRequestBody
import com.blockchain.nabu.models.responses.simplebuy.SimpleBuyCurrency
import com.blockchain.nabu.models.responses.simplebuy.SimpleBuyEligibility
import com.blockchain.nabu.models.responses.simplebuy.SimpleBuyPairsResp
import com.blockchain.nabu.models.responses.simplebuy.SimpleBuyQuoteResponse
import com.blockchain.nabu.models.responses.simplebuy.TransactionsResponse
import com.blockchain.nabu.models.responses.simplebuy.TransferFundsResponse
import com.blockchain.nabu.models.responses.simplebuy.TransferRequest
import com.blockchain.nabu.models.responses.simplebuy.WithdrawLocksCheckRequestBody
import com.blockchain.nabu.models.responses.simplebuy.WithdrawRequestBody
import com.blockchain.nabu.models.responses.swap.CreateOrderRequest
import com.blockchain.nabu.models.responses.swap.CustodialOrderResponse
import com.blockchain.nabu.models.responses.swap.QuoteRequest
import com.blockchain.nabu.models.responses.swap.QuoteResponse
import com.blockchain.nabu.models.responses.swap.SwapLimitsResponse
import com.blockchain.nabu.models.responses.swap.UpdateSwapOrderBody
import com.blockchain.nabu.models.responses.tokenresponse.NabuOfflineTokenRequest
import com.blockchain.nabu.models.responses.tokenresponse.NabuOfflineTokenResponse
import com.blockchain.nabu.models.responses.tokenresponse.NabuSessionTokenResponse
import com.blockchain.veriff.VeriffApplicantAndToken
import info.blockchain.balance.CryptoCurrency
import io.reactivex.Completable
import io.reactivex.Maybe
import io.reactivex.Single
import retrofit2.HttpException
import retrofit2.Retrofit

class NabuService(retrofit: Retrofit) {

    private val service: Nabu = retrofit.create(Nabu::class.java)

    internal fun getAuthToken(
        jwt: String,
        currency: String? = null,
        action: String? = null
    ): Single<NabuOfflineTokenResponse> = service.getAuthToken(
        jwt = NabuOfflineTokenRequest(jwt), currency = currency, action = action
    ).wrapErrorMessage()

    internal fun getSessionToken(
        userId: String,
        offlineToken: String,
        guid: String,
        email: String,
        appVersion: String,
        deviceId: String
    ): Single<NabuSessionTokenResponse> = service.getSessionToken(
        userId,
        offlineToken,
        guid,
        email,
        appVersion,
        CLIENT_TYPE,
        deviceId
    ).wrapErrorMessage()

    internal fun createBasicUser(
        firstName: String,
        lastName: String,
        dateOfBirth: String,
        sessionToken: NabuSessionTokenResponse
    ): Completable = service.createBasicUser(
        NabuBasicUser(firstName, lastName, dateOfBirth),
        sessionToken.authHeader
    )

    internal fun getUser(
        sessionToken: NabuSessionTokenResponse
    ): Single<NabuUser> = service.getUser(
        sessionToken.authHeader
    ).wrapErrorMessage()

    internal fun getAirdropCampaignStatus(
        sessionToken: NabuSessionTokenResponse
    ): Single<AirdropStatusList> = service.getAirdropCampaignStatus(
        sessionToken.authHeader
    ).wrapErrorMessage()

    internal fun updateWalletInformation(
        sessionToken: NabuSessionTokenResponse,
        jwt: String
    ): Single<NabuUser> = service.updateWalletInformation(
        NabuJwt(jwt),
        sessionToken.authHeader
    ).wrapErrorMessage()

    internal fun getCountriesList(
        scope: Scope
    ): Single<List<NabuCountryResponse>> = service.getCountriesList(
        scope.value
    ).wrapErrorMessage()

    internal fun getStatesList(
        countryCode: String,
        scope: Scope
    ): Single<List<NabuStateResponse>> = service.getStatesList(
        countryCode,
        scope.value
    ).wrapErrorMessage()

    internal fun getSupportedDocuments(
        sessionToken: NabuSessionTokenResponse,
        countryCode: String
    ): Single<List<SupportedDocuments>> = service.getSupportedDocuments(
        countryCode,
        sessionToken.authHeader
    ).wrapErrorMessage()
        .map { it.documentTypes }

    internal fun addAddress(
        sessionToken: NabuSessionTokenResponse,
        line1: String,
        line2: String?,
        city: String,
        state: String?,
        postCode: String,
        countryCode: String
    ): Completable = service.addAddress(
        AddAddressRequest.fromAddressDetails(
            line1,
            line2,
            city,
            state,
            postCode,
            countryCode
        ),
        sessionToken.authHeader
    ).wrapErrorMessage()

    internal fun recordCountrySelection(
        sessionToken: NabuSessionTokenResponse,
        jwt: String,
        countryCode: String,
        stateCode: String?,
        notifyWhenAvailable: Boolean
    ): Completable = service.recordSelectedCountry(
        RecordCountryRequest(
            jwt,
            countryCode,
            notifyWhenAvailable,
            stateCode
        ),
        sessionToken.authHeader
    ).wrapErrorMessage()

    internal fun startVeriffSession(
        sessionToken: NabuSessionTokenResponse
    ): Single<VeriffApplicantAndToken> = service.startVeriffSession(
        sessionToken.authHeader
    ).map { VeriffApplicantAndToken(it.applicantId, it.token) }
        .wrapErrorMessage()

    internal fun submitVeriffVerification(
        sessionToken: NabuSessionTokenResponse
    ): Completable = service.submitVerification(
        ApplicantIdRequest(sessionToken.userId),
        sessionToken.authHeader
    ).wrapErrorMessage()

    internal fun recoverUser(
        offlineToken: NabuOfflineTokenResponse,
        jwt: String
    ): Completable = service.recoverUser(
        offlineToken.userId,
        NabuJwt(jwt),
        authorization = "Bearer ${offlineToken.token}"
    ).wrapErrorMessage()

    internal fun registerCampaign(
        sessionToken: NabuSessionTokenResponse,
        campaignRequest: RegisterCampaignRequest,
        campaignName: String
    ): Completable = service.registerCampaign(
        campaignRequest,
        campaignName,
        sessionToken.authHeader
    ).wrapErrorMessage()

    internal fun linkWalletWithMercury(
        sessionToken: NabuSessionTokenResponse
    ): Single<String> = service.connectWalletWithMercury(
        sessionToken.authHeader
    ).map { it.linkId }
        .wrapErrorMessage()

    internal fun linkMercuryWithWallet(
        sessionToken: NabuSessionTokenResponse,
        linkId: String
    ): Completable = service.connectMercuryWithWallet(
        sessionToken.authHeader,
        WalletMercuryLink(linkId)
    ).wrapErrorMessage()

    internal fun sendWalletAddressesToThePit(
        sessionToken: NabuSessionTokenResponse,
        request: SendWithdrawalAddressesRequest
    ): Completable = service.sharePitReceiveAddresses(
        sessionToken.authHeader,
        request
    ).wrapErrorMessage()

    internal fun fetchPitSendToAddressForCrypto(
        sessionToken: NabuSessionTokenResponse,
        cryptoSymbol: String
    ): Single<SendToMercuryAddressResponse> = service.fetchPitSendAddress(
        sessionToken.authHeader,
        SendToMercuryAddressRequest(cryptoSymbol)
    ).wrapErrorMessage()

    internal fun fetchQuote(
        sessionToken: NabuSessionTokenResponse,
        quoteRequest: QuoteRequest
    ): Single<QuoteResponse> = service.fetchQuote(
        sessionToken.authHeader,
        quoteRequest
    ).wrapErrorMessage()

    internal fun createCustodialOrder(
        sessionToken: NabuSessionTokenResponse,
        createOrderRequest: CreateOrderRequest
    ): Single<CustodialOrderResponse> = service.createCustodialOrder(
        sessionToken.authHeader,
        createOrderRequest
    ).wrapErrorMessage()

    internal fun getSwapLimits(
        sessionToken: NabuSessionTokenResponse,
        currency: String
    ): Single<SwapLimitsResponse> = service.fetchSwapLimits(
        sessionToken.authHeader,
        currency
    ).onErrorResumeNext {
        if ((it as? HttpException)?.code() == 409) {
            Single.just(
                SwapLimitsResponse()
            )
        } else {
            Single.error(it)
        }
    }.wrapErrorMessage()

    internal fun fetchSwapActivity(
        sessionToken: NabuSessionTokenResponse
    ): Single<List<CustodialOrderResponse>> =
        service.fetchSwapActivity(sessionToken.authHeader).wrapErrorMessage()

    internal fun getSupportedCurrencies(
        fiatCurrency: String? = null
    ): Single<SimpleBuyPairsResp> =
        service.getSupportedSimpleBuyPairs(fiatCurrency).wrapErrorMessage()

    fun getSimpleBuyBankAccountDetails(
        sessionToken: NabuSessionTokenResponse,
        currency: String
    ): Single<BankAccountResponse> =
        service.getSimpleBuyBankAccountDetails(
            sessionToken.authHeader, SimpleBuyCurrency(currency)
        ).wrapErrorMessage()

    internal fun getSimpleBuyQuote(
        sessionToken: NabuSessionTokenResponse,
        action: String,
        currencyPair: String,
        currency: String,
        amount: String
    ): Single<SimpleBuyQuoteResponse> = service.getSimpleBuyQuote(
        authorization = sessionToken.authHeader,
        action = action,
        currencyPair = currencyPair,
        currency = currency,
        amount = amount
    )

    internal fun getPredefinedAmounts(
        sessionToken: NabuSessionTokenResponse,
        currency: String
    ): Single<List<Map<String, List<Long>>>> = service.getPredefinedAmounts(
        sessionToken.authHeader,
        currency
    ).wrapErrorMessage()

    internal fun getTransactions(
        sessionToken: NabuSessionTokenResponse,
        currency: String
    ): Single<TransactionsResponse> = service.getTransactions(
        sessionToken.authHeader,
        currency
    ).wrapErrorMessage()

    internal fun isEligibleForSimpleBuy(
        sessionToken: NabuSessionTokenResponse,
        fiatCurrency: String
    ): Single<SimpleBuyEligibility> = service.isEligibleForSimpleBuy(
        sessionToken.authHeader,
        fiatCurrency
    ).wrapErrorMessage()

    internal fun createOrder(
        sessionToken: NabuSessionTokenResponse,
        order: CustodialWalletOrder,
        action: String?
    ) = service.createOrder(
        authorization = sessionToken.authHeader, action = action, order = order
    ).onErrorResumeNext {
        if (it is HttpException && it.code() == 409) {
            Single.error(TransactionError.OrderLimitReached)
        } else {
            Single.error(it)
        }
    }.wrapErrorMessage()

    internal fun fetchWithdrawFee(sessionToken: NabuSessionTokenResponse) = service.withdrawFee(
        sessionToken.authHeader
    ).wrapErrorMessage()

    internal fun fetchWithdrawLocksRules(sessionToken: NabuSessionTokenResponse, paymentMethod: PaymentMethodType) =
        service.getWithdrawalLocksCheck(
            sessionToken.authHeader,
            WithdrawLocksCheckRequestBody(paymentMethod.name)
        ).wrapErrorMessage()

    internal fun createWithdrawOrder(
        sessionToken: NabuSessionTokenResponse,
        amount: String,
        currency: String,
        beneficiaryId: String
    ) = service.withdrawOrder(
        sessionToken.authHeader,
        WithdrawRequestBody(beneficiary = beneficiaryId, amount = amount, currency = currency)
    ).wrapErrorMessage()

    internal fun createDepositTransaction(
        sessionToken: NabuSessionTokenResponse,
        currency: String,
        address: String,
        hash: String,
        amount: String,
        product: String
    ) = service.createDepositOrder(
        sessionToken.authHeader,
        DepositRequestBody(
            currency = currency, depositAddress = address, txHash = hash, amount = amount, product = product
        )
    )

    internal fun updateOrder(
        sessionToken: NabuSessionTokenResponse,
        id: String,
        success: Boolean
    ) = service.updateOrder(
        sessionToken.authHeader,
        id,
        UpdateSwapOrderBody.newInstance(success)
    ).wrapErrorMessage()

    internal fun getOutstandingOrders(
        sessionToken: NabuSessionTokenResponse,
        pendingOnly: Boolean
    ) = service.getOrders(
        sessionToken.authHeader,
        pendingOnly
    ).wrapErrorMessage()

    internal fun getSwapTrades(sessionToken: NabuSessionTokenResponse) = service.getSwapOrders(sessionToken.authHeader)

    internal fun getSwapAvailablePairs(sessionToken: NabuSessionTokenResponse) =
        service.getSwapAvailablePairs(sessionToken.authHeader)

    internal fun deleteBuyOrder(
        sessionToken: NabuSessionTokenResponse,
        orderId: String
    ) = service.deleteBuyOrder(
        sessionToken.authHeader, orderId
    ).onErrorResumeNext {
        if (it is HttpException && it.code() == 409) {
            Completable.error(TransactionError.OrderNotCancelable)
        } else {
            Completable.error(it)
        }
    }.wrapErrorMessage()

    fun getBuyOrder(
        sessionToken: NabuSessionTokenResponse,
        orderId: String
    ) = service.getBuyOrder(
        sessionToken.authHeader, orderId
    ).wrapErrorMessage()

    fun deleteCard(
        sessionToken: NabuSessionTokenResponse,
        cardId: String
    ) = service.deleteCard(
        sessionToken.authHeader, cardId
    ).wrapErrorMessage()

    fun removeBeneficiary(
        sessionToken: NabuSessionTokenResponse,
        id: String
    ) = service.removeBeneficiary(
        sessionToken.authHeader, id
    ).wrapErrorMessage()

    fun removeLinkedBank(
        sessionToken: NabuSessionTokenResponse,
        id: String
    ) = service.removeLinkedBank(
        sessionToken.authHeader, id
    ).wrapErrorMessage()

    fun addNewCard(
        sessionToken: NabuSessionTokenResponse,
        addNewCardBodyRequest: AddNewCardBodyRequest
    ) = service.addNewCard(
        sessionToken.authHeader, addNewCardBodyRequest
    ).wrapErrorMessage()

    fun activateCard(
        sessionToken: NabuSessionTokenResponse,
        cardId: String,
        attributes: CardPartnerAttributes
    ) = service.activateCard(
        sessionToken.authHeader, cardId, attributes
    ).wrapErrorMessage()

    fun getCardDetails(
        sessionToken: NabuSessionTokenResponse,
        cardId: String
    ) = service.getCardDetails(
        sessionToken.authHeader, cardId
    ).wrapErrorMessage()

    fun confirmOrder(
        sessionToken: NabuSessionTokenResponse,
        orderId: String,
        confirmBody: ConfirmOrderRequestBody
    ) = service.confirmOrder(
        sessionToken.authHeader, orderId, confirmBody
    ).wrapErrorMessage()

    fun getBalanceForAsset(
        sessionToken: NabuSessionTokenResponse,
        cryptoCurrency: CryptoCurrency
    ) = service.getBalanceForAsset(
        sessionToken.authHeader, cryptoCurrency.networkTicker
    ).flatMapMaybe {
        when (it.code()) {
            200 -> Maybe.just(it.body())
            204 -> Maybe.empty()
            else -> Maybe.error(HttpException(it))
        }
    }.wrapErrorMessage()

    fun getBalanceForAllAssets(
        sessionToken: NabuSessionTokenResponse
    ) = service.getBalanceForAllAssets(
        sessionToken.authHeader
    ).wrapErrorMessage()

    fun transferFunds(
        sessionToken: NabuSessionTokenResponse,
        request: TransferRequest
    ): Single<String> = service.transferFunds(
        sessionToken.authHeader,
        request
    ).map {
        when (it.code()) {
            200 -> it.body()?.id ?: ""
            403 -> if (it.body()?.code == TransferFundsResponse.ERROR_WITHDRAWL_LOCKED)
                throw TransactionError.WithdrawalBalanceLocked
            else
                throw TransactionError.WithdrawalAlreadyPending
            409 -> throw TransactionError.WithdrawalInsufficientFunds
            else -> throw TransactionError.UnexpectedError
        }
    }.wrapErrorMessage()

    fun paymentMethods(
        sessionToken: NabuSessionTokenResponse,
        currency: String,
        eligibleOnly: Boolean
    ) = service.getPaymentMethodsForSimpleBuy(
        authorization = sessionToken.authHeader,
        currency = currency,
        eligibleOnly = eligibleOnly
    ).wrapErrorMessage()

    fun getBeneficiaries(sessionToken: NabuSessionTokenResponse) =
        service.getLinkedBeneficiaries(sessionToken.authHeader).wrapErrorMessage()

    fun linkToABank(
        sessionToken: NabuSessionTokenResponse,
        fiatCurrency: String
    ) = service.linkABank(
        sessionToken.authHeader,
        CreateLinkBankRequestBody(
            fiatCurrency
        )
    ).wrapErrorMessage()

    fun updateAccountProviderId(
        sessionToken: NabuSessionTokenResponse,
        id: String,
        body: UpdateProviderAccountBody
    ) = service.updateProviderAccount(
        sessionToken.authHeader,
        id,
        body
    ).wrapErrorMessage()

    fun getCards(
        sessionToken: NabuSessionTokenResponse
    ) = service.getCards(
        authorization = sessionToken.authHeader
    ).wrapErrorMessage()

    /**
     * If there is no rate for a given asset, this endpoint returns a 204, which must be parsed
     */
    fun getInterestRates(
        sessionToken: NabuSessionTokenResponse,
        currency: String
    ) = service.getInterestRates(authorization = sessionToken.authHeader, currency = currency)
        .flatMapMaybe {
            when (it.code()) {
                200 -> Maybe.just(it.body())
                204 -> Maybe.empty()
                else -> Maybe.error(HttpException(it))
            }
        }
        .wrapErrorMessage()

    fun getInterestAccountBalance(
        sessionToken: NabuSessionTokenResponse,
        currency: String
    ) = service.getInterestAccountDetails(
        authorization = sessionToken.authHeader,
        cryptoSymbol = currency
    ).flatMapMaybe {
        when (it.code()) {
            200 -> Maybe.just(it.body())
            204 -> Maybe.empty()
            else -> Maybe.error(HttpException(it))
        }
    }.wrapErrorMessage()

    fun getInterestAccountDetails(
        sessionToken: NabuSessionTokenResponse,
        currency: String
    ) = service.getInterestAccountDetails(
        authorization = sessionToken.authHeader,
        cryptoSymbol = currency
    ).flatMap {
        when (it.code()) {
            200 -> Single.just(it.body())
            204 -> Single.just(
                InterestAccountDetailsResponse("0", "0", "0", "0")
            )
            else -> Single.error(HttpException(it))
        }
    }.wrapErrorMessage()

    fun getInterestAddress(
        sessionToken: NabuSessionTokenResponse,
        currency: String
    ) = service.getInterestAddress(authorization = sessionToken.authHeader, currency = currency)
        .wrapErrorMessage()

    fun getInterestActivity(
        sessionToken: NabuSessionTokenResponse,
        currency: String
    ) = service.getInterestActivity(authorization = sessionToken.authHeader, product = "savings", currency = currency)
        .wrapErrorMessage()

    fun getInterestLimits(
        sessionToken: NabuSessionTokenResponse,
        currency: String
    ) = service.getInterestLimits(authorization = sessionToken.authHeader, currency = currency)
        .wrapErrorMessage()

    fun getInterestEnabled(
        sessionToken: NabuSessionTokenResponse
    ) = service.getInterestEnabled(authorization = sessionToken.authHeader)
        .wrapErrorMessage()

    fun getInterestEligibility(
        sessionToken: NabuSessionTokenResponse
    ) = service.getInterestEligibility(authorization = sessionToken.authHeader)
        .wrapErrorMessage()

    fun getLinkedBank(
        sessionToken: NabuSessionTokenResponse,
        id: String
    ) = service.getLinkedBank(authorization = sessionToken.authHeader, id = id)
        .wrapErrorMessage()

    fun getLinkedBanks(
        sessionToken: NabuSessionTokenResponse
    ) = service.getLinkedBanks(authorization = sessionToken.authHeader)
        .wrapErrorMessage()

    companion object {
        internal const val CLIENT_TYPE = "APP"
    }
}