package piuk.blockchain.android.ui.customviews

import android.annotation.SuppressLint
import android.content.Context
import android.text.InputType
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatEditText
import com.blockchain.ui.password.SecondPasswordHandler
import io.reactivex.Maybe
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.MaybeSubject
import io.reactivex.subjects.PublishSubject
import piuk.blockchain.android.R
import piuk.blockchain.android.ui.customviews.dialogs.MaterialProgressDialog
import piuk.blockchain.android.util.CurrentContextAccess
import piuk.blockchain.androidcore.data.payload.PayloadDataManager
import piuk.blockchain.androidcoreui.ui.customviews.ToastCustom
import piuk.blockchain.android.util.ViewUtils

class ErrorDialogCancelled : Exception("Dialog Cancelled")

class SecondPasswordDialog(
    private val contextAccess: CurrentContextAccess,
    private val payloadManager: PayloadDataManager
) : SecondPasswordHandler {
    private var progressDlg: MaterialProgressDialog? = null

    override fun validate(ctx: Context, listener: SecondPasswordHandler.ResultListener) {
        if (!hasSecondPasswordSet) {
            listener.onNoSecondPassword()
        } else {
            val passwordField = AppCompatEditText(ctx).apply {
                inputType = InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_VARIATION_PASSWORD or
                    InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                setHint(R.string.password)
            }

            AlertDialog.Builder(ctx, R.style.AlertDialogStyle)
                .setTitle(R.string.app_name)
                .setMessage(R.string.enter_double_encryption_pw)
                .setView(ViewUtils.getAlertDialogPaddedView(ctx, passwordField))
                .setCancelable(false)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    val secondPassword = passwordField.text.toString()
                    doValidatePassword(ctx, secondPassword, listener)
                }.setNegativeButton(android.R.string.cancel) { _, _ -> listener.onCancelled() }
                .show()
        }
    }

    @Deprecated(message = "Context access is deprecated. Use validate(listener) instead")
    override fun validate(listener: SecondPasswordHandler.ResultListener) =
        validate(contextAccess.context!!, listener)

    @SuppressLint("CheckResult")
    private fun doValidatePassword(
        ctx: Context,
        inputPassword: String,
        listener: SecondPasswordHandler.ResultListener
    ) {
        if (inputPassword.isNotEmpty()) {
            showProgressDialog(ctx)
            validateSecondPassword(inputPassword)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doAfterTerminate { dismissProgressDialog() }
                .subscribeBy(
                    onNext = { success: Boolean ->
                        if (success) {
                            setValidatePassword(inputPassword)
                            verifiedAt = System.currentTimeMillis()
                            listener.onSecondPasswordValidated(inputPassword)
                        } else {
                            resetValidatedPassword()
                            showErrorToast(ctx)
                            listener.onCancelled()
                        }
                    },
                    onError = {
                        resetValidatedPassword()
                        showErrorToast(ctx)
                    }
                )
        } else {
            showErrorToast(ctx)
            listener.onCancelled()
        }
    }

    private fun showErrorToast(context: Context) {
        ToastCustom.makeText(
            context,
            context.getString(R.string.double_encryption_password_error),
            ToastCustom.LENGTH_SHORT,
            ToastCustom.TYPE_ERROR
        )
    }

    private fun validateSecondPassword(password: String): Observable<Boolean> {
        return Observable.fromCallable {
            payloadManager.validateSecondPassword(password)
        }
    }

    private fun showProgressDialog(context: Context) {
        dismissProgressDialog()
        progressDlg = MaterialProgressDialog(context).apply {
            setCancelable(false)
            setMessage(R.string.validating_password)
            show()
        }
    }

    private fun dismissProgressDialog() {
        progressDlg?.apply {
            if (isShowing) {
                dismiss()
            }
        }
        progressDlg = null
    }

    override val hasSecondPasswordSet: Boolean
        get() = payloadManager.isDoubleEncrypted

    private var password: String? = null
    private var verifiedAt: Long = 0

    private val isPasswordValid: Boolean
        get() = password != null && System.currentTimeMillis() - verifiedAt < PASSWORD_ACTIVE_TIME_MS

    private fun setValidatePassword(inputPassword: String) {
        password = inputPassword
        verifiedAt = System.currentTimeMillis()
    }

    private fun resetValidatedPassword() {
        password = null
        verifiedAt = 0
    }

    override val verifiedPassword: String?
        get() = when {
            !hasSecondPasswordSet -> ""
            isPasswordValid -> password
            else -> null
        }

    override fun secondPassword(ctx: Context): Maybe<String> {
        val subject = MaybeSubject.create<String>()
        val password = verifiedPassword

        when {
            !hasSecondPasswordSet -> subject.onComplete() // empty if no password
            password == null -> validate(
                ctx,
                object : SecondPasswordHandler.ResultListener {
                    override fun onCancelled() {
                        subject.onError(ErrorDialogCancelled())
                    }

                    override fun onNoSecondPassword() {
                        subject.onComplete()
                    }

                    override fun onSecondPasswordValidated(validatedSecondPassword: String) {
                        subject.onSuccess(validatedSecondPassword)
                    }
                }
            )
            else -> subject.onSuccess(password)
        }
        return subject
    }

    override fun secondPassword(): Maybe<String> {
        val password = PublishSubject.create<String>()

        return Maybe.defer {
            validate(
                object : SecondPasswordHandler.ResultListener {
                    override fun onCancelled() { password.onComplete() }
                    override fun onNoSecondPassword() { password.onComplete() }
                    override fun onSecondPasswordValidated(validatedSecondPassword: String) {
                        password.onNext(validatedSecondPassword)
                    }
                }
            )
            password.firstElement()
        }.subscribeOn(AndroidSchedulers.mainThread())
    }

    companion object {
        private const val PASSWORD_ACTIVE_TIME_MS = 5 * 60 * 1000
    }
}