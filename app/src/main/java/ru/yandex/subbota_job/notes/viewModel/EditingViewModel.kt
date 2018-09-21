package ru.yandex.subbota_job.notes.viewModel

import android.app.Application
import android.arch.lifecycle.AndroidViewModel
import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import ru.yandex.subbota_job.notes.dataModel.*
import ru.yandex.subbota_job.notes.executor.Executors

class EditingViewModel(application: Application) : AndroidViewModel(application){
	// data
	private var snapshotsDao = LocalDatabase.instance(application.applicationContext).snapshots()
	private var notesDao = LocalDatabase.instance(application.applicationContext).noteEdition()
	var maxSeqId : Long = 0
	var curSeqId: Long = 0
	private val _snapshot = MutableLiveData<NoteSnapshot>()
	val snapshot : LiveData<NoteSnapshot> get() = _snapshot
	// actions
	fun clearHistory(){
		Executors.sequence.execute(){
			snapshotsDao.clearAll()
		}
	}
	fun loadEditing(seqId: Long){
		Executors.sequence.execute(){
			_snapshot.postValue(snapshotsDao.getEditing(seqId))
		}
	}
	fun loadDraft(id : Long?){
		Executors.sequence.execute(){
			maxSeqId = snapshotsDao.maxSeqId() ?: -1
			curSeqId = maxSeqId
			var s = NoteSnapshot()
			if (maxSeqId >= 0)
				s = snapshotsDao.getEditing(maxSeqId)
			else {
				maxSeqId = 0
				curSeqId = maxSeqId
				if (id != null) {
					val note = notesDao.getNote(id)
					s.apply {
						seqId = curSeqId
						title = note.title
						body = note.body
						scrollPos = note.scrollPos
						selectionPos = note.selectionPos
						modified = note.modified
					}
				}
				snapshotsDao.addSnapshot(s)
			}
			_snapshot.postValue(s)
		}
	}
	fun addSnapshot(snapshot: NoteSnapshot){
		snapshot.seqId = ++curSeqId
		maxSeqId = curSeqId
		Executors.sequence.execute(){
			snapshotsDao.deleteSince(snapshot.seqId)
			snapshotsDao.addSnapshot(snapshot)
		}
	}
	companion object {
		fun updateNoteBySnapshot(snapshot: NoteSnapshot, note : Note) {
			note.title = snapshot.title
			note.body = snapshot.body
			note.scrollPos = snapshot.scrollPos
			note.selectionPos = snapshot.selectionPos
			note.modified = snapshot.modified
		}
	}
	fun saveNote(snapshot: NoteSnapshot, id : Long?){
		Executors.sequence.execute(){
			val note = id?.let{
				notesDao.getNote(it)
				}
				?: Note().apply{
					this.id = id?:0
					position = snapshot.modified
				}
			updateNoteBySnapshot(snapshot, note)
			if (!note.isEmpty() || id != null)
				notesDao.saveNote(note)
		}
	}
}