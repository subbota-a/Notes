package ru.yandex.subbota_job.notes;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;

public class NotesListActivity extends AppCompatActivity
{

    NotesListAdapter mNotesAdaptor;
    RecyclerView mList;
    public final static String NotesDirectory = "notes";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notes_list);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                createNewNote();
            }
        });

        mNotesAdaptor = new NotesListAdapter(this);
        mNotesAdaptor.updateAsync();

        mList = (RecyclerView)findViewById(R.id.listview);
        assert mList != null;
        mList.setLayoutManager(new LinearLayoutManager(this));
        mList.setAdapter(mNotesAdaptor);

        new RecyclerViewGestureDetector(this, mList, new GestureController());
    }

    class GestureController extends GestureDetector.SimpleOnGestureListener{
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
            editNote(mNotesAdaptor.getItem(position));
            return true;
        }

        @Override
        public void onLongPress(MotionEvent e) {
            int position = getAdapterPosition(e);
            if (position == RecyclerView.NO_POSITION)
                return;
            mNotesAdaptor.toggleSelection(position);
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
