package ru.yandex.subbota_job.notes;

import android.app.ActivityOptions;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.ActivityOptionsCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.view.WindowCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.view.ActionMode;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
import android.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.drive.Drive;

import java.util.Arrays;

public class NotesListActivity extends AppCompatActivity
{
    NotesListAdapter mNotesAdaptor;
    RecyclerView mList;
    FloatingActionButton mNewNote;
    String mFilterString;
    public final static String NotesDirectory = "notes";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notes_list);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        ActionBar actionBar = getSupportActionBar();
        assert actionBar != null;

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

        mNotesAdaptor.beginUpdate();
        mNotesAdaptor.updateAsync(mFilterString);
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
                editNote(position);
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
                mNotesAdaptor.deleteSelectedAsync(findViewById(R.id.coordinatorLayout));
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

    private void editNote(int position) {
        NoteDescription item = mNotesAdaptor.getItem(position);
        Intent intent = new Intent(this, NoteContentActivity.class);
        intent.setData(Uri.fromFile(item.mFileName));
        ActivityOptionsCompat options;
        if (Build.VERSION.SDK_INT >= 21 ) {
            RecyclerView.ViewHolder vh = mList.findViewHolderForAdapterPosition(position);
            View view = vh.itemView;
            view = view.findViewById(android.R.id.text1);
            options = ActivityOptionsCompat.makeSceneTransitionAnimation(this, view, item.mFileName.getName());
        }else
            options = ActivityOptionsCompat.makeCustomAnimation(this, R.anim.go_into_from_right, R.anim.go_away_to_left);
        startActivityForResult(intent, 0,options.toBundle());
    }

    private void createNewNote() {
        Intent intent = new Intent(this, NoteContentActivity.class);
        ActivityOptionsCompat options;
        options = ActivityOptionsCompat.makeCustomAnimation(this, R.anim.go_into_from_right, R.anim.go_away_to_left);
        startActivityForResult(intent, 0, options.toBundle());
    }

    @Override
    protected void onResume() {
        Log.d("NodesListActivity", "onResume");
        super.onResume();
        mNotesAdaptor.endUpdate();
    }

    @Override
    protected void onPause() {
        Log.d("NodesListActivity", "onStop");
        super.onPause();
        mNotesAdaptor.beginUpdate();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_notes_list, menu);
        MenuItem mi = menu.findItem(R.id.action_search);
        new Search(mi);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_sync) {
            SyncService.syncAll(getApplicationContext());
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    class Search implements MenuItemCompat.OnActionExpandListener
            , MenuItem.OnMenuItemClickListener
            , SearchView.OnQueryTextListener
            , SearchView.OnCloseListener
    {
        final SearchView mSearchView;
        final MenuItem mItem;
        public Search(MenuItem mi)
        {
            mItem = mi;
            mi.setOnMenuItemClickListener(this);
            MenuItemCompat.setOnActionExpandListener(mi, this);
            mSearchView = (android.widget.SearchView) mi.getActionView();
            mSearchView.setQueryHint(getResources().getString(R.string.action_search));
            mSearchView.setSubmitButtonEnabled(false);
            mSearchView.setIconified(true);
            mSearchView.setOnCloseListener(this);
            mSearchView.setOnQueryTextListener(this);
        }
        @Override
        public boolean onMenuItemClick(MenuItem item) {
            mSearchView.setQuery(mFilterString, false);
            mSearchView.setIconified(false);
            return true;
        }
        @Override
        public boolean onMenuItemActionExpand(MenuItem item) {
            return true;
        }

        @Override
        public boolean onMenuItemActionCollapse(MenuItem item) {
            Log.d("Search", "onMenuItemActionCollapse");
            mSearchView.clearFocus();
            if (!TextUtils.isEmpty(mFilterString))
                mNotesAdaptor.updateAsync(null);
            mFilterString = null;
            return true;
        }
        @Override
        public boolean onQueryTextSubmit(String query) {
            Log.d("Search", "onQueryTextSubmit");
            return true;
        }

        String setNullOnEmpty(String s)
        {
            return TextUtils.isEmpty(s) ? null : s;
        }
        @Override
        public boolean onQueryTextChange(String query) {
            Log.d("Search", "onQueryTextChange");
            query = setNullOnEmpty(query);
            mFilterString = setNullOnEmpty(mFilterString);
            if (!TextUtils.equals(mFilterString, query)) {
                mFilterString = query;
                mNotesAdaptor.updateAsync(mFilterString);
            }
            return true;
        }

        @Override
        public boolean onClose() {
            Log.d("Search", "onClose");
            mItem.collapseActionView();
            return false;
        }
    }
}
