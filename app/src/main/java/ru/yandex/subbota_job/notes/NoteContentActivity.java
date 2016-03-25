package ru.yandex.subbota_job.notes;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.widget.NestedScrollView;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.util.Date;

public class NoteContentActivity extends AppCompatActivity {
    String mPath;
    EditText mEdit;
    boolean mChanged = false;
    float mScale = 1;
    float mDefaultTextSize;
    ScaleGestureDetector mGesture;
    static final String keyPath = NoteContentActivity.class.getName() + "path";
    static final String keyScale = "scale";

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_note_content);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        assert toolbar != null;
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                NoteContentActivity.this.finish();
            }
        });
        initEdit();

        if (savedInstanceState != null){
            mPath = savedInstanceState.getString(keyPath);
        }else {
            Intent intent = getIntent();
            assert intent != null;
            Uri uri = intent.getData();
            if (uri != null)
                LoadContentAsync(uri.getPath());
        }
    }

    private void initEdit() {
        mEdit = (EditText)findViewById(R.id.editor);
        assert mEdit != null;
        mEdit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                mChanged = true;
            }
        });
        mScale = getPreferences(Context.MODE_PRIVATE).getFloat(keyScale, 1);
        mDefaultTextSize = mEdit.getTextSize();
        setScale();
        mGesture = new ScaleGestureDetector(this, new ScaleGestureDetector.OnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                float nextScale = detector.getScaleFactor();
                mScale *= nextScale;
                setScale();
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
    public boolean dispatchTouchEvent(MotionEvent ev) {
        mGesture.onTouchEvent(ev);
        return mGesture.isInProgress() || super.dispatchTouchEvent(ev);
    }

    private void setScale()
    {
        mEdit.setTextSize(TypedValue.COMPLEX_UNIT_PX, mDefaultTextSize * mScale);
    }
    private String NewPath() {
        File dir = NotesListAdapter.getDirectory(this);
        Date now = new Date();
        File file = new File(dir, String.valueOf(System.currentTimeMillis()) );
        return file.getPath();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mPath != null && !mPath.isEmpty())
            outState.putString(keyPath, mPath);
    }

    @Override
    protected void onPause() {
        super.onPause();
        getPreferences(Context.MODE_PRIVATE).edit().putFloat(keyScale, mScale).commit();
        if (mChanged)
            saveContentAsync();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_note_content, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return super.onOptionsItemSelected(item);
    }

    private void saveContentAsync() {
        Editable editable = mEdit.getText();
        if (editable.length() == 0 && TextUtils.isEmpty(mPath))
            return;
        new AsyncTask<String, Void, Exception>() {
            @Override
            protected Exception doInBackground(String... params) {
                try {
                    String path = TextUtils.isEmpty(mPath) ? NewPath() : mPath;
                    FileWriter writer = new FileWriter(path);
                    try {
                        writer.write(params[0]);
                        mPath = path;
                    }finally {
                        writer.close();
                    }
                }catch(IOException e){
                    return e;
                }
                return null;
            }

            @Override
            protected void onPostExecute(Exception e) {
                Toast toast;
                if (e != null)
                    toast = Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_LONG);
                else
                    toast = Toast.makeText(getApplicationContext(), R.string.noteSaved, Toast.LENGTH_SHORT);
                toast.show();
            }
        }.execute(editable.toString());
        mChanged = false;
    }

    private void LoadContentAsync(String path) {
        new AsyncTask<String, Void, String>() {
            @Override
            protected String doInBackground(String... params) {
                try{
                    String path = params[0];
                    BufferedReader reader = new BufferedReader(new FileReader(path));
                    try {
                        StringBuilder builder = new StringBuilder();
                        int c;
                        while ((c = reader.read()) != -1)
                            builder.append((char) c);
                        mPath = path;
                        return builder.toString();
                    }finally {
                        reader.close();
                    }
                }catch(IOException e){
                    Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_LONG);
                }
                return null;
            }

            @Override
            protected void onPostExecute(String s) {
                if (s != null) {
                    mEdit.setText(s);
                    mChanged = false;
                }
            }
        }.execute(path);
    }
}
