package piuk.blockchain.android.ui.dashboard.announcements

import android.annotation.SuppressLint
import android.graphics.Color
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import kotlinx.android.synthetic.main.item_announcement_standard.view.*
import piuk.blockchain.android.R
import piuk.blockchain.android.ui.adapters.AdapterDelegate
import piuk.blockchain.android.util.gone
import piuk.blockchain.android.util.inflate
import piuk.blockchain.android.util.isVisible
import piuk.blockchain.android.util.visible
import android.graphics.drawable.GradientDrawable
import com.blockchain.notifications.analytics.Analytics
import kotlinx.android.synthetic.main.item_announcement_mini.view.*
import kotlinx.android.synthetic.main.item_announcement_standard.view.icon
import kotlinx.android.synthetic.main.item_announcement_standard.view.msg_body
import kotlinx.android.synthetic.main.item_announcement_standard.view.msg_title

class StdAnnouncementDelegate<in T>(private val analytics: Analytics) :
    AdapterDelegate<T> {

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(
        items: List<T>,
        position: Int,
        holder: RecyclerView.ViewHolder
    ) {
        val announcement = items[position] as StandardAnnouncementCard

        (holder as AnnouncementViewHolder).apply {

            if (announcement.titleText != 0) {
                title.setText(announcement.titleText)
                title.visible()
            } else {
                title.gone()
            }

            if (announcement.background != 0) {
                container.setBackgroundResource(announcement.background)
            } else {
                container.setBackgroundColor(Color.WHITE)
            }

            if (announcement.bodyText != 0) {
                body.setText(announcement.bodyText)
                body.visible()
            } else {
                body.gone()
            }

            if (announcement.iconImage != 0) {
                icon.setImageDrawable(ContextCompat.getDrawable(itemView.context, announcement.iconImage))
                icon.visible()
            } else {
                icon.gone()
            }

            if (announcement.ctaText != 0) {
                ctaBtn.setText(announcement.ctaText)
                ctaBtn.setOnClickListener {
                    analytics.logEvent(AnnouncementAnalyticsEvent.CardActioned(announcement.name))
                    announcement.ctaClicked()
                }
                ctaBtn.visible()
            } else {
                ctaBtn.gone()
            }

            if (announcement.dismissText != 0) {
                dismissBtn.setText(announcement.dismissText)
                dismissBtn.setOnClickListener {
                    analytics.logEvent(AnnouncementAnalyticsEvent.CardDismissed(announcement.name))
                    announcement.dismissClicked()
                }
                dismissBtn.visible()
                closeBtn.gone()
            } else {
                dismissBtn.gone()
            }

            if (announcement.dismissRule != DismissRule.CardPersistent) {
                closeBtn.setOnClickListener {
                    analytics.logEvent(AnnouncementAnalyticsEvent.CardDismissed(announcement.name))
                    announcement.dismissClicked()
                }
                closeBtn.visible()
            } else {
                closeBtn.gone()
                dismissBtn.gone()
            }

            paintButtons(announcement.buttonColor)
        }
        analytics.logEvent(AnnouncementAnalyticsEvent.CardShown(announcement.name))
    }

    override fun isForViewType(items: List<T>, position: Int): Boolean {
        val item = items[position]
        return item is StandardAnnouncementCard
    }

    override fun onCreateViewHolder(parent: ViewGroup): RecyclerView.ViewHolder =
        AnnouncementViewHolder(
            parent.inflate(R.layout.item_announcement_standard)
        )

    private class AnnouncementViewHolder internal constructor(
        itemView: View
    ) : RecyclerView.ViewHolder(itemView) {

        internal val container: View = itemView.card_container
        internal val icon: ImageView = itemView.icon
        internal val title: TextView = itemView.msg_title
        internal val body: TextView = itemView.msg_body
        internal val closeBtn: ImageView = itemView.btn_close
        internal val ctaBtn: TextView = itemView.btn_cta1
        internal val dismissBtn: TextView = itemView.btn_dismiss

        fun paintButtons(@ColorRes btnColour: Int) {
            val colour = ContextCompat.getColor(ctaBtn.context, btnColour)
            if (ctaBtn.isVisible()) {
                ctaBtn.setBackgroundColor(colour)
            }

            if (dismissBtn.isVisible()) {
                val bgColour = ContextCompat.getColor(ctaBtn.context, R.color.announce_background)
                val gd = GradientDrawable()
                gd.setColor(bgColour)
                gd.setStroke(2, colour)
                dismissBtn.background = gd
                dismissBtn.setTextColor(colour)
            }
        }
    }
}

class MiniAnnouncementDelegate<in T>(private val analytics: Analytics) :
    AdapterDelegate<T> {
    override fun isForViewType(items: List<T>, position: Int): Boolean {
        val item = items[position]
        return item is MiniAnnouncementCard
    }

    override fun onCreateViewHolder(parent: ViewGroup): RecyclerView.ViewHolder =
        AnnouncementViewHolder(
            parent.inflate(R.layout.item_announcement_mini)
        )

    override fun onBindViewHolder(
        items: List<T>,
        position: Int,
        holder: RecyclerView.ViewHolder
    ) {
        val announcement = items[position] as MiniAnnouncementCard
        (holder as AnnouncementViewHolder).apply {
            if (announcement.titleText != 0) {
                title.setText(announcement.titleText)
                title.visible()
            } else {
                title.gone()
            }
            if (announcement.bodyText != 0) {
                body.setText(announcement.bodyText)
                body.visible()
            } else {
                body.gone()
            }
            if (announcement.iconImage != 0) {
                icon.setImageDrawable(ContextCompat.getDrawable(itemView.context, announcement.iconImage))
                icon.visible()
            } else {
                icon.gone()
            }

            if (announcement.background != 0) {
                cardContainer.setBackgroundResource(announcement.background)
            } else {
                cardContainer.setBackgroundColor(Color.WHITE)
            }

            if (announcement.hasCta) {
                cardContainer.setOnClickListener {
                    analytics.logEvent(AnnouncementAnalyticsEvent.CardActioned(announcement.name))
                    announcement.ctaClicked()
                }
                actionIcon.visible()
            } else {
                actionIcon.gone()
            }
        }
        analytics.logEvent(AnnouncementAnalyticsEvent.CardShown(announcement.name))
    }

    private class AnnouncementViewHolder internal constructor(
        itemView: View
    ) : RecyclerView.ViewHolder(itemView) {
        internal val icon: ImageView = itemView.icon
        internal val title: TextView = itemView.msg_title
        internal val body: TextView = itemView.msg_body
        internal val cardContainer: View = itemView.mini_card_container
        internal val actionIcon: ImageView = itemView.action_icon
    }
}
