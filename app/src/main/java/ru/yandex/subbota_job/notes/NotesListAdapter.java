package ru.yandex.subbota_job.notes;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Environment;
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
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by subbota on 22.03.2016.
 */
public class NotesListAdapter extends RecyclerView.Adapter<NotesListAdapter.ViewHolder> {
    private final Context mContext;
    private Set<Integer> mSelected;
    private AsyncTask<String, NoteDescription, Void> mLoaderTask;
    private String mFilterString;


    static class ViewHolder extends RecyclerView.ViewHolder{
        public static ViewHolder create(ViewGroup parent)
        {
            return new ViewHolder(
                    LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.notes_item, parent, false));
        }
        public ViewHolder(View itemView) {
            super(itemView);
        }
        public void setText(String text){
            ((TextView)itemView).setText(text);
        }
        public void setSelected(boolean selected){ itemView.setSelected(selected);}
    }
    private ArrayList<NoteDescription> mDataSource = new ArrayList<NoteDescription>();
    public NotesListAdapter(Context context)
    {
        mContext = context;
        mSelected = new HashSet<Integer>();
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
        if (mLoaderTask != null){
            mLoaderTask.cancel(false);
            mLoaderTask = null;
        }
        mDataSource.clear();
        notifyDataSetChanged();
        mLoaderTask = new AsyncTask<String, NoteDescription, Void>() {
            @Override
            protected Void doInBackground(String... params) {
                String filterString = TextUtils.isEmpty(params[0]) ? null : params[0].toLowerCase();
                File file = getOrAddDirectory(mContext);
                if (file == null)
                    return null;
                File[] files = file.listFiles();
                Arrays.sort(files, new Comparator<File>() {
                    // make recent first
                    @Override
                    public int compare(File lhs, File rhs) {
                        return (int)(rhs.lastModified()-lhs.lastModified());
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
                            publishProgress(item);
                        }
                    }catch(IOException e) {
                    }
                }
                return null;
            }
            @Override
            protected void onProgressUpdate(NoteDescription... values) {
                if (isCancelled())
                    return;
                mDataSource.add(values[0]);
                notifyItemInserted(mDataSource.size()-1);
            }
        }.execute(substring);
    }
}
