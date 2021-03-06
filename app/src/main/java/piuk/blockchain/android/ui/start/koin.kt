package piuk.blockchain.android.ui.start

import com.blockchain.koin.payloadScopeQualifier
import org.koin.dsl.module
import piuk.blockchain.androidcore.data.payload.PayloadDataManager

val startupUiModule = module {

    scope(payloadScopeQualifier) {
        factory {
            LandingPresenter(
                environmentSettings = get(),
                prefs = get(),
                rootUtil = get()
            )
        }

        factory {
            LoginPresenter(
                _payloadDataManager = lazy { get<PayloadDataManager>() },
                appUtil = get(),
                analytics = get(),
                prefs = get()
            )
        }

        factory {
            ManualPairingPresenter(
                appUtil = get(),
                authDataManager = get(),
                payloadDataManager = get(),
                prefs = get(),
                analytics = get(),
                crashLogger = get()
            )
        }

        factory {
            PasswordRequiredPresenter(
                appUtil = get(),
                prefs = get(),
                authDataManager = get(),
                payloadDataManager = get(),
                crashLogger = get()
            )
        }
    }
}