package ru.yandex.subbota_job.notes.dataModel

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.Transformations
import android.util.Log
import com.google.android.gms.tasks.Continuation
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

open class SyncData(var modified:Long, var deleted:Boolean){
	constructor(): this(0, false){}
}
class RemoteSyncInfo(val remoteId:String, modified:Long, deleted:Boolean) : SyncData(modified, deleted){}

class FirebaseSnapshotLiveData(private val databaseRef: DatabaseReference): LiveData<DataSnapshot>(){
	private val logTag = "FbSnapshotLiveData"
	private val listener = object : ValueEventListener{
		override fun onCancelled(p0: DatabaseError) {}

		override fun onDataChange(snapshot: DataSnapshot) {
			Log.d(logTag, "onDataChange")
			value = snapshot
		}

	}
	override fun onActive() {
		super.onActive()
		Log.d(logTag, "addValueEventListener")
		databaseRef.addValueEventListener(listener)
	}

	override fun onInactive() {
		Log.d(logTag, "removeEventListener")
		super.onInactive()
		databaseRef.removeEventListener(listener)
	}
}

class FirebaseStorage() {
	private val account = FirebaseAuth.getInstance().currentUser ?: throw Exception("User not authenticated")
	private val fb = FirebaseDatabase.getInstance()
	private val database = fb.getReference(account.uid)
	fun syncData() : LiveData<List<RemoteSyncInfo>> = Transformations.map(FirebaseSnapshotLiveData(database.child("index"))) { snapshot ->
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

	fun update(note: Note): Task<Unit> {
		val key = note.remoteId
		val data = HashMap<String, Any?>()
		data["index/$key"] = SyncData(note.modified, note.deleted)
		data["content/$key"] = note
		return database.updateChildren(data).continueWithTask( Continuation<Void, Task<Unit>> {task: Task<Void> ->
			if (task.isSuccessful)
				Tasks.forResult(Unit)
			else
				Tasks.forException<Unit>(task.exception!!)
		})
	}

	fun getId(): String? {
		return database.child("index").push().key
	}

	fun getNote(remoteId: String): Task<Note> {
		val cs = TaskCompletionSource<Note>()
		database.child("content").child(remoteId).addListenerForSingleValueEvent(object: ValueEventListener{
			override fun onCancelled(p0: DatabaseError) {
				cs.setException(p0.toException())
			}

			override fun onDataChange(p0: DataSnapshot) {
				cs.setResult(p0.getValue(Note::class.java))
			}
		})
		return cs.task
	}
}