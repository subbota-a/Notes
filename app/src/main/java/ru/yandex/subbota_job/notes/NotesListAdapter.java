package ru.yandex.subbota_job.notes;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.FileObserver;
import android.os.Handler;
import android.support.design.widget.Snackbar;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by subbota on 22.03.2016.
 */
public class NotesListAdapter extends RecyclerView.Adapter<NotesListAdapter.ViewHolder> {
    private final Context mContext;
    private Set<Integer> mSelected;
    private AsyncTask<String, Void, ArrayList<NoteDescription>> mLoaderTask;
    private String mFilterString;
    private FileObserver mFileObserver;
    private Handler mUpdateAsync = new Handler();
    private boolean mUpdatePending = false;

    private int mLockUpdate = 0;

    public void beginUpdate(){
        ++mLockUpdate;
    }

    public void endUpdate(){
        if (--mLockUpdate == 0 && mUpdatePending)
            updateAsync(mFilterString);
    }



    static class ViewHolder extends RecyclerView.ViewHolder{
        public static ViewHolder create(ViewGroup parent)
        {
            return new ViewHolder(
                    LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.notes_item, parent, false));
        }
        private TextView mItem;
        public ViewHolder(View itemView) {
            super(itemView);
            mItem = (TextView)itemView.findViewById(android.R.id.text1);
        }
        public void setText(String text){
            mItem.setText(text);
        }
        public void setSelected(boolean selected){ itemView.setSelected(selected);}
    }
    private ArrayList<NoteDescription> mDataSource = new ArrayList<NoteDescription>();
    public NotesListAdapter(Context context)
    {
        mContext = context;
        mSelected = new HashSet<Integer>();
        File dir = getOrAddDirectory(context);
        if (dir != null) {
            mFileObserver = new FileObserver(dir.getPath(), FileObserver.DELETE|FileObserver.MODIFY) {
                @Override
                public void onEvent(int i, String s) {
                    mUpdateAsync.post(new Runnable() {
                        @Override
                        public void run() {
                            updateAsync(mFilterString);
                        }
                    });
                }
            };
            mFileObserver.startWatching();
        }
    }
    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return ViewHolder.create(parent);
    }

    NoteDescription getItem(int position)
    {
        return mDataSource.get(position);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        NoteDescription item = mDataSource.get(position);
        if (item.mPreviewText != null)
            holder.setText(item.mPreviewText);
        holder.setSelected(mSelected.contains(position));
    }
    public boolean toggleSelection(int position)
    {
        boolean isSelected = mSelected.add(position);
        if (!isSelected)
            mSelected.remove(position);
        notifyItemChanged(position);
        return isSelected;
    }
    public int getSelectionCount()
    {
        return mSelected.size();
    }
    public void clearAllSelection()
    {
        if (mSelected.size() > 0) {
            mSelected.clear();
            notifyDataSetChanged();
        }
    }

    public static File getOrAddDirectory(Context context)
    {
        String state = Environment.getExternalStorageState();
        if (state.equals(Environment.MEDIA_MOUNTED)){
            File dir = new File(Environment.getExternalStoragePublicDirectory("Documents"), NotesListActivity.NotesDirectory);
            dir.mkdirs();
            return dir.isDirectory() ? dir : null;
        }else
            return context.getApplicationContext().getFilesDir();
    }
    public void deleteSelectedAsync(final View coordinatorLayout) {
        Integer[] copy = new Integer[mSelected.size()];
        mSelected.toArray(copy);
        mSelected.clear();
        Arrays.sort(copy);
        final NoteDescription[] descr = new NoteDescription[copy.length];
        for(int i=copy.length-1; i>=0; --i) {
            descr[i] = mDataSource.get(copy[i]);
            mDataSource.remove((int)copy[i]);
        }
        notifyDataSetChanged();
        String message = coordinatorLayout.getResources().getString(R.string.removed_notes_message, copy.length);
        Snackbar snackbar = Snackbar.make(coordinatorLayout, message, Snackbar.LENGTH_LONG);
        snackbar.setAction(R.string.undo, new View.OnClickListener() {
            @Override
            public void onClick(View v) {}
        });
        snackbar.setCallback(new Snackbar.Callback() {
            @Override
            public void onDismissed(Snackbar snackbar, int event) {
                super.onDismissed(snackbar, event);
                if (event != Snackbar.Callback.DISMISS_EVENT_ACTION) {
                    new AsyncTask<Context, Void, Void>() {
                        @Override
                        protected Void doInBackground(Context... params) {
                            for (NoteDescription nd : descr) {
                                nd.mFileName.delete();
                                SyncService.onFileChanged(params[0], nd.mFileName.getPath());
                            }
                            return null;
                        }
                    }.execute(coordinatorLayout.getContext());
                } else {
                    updateAsync(mFilterString);
                }
            }
        });
        snackbar.show();
    }

    @Override
    public int getItemCount() {
        return mDataSource == null ? 0 : mDataSource.size();
    }

    public void updateAsync(String substring)
    {
        mFilterString = substring;
        mUpdatePending = true;
        if (mLockUpdate > 0)
            return;
        mUpdatePending = false;
        if (mLoaderTask != null){
            mLoaderTask.cancel(false);
            mLoaderTask = null;
        }
        mLoaderTask = new AsyncTask<String, Void, ArrayList<NoteDescription>>() {
            @Override
            protected ArrayList<NoteDescription> doInBackground(String... params) {
                ArrayList<NoteDescription> ret = new ArrayList<NoteDescription>();
                String filterString = TextUtils.isEmpty(params[0]) ? null : params[0].toLowerCase();
                File file = getOrAddDirectory(mContext);
                if (file == null)
                    return null;
                File[] files = file.listFiles();
                Arrays.sort(files, new Comparator<File>() {
                    // make recent first
                    @Override
                    public int compare(File lhs, File rhs) {
                        long dif = rhs.lastModified()-lhs.lastModified();
                        return dif < 0 ? -1 : dif > 0 ? 1 : 0;
                    }
                });
                for(File f: files){
                    if (isCancelled())
                        break;
                    try {
                        String content = UtfFile.ReadAll(f.getPath());
                        boolean populate;
                        if (TextUtils.isEmpty(filterString))
                            populate = true;
                        else {
                            String lowerCase = content.toLowerCase();
                            populate = lowerCase.contains(filterString);
                        }
                        if (populate) {
                            NoteDescription item = new NoteDescription();
                            String[] pair = UtfFile.Split(content);
                            item.mPreviewText = pair[0]==null ? UtfFile.getLine(pair[1]) : pair[0];
                            item.mFileName = f;
                            ret.add(item);
                        }
                    }catch(IOException e) {
                    }
                }
                return ret;
            }
            @Override
            protected void onPostExecute(ArrayList<NoteDescription> noteDescriptions) {
                mergeTo(noteDescriptions);
            }
        }.execute(substring);
    }

    private void mergeTo(ArrayList<NoteDescription> newDataSource) {
        mDataSource = newDataSource;
        notifyDataSetChanged();
    }
}
