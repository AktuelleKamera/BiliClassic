package tv.biliclassic.download;

import java.io.File;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import tv.biliclassic.util.LocalJsonCrypto;

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
        LocalJsonCrypto.writeText(file, obj.toString());
    }

    public static JSONObject readFromFile(File file) throws Exception {
        if (file == null || !file.isFile()) {
            return null;
        }
        String text = LocalJsonCrypto.readText(file);
        if (text == null || text.length() == 0) return null;
        return readFromString(text);
    }
}
