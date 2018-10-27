package ru.yandex.subbota_job.notes.executor

import androidx.lifecycle.*
import android.os.Looper
import android.util.Log
import com.google.android.gms.tasks.Continuation
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import ru.yandex.subbota_job.notes.dataModel.*
import java.util.concurrent.Callable

class ReliableObserver<T>(val data:LiveData<T>, val f: (T?)->Unit){
	private val observer = Observer<T>{
		if (!stopped)
			f(it)
	}
	init{
		data.observeForever(observer)
	}
	private var stopped = false
	public fun stop(){
		stopped = true
		data.removeObserver(observer)
	}
}
class SyncFirebase(val localStorage: LocalDatabase, private val remoteStorage: FirebaseStorage) {
	private val localDao = localStorage.noteEdition()
	private val localDao2 = localStorage.noteDescription()
	private var localSyncData : List<LocalSyncInfo>? = null
	private var remoteSyncData : List<RemoteSyncInfo>? = null
	private var stop: Boolean = false
	private var subscribed = false
	private var remoteObserver: ReliableObserver<List<RemoteSyncInfo>>? = null
	private var localObserver: ReliableObserver<List<LocalSyncInfo>>? = null

	init{
		subscribe()
	}
	private fun onLocalChanged(it : List<LocalSyncInfo>?)
	{
		Log.d(logTag, "Local storage changed (${it?.size})")
		myassert(subscribed)
		localSyncData = it
		sync()
	}
	private fun onRemoteChanged(it : List<RemoteSyncInfo>?)
	{
		Log.d(logTag, "Remote storage changed (${it?.size})")
		myassert(subscribed)
		remoteSyncData = it
		sync()
	}
	private fun subscribe()
	{
		if (stop)
			return
		Log.d(logTag, "subscribe")
		subscribed = true
		// callback called synchronously!
		remoteObserver = ReliableObserver(remoteStorage.syncData()){ onRemoteChanged(it)}
		localObserver = ReliableObserver(localDao.getSyncInfo()){ onLocalChanged(it)}
	}
	private fun unsubscribe()
	{
		Log.d(logTag, "unsubscribe")
		subscribed = false
		remoteObserver?.stop()
		remoteObserver = null
		remoteSyncData = null

		localObserver?.stop()
		localObserver = null
		localSyncData =  null
	}
	fun release()
	{
		stop = true
		unsubscribe()
	}
	private val pendingTasks = ArrayList<Task<Unit>>()
	private fun myassert(b:Boolean){
		if (!b)
			throw AssertionError()
	}
	private fun myassert(b:Boolean, f:()->String){
		if (!b)
			throw AssertionError(f())
	}
	private fun sync()
	{
		myassert(pendingTasks.isEmpty()){"sync on pending tasks not empty"}

		if (localSyncData==null || remoteSyncData==null)
			return // Is not ready yet

		Log.d(logTag, "local size = ${localSyncData!!.size}, remote size = ${remoteSyncData!!.size}")
		val localIter = localSyncData!!.sortedBy {it.remoteId}.iterator()
		val remoteIter = remoteSyncData!!.sortedBy { it.remoteId }.iterator()
		var local = if (localIter.hasNext()) localIter.next() else null
		var remote  = if (remoteIter.hasNext()) remoteIter.next() else null
		while(local != null || remote != null){
			if (local == null)
				remote = addToLocal(remote!!, remoteIter)
			else if (remote == null)
				local = addToRemote(local, localIter)
			else if (local.remoteId == null || local.remoteId!! < remote.remoteId)
				local = addToRemote(local, localIter)
			else if (local.remoteId!! > remote.remoteId)
				remote = addToLocal(remote, remoteIter)
			else {
				if (local.modified < remote.modified)
					updateLocal(local, remote)
				else if (local.modified > remote.modified)
					updateRemote(remote, local)
				else
					Log.d(logTag, "items are equal ${remote.remoteId}")
				local = if (localIter.hasNext()) localIter.next() else null
				remote = if (remoteIter.hasNext()) remoteIter.next() else null
			}
		}
		if (pendingTasks.isNotEmpty()){
			Log.d(logTag, "Wait for pending tasks(${pendingTasks.size})")
			unsubscribe()
			Tasks.whenAll(pendingTasks).continueWith(Continuation<Void, Unit> {
				myassert(Looper.myLooper() == Looper.getMainLooper())
				this.pendingTasks.clear()
				Log.d(logTag, "Pending tasks completed")
				subscribe() // the last sentence
			})
		}
	}

	private val logTag = "SyncFirebase"
	private fun addToRemote(local: LocalSyncInfo, iter: Iterator<LocalSyncInfo>) : LocalSyncInfo? {
		if (!local.deleted) {
			Log.d(logTag, "addToRemote id=${local.id}")
			val t = Tasks.call(Executors.sequence, Callable {
				val note = localDao.getNote(local.id)
				note.remoteId = remoteStorage.getId()
				localDao.updateNote(note)
				note
			}).continueWithTask(Executors.sequence, Continuation<Note, Task<Unit>>{
				remoteStorage.update(it.result!!)
			})
			pendingTasks.add(t)
		}
		return if (iter.hasNext()) iter.next() else null
	}
	private fun updateRemote(remote: RemoteSyncInfo, local: LocalSyncInfo) {
		Log.d(logTag, "updateRemote ${local.id} -> ${remote.remoteId}")
		val t = Tasks.call(Executors.sequence, Callable {
			localDao.getNote(local.id)
		}).continueWithTask(Executors.sequence, Continuation<Note, Task<Unit>> {
			remoteStorage.update(it.result!!)
		})
		pendingTasks.add(t)
		//Log.d(logTag, "updateRemote completed id=${note.id}, remoteId=${note.remoteId}")
	}

	private fun addToLocal(remote: RemoteSyncInfo, iter: Iterator<RemoteSyncInfo>): RemoteSyncInfo? {
		if (!remote.deleted) {
			Log.d(logTag, "addToLocal ${remote.remoteId}")
			val t = remoteStorage.getNote(remote.remoteId)
				.continueWith(Executors.sequence, Continuation<Note, Unit>{
					val note = it.result!!
					note.deleted = remote.deleted
					note.modified = remote.modified
					localDao.insertNote(note)
					Unit
				})
			pendingTasks.add(t)
		}
		return if (iter.hasNext()) iter.next() else null
	}

	private fun updateLocal(local: LocalSyncInfo, remote: RemoteSyncInfo) {
		Log.d(logTag, "updateLocal ${remote.remoteId} -> ${local.id}")
		if (remote.deleted) {
			pendingTasks.add(Tasks.call(Executors.sequence, Callable {
				localDao2.setNoteDeleted(listOf(local.id), remote.deleted, remote.modified)
			}))
		}else{
			pendingTasks.add(
				remoteStorage.getNote(remote.remoteId)
						.continueWith(Executors.sequence, Continuation<Note, Unit>{
							val note = it.result!!
							note.deleted = remote.deleted
							note.modified = remote.modified
							localDao.updateNote(note)
							Unit
						})
			)
		}
	}

}