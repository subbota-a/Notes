package ru.yandex.subbota_job.notes.dataModel

import androidx.lifecycle.LiveData
import androidx.room.*
import android.content.Context
import io.reactivex.Flowable
import io.reactivex.Observable

open class NoteDescription {
	@PrimaryKey(autoGenerate = true)
	var id: Long = 0
	var title: String = ""
	var position: Long = 0 // position in list
	override fun equals(other: Any?): Boolean {
		if (other == null || other !is NoteDescription)
			return false
		return id==other.id && title==other.title && position==other.position
	}

	override fun hashCode(): Int {
		return id.hashCode()
	}
}

data class LocalSyncInfo(
	val id:Long,
	val remoteId:String?,
	val modified:Long,
	val deleted: Boolean
)

@Entity(tableName = "notes")
class Note: NoteDescription() {
	var deleted: Boolean = false
	var modified : Long = 0; // modification time of title, body, deleted
	var body: String = ""
	var selectionPos: Int = 0
	var scrollPos: Int = 0
	var remoteId: String? = null
	fun makeTitle() {
		if (title.isNullOrBlank() && !body.isNullOrBlank()){
			title = body.lineSequence().first()
		}
	}
	fun isEmpty() = title.isNullOrBlank() && body.isNullOrBlank()
}

@Dao
interface NoteDescriptionDao {
	@Query("SELECT id,title,position FROM notes where deleted = 0 ORDER BY position desc")
	fun getAllNoteDescription(): LiveData<List<NoteDescription>>

	@Query("SELECT id,title,position FROM notes WHERE deleted = 0 AND (title like '%' || :substring || '%' OR body like '%' || :substring || '%') ORDER BY position desc")
	fun getFilteredNoteDescription(substring: String): LiveData<List<NoteDescription>>

	@Query("UPDATE notes SET deleted = :deleted, modified = :modified WHERE id IN(:ids)")
	fun setNoteDeleted(ids: List<Long>, deleted: Boolean, modified : Long)

	@Query("UPDATE notes SET position=:position, modified=:modified WHERE id=:id")
	fun updateNotePosition(id:Long, position:Long, modified:Long)
}

@Dao
interface NoteEditionDao{
	@Query("SELECT * from notes where id=:id")
	fun getNote(id : Long) : Note

	@Update
	fun updateNote(note : Note): Int

	@Insert
	fun insertNote(note : Note) : Long

	@Transaction
	fun saveNote(note : Note){
		if (updateNote(note)==0)
			note.id = insertNote(note)
	}

	@Query("DELETE FROM notes where deleted!=0")
	fun deleteDeletedNotes()

	@Query("SELECT id, remoteId, modified, deleted FROM notes")
	fun getSyncInfo() : Flowable<List<LocalSyncInfo>>
}

@Entity(tableName = "snapshots")
data class NoteSnapshot(
		@PrimaryKey var seqId: Long = 0,
		var title: String = "",
		var body: String = "",
		var selectionPos: Int = 0,
		var scrollPos: Int = 0,
		var modified: Long = 0
)
@Dao
interface SnapshotsDao{
	@Query("DELETE FROM snapshots")
	fun clearAll()

	@Insert
	fun addSnapshot(snapshots: NoteSnapshot)

	@Query("delete from snapshots where seqId >= :seqId")
	fun deleteSince(seqId: Long)

	@Query("select max(seqId) from snapshots")
	fun maxSeqId(): Long?

	@Query("select * from snapshots where seqId = :seqId")
	fun getEditing(seqId:Long): NoteSnapshot
}

@Database(entities = [Note::class, NoteSnapshot::class], version = 1)
abstract class LocalDatabase: RoomDatabase() {
	abstract fun noteDescription(): NoteDescriptionDao
	abstract fun noteEdition() : NoteEditionDao
	abstract fun snapshots() : SnapshotsDao

	companion object {
		private var instance: LocalDatabase? = null
		private val lock = Any()
		fun instance(context: Context): LocalDatabase {
			synchronized(lock) {
				if (instance == null) {
					instance = Room.databaseBuilder(context.applicationContext
							, LocalDatabase::class.java, "Notes.db")
							.build()
				}
				return instance!!
			}
		}
	}
}
