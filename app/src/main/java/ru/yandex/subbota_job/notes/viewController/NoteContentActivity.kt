package ru.yandex.subbota_job.notes.viewController

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders.of
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.v4.view.MenuItemCompat
import android.support.v7.app.ActionBar
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.ShareActionProvider
import android.support.v7.widget.Toolbar
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.util.TypedValue
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import ru.yandex.subbota_job.notes.R
import ru.yandex.subbota_job.notes.dataModel.Note
import ru.yandex.subbota_job.notes.dataModel.NoteSnapshot
import ru.yandex.subbota_job.notes.viewModel.EditingViewModel
import java.util.*

class NoteContentActivity : AppCompatActivity() {
	companion object {
		internal val keyPath = NoteContentActivity::class.java!!.getName() + "path"
		internal val keyContent = NoteContentActivity::class.java!!.getName() + "content"
		internal val keyChanged = NoteContentActivity::class.java!!.getName() + "changed"
		internal val keyScale = "scale"
		val IdKey: String = NoteContentActivity::class.java!!.getName() + "IdKey"
		val ContinueKey: String = NoteContentActivity::class.java!!.getName() + "ContinueKey"
	}
	// views
	private lateinit var mEdit: FlingableEditText
	private lateinit var mActionBar: ActionBar
	private lateinit var mNoteTitle: EditText
	// model
	private var noteId: Long? = null
	private lateinit var viewModel: EditingViewModel

	private var mScale = 1f
	private var mDefaultTextSize: Float = 0.toFloat()
	private var mGesture: ScaleGestureDetector? = null
	private var mShareProvider: ShareActionProvider? = null
	private var needsToSnapshot: Boolean = false
	private var lastModified: Long = 0

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_note_content)
		val toolbar = findViewById<View>(R.id.toolbar) as Toolbar
		setSupportActionBar(toolbar)
		mActionBar = supportActionBar!!
		mActionBar.setDisplayHomeAsUpEnabled(true)
		mActionBar.setDisplayShowTitleEnabled(false)
		mNoteTitle = findViewById(R.id.title_edit) 
		mNoteTitle.nextFocusDownId = R.id.editor
		
		toolbar.setNavigationOnClickListener { saveAndExit() }
		initEdit()

		noteId = intent?.getLongExtra(IdKey, -1)
		if (noteId != null && noteId!! <= 0)
			noteId = null
		val goOn = intent?.getBooleanExtra(ContinueKey, false) ?: false

		viewModel = of(this).get(EditingViewModel::class.java)
		viewModel.snapshot.observe(this, Observer {
			putContent(it)
		})
		if (savedInstanceState == null) {// begin editing
			if (goOn)
				viewModel.loadDraft()
			else {
				viewModel.clearHistory()
				if (noteId != null) {// existing note
					viewModel.loadNote(noteId!!)
				}
			}
		}
	}

	private fun putContent(content: NoteSnapshot?) {
		viewModel.curSeqId = content?.seqId ?: 0
		mEdit.setText(content?.body)
		mEdit.setSelection(content?.selectionPos ?: 0)
		mEdit.scrollY = content?.scrollPos ?: 0
		mNoteTitle.setText(content?.title)
		needsToSnapshot = false
		lastModified = content?.modified ?: 0
	}


	override fun onPause() {
		super.onPause()
	}

	override fun onResume() {
		super.onResume()
		if (noteId == null)	// force show keyboard only for new note
			window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
	}

	override fun onSaveInstanceState(outState: Bundle) {
		super.onSaveInstanceState(outState)
		getPreferences(Context.MODE_PRIVATE).edit().putFloat(keyScale, mScale).apply()
		if (needsToSnapshot)
			saveSnapshot()
	}

	private fun makeSnapshot() = NoteSnapshot(title = mNoteTitle.text.toString(), body = mEdit.text.toString(), selectionPos = mEdit.selectionEnd, scrollPos = mEdit.scrollY, modified = this.lastModified)

	private fun saveSnapshot() {
		val snapshot = makeSnapshot()
		viewModel.addSnapshot(snapshot)
		needsToSnapshot = false
	}

	override fun onBackPressed() {
		saveAndExit()
	}

	private fun saveAndExit() {
		viewModel.saveNote(makeSnapshot(), noteId )
		supportFinishAfterTransition()
	}

	private fun initEdit() {
		Log.d(toString(), "initEdit")
		mEdit = findViewById(R.id.editor)
		mEdit.customSelectionActionModeCallback = object : ActionMode.Callback {
			override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
				mActionBar.show()
				return true
			}

			override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
				for (i in 0 until menu.size()) {
					val item = menu.getItem(i)
					Log.d("onPrepareActionMode", String.format("%s: %d", item.title.toString(), item.itemId))
				}
				Log.d("onPrepareActionMode", menu.javaClass.toString())
				if (menu.findItem(android.R.id.shareText) == null) {
					val item = menu.add(0, android.R.id.shareText, 100, R.string.share)
					item.setIcon(R.drawable.ic_share_24dp)
					item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
				}
				return true
			}

			override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
				Log.d("onActionItemClicked", item.title.toString())
				if (item.itemId == android.R.id.shareText) {
					val intent = Intent(Intent.ACTION_SEND)
					intent.type = resources.getString(R.string.noteMimeType)
					val s = mEdit.text.subSequence(mEdit.selectionStart, mEdit.selectionEnd)
					Log.d("Share", s.toString())
					intent.putExtra(Intent.EXTRA_TEXT, s.toString())
					startActivity(intent)
					return true
				}
				return false
			}

			override fun onDestroyActionMode(mode: ActionMode) {
				Log.d("ActionMode.Callback", "onDestroyActionMode")
			}
		}
		mEdit.onSelectionChanged = { _, _ -> needsToSnapshot = true}
		mEdit.onScrollChanged = {needsToSnapshot = true}
		mScale = getPreferences(Context.MODE_PRIVATE).getFloat(keyScale, 1f)
		mDefaultTextSize = mEdit.textSize
		zoomText(mScale)
		mGesture = ScaleGestureDetector(this, object : ScaleGestureDetector.OnScaleGestureListener {
			override fun onScale(detector: ScaleGestureDetector): Boolean {
				val nextScale = detector.scaleFactor
				zoomText(mScale * nextScale)
				return true
			}

			override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
				return true
			}

			override fun onScaleEnd(detector: ScaleGestureDetector) {}
		})
		mNoteTitle.addTextChangedListener(object : TextWatcher {
			override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
			override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
			override fun afterTextChanged(s: Editable) {
				needsToSnapshot = true
				lastModified = Date().time
			}
		})
		mEdit.addTextChangedListener(object : TextWatcher {
			override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
			override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
			override fun afterTextChanged(s: Editable) {
				updateShareProvider()
				needsToSnapshot = true
				lastModified = Date().time
			}
		})
	}

	override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
		mGesture!!.onTouchEvent(ev)
		if (mGesture!!.isInProgress)
			Log.d("NoteContextActivity", "mGesture.isInProgress()")
		return mGesture!!.isInProgress || super.dispatchTouchEvent(ev)
	}

	private fun zoomText(nextScale: Float) {
		@Suppress("NAME_SHADOWING")
		var nextScale = nextScale
		nextScale = Math.min(nextScale.toDouble(), 3.0).toFloat()
		nextScale = Math.max(nextScale.toDouble(), 0.5).toFloat()
		mScale = nextScale
		mEdit.setTextSize(TypedValue.COMPLEX_UNIT_PX, mDefaultTextSize * mScale)
	}

	private fun updateShareProvider() {
		if (mShareProvider != null) {
			val intent = Intent(Intent.ACTION_SEND)
			intent.type = resources.getString(R.string.noteMimeType)
			if (mEdit.selectionStart != mEdit.selectionEnd)
				intent.putExtra(Intent.EXTRA_TEXT, mEdit.text.subSequence(mEdit.selectionStart, mEdit.selectionEnd).toString())
			else
				intent.putExtra(Intent.EXTRA_TEXT, mEdit.text.toString())
			mShareProvider!!.setShareIntent(intent)
		}
	}

	override fun onCreateOptionsMenu(menu: Menu): Boolean {
		menuInflater.inflate(R.menu.menu_note_content, menu)
		mShareProvider = MenuItemCompat.getActionProvider(menu.findItem(R.id.share_action)) as ShareActionProvider
		updateShareProvider()
		return true
	}

}
