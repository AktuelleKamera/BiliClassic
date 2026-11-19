package tv.biliclassic.player;

public final class PlayerTimeUtil {

    private PlayerTimeUtil() {
    }

    public static String format(int ms) {
        if (ms < 0) ms = 0;
        int totalSeconds = ms / 1000;
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        int seconds = totalSeconds % 60;
        if (hours > 0) {
            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format("%02d:%02d", minutes, seconds);
    }
}
