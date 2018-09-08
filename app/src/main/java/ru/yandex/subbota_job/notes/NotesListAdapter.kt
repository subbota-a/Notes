package ru.yandex.subbota_job.notes

import android.app.Application
import android.content.Context
import android.os.AsyncTask
import android.os.Build
import android.os.Environment
import android.os.FileObserver
import android.os.Handler
import android.support.design.widget.Snackbar
import android.support.v7.widget.RecyclerView
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import java.io.File
import java.io.IOException
import java.util.Arrays
import java.util.HashSet
import kotlin.collections.ArrayList

/**
 * Created by subbota on 22.03.2016.
 */
internal class NotesListAdapter(private val mContext: Context) : RecyclerView.Adapter<NotesListAdapter.ViewHolder>() {
	private val mSelected: MutableSet<Int>
	private var mLoaderTask: AsyncTask<String, Void, ArrayList<NoteDescription>>? = null
	private var mFilterString: String? = null
	private var mFileObserver: FileObserver? = null
	private val mUpdateAsync = Handler()
	private var mUpdatePending = false

	private var mLockUpdate = 0
	private var mDataSource: ArrayList<NoteDescription>? = ArrayList()
	val selectionCount: Int
		get() = mSelected.size

	fun beginUpdate() {
		++mLockUpdate
	}

	fun endUpdate() {
		if (--mLockUpdate == 0 && mUpdatePending)
			updateAsync(mFilterString)
	}


	internal class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
		private val mItem: TextView

		init {
			mItem = itemView.findViewById<View>(android.R.id.text1) as TextView
		}

		fun bindItem(n: NoteDescription) {
			mItem.text = n.mPreviewText
			mItem.maxLines = if (n.mHasTitle) 1 else 2
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
				mItem.transitionName = n.mFileName!!.name
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

	init {
		mSelected = HashSet()
		val dir = getOrAddDirectory(mContext)
		if (dir != null) {
			mFileObserver = object : FileObserver(dir.path, FileObserver.DELETE or FileObserver.MODIFY) {
				override fun onEvent(i: Int, s: String?) {
					mUpdateAsync.post { updateAsync(mFilterString) }
				}
			}
			mFileObserver!!.startWatching()
		}
	}

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
		return ViewHolder.create(parent)
	}

	internal fun getItem(position: Int): NoteDescription {
		return mDataSource!![position]
	}

	override fun onBindViewHolder(holder: ViewHolder, position: Int) {
		val item = mDataSource!![position]
		holder.bindItem(item)
		holder.setSelected(mSelected.contains(position))
	}

	fun toggleSelection(position: Int): Boolean {
		val isSelected = mSelected.add(position)
		if (!isSelected)
			mSelected.remove(position)
		notifyItemChanged(position)
		return isSelected
	}

	fun clearAllSelection() {
		if (mSelected.size > 0) {
			mSelected.clear()
			notifyDataSetChanged()
		}
	}

	fun deleteSelectedAsync(coordinatorLayout: View) {
		val descr = ArrayList<NoteDescription>()
		for (i in mSelected.asSequence().sortedByDescending { x -> x }){
			descr.add(mDataSource!![i])
			mDataSource!!.removeAt(i)
		}
		notifyDataSetChanged()
		val message = coordinatorLayout.resources.getString(R.string.removed_notes_message, mSelected.size)
		val snackbar = Snackbar.make(coordinatorLayout, message, Snackbar.LENGTH_LONG)
		snackbar.setAction(R.string.undo) { }
		snackbar.addCallback(object : Snackbar.Callback() {
			override fun onDismissed(snackbar: Snackbar?, event: Int) {
				super.onDismissed(snackbar, event)
				if (event != Snackbar.Callback.DISMISS_EVENT_ACTION) {
					object : AsyncTask<Context, Void, Void>() {
						override fun doInBackground(vararg params: Context): Void? {
							for (nd in descr) {
								nd.mFileName!!.delete()
								SyncService.onFileChanged(params[0], nd.mFileName!!.path)
							}
							return null
						}
					}.execute(coordinatorLayout.context.applicationContext)
				} else {
					updateAsync(mFilterString)
				}
			}
		})
		mSelected.clear()
		snackbar.show()
	}

	override fun getItemCount(): Int {
		return if (mDataSource == null) 0 else mDataSource!!.size
	}

	fun updateAsync(substring: String?) {
		mFilterString = substring
		mUpdatePending = true
		if (mLockUpdate > 0)
			return
		mUpdatePending = false
		if (mLoaderTask != null) {
			mLoaderTask!!.cancel(false)
			mLoaderTask = null
		}
		mLoaderTask = object : AsyncTask<String, Void, ArrayList<NoteDescription>>() {
			override fun doInBackground(vararg params: String): ArrayList<NoteDescription>? {
				val ret = ArrayList<NoteDescription>()
				val filterString = if (TextUtils.isEmpty(params[0])) null else params[0].toLowerCase()
				val file = getOrAddDirectory(mContext) ?: return null
/*
                try {
                    Thread.sleep(3000);
                }catch(InterruptedException e){
                    return null;
                }
*/
				val files = file.listFiles()
				Arrays.sort(files) { lhs, rhs ->
					// make recent first
					val dif = rhs.lastModified() - lhs.lastModified()
					if (dif < 0) -1 else if (dif > 0) 1 else 0
				}
				for (f in files) {
					if (isCancelled)
						break
					try {
						val content = UtfFile.ReadAll(f.path)
						val populate: Boolean
						if (TextUtils.isEmpty(filterString))
							populate = true
						else {
							val lowerCase = content.toLowerCase()
							populate = lowerCase.contains(filterString!!)
						}
						if (populate) {
							val item = NoteDescription()
							val pair = UtfFile.Split(content)
							item.mHasTitle = !TextUtils.isEmpty(pair[0])
							item.mPreviewText = if (item.mHasTitle) pair[0] else pair[1]?.let { UtfFile.getLine(it) }
							item.mFileName = f
							ret.add(item)
						}
					} catch (e: IOException) {
					}

				}
				return ret
			}

			override fun onPostExecute(noteDescriptions: ArrayList<NoteDescription>) {
				mergeTo(noteDescriptions)
			}
		}.execute(substring)
	}

	private fun mergeTo(newDataSource: ArrayList<NoteDescription>) {
		mDataSource = newDataSource
		notifyDataSetChanged()
	}

	companion object {

		fun getOrAddDirectory(context: Context): File? {
			val dir = File(context.applicationContext.filesDir, NotesListActivity.NotesDirectory)
			dir.mkdirs()
			return if (dir.isDirectory) dir else null
		}
	}
}
