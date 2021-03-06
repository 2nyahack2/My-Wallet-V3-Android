package piuk.blockchain.androidcoreui.ui.base;

import android.os.Bundle;

import androidx.annotation.CallSuper;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDialogFragment;
import piuk.blockchain.androidcoreui.utils.logging.Logging;

@Deprecated
public abstract class BaseDialogFragment<VIEW extends View, PRESENTER extends BasePresenter<VIEW>>
        extends AppCompatDialogFragment {

    private PRESENTER presenter;

    @CallSuper
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        presenter = createPresenter();
        presenter.initView(getMvpView());

        Logging.INSTANCE.logContentView(getClass().getSimpleName());
    }

    @CallSuper
    @Override
    public void onPause() {
        super.onPause();
        presenter.onViewPaused();
    }

    @CallSuper
    @Override
    public void onDestroy() {
        super.onDestroy();
        presenter.onViewDestroyed();
    }

    protected void onViewReady() {
        presenter.onViewReady();
    }

    protected PRESENTER getPresenter() {
        return presenter;
    }

    protected abstract PRESENTER createPresenter();

    protected abstract VIEW getMvpView();

}
