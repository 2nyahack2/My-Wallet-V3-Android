package piuk.blockchain.android.ui.scan

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Region
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import piuk.blockchain.android.R

class ViewfinderReticle @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val maskColour: Int = ContextCompat.getColor(context, R.color.qr_viewfinder_bg)
    private val reticleColor: Int = ContextCompat.getColor(context, R.color.qr_viewfinder_reticle_outline)

    private var targetRect: Rect = Rect()
    internal fun setTargetRect(r: Rect) {
        targetRect = r
    }

    private fun createClippingReticleMask(frame: RectF) =
        Path().apply {
            addRoundRect(frame, CORNER_RADIUS, CORNER_RADIUS, Path.Direction.CCW)

            // Chop out a percentage at the middle of each edge
            val width = frame.width()
            val height = frame.height()
            val cutWidth = width * RETICLE_GAP_PERCENT
            val cutHeight = height * RETICLE_GAP_PERCENT
            val cutWidthStart = frame.left + (width - cutWidth) / 2
            val cutWidthEnd = cutWidthStart + cutWidth
            val cutHeightStart = frame.top + (height - cutHeight) / 2
            val cutHeightEnd = cutHeightStart + cutHeight

            addRect(cutWidthStart, frame.top - RETICLE_WEIGHT, cutWidthEnd, frame.top, Path.Direction.CCW)
            addRect(cutWidthStart, frame.bottom, cutWidthEnd, frame.bottom + RETICLE_WEIGHT, Path.Direction.CCW)
            addRect(frame.left - RETICLE_WEIGHT, cutHeightStart, frame.left, cutHeightEnd, Path.Direction.CCW)
            addRect(frame.right, cutHeightStart, frame.right + RETICLE_WEIGHT, cutHeightEnd, Path.Direction.CCW)
        }

    @SuppressLint("CanvasSize")
    public override fun onDraw(canvas: Canvas) {
        if (targetRect.isEmpty) {
            return
        }

        val frame = targetRect.toRectF()
        val width = canvas.width
        val height = canvas.height

        with(canvas) {
            val path = createClippingReticleMask(frame)
            // TODO Replace with clipOutPath(path) when we hit API 26
            clipPath(path, Region.Op.DIFFERENCE)

            paint.color = maskColour
            drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

            paint.color = reticleColor
            drawRoundRect(frame + RETICLE_WEIGHT, CORNER_RADIUS, CORNER_RADIUS, paint)
        }
    }

    companion object {
        private const val CORNER_RADIUS = 50.0F
        private const val RETICLE_WEIGHT = 7
        private const val RETICLE_GAP_PERCENT = 0.25F
    }
}

private fun Rect.toRectF(): RectF =
    RectF(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())

private operator fun RectF.plus(i: Int): RectF =
    i.toFloat().let {
        RectF(
            left - it,
            top - it,
            right + it,
            bottom + it
        )
    }
