package piuk.blockchain.android.ui.recover

import com.blockchain.notifications.analytics.Analytics
import com.squareup.moshi.Moshi
import info.blockchain.wallet.bip44.HDWalletFactory
import info.blockchain.wallet.metadata.Metadata
import info.blockchain.wallet.metadata.MetadataDerivation
import info.blockchain.wallet.metadata.MetadataInteractor
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import org.bitcoinj.crypto.MnemonicCode
import org.bitcoinj.crypto.MnemonicException
import org.bitcoinj.params.BitcoinMainNetParams
import java.util.Locale
import piuk.blockchain.android.R
import piuk.blockchain.android.ui.createwallet.WalletCreationEvent
import piuk.blockchain.androidcore.data.auth.metadata.WalletCredentialsMetadata
import piuk.blockchain.androidcore.data.payload.PayloadDataManager
import piuk.blockchain.androidcore.utils.PersistentPrefs
import piuk.blockchain.androidcore.utils.helperfunctions.unsafeLazy
import piuk.blockchain.androidcoreui.ui.base.BasePresenter
import timber.log.Timber
import java.util.NoSuchElementException

class RecoverFundsPresenter(
    private val payloadDataManager: PayloadDataManager,
    private val prefs: PersistentPrefs,
    private val metadataInteractor: MetadataInteractor,
    private val metadataDerivation: MetadataDerivation,
    private val moshi: Moshi,
    private val analytics: Analytics
) : BasePresenter<RecoverFundsView>() {

    private val mnemonicChecker: MnemonicCode by unsafeLazy {
        // We only support US english mnemonics atm
        val wis = HDWalletFactory::class.java.classLoader?.getResourceAsStream(
            "wordlist/" + Locale("en", "US") + ".txt"
        ) ?: throw MnemonicException.MnemonicWordException("cannot read BIP39 word list")

        MnemonicCode(wis, null)
    }

    override fun onViewReady() {
        // No-op
    }

    fun onContinueClicked(recoveryPhrase: String) =
        try {
            if (recoveryPhrase.isEmpty() || !isValidMnemonic(recoveryPhrase)) {
                view.showError(R.string.invalid_recovery_phrase)
            } else {
                recoverWallet(recoveryPhrase)
            }
        } catch (e: Exception) {
            // This should never happen
            Timber.wtf(e)
            view.showError(R.string.restore_failed)
        }

    private fun isValidMnemonic(
        recoveryPhrase: String
    ): Boolean = try {
        val words = recoveryPhrase.trim().split("\\s+".toRegex())
        mnemonicChecker.check(words)
        true
    } catch (e: MnemonicException) {
        false
    }

    private fun recoverCredentials(recoveryPhrase: String): Single<WalletCredentialsMetadata> {
        require(recoveryPhrase.isNotEmpty())

        val params = BitcoinMainNetParams.get()
        val masterKey = payloadDataManager.generateMasterKeyFromSeed(recoveryPhrase, params)
        val metadataNode = metadataDerivation.deriveMetadataNode(masterKey)

        return metadataInteractor.loadRemoteMetadata(
            Metadata.newInstance(
                metaDataHDNode = metadataDerivation.deserializeMetadataNode(metadataNode),
                type = WalletCredentialsMetadata.WALLET_CREDENTIALS_METADATA_NODE,
                metadataDerivation = metadataDerivation
            )
        )
        .map {
            moshi.adapter(WalletCredentialsMetadata::class.java).fromJson(it) ?: throw NoSuchElementException()
        }.toSingle()
    }

    private fun recoverWallet(recoveryPhrase: String) {
        compositeDisposable += recoverCredentials(recoveryPhrase)
            .flatMapCompletable { creds ->
                payloadDataManager.initializeAndDecrypt(
                    creds.sharedKey,
                    creds.guid,
                    creds.password
                )
            }
        .observeOn(Schedulers.io())
        .subscribeOn(AndroidSchedulers.mainThread())
        .doOnSubscribe {
            view.showProgressDialog(R.string.restoring_wallet)
        }.doOnTerminate {
            view.dismissProgressDialog()
        }.subscribeBy(
            onComplete = {
                prefs.setValue(PersistentPrefs.KEY_SHARED_KEY, payloadDataManager.wallet!!.sharedKey)
                prefs.setValue(PersistentPrefs.KEY_WALLET_GUID, payloadDataManager.wallet!!.guid)
                prefs.isOnboardingComplete = true
                analytics.logEvent(WalletCreationEvent.RecoverWalletEvent(true))
                view.startPinEntryActivity()
            },
            onError = {
                Timber.e(it)
                view.gotoCredentialsActivity(recoveryPhrase)
            }
        )
    }
}
