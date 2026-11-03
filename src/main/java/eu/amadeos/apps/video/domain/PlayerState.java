package eu.amadeos.apps.video.domain;

import java.util.List;
import java.util.Objects;

// --8<-- [start:state]
/**
 * What the player is doing, as the UI shows it. Immutable; the player publishes a new state on every change.
 *
 * @param title    media title (from the file's tags, the URL or the DVD), empty when idle
 * @param position seconds from the start, -1 if unknown
 * @param duration seconds, -1 if unknown (live streams)
 * @param chapter  current chapter, 0-based; -1 without chapters
 * @param error    why playback failed, empty otherwise
 * @param discTitle  index in {@code discTitles} of the title playing; -1 if unknown or without a DVD
 * @param discTitles the DVD's titles, empty without a DVD
 */
public record PlayerState(Status status, String title, double position, double duration, boolean paused,
                          int chapter, int chapters, String error, int discTitle, List<DiscTitle> discTitles) {
    public enum Status {
        /** Nothing loaded. */
        IDLE,
        /** Opening a file, connecting to a stream, reading a disc. */
        LOADING,
        PLAYING,
        /** At the end; the last picture stays. */
        ENDED,
        FAILED
    }

    public static final PlayerState IDLE = new PlayerState(Status.IDLE, "", -1, -1, false, -1, 0, "", -1, List.of());
    // --8<-- [end:state]

    public PlayerState {
        Objects.requireNonNull(status, "status");
        title = title == null ? "" : title;
        error = error == null ? "" : error;
        discTitles = discTitles == null ? List.of() : List.copyOf(discTitles);
    }

    public boolean hasMedia() {
        return status == Status.PLAYING || status == Status.ENDED;
    }

    public PlayerState withStatus(Status s) {
        return new PlayerState(s, title, position, duration, paused, chapter, chapters, s == Status.FAILED ? error : "",
                discTitle, discTitles);
    }

    public PlayerState withTitle(String t) {
        return new PlayerState(status, t, position, duration, paused, chapter, chapters, error, discTitle, discTitles);
    }

    public PlayerState withPosition(double p) {
        return new PlayerState(status, title, p, duration, paused, chapter, chapters, error, discTitle, discTitles);
    }

    public PlayerState withDuration(double d) {
        return new PlayerState(status, title, position, d, paused, chapter, chapters, error, discTitle, discTitles);
    }

    public PlayerState withPaused(boolean p) {
        return new PlayerState(status, title, position, duration, p, chapter, chapters, error, discTitle, discTitles);
    }

    public PlayerState withChapter(int c) {
        return new PlayerState(status, title, position, duration, paused, c, chapters, error, discTitle, discTitles);
    }

    public PlayerState withChapters(int c) {
        return new PlayerState(status, title, position, duration, paused, chapter, c, error, discTitle, discTitles);
    }

    public PlayerState withDiscTitle(int t) {
        return new PlayerState(status, title, position, duration, paused, chapter, chapters, error, t, discTitles);
    }

    public PlayerState withDiscTitles(List<DiscTitle> titles) {
        return new PlayerState(status, title, position, duration, paused, chapter, chapters, error, discTitle, titles);
    }

    /**
     * The DVD's main film: index of the longest title; -1 without titles. mpv starts {@code dvd://} with it.
     */
    public int mainDiscTitle() {
        int best = -1;
        for (int i = 0; i < discTitles.size(); i++) {
            if (best < 0 || discTitles.get(i).length() > discTitles.get(best).length()) best = i;
        }
        return best;
    }

    public PlayerState failed(String why) {
        return new PlayerState(Status.FAILED, title, -1, -1, false, -1, 0, why, -1, List.of());
    }

    /** A new file is being opened: everything about the previous one is forgotten. */
    public static PlayerState loading(String title) {
        return new PlayerState(Status.LOADING, title, -1, -1, false, -1, 0, "", -1, List.of());
    }
}
