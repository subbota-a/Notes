package ru.yandex.subbota_job.notes.viewController

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.support.design.widget.FloatingActionButton
import android.support.design.widget.Snackbar
import android.support.v4.app.ActivityCompat
import android.support.v4.app.ActivityOptionsCompat
import android.support.v7.app.AppCompatActivity
import android.support.v7.view.ActionMode
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.Toolbar
import android.text.TextUtils
import android.util.Log
import android.view.GestureDetector
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.widget.SearchView
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import ru.yandex.subbota_job.notes.*
import ru.yandex.subbota_job.notes.executor.ImportService
import ru.yandex.subbota_job.notes.viewModel.NotesListViewModel
import com.google.firebase.auth.FirebaseAuth


class NotesListActivity : AppCompatActivity() {
	private lateinit var mNotesAdaptor: NoteDescriptionListAdapter
	private lateinit var mList: RecyclerView
	private var mNewNote: FloatingActionButton? = null
	//private var mFilterString: String? = null
	private var editedFile: String? = null

	private lateinit var viewModel: NotesListViewModel

	private lateinit var signInClient: GoogleSignInClient

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		if (!isAuthenticated()) {
			startAuthentication()
			return
		}

		setContentView(R.layout.activity_notes_list)
		val toolbar = findViewById<Toolbar>(R.id.toolbar)
		setSupportActionBar(toolbar)

		viewModel = of(this).get(NotesListViewModel::class.java)

		mNotesAdaptor = NoteDescriptionListAdapter(this)

		mList = findViewById(R.id.listview)!!
		mList.layoutManager = LinearLayoutManager(this)
		mList.adapter = mNotesAdaptor

		if (!TextUtils.isEmpty(editedFile)) {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
				postponeEnterTransition()
			}
		}
		/*
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
		*/
		RecyclerViewGestureDetector(this, mList!!, GestureController())

		mNewNote = findViewById(R.id.fab)
		mNewNote!!.setOnClickListener { editNote(null, false) }

		viewModel.undoSnackbar.observe(this, Observer {
			if (it != null && !it.isEmpty())
				showUndoDeleteSnackbar(it)
		})
		if (getPreferences(Context.MODE_PRIVATE).contains(KeyEditingId)) {
			val id = getPreferences(Context.MODE_PRIVATE).getLong(KeyEditingId, 0)
			editNote(if (id==0L) null else id, true)
		}
		startImport(false)
	}

	override fun onSaveInstanceState(outState: Bundle) {
		super.onSaveInstanceState(outState)
	}

	internal inner class GestureController : GestureDetector.SimpleOnGestureListener(), ActionMode.Callback {
		private var mActionMode: ActionMode? = null
		val isSelectionMode: Boolean get() = mActionMode != null
		init{
			viewModel.activeMode.observe(this@NotesListActivity, Observer<Boolean>{
				if (it == isSelectionMode) return@Observer
				if (it!!)
					beginSelectionMode()
				else
					endSelectionMode()
			})
		}

		fun getItemId(e: MotionEvent): Long {
			val itemView = mList!!.findChildViewUnder(e.x, e.y) ?: return RecyclerView.NO_ID
			return mList!!.getChildItemId(itemView)
		}

		override fun onSingleTapUp(e: MotionEvent): Boolean {
			val id = getItemId(e)
			if (id == RecyclerView.NO_ID)
				return false
			if (isSelectionMode)
				toggleSelection(id)
			else
				editNote(id, false)
			return true
		}

		override fun onLongPress(e: MotionEvent) {
			val id = getItemId(e)
			if (id == RecyclerView.NO_ID)
				return
			toggleSelection(id)
		}

		fun toggleSelection(id:Long) {
			if (!isSelectionMode)
				beginSelectionMode()
			viewModel.selectedIds.toggle(id)
			val count = viewModel.selectedIds.count
			mActionMode!!.title = count.toString()
			if (count == 0)
				endSelectionMode()
		}

		private fun endSelectionMode() {
			mActionMode!!.finish()
		}

		private fun beginSelectionMode() {
			mActionMode = startSupportActionMode(this)
			viewModel.activeMode.value = true
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
				val ids = viewModel.selectedIds.items.toList()
				endSelectionMode()
				softDelete(ids)
				return true
			}
			return false
		}
		override fun onDestroyActionMode(mode: ActionMode) {
			mActionMode = null
			viewModel.activeMode.value = false
			viewModel.selectedIds.clear()
			mNewNote!!.show()
		}
	}
	private fun softDelete(ids: List<Long>){
		viewModel.softDelete(ids, true)
		viewModel.undoSnackbar.value = ids
	}

	private fun showUndoDeleteSnackbar(ids: List<Long>) {
		val message = getString(R.string.removed_notes_message, ids.size)
		val snackbar = Snackbar.make(findViewById(R.id.coordinatorLayout), message, Snackbar.LENGTH_LONG)
		snackbar.setAction(R.string.undo) {
			viewModel.softDelete(ids, false)
		}
		snackbar.addCallback(object: Snackbar.Callback(){
			override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
				Log.d("showUndoDeleteSnackbar", "onDismissed $event")
				viewModel.undoSnackbar.value = null
			}
		})
		snackbar.show()
	}

	private fun editNote(id:Long?, continueEditing: Boolean) {
		val intent = Intent(this, NoteContentActivity::class.java)
		if (id != null)
			intent.putExtra(NoteContentActivity.IdKey, id)
		intent.putExtra(NoteContentActivity.ContinueKey, continueEditing)
		/*if (Build.VERSION.SDK_INT >= 21 ) {
            RecyclerView.ViewHolder vh = mList.findViewHolderForAdapterPosition(position);
            View view = vh.itemView;
            view = view.findViewById(android.R.id.text1);
            options = ActivityOptionsCompat.makeSceneTransitionAnimation(this, view, item.mFileName.getName());
        }else*/
		val options = ActivityOptionsCompat.makeCustomAnimation(this, R.anim.go_into_from_right, R.anim.go_away_to_left)
		ActivityCompat.startActivityForResult(this, intent, RC_EDITING, options.toBundle())
		getPreferences(Context.MODE_PRIVATE).edit().putLong(KeyEditingId, id ?: 0).apply()
	}

	override fun onResume() {
		Log.d("NodesListActivity", "onResume")
		super.onResume()
	}

	private fun isAuthenticated() : Boolean = FirebaseAuth.getInstance()?.currentUser != null

	private fun startAuthentication(){
		val intent = Intent(this, ConnectionActivity::class.java)
				.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
				.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
		startActivity(intent)
	}

	private fun startImport(force:Boolean = false) {
		ImportService.startImport(this, force)
	}

	override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
		super.onActivityResult(requestCode, resultCode, data)
		when(requestCode) {
			RC_EDITING -> getPreferences(Context.MODE_PRIVATE).edit().remove(KeyEditingId).apply()
		}
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
		if (item.itemId == R.id.action_import) {
			startImport(force = true)
			return true
		}
		return super.onOptionsItemSelected(item)
	}

	internal inner class Search(val mItem: MenuItem) : MenuItem.OnActionExpandListener, MenuItem.OnMenuItemClickListener, SearchView.OnQueryTextListener, SearchView.OnCloseListener {
		private val mSearchView: SearchView
		private var searchMode : Boolean  = false
		init {
			mItem.setOnMenuItemClickListener(this)
			mItem.setOnActionExpandListener(this)
			mSearchView = mItem.actionView as android.widget.SearchView
			mSearchView.queryHint = resources.getString(R.string.action_search)
			mSearchView.isSubmitButtonEnabled = false
			mSearchView.isIconified = true
			mSearchView.setOnCloseListener(this)
			mSearchView.setOnQueryTextListener(this)
			viewModel.searchMode.observe(this@NotesListActivity, Observer {
				if (it!! == searchMode)
					return@Observer
				if (it) {
					beginSearchMode()
					mItem.expandActionView()
				}else {
					endSearchMode()
					mItem.collapseActionView()
				}
			})
		}

		private fun beginSearchMode()
		{
			mSearchView.setQuery(viewModel.filterString, false)
			mSearchView.isIconified = false
			searchMode = true
			viewModel.searchMode.value = searchMode
		}
		private fun endSearchMode()
		{
			mSearchView.clearFocus()
			viewModel.filterString = null
			searchMode = false
			viewModel.searchMode.value = searchMode
		}
		override fun onMenuItemClick(item: MenuItem): Boolean {
			// expandActionView is called internally
			return true
		}

		override fun onMenuItemActionExpand(item: MenuItem): Boolean {
			beginSearchMode()
			return true
		}

		override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
			Log.d("Search", "onMenuItemActionCollapse")
			endSearchMode()
			return true
		}

		override fun onQueryTextSubmit(query: String): Boolean {
			Log.d("Search", "onQueryTextSubmit")
			return true
		}

		private fun setNullOnEmpty(s: String?): String? {
			return if (TextUtils.isEmpty(s)) null else s
		}

		override fun onQueryTextChange(query_: String?): Boolean {
			Log.d("Search", "onQueryTextChange")
			viewModel.filterString = setNullOnEmpty(query_)
			return true
		}

		override fun onClose(): Boolean {
			Log.d("Search", "onClose")
			mItem.collapseActionView()
			return false
		}
	}

	companion object {
		private const val RC_EDITING = 11;
		private const val KeyEditingId = "EditingNoteId"
	}
}
