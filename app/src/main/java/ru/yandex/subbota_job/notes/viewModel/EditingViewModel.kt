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
	var maxSeqId = 0L
	var curSeqId: Long = 0
	private val _snapshot = MutableLiveData<NoteSnapshot>()
	val snapshot : LiveData<NoteSnapshot> get() = _snapshot
	// actions
	fun clearHistory(){
		Executors.sequence.execute(){
			snapshotsDao.clearAll()
		}
	}
	fun loadNote(id : Long){
		Executors.sequence.execute(){
			val note = notesDao.getNote(id)
			_snapshot.postValue(NoteSnapshot().apply {
				seqId = 0
				title = note.title
				body = note.body
				scrollPos = note.scrollPos
				selectionPos = note.selectionPos
				modified = note.modified
			})
		}
	}
	fun loadEditing(seqId: Long){
		Executors.sequence.execute(){
			_snapshot.postValue(snapshotsDao.getEditing(seqId))
		}
	}
	fun loadDraft(){
		Executors.sequence.execute(){
			val seqId = snapshotsDao.maxSeqId()
			_snapshot.postValue(if (seqId != null) snapshotsDao.getEditing(seqId) else null)
		}
	}
	fun addSnapshot(snapshot: NoteSnapshot){
		snapshot.seqId = ++curSeqId
		Executors.sequence.execute(){
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
			note.makeTitle()
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