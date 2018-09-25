package ru.yandex.subbota_job.notes.viewController

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders.of
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.support.v4.view.MenuItemCompat
import android.support.v7.app.ActionBar
import android.support.v7.app.AppCompatActivity
import android.support.v7.view.SupportMenuInflater
import android.support.v7.view.menu.ActionMenuItemView
import android.support.v7.view.menu.MenuBuilder
import android.support.v7.widget.ActionMenuView
import android.support.v7.widget.ShareActionProvider
import android.support.v7.widget.Toolbar
import android.text.*
import android.text.style.*
import android.util.Log
import android.util.StateSet
import android.util.TypedValue
import android.view.*
import android.widget.EditText
import android.widget.TextView
import com.google.android.gms.tasks.CancellationToken
import com.google.android.gms.tasks.CancellationTokenSource
import ru.yandex.subbota_job.notes.Markup
import ru.yandex.subbota_job.notes.R
import ru.yandex.subbota_job.notes.dataModel.NoteSnapshot
import ru.yandex.subbota_job.notes.viewModel.EditingViewModel
import java.lang.ref.WeakReference
import java.util.*

class NoteContentActivity : AppCompatActivity() {
	companion object {
		internal val keyPath = NoteContentActivity::class.java.getName() + "path"
		internal val keyContent = NoteContentActivity::class.java.getName() + "content"
		internal val keyChanged = NoteContentActivity::class.java.getName() + "changed"
		internal val keyScale = "scale"
		val IdKey: String = NoteContentActivity::class.java.getName() + "IdKey"
		val ContinueKey: String = NoteContentActivity::class.java.getName() + "ContinueKey"
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
	private val spannable : SpannableStringBuilder get()= mEdit.text as SpannableStringBuilder
	private lateinit var mFormatCharToolbar: ActionMenuView

	private class PendingSaveSnapshot(val activity: WeakReference<NoteContentActivity>, val cancelToken:CancellationToken):Runnable{
		override fun run() {
			if (cancelToken.isCancellationRequested)
				return;
			activity.get()?.saveSnapshot()
		}
		companion object {
			private var delayedSnapshot : CancellationTokenSource? = null
			private val handler = Handler()

			fun cancel(){
				delayedSnapshot?.cancel()
				delayedSnapshot = null
			}
			fun postDelayed(activity:NoteContentActivity)
			{
				cancel()
				delayedSnapshot = CancellationTokenSource()
				handler.postDelayed(PendingSaveSnapshot(WeakReference(activity), delayedSnapshot!!.token), 1000)
			}
		}
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_note_content)
		val toolbar = findViewById<Toolbar>(R.id.toolbar)
		setSupportActionBar(toolbar)
		mActionBar = supportActionBar!!
		mActionBar.setDisplayHomeAsUpEnabled(true)
		mActionBar.setDisplayShowTitleEnabled(false)
		mNoteTitle = findViewById(R.id.title_edit) 
		mNoteTitle.nextFocusDownId = R.id.editor

		toolbar.setNavigationOnClickListener { saveAndExit() }
		mFormatCharToolbar = findViewById(R.id.char_style_toolbar)

		menuInflater.inflate(R.menu.menu_formatting, mFormatCharToolbar.menu)
		makePressableItem(mFormatCharToolbar.menu)
		mFormatCharToolbar.setOnMenuItemClickListener { format(it.itemId) }
		// test
		mFormatCharToolbar.menu.findItem(R.id.bold_format).setChecked(true)
		//mFormatCharToolbar.menu.findItem(R.id.bold_format).icon.state = IntArray(1){android.R.attr.state_pressed}

		initEdit()

		noteId = intent?.getLongExtra(IdKey, -1)
		if (noteId != null && noteId!! <= 0)
			noteId = null
		val goOn = intent?.getBooleanExtra(ContinueKey, false) ?: false

		viewModel = of(this).get(EditingViewModel::class.java)
		viewModel.snapshot.observe(this, Observer {
			putContent(it!!)
		})
		if (savedInstanceState == null) {// begin editing
			if (!goOn)
				viewModel.clearHistory()
			viewModel.loadDraft(noteId)
		}
	}

	private fun makePressableItem(menu: Menu) {
		val background = resources.getDrawable(R.drawable.active_format_background, theme)
		val active_state = IntArray(1){android.R.attr.state_checked}
		for(i in 0 until menu.size()) {
			var src = menu.getItem(i)!!.icon
			val active = LayerDrawable(arrayOf(background, src))
			val state = StateListDrawable()
			state.addState(active_state, active)
			state.addState(StateSet.WILD_CARD, src)
			menu.getItem(i)!!.icon = state
		}
	}


	fun userChanged() {
		needsToSnapshot = true
		lastModified = Date().time
		PendingSaveSnapshot.postDelayed(this)
	}

	private fun putContent(content: NoteSnapshot) {
		viewModel.curSeqId = content.seqId
		mEdit.setText(Markup.fromString(content.body), TextView.BufferType.EDITABLE)
		mEdit.setSelection(kotlin.math.min(spannable.length, content.selectionPos))
		mEdit.scrollY = content.scrollPos
		mNoteTitle.setText(content.title)
		needsToSnapshot = false
		lastModified = content.modified
		PendingSaveSnapshot.cancel()
		updateUndoRedo()
	}

	override fun onResume() {
		super.onResume()
		if (noteId == null)	// force show keyboard only for new note
			window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
	}

	override fun onDestroy() {
		super.onDestroy()
		PendingSaveSnapshot.cancel()
	}

	override fun onSaveInstanceState(outState: Bundle) {
		super.onSaveInstanceState(outState)
		getPreferences(Context.MODE_PRIVATE).edit().putFloat(keyScale, mScale).apply()
		PendingSaveSnapshot.cancel()
		if (needsToSnapshot)
			saveSnapshot()
	}

	private fun makeSnapshot() : NoteSnapshot{
		mEdit.clearComposingText()
		val body = Markup.toString(spannable)
		return NoteSnapshot(title = mNoteTitle.text.toString(), body = body, selectionPos = mEdit.selectionEnd, scrollPos = mEdit.scrollY, modified = this.lastModified)
	}

	private fun saveSnapshot() {
		if (!needsToSnapshot)
			return
		val snapshot = makeSnapshot()
		viewModel.addSnapshot(snapshot)
		needsToSnapshot = false
		updateUndoRedo()
	}

	private fun updateUndoRedo() {
		invalidateOptionsMenu()
	}

	override fun onBackPressed() {
		saveAndExit()
	}

	private fun saveAndExit() {
		val snapshot = makeSnapshot()
		if (snapshot.title.isBlank())
			snapshot.title = spannable.lineSequence().firstOrNull() ?: ""
		viewModel.saveNote(snapshot, noteId )
		supportFinishAfterTransition()
	}

	private fun removeCharacterStyleFromRange(ss:Int, se:Int, spans: Iterable<CharacterStyle>)
	{
		for(s in spans){
			val st = spannable.getSpanStart(s)
			val en = spannable.getSpanEnd(s)
			spannable.removeSpan(s)
			if (st<ss)
				spannable.setSpan(CharacterStyle.wrap(s), st, ss, Spanned.SPAN_EXCLUSIVE_INCLUSIVE)
			if (se<en)
				spannable.setSpan(CharacterStyle.wrap(s), se, en, Spanned.SPAN_EXCLUSIVE_INCLUSIVE)
		}
	}
	private fun toggleStyle(ss:Int, se:Int, spans: Iterable<CharacterStyle>, style : Lazy<CharacterStyle>){
		if (!spans.any())
			spannable.setSpan(style.value, ss, se, Spanned.SPAN_EXCLUSIVE_INCLUSIVE)
		else
			removeCharacterStyleFromRange(ss,se,spans)
		userChanged()
	}
	private fun replaceStyle(ss:Int, se:Int, spans: Iterable<CharacterStyle>, style : CharacterStyle?)
	{
		removeCharacterStyleFromRange(ss,se,spans)
		if (style!=null)
			spannable.setSpan(style, ss, se, Spanned.SPAN_EXCLUSIVE_INCLUSIVE)
		userChanged()
	}
	private fun format(menuItemId: Int): Boolean {
		val ss = mEdit.selectionStart
		val se = mEdit.selectionEnd
		mEdit.clearComposingText()
		when(menuItemId){
			R.id.bold_format, R.id.italic_format ->{
				val typeface = when (menuItemId) { R.id.bold_format -> Typeface.BOLD else -> Typeface.ITALIC}
				val spans = spannable.getSpans(ss, se, StyleSpan::class.java).filter{s -> s.style == typeface}
				toggleStyle(ss, se, spans, lazy{StyleSpan(typeface)})
			}
			R.id.strikethrough_format ->{
				val spans = spannable.getSpans(ss, se, StrikethroughSpan::class.java).asIterable()
				toggleStyle(ss, se, spans, lazy{StrikethroughSpan()})
			}
			R.id.underline_format->{
				val spans = spannable.getSpans(ss, se, UnderlineSpan::class.java).asIterable()
				toggleStyle(ss, se, spans, lazy{UnderlineSpan()})
			}
			R.id.color_fill_format->{
				val spans = spannable.getSpans(ss, se, BackgroundColorSpan::class.java).asIterable()
				toggleStyle(ss, se, spans, lazy{BackgroundColorSpan(Color.rgb(0xFF,0xF1, 0x76))})
			}
			R.id.color_text_format->{
				val spans = spannable.getSpans(ss, se, ForegroundColorSpan::class.java).asIterable()
				toggleStyle(ss, se, spans, lazy{ForegroundColorSpan(Color.rgb(0xD5,0x00,0x00))})
			}
			R.id.increase_font_size, R.id.decrease_font_size ->{
				val spans = spannable.getSpans(ss, se, RelativeSizeSpan::class.java)
				var newSpan : RelativeSizeSpan? = null
				var perc = spans.firstOrNull()?.sizeChange ?: 1f
				if (menuItemId == R.id.increase_font_size)
					perc += 0.1f
				else
					perc -= 0.1f
				if (perc != 0f)
					newSpan = RelativeSizeSpan(perc)
				replaceStyle(ss, se, spans.asIterable(), newSpan)
			}
			else -> return false
		}
		return true
	}

	private fun initEdit() {
		Log.d(toString(), "initEdit")
		mEdit = findViewById(R.id.editor)
		mEdit.customSelectionActionModeCallback = object : ActionMode.Callback {
			var count = 0
			override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
				Log.d("ActionMode", "onCreateActionMode")
				return true
			}

			override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
				/*
				for (i in 0 until menu.size()) {
					val item = menu.getItem(i)
					Log.d("onPrepareActionMode", String.format("%s: %d", item.title.toString(), item.itemId))
				}
				Log.d("onPrepareActionMode", menu.javaClass.toString())
				*/
				//mode.menuInflater.inflate(R.menu.menu_formatting, menu)

				/*if (menu.findItem(android.R.id.shareText) == null) {
					val item = menu.add(0, android.R.id.shareText, 100, R.string.share)
					item.setIcon(R.drawable.ic_share_24dp)
					item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
				}*/
				return true
			}
			override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
				Log.d("onActionItemClicked", item.title.toString())
				when (item.itemId){
					android.R.id.shareText -> {
						val intent = Intent(Intent.ACTION_SEND)
						intent.type = resources.getString(R.string.noteMimeType)
						val s = mEdit.text.subSequence(mEdit.selectionStart, mEdit.selectionEnd)
						Log.d("Share", s.toString())
						intent.putExtra(Intent.EXTRA_TEXT, s.toString())
						startActivity(intent)
						return true
					}
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
				userChanged()
			}
		})
		mEdit.addTextChangedListener(object : TextWatcher {
			override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
			override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
			override fun afterTextChanged(s: Editable) {
				updateShareProvider()
				userChanged()
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

	override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
		menu?.findItem(R.id.undo_action)?.icon?.alpha = if (viewModel.curSeqId>0) 255 else 125
		menu?.findItem(R.id.redo_action)?.icon?.alpha = if (viewModel.curSeqId<viewModel.maxSeqId) 255 else 125
		return super.onPrepareOptionsMenu(menu)
	}
	private fun splitCharacterStyles(ss:Int, se:Int)
	{
		for(s in spannable.getSpans(ss,se,CharacterStyle::class.java)){
			val st = spannable.getSpanStart(s)
			val en = spannable.getSpanEnd(s)
			if (st<ss)
				spannable.setSpan(CharacterStyle.wrap(s), st, ss, Spanned.SPAN_EXCLUSIVE_INCLUSIVE)
			if (se<en)
				spannable.setSpan(CharacterStyle.wrap(s), se, en, Spanned.SPAN_EXCLUSIVE_INCLUSIVE)
			if (st<ss || se<en){
				spannable.removeSpan(s)
				spannable.setSpan(CharacterStyle.wrap(s), ss, se, Spanned.SPAN_EXCLUSIVE_INCLUSIVE)
			}
		}

	}
	private fun<T> replaceParagraphStyle(ss:Int, se:Int, style : ()->T, type: Class<T>){
		var st = ss
		while(st > 0 && spannable[st-1]!='\n')
			--st
		var en = st
		do {
			while (en < spannable.length && spannable[en] != '\n')
				++en
			val spans = spannable.getSpans(st, en, type)
			for (s in spans)
				spannable.removeSpan(s)
			splitCharacterStyles(st,en)
			spannable.setSpan(style(), st, en, Spanned.SPAN_PARAGRAPH)
			++en
			st = en
		}while (st < se)
		userChanged()
	}

	override fun onOptionsItemSelected(item: MenuItem?): Boolean {
		val ss = mEdit.selectionStart
		val se = mEdit.selectionEnd
		when(item!!.itemId){
			R.id.undo_action -> {
				if (viewModel.curSeqId>0) {
					saveSnapshot()
					viewModel.loadEditing(viewModel.curSeqId - 1)
				}
				return true
			}

			R.id.redo_action -> {
				if (viewModel.curSeqId < viewModel.maxSeqId)
					viewModel.loadEditing(viewModel.curSeqId + 1)
				return true
			}
			R.id.format_align_left -> replaceParagraphStyle(ss, se, {AlignmentSpan.Standard(Layout.Alignment.ALIGN_NORMAL)}, AlignmentSpan::class.java)
			R.id.format_align_center -> replaceParagraphStyle(ss, se, {AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER)}, AlignmentSpan::class.java)
			R.id.format_align_right -> replaceParagraphStyle(ss, se, {AlignmentSpan.Standard(Layout.Alignment.ALIGN_OPPOSITE)}, AlignmentSpan::class.java)
		}
		return super.onOptionsItemSelected(item)
	}
}
