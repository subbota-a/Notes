package ru.yandex.subbota_job.notes.viewController

import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import android.graphics.Canvas
import android.os.Build
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.ItemTouchHelper
import android.view.*
import android.widget.TextView
import ru.yandex.subbota_job.notes.R
import ru.yandex.subbota_job.notes.dataModel.NoteDescription
import ru.yandex.subbota_job.notes.viewModel.NotesListViewModel
import ru.yandex.subbota_job.notes.viewModel.SelectedItems
import android.util.Log
import android.view.LayoutInflater
import com.google.android.gms.tasks.Continuation

interface DragAndMove {
	fun moveItem(srcPos: Int, dstPos: Int): Boolean
	fun beginDrag(holder: androidx.recyclerview.widget.RecyclerView.ViewHolder)
	fun endDrag()
}
interface ItemClickListener{
	fun itemTapUp(holder: androidx.recyclerview.widget.RecyclerView.ViewHolder) : Boolean
	fun itemLongPress(holder: androidx.recyclerview.widget.RecyclerView.ViewHolder) : Boolean
}
typealias OnChanged = (dragMode:Boolean)->Unit

class NoteDescriptionListAdapter(var activity : androidx.fragment.app.FragmentActivity, val itemClickListener: ItemClickListener, val dragModeListener: OnChanged): androidx.recyclerview.widget.RecyclerView.Adapter<NoteDescriptionListAdapter.ViewHolder>(), DragAndMove{
	private val logTag = "NoteListAdapter"
	private var mList = ArrayList<NoteDescription>()
	private val viewModel = ViewModelProviders.of(activity).get(NotesListViewModel::class.java)
	private var mSelected  = SelectedItems<Long>()
	private var mItemTouchHelper: ItemTouchHelper? = null
	private val observer = Observer<List<NoteDescription>>{
		if (mList.equals(it))
			return@Observer
		mList.clear()
		it?.also{ mList.addAll(it)}
		notifyDataSetChanged()
	}
	var filteredLiveData : LiveData<List<NoteDescription>>? = null
	init{
		beginListenData()
		viewModel.selectedIds.liveData.observe(this.activity, Observer{
			val gone = mSelected.items.minus(it!!.items)
			val come = it.items.minus(mSelected.items)
			val difference = gone + come
			mSelected.items = it.items
			difference.forEach { id -> notifyItemChanged(mList.indexOfFirst { item -> item.id == id }) }
		})
		setHasStableIds(true)
	}

	var defferCount: Int = 0
	private fun beginListenData()
	{
		if (defferCount++ == 0) {
			filteredLiveData = viewModel.filteredList()
			filteredLiveData!!.observe(activity, observer)
		}
	}
	private fun terminateListenData()
	{
		if (--defferCount == 0) {
			filteredLiveData?.removeObserver(observer)
			filteredLiveData = null
		}
	}
	override fun getItemId(position: Int): Long {
		return mList[position].id
	}

	override fun getItemCount() = mList.size

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
		return ViewHolder.create(this, itemClickListener, parent)
	}

	override fun onBindViewHolder(holder: ViewHolder, position: Int) {
		val item = mList[position]
		holder.bindItem(mList[position])
		holder.setSelected(mSelected.contains(item.id))
	}

	override fun onAttachedToRecyclerView(recyclerView: androidx.recyclerview.widget.RecyclerView) {
		super.onAttachedToRecyclerView(recyclerView)
		//mInflater = LayoutInflater.FromContext(recyclerView.Context)
		val elevationOn = recyclerView.resources.displayMetrics.density * 24
		val elevationOff = recyclerView.resources.displayMetrics.density * 4
		val callback = ItemTouchHelperCallback(this, elevationOn, elevationOff)
		mItemTouchHelper = ItemTouchHelper(callback)
		mItemTouchHelper!!.attachToRecyclerView(recyclerView)
	}

	override fun moveItem(srcPos: Int, dstPos: Int): Boolean {
		val srcItem = mList[srcPos]
		val dstItem = mList[dstPos]

		val tmp = srcItem.position
		srcItem.position = dstItem.position
		dstItem.position = tmp

		mList.removeAt(srcPos)
		mList.add(dstPos, srcItem)
		notifyItemMoved(srcPos, dstPos)
		terminateListenData()
		viewModel.saveNotesPositions(listOf(srcItem, dstItem)).continueWith(Continuation<Unit,Unit> { beginListenData() })
		Log.d(logTag, "moveItem $srcPos->$dstPos")
		return true;
	}

	override fun beginDrag(holder: androidx.recyclerview.widget.RecyclerView.ViewHolder) {
		Log.d(logTag, "beginDrag")
		terminateListenData()
		mItemTouchHelper?.startDrag(holder)
		dragModeListener(true)
	}

	override fun endDrag() {
		Log.d(logTag, "endDrag")
		beginListenData()
		dragModeListener(false)
	}

	class ViewHolder(private val adapter: DragAndMove, private val clickListener: ItemClickListener, itemView: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView) {
		private val mItem: TextView = itemView.findViewById(R.id.title)
		init{
			mItem.setOnLongClickListener { clickListener.itemLongPress(this) }
			mItem.setOnClickListener { clickListener.itemTapUp(this) }
			val dragHandle: View = itemView.findViewById(R.id.drag_handle);
			dragHandle.setOnTouchListener(View.OnTouchListener { _, event ->
				if (event.actionMasked == MotionEvent.ACTION_DOWN) adapter.beginDrag(this);
				false
			})
		}

		fun bindItem(n: NoteDescription) {
			mItem.text = n.title
			mItem.maxLines = 1
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
				mItem.transitionName = n.id.toString()
			}
		}

		fun setSelected(selected: Boolean) {
			itemView.isSelected = selected
		}

		companion object {
			fun create(adapter: DragAndMove, itemClickListener: ItemClickListener, parent: ViewGroup): ViewHolder {
				return ViewHolder(adapter, itemClickListener,
						LayoutInflater.from(parent.context)
								.inflate(R.layout.notes_item, parent, false))
			}
		}
	}

	class ItemTouchHelperCallback(private val mAdapter: DragAndMove, private val mElevation: Float, private val elevationOff:Float) : ItemTouchHelper.Callback() {
		private val logTag = "ItemTouchHelper"
		override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
			TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
		}

		override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
			return makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0);
		}

		override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
			return mAdapter.moveItem(viewHolder.adapterPosition, target.adapterPosition);
		}

		override fun onChildDraw(c: Canvas, recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean) {
			val v = viewHolder.itemView;
			v.translationX = dX;
			v.translationY = dY;
			v.elevation = if (isCurrentlyActive) mElevation else elevationOff;
			v.alpha = if (isCurrentlyActive) 0.8f else 1f;

		}

		override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
			val v = viewHolder.itemView;
			v.elevation = elevationOff
			v.alpha = 1f;
		}

		override fun onSelectedChanged(viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder?, actionState: Int) {
			super.onSelectedChanged(viewHolder, actionState)
			if (actionState == ItemTouchHelper.ACTION_STATE_IDLE)
				mAdapter.endDrag()
		}

		override fun canDropOver(recyclerView: RecyclerView, current: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean  = true

		override fun isItemViewSwipeEnabled(): Boolean = false
		override fun isLongPressDragEnabled(): Boolean = false
	}
}