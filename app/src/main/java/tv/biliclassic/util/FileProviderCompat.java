package tv.biliclassic.util;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.res.XmlResourceParser;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.os.ParcelFileDescriptor;

import org.xmlpull.v1.XmlPullParser;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashMap;

import tv.biliclassic.R;

public class FileProviderCompat extends ContentProvider {

    private static final String AUTHORITY = "tv.biliclassic.fileprovider";
    private static HashMap<String, File> sRoots;
    private static boolean sRootsInitialized;

    private static void ensureRoots(Context context) {
        if (sRootsInitialized) return;
        sRootsInitialized = true;
        sRoots = new HashMap<String, File>();
        try {
            XmlResourceParser parser = context.getResources().getXml(R.xml.file_paths);
            int eventType = parser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    String tag = parser.getName();
                    String path = parser.getAttributeValue(null, "path");
                    if ("external-path".equals(tag)) {
                        File root = Environment.getExternalStorageDirectory();
                        if (path != null && path.length() > 0) {
                            root = new File(root, path);
                        }
                        sRoots.put("external-path", root);
                    } else if ("files-path".equals(tag)) {
                        File root = context.getFilesDir();
                        if (path != null && path.length() > 0) {
                            root = new File(root, path);
                        }
                        sRoots.put("files-path", root);
                    } else if ("cache-path".equals(tag)) {
                        File root = context.getCacheDir();
                        if (path != null && path.length() > 0) {
                            root = new File(root, path);
                        }
                        sRoots.put("cache-path", root);
                    }
                }
                eventType = parser.next();
            }
            parser.close();
        } catch (Exception e) {
        }
    }

    public static Uri getUriForFile(Context context, File file) {
        ensureRoots(context);
        String filePath = file.getAbsolutePath();
        for (HashMap.Entry<String, File> entry : sRoots.entrySet()) {
            String rootPath = entry.getValue().getAbsolutePath();
            if (filePath.startsWith(rootPath)) {
                String relativePath = filePath.substring(rootPath.length());
                while (relativePath.startsWith("/")) {
                    relativePath = relativePath.substring(1);
                }
                String encoded = Uri.encode(entry.getKey()) + "/" + Uri.encode(relativePath, "/");
                return Uri.parse("content://" + AUTHORITY + "/" + encoded);
            }
        }
        throw new IllegalArgumentException("File not within configured roots: " + filePath);
    }

    @Override
    public boolean onCreate() {
        ensureRoots(getContext());
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File file = resolveFile(uri);
        if (file == null) {
            throw new FileNotFoundException("URI not found: " + uri);
        }
        int fileMode;
        if ("r".equals(mode)) {
            fileMode = ParcelFileDescriptor.MODE_READ_ONLY;
        } else if ("w".equals(mode) || "wt".equals(mode)) {
            fileMode = ParcelFileDescriptor.MODE_WRITE_ONLY;
        } else if ("rw".equals(mode) || "rwt".equals(mode)) {
            fileMode = ParcelFileDescriptor.MODE_READ_WRITE;
        } else {
            fileMode = ParcelFileDescriptor.MODE_READ_ONLY;
        }
        return ParcelFileDescriptor.open(file, fileMode);
    }

    private File resolveFile(Uri uri) {
        String uriPath = uri.getPath();
        if (uriPath == null) return null;
        while (uriPath.startsWith("/")) {
            uriPath = uriPath.substring(1);
        }
        int slash = uriPath.indexOf('/');
        if (slash < 0) return null;
        String tag = Uri.decode(uriPath.substring(0, slash));
        String subPath = Uri.decode(uriPath.substring(slash + 1));
        File root = sRoots.get(tag);
        if (root == null) return null;
        return new File(root, subPath);
    }

    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) { return null; }
}
