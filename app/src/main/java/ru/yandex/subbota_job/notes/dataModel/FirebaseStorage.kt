package ru.yandex.subbota_job.notes.dataModel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import android.util.Log
import com.google.android.gms.tasks.Continuation
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import io.reactivex.*

open class SyncData(var modified:Long, var deleted:Boolean){
	constructor(): this(0, false){}
}
class RemoteSyncInfo(val remoteId:String, modified:Long, deleted:Boolean) : SyncData(modified, deleted){}

fun DatabaseReference.observe(): Flowable<DataSnapshot>{
	val logTag = "FbSnapshotObservable"
	return Flowable.create<DataSnapshot>({ emitter ->
		if (emitter.isCancelled) return@create
		val listener = object : ValueEventListener{
			override fun onCancelled(p0: DatabaseError) {
				emitter.tryOnError(p0.toException())
			}

			override fun onDataChange(snapshot: DataSnapshot) {
				Log.d(logTag, "onDataChange")
				try {
					if (!emitter.isCancelled)
						emitter.onNext(snapshot)
				}catch(e: InterruptedException ){
					emitter.tryOnError(e)
				}
			}
		}
		emitter.setCancellable {
			Log.d(logTag, "removeEventListener")
			this.removeEventListener(listener)
		}
		Log.d(logTag, "addValueEventListener")
		this.addValueEventListener(listener)
	}, BackpressureStrategy.LATEST)
}

class FirebaseStorage() {
	private val account = FirebaseAuth.getInstance().currentUser ?: throw Exception("User not authenticated")
	private val fb = FirebaseDatabase.getInstance()
	private val database = fb.getReference(account.uid)
	fun syncData() : Flowable<List<RemoteSyncInfo>> = database.child("index").observe().map{ snapshot ->
		val ret = ArrayList<RemoteSyncInfo>()
		if (snapshot.exists())
		{
			for(x in snapshot.children){
				val sd = x.getValue(SyncData::class.java)!!
				ret.add(RemoteSyncInfo(x.key!!, sd.modified, sd.deleted))
			}
		}
		ret
	}

	fun update(note: Note): Completable {
		val key = note.remoteId
		val data = HashMap<String, Any?>()
		data["index/$key"] = SyncData(note.modified, note.deleted)
		data["content/$key"] = note
		return Completable.create{emitter ->
			val listener = object: DatabaseReference.CompletionListener{
				override fun onComplete(p0: DatabaseError?, p1: DatabaseReference) {
					if (emitter.isDisposed)
						return
					if (p0 != null)
						emitter.onError(p0.toException())
					else
						emitter.onComplete()
				}
			}
			database.updateChildren(data, listener)
		}
	}

	fun getId(): String? {
		return database.child("index").push().key
	}

	fun getNote(remoteId: String): Single<Note> {
		return Single.create<Note>{emitter ->
			val listener = object: ValueEventListener{
				override fun onCancelled(p0: DatabaseError) {
					emitter.onError(p0.toException())
				}

				override fun onDataChange(p0: DataSnapshot) {
					try {
						if (!emitter.isDisposed)
							emitter.onSuccess(p0.getValue(Note::class.java) as Note)
					}catch(t : Throwable){
						emitter.tryOnError(t)
					}
				}
			}
			database.child("content").child(remoteId).addListenerForSingleValueEvent(listener)
			emitter.setCancellable {
				database.child("content").child(remoteId).removeEventListener(listener)
			}
		}
	}
}