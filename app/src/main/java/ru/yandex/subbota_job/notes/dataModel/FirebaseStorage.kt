package ru.yandex.subbota_job.notes.dataModel

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.Transformations
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

data class RemoteSyncInfo(val remoteId:String, val modified:Long)

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
				ret.add(RemoteSyncInfo(x.key!!, x.getValue(Long::class.java)!!))
			}
		}
		ret
	}
	fun add(note: Note) : Task<Void> {
		val ref = database.child("index").push()
		val key = ref.key
		note.remoteId = key
		val data = HashMap<String, Any>()
		data["index/$key"] = note.modified
		data["content/$key"] = note
		return database.updateChildren(data)
	}

	fun update(note: Note): Task<Void> {
		val key = note.remoteId
		val data = HashMap<String, Any>()
		data["index/$key"] = note.modified
		data["content/$key"] = note
		return database.updateChildren(data)
	}

	fun getId(): String? {
		return database.child("index").push().key
	}
}