package piuk.blockchain.android.util

import android.text.TextWatcher
import androidx.viewpager.widget.ViewPager
import com.google.android.material.tabs.TabLayout

inline fun ViewPager.setOnPageChangeListener(func: OnPageChangeListener.() -> Unit) {
    val listener = OnPageChangeListener()
    listener.func()
    addOnPageChangeListener(listener)
}

@Suppress("unused")
class OnPageChangeListener : ViewPager.OnPageChangeListener {

    private var onPageScrollStateChanged: ((state: Int) -> Unit)? = null
    private var onPageScrolled: ((position: Int, positionOffset: Float) -> Unit)? = null
    private var onPageSelected: ((position: Int) -> Unit)? = null

    override fun onPageScrollStateChanged(state: Int) {
        onPageScrollStateChanged?.invoke(state)
    }

    override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
        onPageScrolled?.invoke(position, positionOffset)
    }

    override fun onPageSelected(position: Int) {
        onPageSelected?.invoke(position)
    }

    fun onPageScrollStateChanged(func: (state: Int) -> Unit) {
        onPageScrollStateChanged = func
    }

    fun onPageScrolled(func: (position: Int, positionOffset: Float) -> Unit) {
        onPageScrolled = func
    }

    fun onPageSelected(func: (position: Int) -> Unit) {
        onPageSelected = func
    }
}

/**
 * Technically an extension function, but allows us to use a functional interface instead of
 * having to implement all methods and returns the only thing we're interested in, which in this
 * case is the actual position.
 */
fun TabLayout.setOnTabSelectedListener(function: (position: Int) -> Unit) {
    addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
        override fun onTabReselected(p0: TabLayout.Tab?) {
            // No-op
        }

        override fun onTabUnselected(p0: TabLayout.Tab?) {
            // No-op
        }

        override fun onTabSelected(p0: TabLayout.Tab?) {
            // Pass the currently selected tab position
            function(selectedTabPosition)
        }
    })
}

@Suppress("unused")
abstract class AfterTextChangedWatcher : TextWatcher {
    final override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
        // No-op
    }

    final override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
        // No-op
    }
}
