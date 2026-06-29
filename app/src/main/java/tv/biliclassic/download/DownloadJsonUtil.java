package tv.biliclassic.download;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

public class DownloadJsonUtil {

    public static String writeToString(JSONObject obj) {
        return obj.toString();
    }

    public static JSONObject readFromString(String text) throws JSONException {
        Object oRoot = new JSONTokener(text).nextValue();
        if (!(oRoot instanceof JSONObject)) {
            throw new JSONException("not valid JSONObject");
        }
        return (JSONObject) oRoot;
    }

    public static void writeToFile(JSONObject obj, File file) throws Exception {
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory()) {
            parent.mkdirs();
        }
        OutputStreamWriter writer = null;
        try {
            writer = new OutputStreamWriter(new FileOutputStream(file), "UTF-8");
            writer.write(obj.toString());
            writer.flush();
        } finally {
            if (writer != null) {
                try { writer.close(); } catch (Exception e) { }
            }
        }
    }

    public static JSONObject readFromFile(File file) throws Exception {
        if (!file.isFile()) {
            return null;
        }
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return readFromString(sb.toString());
        } finally {
            if (reader != null) {
                try { reader.close(); } catch (Exception e) { }
            }
        }
    }
}
