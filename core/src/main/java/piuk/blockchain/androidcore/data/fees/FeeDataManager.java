package piuk.blockchain.androidcore.data.fees;

import info.blockchain.balance.CryptoCurrency;
import info.blockchain.wallet.api.Environment;
import info.blockchain.wallet.api.FeeApi;
import info.blockchain.wallet.api.data.FeeOptions;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;

import piuk.blockchain.androidcore.data.api.EnvironmentConfig;
import piuk.blockchain.androidcore.data.rxjava.RxBus;
import piuk.blockchain.androidcore.data.rxjava.RxPinning;

public class FeeDataManager {

    private final RxPinning rxPinning;
    private FeeApi feeApi;
    private EnvironmentConfig environmentSettings;

    public FeeDataManager(FeeApi feeApi, EnvironmentConfig environmentSettings, RxBus rxBus) {
        this.feeApi = feeApi;
        this.environmentSettings = environmentSettings;
        rxPinning = new RxPinning(rxBus);
    }

    /**
     * Returns a {@link FeeOptions} object which contains both a "regular" and a "priority" fee
     * option, both listed in Satoshis/byte.
     *
     * @return An {@link Observable} wrapping a {@link FeeOptions} object
     */
    public Observable<FeeOptions> getBtcFeeOptions() {
        if (environmentSettings.getEnvironment().equals(Environment.TESTNET)) {
            return Observable.just(FeeOptions.Companion.testnetFeeOptions());
        } else {
            return rxPinning.call(() -> feeApi.getBtcFeeOptions())
                .onErrorReturnItem(FeeOptions.Companion.defaultFee(CryptoCurrency.BTC))
                .observeOn(AndroidSchedulers.mainThread());
        }
    }

    /**
     * Returns a {@link FeeOptions} object which contains both a "regular" and a "priority" fee
     * option for Ethereum.
     *
     * @return An {@link Observable} wrapping a {@link FeeOptions} object
     */
    public Observable<FeeOptions> getEthFeeOptions() {
        if (environmentSettings.getEnvironment().equals(Environment.TESTNET)) {
            //No Test environment for Eth
            return Observable.just(FeeOptions.Companion.testnetFeeOptions());
        } else {
            return rxPinning.call(() -> feeApi.getEthFeeOptions())
                    .onErrorReturnItem(FeeOptions.Companion.defaultFee(CryptoCurrency.ETHER))
                    .observeOn(AndroidSchedulers.mainThread());
        }
    }

    /**
     * Returns a {@link FeeOptions} object which contains a "regular" fee
     * option, both listed in Satoshis/byte.
     *
     * @return An {@link Observable} wrapping a {@link FeeOptions} object
     */
    public Observable<FeeOptions> getBchFeeOptions() {
        return feeApi.getBchFeeOptions()
                .onErrorReturnItem(FeeOptions.Companion.defaultFee(CryptoCurrency.BCH));
    }

    /**
     * Returns a {@link FeeOptions} object for XLM fees.
     */
    public Observable<FeeOptions> getXlmFeeOptions() {
        return feeApi.getXlmFeeOptions()
                .onErrorReturnItem(FeeOptions.Companion.defaultFee(CryptoCurrency.XLM));
    }
}
