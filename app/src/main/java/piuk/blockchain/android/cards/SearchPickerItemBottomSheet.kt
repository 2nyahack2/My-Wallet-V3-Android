package piuk.blockchain.android.cards

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.annotation.NonNull
import androidx.annotation.Nullable
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.android.synthetic.main.picker_layout.*
import piuk.blockchain.android.R
import piuk.blockchain.androidcore.utils.helperfunctions.unsafeLazy
import piuk.blockchain.android.util.AfterTextChangedWatcher
import java.io.Serializable
import java.util.Locale

class SearchPickerItemBottomSheet : BottomSheetDialogFragment() {

    private val searchResults = mutableListOf<PickerItem>()
    private val adapter by unsafeLazy {
        PickerItemsAdapter {
            (parentFragment as? PickerItemListener)?.onItemPicked(it)
            dismiss()
        }
    }
    private val items: List<PickerItem> by unsafeLazy {
        (arguments?.getSerializable(PICKER_ITEMS) as? List<PickerItem>) ?: emptyList()
    }

    override fun onCreateView(
        @NonNull inflater: LayoutInflater,
        @Nullable container: ViewGroup?,
        @Nullable savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.picker_layout, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        country_code_picker_search.addTextChangedListener(object : AfterTextChangedWatcher() {
            override fun afterTextChanged(searchQuery: Editable) {
                search(searchQuery.toString())
            }
        })

        val layoutManager = LinearLayoutManager(activity)

        picker_recycler_view.layoutManager = layoutManager
        picker_recycler_view.adapter = adapter
        adapter.items = items

        country_code_picker_search.setOnEditorActionListener { _, _, _ ->
            val imm: InputMethodManager = country_code_picker_search.context
                .getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(country_code_picker_search.windowToken, 0)
            true
        }
        configureRootViewMinHeight()
    }

    private fun configureRootViewMinHeight() {
        val displayMetrics = DisplayMetrics()
        activity?.windowManager?.defaultDisplay?.getMetrics(displayMetrics)?.let {
            rootView.minimumHeight = (displayMetrics.heightPixels * 0.6).toInt()
        }
    }

    private fun search(searchQuery: String) {
        searchResults.clear()
        for (item in items) {
            if (item.label.toLowerCase(Locale.getDefault()).contains(searchQuery.toLowerCase(Locale.getDefault()))) {
                searchResults.add(item)
            }
        }
        adapter.items = searchResults
    }

    companion object {
        private const val PICKER_ITEMS = "PICKER_ITEMS"
        fun newInstance(items: List<PickerItem>): SearchPickerItemBottomSheet =
            SearchPickerItemBottomSheet().apply {
                arguments = Bundle().also {
                    it.putSerializable(PICKER_ITEMS, items as Serializable)
                }
            }
    }
}

interface PickerItem {
    val label: String
    val code: String
    val icon: String?
}

interface PickerItemListener {
    fun onItemPicked(item: PickerItem)
}