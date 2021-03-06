package piuk.blockchain.android.scan;

import android.graphics.Bitmap;

import info.blockchain.wallet.crypto.AESUtil;

import org.spongycastle.util.encoders.Hex;

import java.nio.charset.StandardCharsets;

import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

public class QrCodeDataManager {

    private static final int PAIRING_CODE_PBKDF2_ITERATIONS = 10;

    public QrCodeDataManager() {
        // Empty constructor for injection
    }

    /**
     * Generates a QR code in Bitmap format from a given URI to specified square dimensions
     *
     * @param uri        A string to be encoded
     * @param dimensions The dimensions of the QR code to be returned
     * @return An Single wrapping the generate Bitmap operation
     */
    public Single<Bitmap> generateQrCode(String uri, int dimensions) {
        return generateQrCodeSingle(uri, dimensions)
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread());
    }

    /**
     * Generates a pairing QR code in Bitmap format from a given password, sharedkey and encryption
     * phrase to specified dimensions, wrapped in an Observable. Will throw an error if the Bitmap
     * is null.
     *
     * @param password         Wallet's plain text password
     * @param sharedKey        Wallet's plain text sharedkey
     * @param encryptionPhrase The pairing encryption password
     * @param dimensions       The dimensions of the QR code to be returned
     */
    public Observable<Bitmap> generatePairingCode(String guid, String password, String sharedKey, String encryptionPhrase, int dimensions) {
        return generatePairingCodeObservable(guid, password, sharedKey, encryptionPhrase, dimensions)
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread());
    }

    private Observable<Bitmap> generateQrCodeObservable(String uri, int dimensions) {
        return Observable.fromCallable(() -> {
            QRCodeEncoder qrCodeEncoder = new QRCodeEncoder(uri, dimensions);
            return qrCodeEncoder.encodeAsBitmap();
        });
    }

    private Single<Bitmap> generateQrCodeSingle(String uri, int dimensions) {
        return Single.fromCallable(() -> {
            QRCodeEncoder qrCodeEncoder = new QRCodeEncoder(uri, dimensions);
            return qrCodeEncoder.encodeAsBitmap();
        });
    }

    private Observable<Bitmap> generatePairingCodeObservable(String guid,
                                                             String password,
                                                             String sharedKey,
                                                             String encryptionPhrase,
                                                             int dimensions) {
        return Observable.fromCallable(() -> {
            String pwHex = Hex.toHexString(password.getBytes(StandardCharsets.UTF_8));
            String encrypted = AESUtil.encrypt(sharedKey + "|" + pwHex, encryptionPhrase, PAIRING_CODE_PBKDF2_ITERATIONS);
            String qrData = "1|" + guid + "|" + encrypted;

            QRCodeEncoder qrCodeEncoder = new QRCodeEncoder(qrData, dimensions);
            return qrCodeEncoder.encodeAsBitmap();
        });
    }
}
