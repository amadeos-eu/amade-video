package eu.amadeos.apps.video.domain;

import java.util.Objects;

/**
 * What the player is doing, as the UI shows it. Immutable; the player publishes a new state on every change.
 *
 * @param title    media title (from the file's tags, the URL or the DVD), empty when idle
 * @param position seconds from the start, -1 if unknown
 * @param duration seconds, -1 if unknown (live streams)
 * @param chapter  current chapter, 0-based; -1 without chapters
 * @param error    why playback failed, empty otherwise
 */
public record PlayerState(Status status, String title, double position, double duration, boolean paused,
                          int chapter, int chapters, String error) {
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

    public static final PlayerState IDLE = new PlayerState(Status.IDLE, "", -1, -1, false, -1, 0, "");

    public PlayerState {
        Objects.requireNonNull(status, "status");
        title = title == null ? "" : title;
        error = error == null ? "" : error;
    }

    public boolean hasMedia() {
        return status == Status.PLAYING || status == Status.ENDED;
    }

    public PlayerState withStatus(Status s) {
        return new PlayerState(s, title, position, duration, paused, chapter, chapters, s == Status.FAILED ? error : "");
    }

    public PlayerState withTitle(String t) {
        return new PlayerState(status, t, position, duration, paused, chapter, chapters, error);
    }

    public PlayerState withPosition(double p) {
        return new PlayerState(status, title, p, duration, paused, chapter, chapters, error);
    }

    public PlayerState withDuration(double d) {
        return new PlayerState(status, title, position, d, paused, chapter, chapters, error);
    }

    public PlayerState withPaused(boolean p) {
        return new PlayerState(status, title, position, duration, p, chapter, chapters, error);
    }

    public PlayerState withChapter(int c) {
        return new PlayerState(status, title, position, duration, paused, c, chapters, error);
    }

    public PlayerState withChapters(int c) {
        return new PlayerState(status, title, position, duration, paused, chapter, c, error);
    }

    public PlayerState failed(String why) {
        return new PlayerState(Status.FAILED, title, -1, -1, false, -1, 0, why);
    }

    /** A new file is being opened: everything about the previous one is forgotten. */
    public static PlayerState loading(String title) {
        return new PlayerState(Status.LOADING, title, -1, -1, false, -1, 0, "");
    }
}
