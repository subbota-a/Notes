package ru.yandex.subbota_job.notes;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Environment;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckedTextView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.Manifest;

/**
 * Created by subbota on 22.03.2016.
 */
public class NotesListAdapter extends RecyclerView.Adapter<NotesListAdapter.ViewHolder> {
    private final Context mContext;
    private Set<Integer> mSelected;

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
    private ArrayList<NoteDescription> mDataSource;
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
        mSelected.clear();
        notifyDataSetChanged();
    }

    public static File getDirectory(Context context)
    {
//        if (PackageManager.PERMISSION_DENIED == ContextCompat.checkSelfPermission(context, "android.permission.WRITE_EXTERNAL_STORAGE")){
//            return null;
//        }
//        String state = Environment.getExternalStorageState();
//        if (state.equals(Environment.MEDIA_MOUNTED)){
//            //File dir = new File(Environment.getExternalStoragePublicDirectory("Documents"), NotesListActivity.NotesDirectory);
//            File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), NotesListActivity.NotesDirectory);
//            if (!dir.isDirectory())
//                dir.mkdirs();
//            if (dir.isDirectory())
//                return dir;
//        }
//        // Ничего не получается...
        return context.getFilesDir();
    }
    public void updateAsync(){
        new AsyncTask<Void, Void, ArrayList<NoteDescription>>() {
            @Override
            protected ArrayList<NoteDescription> doInBackground(Void... params) {
                File file = getDirectory(mContext);
                File[] fileNames = file.listFiles();
                Arrays.sort(fileNames, new Comparator<File>() {
                    // make recent first
                    @Override
                    public int compare(File lhs, File rhs) {
                        return (int)(rhs.lastModified()-lhs.lastModified());
                    }
                });
                ArrayList<NoteDescription> ret = new ArrayList<NoteDescription>(fileNames.length);
                for(final File f: fileNames) {
                    NoteDescription item = new NoteDescription();
                    item.mFileName = f;
                    ret.add(item);
                }
                return ret;
            }

            @Override
            protected void onPostExecute(ArrayList<NoteDescription> newDataSource) {
                onDataSourceLoaded(newDataSource);
            }
        }.execute();

    }

    private void onDataSourceLoaded(ArrayList<NoteDescription> newDataSource) {
        mDataSource = newDataSource;
        notifyDataSetChanged();
        new AsyncTask<Void, Integer, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                for(int i=0; i<mDataSource.size(); ++i){
                    NoteDescription item = mDataSource.get(i);
                    try {
                        BufferedReader reader = new BufferedReader(new FileReader(item.mFileName));
                        item.mPreviewText = reader.readLine();
                    }catch(IOException e) {
                        item.mPreviewText = e.getMessage();
                    }
                    publishProgress(i);
                }
                return null;
            }

            @Override
            protected void onProgressUpdate(Integer... values) {
                notifyItemChanged(values[0]);
            }
        }.execute();
    }

    @Override
    public int getItemCount() {
        return mDataSource == null ? 0 : mDataSource.size();
    }
}
