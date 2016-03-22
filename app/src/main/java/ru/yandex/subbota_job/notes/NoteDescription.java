package ru.yandex.subbota_job.notes;

import java.net.URI;

/**
 * Created by subbota on 22.03.2016.
 */
public class NoteDescription {
    URI mFileName;
    String mPreviewText;
    // and so on

    @Override
    public String toString() {
        return mPreviewText.isEmpty() ? mFileName.getPath() : mPreviewText;
    }
}
