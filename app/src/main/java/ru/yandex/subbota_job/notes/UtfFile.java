package ru.yandex.subbota_job.notes;

import android.text.TextUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;

public final class UtfFile {
    static final String beginTag = "<title>";
    static final String endTag = "</title>";
    public static String[] Split(String fileContent)
    {
        String[] ret = new String[2];
        int endTagPos = fileContent.indexOf(endTag);
        if (fileContent.startsWith(beginTag) && endTagPos >=0){
            ret[0] = fileContent.substring(beginTag.length(), endTagPos);
            ret[1] = fileContent.substring(endTagPos+endTag.length());
        }else
            ret[1] = fileContent;
        return ret;
    }
    public static String Join(String title, String content)
    {
        StringBuilder sb = new StringBuilder();
        if (!TextUtils.isEmpty(title))
            sb.append(beginTag).append(title).append(endTag);
        sb.append(content);
        return sb.toString();
    }
    public static String ReadAll(String path) throws IOException {
        FileInputStream inputStream = new FileInputStream(path);
        try {
            File f = new File(path);
            byte[] data = new byte[(int)f.length()];
            int count = inputStream.read(data);
            int start;
            String encoding;
            if (count >= 3 && data[0]==(byte)0xef && data[1]==(byte)0xbb && data[2]==(byte)0xbf) {
                encoding = "UTF-8";
                start = 3;
            }else if (count >= 2 && data[0]==(byte)0xfe && data[1]==(byte)0xff) {
                encoding = "UTF-16BE";
                start = 2;
            }else if (count >= 2 && data[0]==(byte)0xff && data[1]==(byte)0xfe) {
                encoding = "UTF-16LE";
                start = 2;
            }else {
                encoding = "windows-1251";
                start = 0;
            }
            return new String(data, start, count-start, encoding);
        }finally {
            inputStream.close();
        }
    }
    public static String getLine(String text)
    {
        int n = text.indexOf('\n');
        int r = text.indexOf('\r');
        if (n == -1 && r == -1)
            return text;
        if (n == -1)
            n = Integer.MAX_VALUE;
        if (r == -1)
            r = Integer.MAX_VALUE;
        return text.substring(0, Math.min(n,r));
    }
    public static void Write(String path, String data) throws IOException {
        FileOutputStream outputStream = new FileOutputStream(path);
        outputStream.write(new byte[]{(byte) 0xef, (byte) 0xbb, (byte) 0xbf});
        try {
            outputStream.write(data.getBytes("UTF-8"));
        }finally {
            outputStream.close();
        }
    }
}
