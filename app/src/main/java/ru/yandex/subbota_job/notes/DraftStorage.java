package ru.yandex.subbota_job.notes;

import android.content.Context;
import android.text.TextUtils;

/**
 * Created by subbota on 27.09.2016.
 */

class DraftStorage {
    private Context mContext;
    private final String keyPath = this.getClass().toString() + "path";
    private final String keyContent = this.getClass().toString() + "content";
    private final String preferenceName = this.getClass().toString();
    public DraftStorage(Context context)
    {
        mContext = context;
    }
    public String getDraftPath()
    {
        return mContext.getSharedPreferences(preferenceName, Context.MODE_PRIVATE).getString(keyPath, "");
    }
    public String getDraftContent()
    {
        return mContext.getSharedPreferences(preferenceName, Context.MODE_PRIVATE).getString(keyContent, "");
    }
    public void saveDraft(String path, String content)
    {
        mContext.getSharedPreferences(preferenceName, Context.MODE_PRIVATE).edit()
                .putString(keyPath, path).putString(keyContent, content).apply();
    }
    public void clearDraft()
    {
        mContext.getSharedPreferences(preferenceName, Context.MODE_PRIVATE).edit().clear().apply();
    }
}
