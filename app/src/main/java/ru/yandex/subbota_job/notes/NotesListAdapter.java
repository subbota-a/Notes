package ru.yandex.subbota_job.notes;

import android.os.AsyncTask;
import android.os.Environment;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.CheckedTextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;

/**
 * Created by subbota on 22.03.2016.
 */
public class NotesListAdapter extends RecyclerView.Adapter<NotesListAdapter.ViewHolder> {
    static class ViewHolder extends RecyclerView.ViewHolder{
        CheckedTextView mView;
        public ViewHolder(CheckedTextView itemView) {
            super(itemView);
            mView = itemView;
        }
    }
    ArrayList<NoteDescription> mDataSource;
    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        CheckedTextView view = (CheckedTextView)LayoutInflater.from(parent.getContext())
                .inflate(android.R.layout.simple_list_item_multiple_choice, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        holder.mView.setText(mDataSource.get(position).toString());
    }

    public void updateAsync(){
        new AsyncTask<Void, Void, ArrayList<NoteDescription>>() {
            @Override
            protected ArrayList<NoteDescription> doInBackground(Void... params) {
                File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), NotesListActivity.NotesDirectory);
                file.mkdirs();
                File[] fileNames = file.listFiles();
                ArrayList<NoteDescription> ret = new ArrayList<NoteDescription>(fileNames.length);
                for(final File f: fileNames) {
                    NoteDescription item = new NoteDescription();
                    item.mFileName = f.toURI();
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
                        BufferedReader reader = new BufferedReader(new FileReader(item.mFileName.getPath()));
                        item.mPreviewText = reader.readLine();
                        publishProgress(i);
                    }catch(IOException e) {
                    }
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
