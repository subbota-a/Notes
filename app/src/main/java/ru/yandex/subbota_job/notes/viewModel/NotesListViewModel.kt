package ru.yandex.subbota_job.notes.viewModel

import android.app.Application
import android.arch.lifecycle.AndroidViewModel
import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.Transformations
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import ru.yandex.subbota_job.notes.dataModel.FirebaseStorage
import ru.yandex.subbota_job.notes.dataModel.LocalDatabase
import ru.yandex.subbota_job.notes.dataModel.NoteDescription
import ru.yandex.subbota_job.notes.executor.Executors
import ru.yandex.subbota_job.notes.executor.SyncFirebase
import java.util.*

class SelectedItems<T>{
	private val ids = HashSet<T>()
	private val self = MutableLiveData<SelectedItems<T>>().apply { value = this@SelectedItems }
	val liveData: LiveData<SelectedItems<T>> get() = self
	private fun throwChanged(){
		self.value = this
	}
	val count : Int get()= ids.size
	fun contains(id : T) = ids.contains(id)
	fun toggle(id : T) : Boolean{
		val isSelected = ids.add(id)
		if (!isSelected)
			ids.remove(id)
		throwChanged()
		return isSelected
	}
	fun clear() {
		ids.clear()
		throwChanged()
	}
	var items : Iterable<T>
		get() = ids
		set(value){
			ids.clear()
			ids.addAll(value)
			throwChanged()
		}
}

class NotesListViewModel(application: Application) : AndroidViewModel(application) {
	private val db = LocalDatabase.instance(application.applicationContext)
	private var notesDao = db.noteDescription()
	private val _filterString = MutableLiveData<String>().apply { value = null }
	var filterString : String?
		get() = _filterString.value
		set(value){
			_filterString.value = value
		}
	val filteredList : LiveData<List<NoteDescription>> = Transformations.switchMap(_filterString) {
		f -> if (f.isNullOrEmpty()) notesDao.getAllNoteDescription() else notesDao.getFilteredNoteDescription(f)
	}
	val selectedIds = SelectedItems<Long>()

	var activeMode = MutableLiveData<Boolean>().apply { value = false }
	var searchMode = MutableLiveData<Boolean>().apply { value = false }
	var undoSnackbar = MutableLiveData<List<Long>>()

	fun softDelete(ids: List<Long>, delete: Boolean)
	{
		Executors.sequence.execute(){
			notesDao.setNoteDeleted(ids, delete, Date().time)
		}
	}
	private val _googleAccount = MutableLiveData<GoogleSignInAccount>()
	val googleAccount : LiveData<GoogleSignInAccount> get() = _googleAccount
	val sync = SyncFirebase(db, FirebaseStorage())
	override fun onCleared() {
		sync.release()
		super.onCleared()
	}
}