package eu.amadeos.video.ui.main;

import java.util.Locale;

/** Playing times as on a video recorder: {@code 04:07}, {@code 1:52:10}; {@code --:--} if unknown. */
final class TimeFormat {
    private TimeFormat() {
    }

    static String clock(double seconds) {
        if (seconds < 0 || Double.isNaN(seconds)) return "--:--";
        long s = (long) seconds;
        return s >= 3600 ? String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
                : String.format(Locale.ROOT, "%02d:%02d", s / 60, s % 60);
    }

    /** {@code 04:07 / 1:52:10}; live streams without a duration show the position only. */
    static String position(double position, double duration) {
        return duration > 0 ? clock(position) + " / " + clock(duration) : clock(position);
    }
}
