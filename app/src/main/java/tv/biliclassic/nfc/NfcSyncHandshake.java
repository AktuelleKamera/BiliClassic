package tv.biliclassic.nfc;

public final class NfcSyncHandshake {

    public static final String AID = "F0010203040506";
    private static volatile String sToken;
    private static volatile String sData;

    private NfcSyncHandshake() {
    }

    public static void setToken(String token) {
        sToken = token;
    }

    public static String getToken() {
        return sToken;
    }

    public static void setData(String data) {
        sData = data;
    }

    public static String getData() {
        return sData;
    }

    public static void clear() {
        sToken = null;
        sData = null;
    }
}
