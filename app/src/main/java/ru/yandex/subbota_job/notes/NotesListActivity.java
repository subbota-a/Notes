package ru.yandex.subbota_job.notes;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.view.ActionMode;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;

import java.util.Arrays;

public class NotesListActivity extends AppCompatActivity
{
    NotesListAdapter mNotesAdaptor;
    RecyclerView mList;
    FloatingActionButton mNewNote;
    public final static String NotesDirectory = "notes";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notes_list);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mNotesAdaptor = new NotesListAdapter(this);

        mList = (RecyclerView)findViewById(R.id.listview);
        assert mList != null;
        mList.setLayoutManager(new LinearLayoutManager(this));
        mList.setAdapter(mNotesAdaptor);

        new RecyclerViewGestureDetector(this, mList, new GestureController());


        mNewNote = (FloatingActionButton) findViewById(R.id.fab);
        mNewNote.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                createNewNote();
            }
        });
    }

    class GestureController extends GestureDetector.SimpleOnGestureListener implements ActionMode.Callback{
        private ActionMode mActionMode;
        public boolean isSelectionMode(){
            return mActionMode != null;
        }
        int getAdapterPosition(MotionEvent e)
        {
            View itemView = mList.findChildViewUnder(e.getX(), e.getY());
            if (itemView == null)
                return RecyclerView.NO_POSITION;
            return mList.getChildAdapterPosition(itemView);
        }
        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            int position = getAdapterPosition(e);
            if (position == RecyclerView.NO_POSITION)
                return false;
            if (isSelectionMode())
                toggleSelection(position);
            else
                editNote(mNotesAdaptor.getItem(position));
            return true;
        }

        @Override
        public void onLongPress(MotionEvent e) {
            int position = getAdapterPosition(e);
            if (position == RecyclerView.NO_POSITION)
                return;
            toggleSelection(position);
        }
        void toggleSelection(int position)
        {
            if (!isSelectionMode())
                beginSelectionMode();
            mNotesAdaptor.toggleSelection(position);
            int count = mNotesAdaptor.getSelectionCount();
            mActionMode.setTitle(String.valueOf(count));
            if (count==0)
                endSelectionMode();
        }

        private void endSelectionMode() {
            mActionMode.finish();
        }

        private void beginSelectionMode() {
            mActionMode = startSupportActionMode(this);
            mNewNote.hide();
        }

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            mode.getMenuInflater().inflate(R.menu.selected_notes_menu, menu);
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            if (item.getItemId() == R.id.action_delete) {
                mNotesAdaptor.deleteSelectedAsync();
                endSelectionMode();
                return true;
            }
            return false;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            mActionMode = null;
            mNotesAdaptor.clearAllSelection();
            mNewNote.show();
        }
    }
    private void editNote(NoteDescription item) {
        Intent intent = new Intent(this, NoteContentActivity.class);
        intent.setData(Uri.fromFile(item.mFileName));
        startActivityForResult(intent, 0);
    }

    private void createNewNote() {
        Intent intent = new Intent(this, NoteContentActivity.class);
        startActivityForResult(intent, 0);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (PackageManager.PERMISSION_DENIED == ContextCompat.checkSelfPermission(this, "android.permission.WRITE_EXTERNAL_STORAGE")){
            ActivityCompat.requestPermissions(this, new String[]{"android.permission.WRITE_EXTERNAL_STORAGE"}, 0);
        }else
            mNotesAdaptor.updateAsync();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (!Arrays.asList(grantResults).contains(PackageManager.PERMISSION_DENIED))
            mNotesAdaptor.updateAsync();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        mNotesAdaptor.updateAsync();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_notes_list, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
//        if (id == R.id.action_settings) {
//            return true;
//        }

        return super.onOptionsItemSelected(item);
    }

}
