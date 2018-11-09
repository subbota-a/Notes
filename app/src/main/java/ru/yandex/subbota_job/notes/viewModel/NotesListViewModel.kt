package ru.yandex.subbota_job.notes.viewModel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import androidx.room.Transaction
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import io.reactivex.Completable
import io.reactivex.Scheduler
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.functions.Action
import io.reactivex.schedulers.Schedulers
import ru.yandex.subbota_job.notes.dataModel.FirebaseStorage
import ru.yandex.subbota_job.notes.dataModel.LocalDatabase
import ru.yandex.subbota_job.notes.dataModel.NoteDescription
import ru.yandex.subbota_job.notes.executor.Executors
import ru.yandex.subbota_job.notes.executor.SyncFirebase
import java.lang.Exception
import java.util.*
import java.util.concurrent.Callable

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
data class DeleteResult(val ids:List<Long>, val delete:Boolean, val throwable: Throwable?)

class NotesListViewModel(application: Application) : AndroidViewModel(application) {
	private val db = LocalDatabase.instance(application.applicationContext)
	private var notesDao = db.noteDescription()
	private val _filterString = MutableLiveData<String>().apply { value = null }
	var filterString : String?
		get() = _filterString.value
		set(value){
			_filterString.value = value
		}
	fun filteredList() : LiveData<List<NoteDescription>> = Transformations.switchMap(_filterString) {
		f -> if (f.isNullOrEmpty()) notesDao.getAllNoteDescription() else notesDao.getFilteredNoteDescription(f)
	}
	val selectedIds = SelectedItems<Long>()

	var activeMode = MutableLiveData<Boolean>().apply { value = false }
	var searchMode = MutableLiveData<Boolean>().apply { value = false }
	private val _deleteResult = MutableLiveData<Event<DeleteResult>>()
	val deleteResult : LiveData<Event<DeleteResult>> get() = _deleteResult

	fun softDelete(ids: List<Long>, delete: Boolean)
	{
		Completable.fromAction {
			notesDao.setNoteDeleted(ids, delete, Date().time)
		}.subscribeOn(Schedulers.io())
		.observeOn(AndroidSchedulers.mainThread())
		.subscribe(
				{ _deleteResult.value = Event(DeleteResult(ids, delete,null)) }
				, {throwable -> _deleteResult.value = Event(DeleteResult(ids, delete, throwable)) })
	}
	fun saveNotesPositions(notes:List<NoteDescription>): Task<Unit>
	{
		return Tasks.call(Executors.sequence, Callable{
			db.beginTransaction()
			try {
				val modified = Date().time
				for (n in notes)
					notesDao.updateNotePosition(n.id, n.position, modified)
				db.setTransactionSuccessful()
			} finally {
				db.endTransaction()
			}
		})
	}
	private val _googleAccount = MutableLiveData<GoogleSignInAccount>()
	val googleAccount : LiveData<GoogleSignInAccount> get() = _googleAccount
	val sync = SyncFirebase(db, FirebaseStorage())
	override fun onCleared() {
		sync.release()
		super.onCleared()
	}
}