package ru.yandex.subbota_job.notes.executor

import androidx.lifecycle.*
import android.os.Looper
import android.util.Log
import androidx.lifecycle.Observer
import com.google.android.gms.tasks.Continuation
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import io.reactivex.*
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.functions.BiFunction
import io.reactivex.schedulers.Schedulers
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

fun <T> next(i: Iterator<T>) : T? = if (i.hasNext()) i.next() else null

fun getModifications(localSyncData:List<LocalSyncInfo>, remoteSyncData:List<RemoteSyncInfo>) =
		Flowable.create<Pair<LocalSyncInfo?, RemoteSyncInfo?>>({emitter ->
			val localIter = localSyncData.sortedBy {it.remoteId}.iterator()
			val remoteIter = remoteSyncData.sortedBy { it.remoteId }.iterator()
			var local = next(localIter)
			var remote  = next(remoteIter)
			while(!emitter.isCancelled && (local != null || remote != null)){
				if (local == null) {
					emitter.onNext(Pair(null, remote))
					remote = next(remoteIter)
				}else if (remote == null) {
					emitter.onNext(Pair(local, null))
					local = next(localIter)
				}else if (local.remoteId == null || local.remoteId!! < remote.remoteId) {
					emitter.onNext(Pair(local, null))
					local = next(localIter)
				}else if (local.remoteId!! > remote.remoteId) {
					emitter.onNext(Pair(null, remote))
					remote = next(remoteIter)
				}else {
					if (local.modified != remote.modified)
						emitter.onNext(Pair(local, remote))
					local = next(localIter)
					remote = next(remoteIter)
				}
			}
			emitter.onComplete()
		}, BackpressureStrategy.BUFFER)

class SyncFirebase(val localStorage: LocalDatabase, private val remoteStorage: FirebaseStorage) {
	private val localDao = localStorage.noteEdition()
	private val localDao2 = localStorage.noteDescription()
	private var stop: Boolean = false
	private var subscribed : Disposable? = null

	init{
		subscribe()
	}
	private fun subscribe()
	{
		if (stop)
			return
		Log.d(logTag, "subscribe")
		subscribed = Flowable.combineLatest(localDao.getSyncInfo(), remoteStorage.syncData(), BiFunction { t1:List<LocalSyncInfo>, t2:List<RemoteSyncInfo> -> Pair(t1,t2) })
				.subscribeOn(Schedulers.computation())
				.onBackpressureBuffer(1, { }, BackpressureOverflowStrategy.DROP_OLDEST)
				.concatMap {
					getModifications(it.first, it.second)
					.subscribeOn(Schedulers.io())
					.map{
						myassert(it.first!=null || it.second!=null)
						if (it.first == null){
							addToLocal(it.second!!)
						}else if (it.second == null){
							addToRemote(it.first!!)
						}else{
							if (it.first!!.modified < it.second!!.modified)
								updateLocal(it.first!!, it.second!!)
							else if (it.first!!.modified > it.second!!.modified)
								updateRemote(it.second!!, it.first!!)
							else
								Completable.complete()
						}
					}.concatMap{ it.toFlowable<Unit>() }
					.materialize()
					.filter{ it.isOnError }
					.map{ it.error!! }
				}
				.onBackpressureLatest()
				.observeOn(AndroidSchedulers.mainThread(), false, 1)
				.subscribe{
					Log.d(logTag, it.localizedMessage)
				}
	}
	private fun unsubscribe()
	{
		Log.d(logTag, "unsubscribe")
		subscribed?.dispose()
		subscribed = null
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
		myassert(Looper.myLooper() != Looper.getMainLooper())
		myassert(pendingTasks.isEmpty()){"sync on pending tasks not empty"}

		if (pendingTasks.isNotEmpty()){
			Log.d(logTag, "Wait for pending tasks(${pendingTasks.size})")
			unsubscribe()
			Tasks.whenAll(pendingTasks).continueWith {
				this.pendingTasks.clear()
				Log.d(logTag, "Pending tasks completed")
				subscribe() // the last sentence
			}
		}
	}

	private val logTag = "SyncFirebase"

	private fun addToRemote(local: LocalSyncInfo) : Completable{
		Log.d(logTag, "addToRemote id=${local.id}, deleted=${local.deleted}")
		if (!local.deleted) {
			return Single.fromCallable{localDao.getNote(local.id)}
					.observeOn(Schedulers.io())
					.flatMapCompletable { note ->
						if (note.remoteId == null) {
							note.remoteId = remoteStorage.getId()
							Completable.fromAction(){
								localDao.updateNote(note)
							}
						}else
							 remoteStorage.update(note)
					}
		}else {
			return Completable.complete()
		}
	}
	private fun addToLocal(remote: RemoteSyncInfo): Completable{
		Log.d(logTag, "addToLocal ${remote.remoteId}, deleted=${remote.deleted}")
		if (!remote.deleted) {
			return remoteStorage.getNote(remote.remoteId)
					.observeOn(Schedulers.io())
					.flatMapCompletable {note ->
						note.deleted = remote.deleted
						note.modified = remote.modified
						Completable.fromAction { localDao.insertNote(note) }
					}
		}else
			return Completable.complete()
	}
	private fun updateRemote(remote: RemoteSyncInfo, local: LocalSyncInfo): Completable {
		Log.d(logTag, "updateRemote ${local.remoteId} -> ${remote.remoteId}")
		return Single.fromCallable { localDao.getNote(local.id) }
				.observeOn(Schedulers.io())
				.flatMapCompletable{ node -> remoteStorage.update(node) }
	}

	private fun updateLocal(local: LocalSyncInfo, remote: RemoteSyncInfo):Completable {
		Log.d(logTag, "updateLocal ${remote.remoteId} -> ${local.remoteId}")
		if (remote.deleted) {
			return Completable.fromAction{
				localDao2.setNoteDeleted(listOf(local.id), remote.deleted, remote.modified)
			}.observeOn(Schedulers.io())
		}else{
			return remoteStorage.getNote(remote.remoteId)
				.observeOn(Schedulers.io())
				.flatMapCompletable{ note ->
					note.deleted = remote.deleted
					note.modified = remote.modified
					Completable.fromAction{localDao.updateNote(note)}
				}
		}
	}

}