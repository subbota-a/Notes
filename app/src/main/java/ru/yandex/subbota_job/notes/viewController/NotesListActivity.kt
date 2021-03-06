package ru.yandex.subbota_job.notes.viewController

import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityOptionsCompat
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.appcompat.widget.Toolbar
import android.text.TextUtils
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.SearchView
import android.widget.Toast
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import ru.yandex.subbota_job.notes.*
import ru.yandex.subbota_job.notes.executor.ImportService
import ru.yandex.subbota_job.notes.viewModel.NotesListViewModel
import com.google.firebase.auth.FirebaseAuth
import ru.yandex.subbota_job.notes.viewModel.DeleteResult
import ru.yandex.subbota_job.notes.viewModel.EventObserver


class NotesListActivity : AppCompatActivity() {
	private lateinit var mNotesAdaptor: NoteDescriptionListAdapter
	private lateinit var mList: androidx.recyclerview.widget.RecyclerView
	private var mNewNote: FloatingActionButton? = null
	//private var mFilterString: String? = null
	private var editedFile: String? = null

	private lateinit var viewModel: NotesListViewModel

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

		val selectionController = SelectionController()
		mNotesAdaptor = NoteDescriptionListAdapter(this, selectionController){ addingSuppresed(it)}

		mList = findViewById(R.id.listview)!!
		mList.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
		mList.adapter = mNotesAdaptor

		if (!TextUtils.isEmpty(editedFile)) {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
				postponeEnterTransition()
			}
		}
		mNewNote = findViewById(R.id.fab)
		mNewNote!!.setOnClickListener { editNote(null, false) }

		viewModel.deleteResult.observe(this, EventObserver {deleteResult ->
			if (deleteResult == null) return@EventObserver
			if (deleteResult.throwable != null)
				Toast.makeText(applicationContext, deleteResult.throwable.localizedMessage, Toast.LENGTH_LONG).show()
			else {
				if (deleteResult.delete) {
					val message = getString(R.string.removed_notes_message, deleteResult.ids.size)
					val snackbar = Snackbar.make(findViewById(R.id.coordinatorLayout), message, Snackbar.LENGTH_LONG)
					snackbar.setAction(R.string.undo) {
						viewModel.softDelete(deleteResult.ids, false)
					}
					snackbar.show()
				}
			}
		})
		if (getPreferences(Context.MODE_PRIVATE).contains(KeyEditingId)) {
			val id = getPreferences(Context.MODE_PRIVATE).getLong(KeyEditingId, 0)
			editNote(if (id==0L) null else id, true)
		}
	}

	override fun onSaveInstanceState(outState: Bundle) {
		super.onSaveInstanceState(outState)
	}

	internal inner class SelectionController : ActionMode.Callback, ItemClickListener {
		private var mActionMode: ActionMode? = null
		private val isSelectionMode: Boolean get() = mActionMode != null
		init{
			viewModel.activeMode.observe(this@NotesListActivity, Observer<Boolean>{
				if (it == isSelectionMode) return@Observer
				if (it!!)
					beginSelectionMode()
				else
					endSelectionMode()
			})
		}

		override fun itemTapUp(holder: androidx.recyclerview.widget.RecyclerView.ViewHolder): Boolean {
			val id = holder.itemId
			if (isSelectionMode)
				toggleSelection(id)
			else
				editNote(id, false)
			return true
		}

		override fun itemLongPress(holder: androidx.recyclerview.widget.RecyclerView.ViewHolder): Boolean {
			toggleSelection(holder.itemId)
			return true
		}

		private fun toggleSelection(id:Long) {
			if (!isSelectionMode)
				beginSelectionMode()
			viewModel.selectedIds.toggle(id)
			val count = viewModel.selectedIds.count
			mActionMode!!.title = count.toString()
			if (count == 0)
				endSelectionMode()
		}

		fun endSelectionMode() {
			mActionMode?.finish()
		}

		private fun beginSelectionMode() {
			mActionMode = startSupportActionMode(this)
			viewModel.activeMode.value = true
			addingSuppresed(true)
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
			addingSuppresed(false)
		}
	}

	private fun addingSuppresed(suppress: Boolean){
		if (suppress)
			mNewNote!!.hide()
		else
			mNewNote!!.show()
	}
	private fun softDelete(ids: List<Long>){
		viewModel.softDelete(ids, true)
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
			addingSuppresed(true)
		}
		private fun endSearchMode()
		{
			mSearchView.clearFocus()
			viewModel.filterString = null
			searchMode = false
			viewModel.searchMode.value = searchMode
			addingSuppresed(false)
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
