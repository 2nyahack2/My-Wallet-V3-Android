package piuk.blockchain.android.ui.transfer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import kotlinx.android.synthetic.main.fragment_transfer.*
import piuk.blockchain.android.R
import piuk.blockchain.android.ui.transfer.receive.TransferReceiveFragment
import piuk.blockchain.android.ui.transfer.send.TransferSendFragment
import piuk.blockchain.androidcore.utils.helperfunctions.unsafeLazy
import piuk.blockchain.android.util.inflate

class TransferFragment : Fragment() {

    private val startingView: TransferViewType by unsafeLazy {
        arguments?.getSerializable(PARAM_START_VIEW) as? TransferViewType ?: TransferViewType.TYPE_SEND
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = container?.inflate(R.layout.fragment_transfer)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        transfer_tabs.setupWithViewPager(transfer_pager)

        transfer_pager.adapter = TransferPagerAdapter(
            listOf(getString(R.string.send), getString(R.string.common_receive)),
            childFragmentManager
        )

        transfer_pager.setCurrentItem(
            when (startingView) {
                TransferViewType.TYPE_SEND -> TransferViewType.TYPE_SEND.ordinal
                TransferViewType.TYPE_RECEIVE -> TransferViewType.TYPE_RECEIVE.ordinal
            }, true
        )
    }

    companion object {

        private const val PARAM_START_VIEW = "show_view"

        fun newInstance(transferViewType: TransferViewType = TransferViewType.TYPE_SEND): TransferFragment =
            TransferFragment().apply {
                arguments = Bundle().apply {
                    putSerializable(PARAM_START_VIEW, transferViewType)
                }
            }
    }

    enum class TransferViewType {
        TYPE_SEND,
        TYPE_RECEIVE
    }
}

class TransferPagerAdapter(
    private val titlesList: List<String>,
    fragmentManager: FragmentManager
) : FragmentPagerAdapter(fragmentManager, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {

    override fun getCount(): Int = titlesList.size
    override fun getPageTitle(position: Int): CharSequence =
        titlesList[position]

    override fun getItem(position: Int): Fragment =
        when (position) {
            0 -> TransferSendFragment.newInstance()
            else -> TransferReceiveFragment.newInstance()
        }
}