package ru.yandex.subbota_job.notes;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.ShareActionProvider;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.Toast;

import java.io.File;

public class NoteContentActivity extends AppCompatActivity {
    private String mPath;
    private EditText mEdit;
    private ActionBar mActionBar;
    private boolean mChanged = false;
    private float mScale = 1;
    private float mDefaultTextSize;
    private ScaleGestureDetector mGesture;
    private ShareActionProvider mShareProvider;
    private EditText mNoteTitle;
    private boolean mLoading = false;
    static final String keyPath = NoteContentActivity.class.getName() + "path";
    static final String keyChanged = NoteContentActivity.class.getName() + "changed";
    static final String keyScale = "scale";

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_note_content);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        assert toolbar != null;
        setSupportActionBar(toolbar);
        mActionBar = getSupportActionBar();
        assert mActionBar != null;
        mActionBar.setDisplayHomeAsUpEnabled(true);
        mActionBar.setDisplayShowTitleEnabled(false);
        mNoteTitle = (EditText)findViewById(R.id.title_edit);
        assert mNoteTitle != null;
        mNoteTitle.setNextFocusDownId(R.id.editor);
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveAndExit();
            }
        });
        initEdit();

        if (savedInstanceState != null){
            mPath = savedInstanceState.getString(keyPath);
            mChanged = savedInstanceState.getBoolean(keyChanged);
        }else {
            Intent intent = getIntent();
            assert intent != null;
            Uri uri = intent.getData();
            if (uri != null)
                LoadContentAsync(uri.getPath());
            else// force show keyboard only for new note
                getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        //if (mPath != null && !mPath.isEmpty())
        outState.putString(keyPath, mPath);
        outState.putBoolean(keyChanged, mChanged);
    }

    @Override
    public void onBackPressed() {
        saveAndExit();
    }

    private void saveAndExit()
    {
        if (mChanged)
            saveContent();
        finish();
        overridePendingTransition(R.anim.go_into_from_left, R.anim.go_away_to_right);
    }

    private void initEdit() {
        Log.d(toString(), "initEdit");
        mEdit = (EditText)findViewById(R.id.editor);
        assert mEdit != null;
        mEdit.setCustomSelectionActionModeCallback(new ActionMode.Callback() {
            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                mActionBar.show();
                return true;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                for (int i = 0; i < menu.size(); ++i) {
                    MenuItem item = menu.getItem(i);
                    Log.d("onPrepareActionMode", String.format("%s: %d", item.getTitle().toString(), item.getItemId()));
                }
                Log.d("onPrepareActionMode", menu.getClass().toString());
                if (menu.findItem(android.R.id.shareText) == null) {
                    MenuItem item = menu.add(0, android.R.id.shareText, 100, R.string.share);
                    item.setIcon(R.drawable.ic_share_24dp);
                    item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
                }
                return true;
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                Log.d("onActionItemClicked", item.getTitle().toString());
                if (item.getItemId() == android.R.id.shareText) {
                    Intent intent = new Intent(Intent.ACTION_SEND);
                    intent.setType(getResources().getString(R.string.noteMimeType));
                    CharSequence s = mEdit.getText().subSequence(mEdit.getSelectionStart(), mEdit.getSelectionEnd());
                    Log.d("Share", s.toString());
                    intent.putExtra(Intent.EXTRA_TEXT, s.toString());
                    startActivity(intent);
                    return true;
                }
                return false;
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {
                Log.d("ActionMode.Callback", "onDestroyActionMode");
            }
        });
        mScale = getPreferences(Context.MODE_PRIVATE).getFloat(keyScale, 1);
        mDefaultTextSize = mEdit.getTextSize();
        zoomText(mScale);
        mGesture = new ScaleGestureDetector(this, new ScaleGestureDetector.OnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                float nextScale = detector.getScaleFactor();
                zoomText(mScale * nextScale);
                return true;
            }

            @Override
            public boolean onScaleBegin(ScaleGestureDetector detector) {
                return true;
            }

            @Override
            public void onScaleEnd(ScaleGestureDetector detector) {
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()){
            case R.id.clear_action:
                mEdit.setText(null);
                mChanged = false;
                return true;
            case R.id.undo_action:
                if (TextUtils.isEmpty(mPath))
                    mEdit.setText(null);
                else
                    LoadContentAsync(mPath);
                mChanged = false;
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        mGesture.onTouchEvent(ev);
        return mGesture.isInProgress() || super.dispatchTouchEvent(ev);
    }

    private void zoomText(float nextScale)
    {
        nextScale = (float)Math.min(nextScale, 3.0);
        nextScale = (float)Math.max(nextScale, 0.5);
        mScale = nextScale;
        mEdit.setTextSize(TypedValue.COMPLEX_UNIT_PX, mDefaultTextSize * mScale);
    }
    private String NewPath() throws RuntimeException  {
        File dir = NotesListAdapter.getOrAddDirectory(this);
        if (dir == null)
            throw new RuntimeException (getString(R.string.no_dest_dir));
        File file = new File(dir, String.format("%d.txt", System.currentTimeMillis()) );
        return file.getPath();
    }

    @Override
    protected void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        mNoteTitle.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (mLoading)
                    return;
                mChanged = true;
            }
        });
        mEdit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (mLoading)
                    return;
                updateShareProvider();
                mChanged = true;
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        getPreferences(Context.MODE_PRIVATE).edit().putFloat(keyScale, mScale).commit();
    }

    private void updateShareProvider()
    {
        if (mShareProvider!=null) {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType(getResources().getString(R.string.noteMimeType));
            if (mEdit.getSelectionStart()!=mEdit.getSelectionEnd())
                intent.putExtra(Intent.EXTRA_TEXT, mEdit.getText().subSequence(mEdit.getSelectionStart(),mEdit.getSelectionEnd()).toString());
            else
                intent.putExtra(Intent.EXTRA_TEXT, mEdit.getText().toString());
            mShareProvider.setShareIntent(intent);
        }
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_note_content, menu);
        mShareProvider = (ShareActionProvider)MenuItemCompat.getActionProvider(menu.findItem(R.id.share_action));
        updateShareProvider();
        return true;
    }

    private void saveContent() {
        Editable editable = mEdit.getText();
        if (editable.length() == 0 && TextUtils.isEmpty(mPath))
            return;
        try {
            String path = TextUtils.isEmpty(mPath) ? NewPath() : mPath;
            String all = UtfFile.Join(getCustomTitle(), editable.toString());
            UtfFile.Write(path, all);
            mPath = path;
            SyncService.onFileChanged(getApplicationContext(), mPath);
            Toast.makeText(getApplicationContext(), R.string.noteSaved, Toast.LENGTH_SHORT).show();
            mChanged = false;
        }catch(Exception e){
            Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private String getCustomTitle() {
        return mNoteTitle.getText().toString();
    }

    private void LoadContentAsync(String path) {
        mLoading = true;
        new AsyncTask<String, Void, String[]>() {
            @Override
            protected String[] doInBackground(String... params) {
                try{
                    String path = params[0];
                    String ret = UtfFile.ReadAll(path);
                    mPath = path;
                    return UtfFile.Split(ret);
                }catch(Exception e){
                    Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_LONG).show();
                }
                return null;
            }

            @Override
            protected void onPostExecute(String[] s) {
                if (s != null) {
                    Log.d("LoadContentAsync","mEdit.setText");
                    mEdit.setText(s[1]);
                    mNoteTitle.setText(s[0]);
                }
                mChanged = false;
                mLoading = false;
            }
        }.execute(path);
    }
}
