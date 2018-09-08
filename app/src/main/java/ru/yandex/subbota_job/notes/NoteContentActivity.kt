package ru.yandex.subbota_job.notes

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.support.v4.view.MenuItemCompat
import android.support.v4.view.ViewCompat
import android.support.v7.app.ActionBar
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.ShareActionProvider
import android.support.v7.widget.Toolbar
import android.text.Editable
import android.text.TextUtils
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
import android.widget.Toast

import java.io.File
import java.io.IOException
import java.util.Objects

class NoteContentActivity : AppCompatActivity() {
	private lateinit var mPath: String
	private var mEdit: EditText? = null
	private var mActionBar: ActionBar? = null
	private var mChanged = false
	private var mScale = 1f
	private var mDefaultTextSize: Float = 0.toFloat()
	private var mGesture: ScaleGestureDetector? = null
	private var mShareProvider: ShareActionProvider? = null
	private var mNoteTitle: EditText? = null
	private var mLoading = false
	private var mRestarted = false

	private val content: String
		get() = UtfFile.Join(customTitle, mEdit!!.text.toString())
	private val isNeedSave: Boolean
		get() = mChanged && !(mEdit!!.text.length == 0 && mNoteTitle!!.text.length == 0 && TextUtils.isEmpty(mPath))

	private val customTitle: String
		get() = mNoteTitle!!.text.toString()

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_note_content)
		val toolbar = findViewById<View>(R.id.toolbar) as Toolbar
		assert(toolbar != null)
		setSupportActionBar(toolbar)
		mActionBar = supportActionBar
		assert(mActionBar != null)
		mActionBar!!.setDisplayHomeAsUpEnabled(true)
		mActionBar!!.setDisplayShowTitleEnabled(false)
		mNoteTitle = findViewById<View>(R.id.title_edit) as EditText
		assert(mNoteTitle != null)
		mNoteTitle!!.nextFocusDownId = R.id.editor
		toolbar.setNavigationOnClickListener { saveAndExit() }
		initEdit()

		if (savedInstanceState != null) {
			mPath = savedInstanceState.getString(keyPath)
			mChanged = savedInstanceState.getBoolean(keyChanged)
			mRestarted = true
		} else {
			val intent = intent
			assert(intent != null)
			val uri = intent!!.data
			if (uri != null)
				mPath = uri.path
			if (TextUtils.isEmpty(mPath))
				mPath = ""
			try {
				val draftStorage = DraftStorage(this)
				if (TextUtils.equals(mPath, draftStorage.draftPath)) {
					putContent(draftStorage.draftContent)
					mChanged = true
				} else if (!TextUtils.isEmpty(mPath))
					putContent(loadContent())
			} catch (e: Exception) {
				Toast.makeText(applicationContext, e.message, Toast.LENGTH_LONG).show()
				finish()
			}

		}
		if (!TextUtils.isEmpty(mPath)) {
			val f = File(mPath)
			val transitionName = f.name
			if (TextUtils.isEmpty(mNoteTitle!!.text))
				ViewCompat.setTransitionName(mEdit, transitionName)
			else
				ViewCompat.setTransitionName(mNoteTitle, transitionName)
		}
	}

	override fun onPause() {
		super.onPause()
		getPreferences(Context.MODE_PRIVATE).edit().putFloat(keyScale, mScale).commit()
		if (isNeedSave) {
			DraftStorage(this).saveDraft(mPath, content)
			Toast.makeText(applicationContext, "Черновик сохранён", Toast.LENGTH_SHORT).show()
		} else
			DraftStorage(this).clearDraft()
	}

	override fun onResume() {
		super.onResume()
		if (TextUtils.isEmpty(mPath))
		// force show keyboard only for new note
			window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
	}

	override fun onSaveInstanceState(outState: Bundle) {
		super.onSaveInstanceState(outState)
		outState.putString(keyPath, mPath)
		outState.putBoolean(keyChanged, mChanged)
	}

	override fun onBackPressed() {
		saveAndExit()
	}

	private fun saveAndExit() {
		if (isNeedSave) {
			saveContent()
			setResult(AppCompatActivity.RESULT_OK)
		} else {
			setResult(AppCompatActivity.RESULT_CANCELED)
		}
		DraftStorage(this).clearDraft()
		supportFinishAfterTransition()
		/*
        if (mRestarted){
            finish();
            overridePendingTransition(R.anim.go_into_from_left, R.anim.go_away_to_right);
        }else
            supportFinishAfterTransition();
*/
	}

	private fun initEdit() {
		Log.d(toString(), "initEdit")
		mEdit = findViewById<View>(R.id.editor) as EditText
		assert(mEdit != null)
		mEdit!!.customSelectionActionModeCallback = object : ActionMode.Callback {
			override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
				mActionBar!!.show()
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
					val s = mEdit!!.text.subSequence(mEdit!!.selectionStart, mEdit!!.selectionEnd)
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
		mScale = getPreferences(Context.MODE_PRIVATE).getFloat(keyScale, 1f)
		mDefaultTextSize = mEdit!!.textSize
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
	}

	override fun onOptionsItemSelected(item: MenuItem): Boolean {
		when (item.itemId) {
			R.id.clear_action -> {
				mEdit!!.setText(null)
				mChanged = false
				return true
			}
			R.id.undo_action -> {
				DraftStorage(this).clearDraft()
				if (TextUtils.isEmpty(mPath))
					mEdit!!.setText(null)
				else {
					try {
						putContent(loadContent())
					} catch (e: Exception) {
						Toast.makeText(applicationContext, e.message, Toast.LENGTH_LONG).show()
					}

				}
				mChanged = false
				return true
			}
		}
		return super.onOptionsItemSelected(item)
	}

	override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
		mGesture!!.onTouchEvent(ev)
		if (mGesture!!.isInProgress)
			Log.d("NoteContextActivity", "mGesture.isInProgress()")
		return mGesture!!.isInProgress || super.dispatchTouchEvent(ev)
	}

	private fun zoomText(nextScale: Float) {
		var nextScale = nextScale
		nextScale = Math.min(nextScale.toDouble(), 3.0).toFloat()
		nextScale = Math.max(nextScale.toDouble(), 0.5).toFloat()
		mScale = nextScale
		mEdit!!.setTextSize(TypedValue.COMPLEX_UNIT_PX, mDefaultTextSize * mScale)
	}

	@Throws(RuntimeException::class)
	private fun NewPath(): String {
		val dir = NotesListAdapter.getOrAddDirectory(this)
				?: throw RuntimeException(getString(R.string.no_dest_dir))
		val file = File(dir, String.format("%d.txt", System.currentTimeMillis()))
		return file.path
	}

	override fun onPostCreate(savedInstanceState: Bundle?) {
		super.onPostCreate(savedInstanceState)
		mNoteTitle!!.addTextChangedListener(object : TextWatcher {
			override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

			override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}

			override fun afterTextChanged(s: Editable) {
				if (mLoading)
					return
				mChanged = true
			}
		})
		mEdit!!.addTextChangedListener(object : TextWatcher {
			override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

			override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}

			override fun afterTextChanged(s: Editable) {
				if (mLoading)
					return
				updateShareProvider()
				mChanged = true
			}
		})
	}


	private fun updateShareProvider() {
		if (mShareProvider != null) {
			val intent = Intent(Intent.ACTION_SEND)
			intent.type = resources.getString(R.string.noteMimeType)
			if (mEdit!!.selectionStart != mEdit!!.selectionEnd)
				intent.putExtra(Intent.EXTRA_TEXT, mEdit!!.text.subSequence(mEdit!!.selectionStart, mEdit!!.selectionEnd).toString())
			else
				intent.putExtra(Intent.EXTRA_TEXT, mEdit!!.text.toString())
			mShareProvider!!.setShareIntent(intent)
		}
	}

	override fun onCreateOptionsMenu(menu: Menu): Boolean {
		menuInflater.inflate(R.menu.menu_note_content, menu)
		mShareProvider = MenuItemCompat.getActionProvider(menu.findItem(R.id.share_action)) as ShareActionProvider
		updateShareProvider()
		return true
	}

	private fun saveContent() {
		try {
			val path = if (TextUtils.isEmpty(mPath)) NewPath() else mPath
			UtfFile.Write(path, content)
			mPath = path
			mChanged = false
			SyncService.onFileChanged(applicationContext, mPath)
			Toast.makeText(applicationContext, R.string.noteSaved, Toast.LENGTH_SHORT).show()
		} catch (e: Exception) {
			Toast.makeText(applicationContext, e.message, Toast.LENGTH_LONG).show()
		}

	}

	@Throws(IOException::class)
	private fun loadContent(): String {
		return UtfFile.ReadAll(mPath)
	}

	private fun putContent(content: String) {
		mLoading = true
		try {
			val s = UtfFile.Split(content)
			mEdit!!.setText(s[1])
			mNoteTitle!!.setText(s[0])
		} catch (e: Exception) {
			Toast.makeText(applicationContext, e.message, Toast.LENGTH_LONG).show()
		}

		mChanged = false
		mLoading = false
	}

	companion object {
		internal val keyPath = NoteContentActivity::class.java!!.getName() + "path"
		internal val keyContent = NoteContentActivity::class.java!!.getName() + "content"
		internal val keyChanged = NoteContentActivity::class.java!!.getName() + "changed"
		internal val keyScale = "scale"
	}
}
