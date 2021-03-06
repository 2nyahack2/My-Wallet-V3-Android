package piuk.blockchain.android.ui.activity.detail.adapter

import piuk.blockchain.android.ui.adapters.AdapterDelegatesManager
import piuk.blockchain.android.ui.adapters.DelegationAdapter

class ActivityDetailsDelegateAdapter(
    onActionItemClicked: () -> Unit,
    onDescriptionItemUpdated: (String) -> Unit
) : DelegationAdapter<Any>(AdapterDelegatesManager(), emptyList()) {

    init {
        // Add all necessary AdapterDelegate objects here
        with(delegatesManager) {
            addAdapterDelegate(ActivityDetailInfoItemDelegate())
            addAdapterDelegate(ActivityDetailDescriptionItemDelegate(onDescriptionItemUpdated))
            addAdapterDelegate(ActivityDetailActionItemDelegate(onActionItemClicked))
        }
    }
}
