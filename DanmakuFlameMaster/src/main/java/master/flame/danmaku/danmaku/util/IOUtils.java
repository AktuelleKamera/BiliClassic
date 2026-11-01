package master.flame.danmaku.danmaku.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;

/**
 * Created by MoiTempete.
 */
public class IOUtils {
    public static String getString(InputStream in){
        byte[] data = getBytes(in);
        if (data == null) return null;
        try {
            return new String(data, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return new String(data);
        }
    }
    public static byte[] getBytes(InputStream in){

        try {
            int initialSize = in.available();
            if (initialSize <= 8192) initialSize = 8192;
            ByteArrayOutputStream baos = new ByteArrayOutputStream(initialSize);
            byte[] buffer = new byte[8192];
            int len = 0;
            while ((len = in.read(buffer)) != -1)
                baos.write(buffer, 0, len);
            in.close();
            return baos.toByteArray();
        } catch (IOException e) {
            return null;
        }
    }
    public static void closeQuietly(InputStream in){
    	try {
    		if(in != null)
    			in.close();
		} catch (IOException ignore) {}
    }
    public static void closeQuietly(OutputStream out){
    	try {
    		if(out != null)
    			out.close();
    	} catch (IOException ignore) {}
    }
}
