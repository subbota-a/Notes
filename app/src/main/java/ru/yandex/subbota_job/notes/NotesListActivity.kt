package ru.yandex.subbota_job.notes

import android.annotation.TargetApi
import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.support.design.widget.FloatingActionButton
import android.support.v4.app.ActivityCompat
import android.support.v4.app.ActivityOptionsCompat
import android.support.v4.app.DialogFragment
import android.support.v4.view.MenuItemCompat
import android.support.v7.app.ActionBar
import android.support.v7.app.AppCompatActivity
import android.support.v7.view.ActionMode
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.text.TextUtils
import android.util.Log
import android.widget.SearchView
import android.support.v7.widget.Toolbar
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.Menu
import android.view.MenuItem

import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.SupportErrorDialogFragment

class NotesListActivity : AppCompatActivity() {
	private var mNotesAdaptor: NotesListAdapter? = null
	private var mList: RecyclerView? = null
	private var mNewNote: FloatingActionButton? = null
	private var mFilterString: String? = null
	private var editedFile: String? = null

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_notes_list)
		val toolbar = findViewById<Toolbar>(R.id.toolbar)
		setSupportActionBar(toolbar)

		val actionBar = supportActionBar
		assert(actionBar != null)

		mNotesAdaptor = NotesListAdapter(this)

		mList = findViewById(R.id.listview)
		assert(mList != null)
		mList!!.layoutManager = LinearLayoutManager(this)
		mList!!.adapter = mNotesAdaptor

		if (savedInstanceState != null) {
			editedFile = savedInstanceState.getString(editedFileKey)
		}
		if (!TextUtils.isEmpty(editedFile)) {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
				postponeEnterTransition()
			}
		}
		mNotesAdaptor!!.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
			override fun onChanged() {
				super.onChanged()
				//                mNotesAdaptor.unregisterAdapterDataObserver(this);
				if (!TextUtils.isEmpty(editedFile))
					if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
						startPostponedEnterTransition()
					}
				val m = mList!!.layoutManager as LinearLayoutManager
				val pos1 = m.findFirstCompletelyVisibleItemPosition()
				val pos2 = m.findLastCompletelyVisibleItemPosition()
				Log.d("onChanged", String.format("%d,%d", pos1, pos2))
				var i = 0
				while (i < mNotesAdaptor!!.itemCount && !TextUtils.isEmpty(editedFile)) {
					if (mNotesAdaptor!!.getItem(i).mFileName!!.name == editedFile) {
						if (i < pos1 || i > pos2)
							m.scrollToPosition(i)
						break
					}
					++i
				}
				editedFile = null
			}
		})

		RecyclerViewGestureDetector(this, mList!!, GestureController())


		mNewNote = findViewById(R.id.fab)
		mNewNote!!.setOnClickListener { createNewNote() }

		//mNotesAdaptor.beginUpdate();
		mNotesAdaptor!!.updateAsync(mFilterString)
	}

	override fun onSaveInstanceState(outState: Bundle) {
		super.onSaveInstanceState(outState)
		outState.putString(editedFileKey, editedFile)
	}

	override fun onPostCreate(savedInstanceState: Bundle?) {
		super.onPostCreate(savedInstanceState)
		val draft = DraftStorage(this)
		if (!TextUtils.isEmpty(draft.draftContent)) {
			val intent = Intent(this, NoteContentActivity::class.java)
			intent.data = Uri.Builder().path(draft.draftPath).build()
			startActivityForResult(intent, 0)
		}
	}

	internal inner class GestureController : GestureDetector.SimpleOnGestureListener(), ActionMode.Callback {
		private var mActionMode: ActionMode? = null
		val isSelectionMode: Boolean
			get() = mActionMode != null

		fun getAdapterPosition(e: MotionEvent): Int {
			val itemView = mList!!.findChildViewUnder(e.x, e.y) ?: return RecyclerView.NO_POSITION
			return mList!!.getChildAdapterPosition(itemView)
		}

		override fun onSingleTapUp(e: MotionEvent): Boolean {
			val position = getAdapterPosition(e)
			if (position == RecyclerView.NO_POSITION)
				return false
			if (isSelectionMode)
				toggleSelection(position)
			else
				editNote(position)
			return true
		}

		override fun onLongPress(e: MotionEvent) {
			val position = getAdapterPosition(e)
			if (position == RecyclerView.NO_POSITION)
				return
			toggleSelection(position)
		}

		fun toggleSelection(position: Int) {
			if (!isSelectionMode)
				beginSelectionMode()
			mNotesAdaptor!!.toggleSelection(position)
			val count = mNotesAdaptor!!.selectionCount
			mActionMode!!.title = count.toString()
			if (count == 0)
				endSelectionMode()
		}

		private fun endSelectionMode() {
			mActionMode!!.finish()
		}

		private fun beginSelectionMode() {
			mActionMode = startSupportActionMode(this)
			mNewNote!!.hide()
		}

		override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
			mode.menuInflater.inflate(R.menu.selected_notes_menu, menu)
			return true
		}

		override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
			return false
		}

		override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
			if (item.itemId == R.id.action_delete) {
				mNotesAdaptor!!.deleteSelectedAsync(findViewById(R.id.coordinatorLayout))
				endSelectionMode()
				return true
			}
			return false
		}

		override fun onDestroyActionMode(mode: ActionMode) {
			mActionMode = null
			mNotesAdaptor!!.clearAllSelection()
			mNewNote!!.show()
		}
	}

	private fun editNote(position: Int) {
		val item = mNotesAdaptor!!.getItem(position)
		val intent = Intent(this, NoteContentActivity::class.java)
		intent.data = Uri.fromFile(item.mFileName)
		val options: ActivityOptionsCompat
		/*if (Build.VERSION.SDK_INT >= 21 ) {
            RecyclerView.ViewHolder vh = mList.findViewHolderForAdapterPosition(position);
            View view = vh.itemView;
            view = view.findViewById(android.R.id.text1);
            options = ActivityOptionsCompat.makeSceneTransitionAnimation(this, view, item.mFileName.getName());
        }else*/
		options = ActivityOptionsCompat.makeCustomAnimation(this, R.anim.go_into_from_right, R.anim.go_away_to_left)
		ActivityCompat.startActivityForResult(this, intent, 0, options.toBundle())
		editedFile = item.mFileName!!.name
	}

	private fun createNewNote() {
		val intent = Intent(this, NoteContentActivity::class.java)
		val options: ActivityOptionsCompat
		options = ActivityOptionsCompat.makeCustomAnimation(this, R.anim.go_into_from_right, R.anim.go_away_to_left)
		ActivityCompat.startActivityForResult(this, intent, 0, options.toBundle())
	}

	override fun onResume() {
		Log.d("NodesListActivity", "onResume")
		super.onResume()
		//mNotesAdaptor.endUpdate();
	}

	override fun onPause() {
		Log.d("NodesListActivity", "onPause")
		super.onPause()
		//mNotesAdaptor.beginUpdate();
	}

	override fun onCreateOptionsMenu(menu: Menu): Boolean {
		menuInflater.inflate(R.menu.menu_notes_list, menu)
		val mi = menu.findItem(R.id.action_search)
		Search(mi)
		return true
	}

	override fun onOptionsItemSelected(item: MenuItem): Boolean {
		if (item.itemId == R.id.action_sync) {
			if (!SyncService.isSyncAvailable(this)) {
				SyncService.showAvailableError(this, 2)
			} else
				SyncService.syncAll(applicationContext)
			return true
		}
		return super.onOptionsItemSelected(item)
	}

	internal inner class Search(val mItem: MenuItem) : MenuItem.OnActionExpandListener, MenuItem.OnMenuItemClickListener, SearchView.OnQueryTextListener, SearchView.OnCloseListener {
		val mSearchView: SearchView

		init {
			mItem.setOnMenuItemClickListener(this)
			mItem.setOnActionExpandListener(this)
			mSearchView = mItem.actionView as android.widget.SearchView
			mSearchView.queryHint = resources.getString(R.string.action_search)
			mSearchView.isSubmitButtonEnabled = false
			mSearchView.isIconified = true
			mSearchView.setOnCloseListener(this)
			mSearchView.setOnQueryTextListener(this)
		}

		override fun onMenuItemClick(item: MenuItem): Boolean {
			mSearchView.setQuery(mFilterString, false)
			mSearchView.isIconified = false
			return true
		}

		override fun onMenuItemActionExpand(item: MenuItem): Boolean {
			return true
		}

		override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
			Log.d("Search", "onMenuItemActionCollapse")
			mSearchView.clearFocus()
			if (!TextUtils.isEmpty(mFilterString))
				mNotesAdaptor!!.updateAsync(null)
			mFilterString = null
			return true
		}

		override fun onQueryTextSubmit(query: String): Boolean {
			Log.d("Search", "onQueryTextSubmit")
			return true
		}

		fun setNullOnEmpty(s: String?): String? {
			return if (TextUtils.isEmpty(s)) null else s
		}

		override fun onQueryTextChange(query: String?): Boolean {
			var query = query
			Log.d("Search", "onQueryTextChange")
			query = setNullOnEmpty(query)
			mFilterString = setNullOnEmpty(mFilterString)
			if (!TextUtils.equals(mFilterString, query)) {
				mFilterString = query
				mNotesAdaptor!!.updateAsync(mFilterString)
			}
			return true
		}

		override fun onClose(): Boolean {
			Log.d("Search", "onClose")
			mItem.collapseActionView()
			return false
		}
	}

	companion object {
		val NotesDirectory = "notes"
		private val editedFileKey = "editedFileKey"
	}
}
