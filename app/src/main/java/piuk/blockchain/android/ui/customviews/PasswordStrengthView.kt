package piuk.blockchain.android.ui.customviews

import android.animation.ObjectAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.animation.DecelerateInterpolator
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import info.blockchain.wallet.util.PasswordUtil
import kotlinx.android.synthetic.main.view_password_strength.view.*
import piuk.blockchain.android.R
import kotlin.math.roundToInt

class PasswordStrengthView(context: Context, attrs: AttributeSet) : ConstraintLayout(context, attrs) {

    private val strengthVerdicts = intArrayOf(
        R.string.strength_weak,
        R.string.strength_medium,
        R.string.strength_normal,
        R.string.strength_strong
    )

    private val strengthProgressDrawables = intArrayOf(
        R.drawable.progress_red,
        R.drawable.progress_orange,
        R.drawable.progress_blue,
        R.drawable.progress_green
    )

    private val strengthColors = intArrayOf(
        R.color.product_red_medium,
        R.color.product_orange_medium,
        R.color.primary_blue_medium,
        R.color.product_green_medium
    )

    init {
        inflate(context, R.layout.view_password_strength, this)
        pass_strength_bar.max = 100
    }

    // TODO make other methods private and only expose the update method - unify how we define password strengths
    fun updatePasswordStrength(password: String) {
        val passwordStrength = PasswordUtil.getStrength(password).roundToInt()
        setStrengthProgress(passwordStrength)

        when (passwordStrength) {
            in 0..25 -> updateLevelUI(0)
            in 26..50 -> updateLevelUI(1)
            in 51..75 -> updateLevelUI(2)
            in 76..100 -> updateLevelUI(3)
        }
    }

    fun setStrengthProgress(score: Int) {
        ObjectAnimator.ofInt(
            pass_strength_bar,
            "progress",
            pass_strength_bar.progress,
            score * 10
        ).apply {
            duration = 300
            interpolator = DecelerateInterpolator()
            start()
        }
    }

    fun updateLevelUI(level: Int) {
        pass_strength_verdict.setText(strengthVerdicts[level])
        pass_strength_bar.progressDrawable =
            ContextCompat.getDrawable(context, strengthProgressDrawables[level])
        pass_strength_verdict.setText(strengthVerdicts[level])
        pass_strength_verdict.setTextColor(
            ContextCompat.getColor(context, strengthColors[level])
        )
    }
}