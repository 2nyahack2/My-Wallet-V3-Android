package piuk.blockchain.android.ui.customviews.account

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import info.blockchain.balance.Money
import io.reactivex.Maybe
import io.reactivex.Single
import kotlinx.android.synthetic.main.pending_balance_row.view.*
import piuk.blockchain.android.R
import piuk.blockchain.android.coincore.BlockchainAccount

interface CellDecorator {
    fun view(context: Context): Maybe<View>
    fun isEnabled(): Single<Boolean> = Single.just(true)
}

class DefaultCellDecorator : CellDecorator {
    override fun view(context: Context): Maybe<View> = Maybe.empty()
}

class PendingBalanceAccountDecorator(
    private val account: BlockchainAccount
) : CellDecorator {
    override fun view(context: Context): Maybe<View> {
        return account.pendingBalance.flatMapMaybe {
            if (it.isZero)
                Maybe.empty<View>()
            else Maybe.just(composePendingBalanceView(context, it))
        }
    }

    private fun composePendingBalanceView(context: Context, balance: Money): View {
        val view = LayoutInflater.from(context).inflate(
            R.layout.pending_balance_row,
            null,
            false
        )
        view.pending_balance.text = balance.toStringWithSymbol()
        return view
    }
}

fun ConstraintLayout.addViewToBottomWithConstraints(
    view: View,
    bottomOfView: View? = null,
    startOfView: View? = null,
    endOfView: View? = null
) {
    // we need to remove the view first in favour of recycling
    removeView(this.findViewWithTag(BOTTOM_VIEW_TAG))
    view.id = View.generateViewId()
    view.tag = BOTTOM_VIEW_TAG
    addView(view, ConstraintLayout.LayoutParams.MATCH_CONSTRAINT, ViewGroup.LayoutParams.WRAP_CONTENT)
    val constraintSet = ConstraintSet()
    constraintSet.clone(this)
    constraintSet.connect(
        view.id,
        ConstraintSet.BOTTOM,
        ConstraintSet.PARENT_ID,
        ConstraintSet.BOTTOM,
        resources.getDimensionPixelSize(R.dimen.very_small_margin)
    )

    bottomOfView?.let {
        constraintSet.clear(it.id, ConstraintSet.BOTTOM)
        constraintSet.connect(view.id,
            ConstraintSet.TOP,
            it.id,
            ConstraintSet.BOTTOM,
            resources.getDimensionPixelSize(R.dimen.smallest_margin))
    }

    startOfView?.let {
        constraintSet.connect(view.id, ConstraintSet.START, it.id, ConstraintSet.START)
    } ?: constraintSet.connect(view.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)

    endOfView?.let {
        constraintSet.connect(view.id, ConstraintSet.END, it.id, ConstraintSet.END)
    } ?: constraintSet.connect(view.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)

    constraintSet.applyTo(this)
}

fun ConstraintLayout.removePossibleBottomView() {
    removeView(this.findViewWithTag(BOTTOM_VIEW_TAG))
}

private const val BOTTOM_VIEW_TAG = "BOTTOM_VIEW"