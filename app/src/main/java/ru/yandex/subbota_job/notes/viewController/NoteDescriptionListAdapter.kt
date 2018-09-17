package ru.yandex.subbota_job.notes.viewController

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.os.Build
import android.support.v4.app.FragmentActivity
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import ru.yandex.subbota_job.notes.dataModel.NoteDescription
import ru.yandex.subbota_job.notes.R
import ru.yandex.subbota_job.notes.viewModel.NotesListViewModel
import ru.yandex.subbota_job.notes.viewModel.SelectedItems

class NoteDescriptionListAdapter(var activity : FragmentActivity): RecyclerView.Adapter<NoteDescriptionListAdapter.ViewHolder>() {
	private var mList = ArrayList<NoteDescription>()
	private val viewModel = ViewModelProviders.of(activity).get(NotesListViewModel::class.java)
	private var mSelected  = SelectedItems<Long>()
	init{
		viewModel.filteredList.observe( this.activity, Observer<List<NoteDescription>>{
			mList.clear()
			it?.also{ mList.addAll(it)}
			notifyDataSetChanged()
		})
		viewModel.selectedIds.liveData.observe(this.activity, Observer{
			val gone = mSelected.items.minus(it!!.items)
			val come = it!!.items.minus(mSelected.items)
			val difference = gone + come
			mSelected.items = it.items
			difference.forEach { id -> notifyItemChanged(mList.indexOfFirst { item -> item.id == id }) }
		})
		setHasStableIds(true)
	}

	override fun getItemId(position: Int): Long {
		return mList[position].id!!
	}

	override fun getItemCount() = mList.size

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
		return ViewHolder.create(parent)
	}

	override fun onBindViewHolder(holder: ViewHolder, position: Int) {
		val item = mList[position]
		holder.bindItem(mList[position])
		holder.setSelected(mSelected.contains(item.id!!))
	}

	class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
		private val mItem: TextView = itemView.findViewById<View>(android.R.id.text1) as TextView

		fun bindItem(n: NoteDescription) {
			mItem.text = n.title
			mItem.maxLines = 1
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
				mItem.transitionName = n.id?.toString()
			}
		}

		fun setSelected(selected: Boolean) {
			itemView.isSelected = selected
		}

		companion object {
			fun create(parent: ViewGroup): ViewHolder {
				return ViewHolder(
						LayoutInflater.from(parent.context)
								.inflate(R.layout.notes_item, parent, false))
			}
		}
	}
}