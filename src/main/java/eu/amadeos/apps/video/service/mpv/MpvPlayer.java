package eu.amadeos.apps.video.service.mpv;

import com.google.inject.Singleton;
import eu.amadeos.apps.video.domain.MediaSource;
import eu.amadeos.apps.video.domain.PlayerState;
import eu.amadeos.commons.mpv.LibMpv;
import eu.amadeos.commons.mpv.Mpv;
import eu.amadeos.commons.mpv.MpvEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * The player of Video: amade-commons' {@link Mpv} plays into a native window of the UI ({@link #attach}),
 * and this class keeps the state of a file, stream or DVD on top. Commands can be sent from any thread;
 * state changes are published to the listeners on the player's event thread.
 *
 * <p>mpv options can be overridden with system properties {@code mpv.<option>}, e.g. {@code -Dmpv.ao=null}.
 */
@Singleton
public final class MpvPlayer implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(MpvPlayer.class);
    // reply_userdata of the observed properties
    private static final long TIME = 1, DURATION = 2, PAUSE = 3, CHAPTER = 4, CHAPTERS = 5, EOF = 6, TITLE = 7;

    private final Mpv mpv = new Mpv();
    private final List<Consumer<PlayerState>> listeners = new CopyOnWriteArrayList<>();
    /** Guards state updates; not the player's monitor, which close() holds while joining the event thread. */
    private final Object stateLock = new Object();
    private volatile PlayerState state = PlayerState.IDLE;

    /** False if libmpv is not installed; nothing can be played then. */
    public boolean isAvailable() {
        return mpv.isAvailable();
    }

    public String unavailableReason() {
        return mpv.unavailableReason();
    }

    /** libmpv's client API version, e.g. "2.5". */
    public String apiVersion() {
        return mpv.apiVersion();
    }

    /**
     * Creates the player inside the given native window (X11 window id, HWND). The window must exist,
     * i.e. its component must be displayable.
     */
    public synchronized void attach(long windowId) {
        Map<String, String> options = new LinkedHashMap<>();
        options.put("keep-open", "yes");
        options.put("idle", "yes");
        options.put("input-default-bindings", "no");
        options.put("input-vo-keyboard", "no");
        options.put("osd-level", "1");
        options.put("ytdl", "yes");
        mpv.attach(windowId, options, this::handle);
        mpv.observe(TIME, "time-pos", LibMpv.FORMAT_DOUBLE);
        mpv.observe(DURATION, "duration", LibMpv.FORMAT_DOUBLE);
        mpv.observe(PAUSE, "pause", LibMpv.FORMAT_FLAG);
        mpv.observe(CHAPTER, "chapter", LibMpv.FORMAT_INT64);
        mpv.observe(CHAPTERS, "chapters", LibMpv.FORMAT_INT64);
        mpv.observe(EOF, "eof-reached", LibMpv.FORMAT_FLAG);
        mpv.observe(TITLE, "media-title", LibMpv.FORMAT_STRING);
    }

    public PlayerState state() {
        return state;
    }

    /** Called on the player's event thread with every new state. */
    public void addListener(Consumer<PlayerState> listener) {
        listeners.add(listener);
    }

    public void open(MediaSource source) {
        if (!mpv.isAttached()) return;
        if (source instanceof MediaSource.Dvd dvd) mpv.setProperty("dvd-device", dvd.device());
        update(s -> PlayerState.loading(source.displayName()));
        command("loadfile", source.target(), "replace");
        command("set", "pause", "no");
    }

    /** Pauses or resumes; at the end, starts again from the beginning. */
    public void togglePause() {
        if (state.status() == PlayerState.Status.ENDED) {
            command("seek", "0", "absolute");
            command("set", "pause", "no");
        } else {
            command("cycle", "pause");
        }
    }

    public void seekBy(double seconds) {
        command("seek", Double.toString(seconds), "relative");
    }

    public void seekTo(double seconds) {
        command("seek", Double.toString(Math.max(0, seconds)), "absolute");
    }

    /** Jumps by whole chapters, e.g. -1 / +1. */
    public void chapter(int delta) {
        command("add", "chapter", Integer.toString(delta));
    }

    public void stop() {
        command("stop");
    }

    @Override
    public synchronized void close() {
        mpv.close();
    }

    // ---- events ----------------------------------------------------------------------------------

    // --8<-- [start:handle]
    void handle(MpvEvent event) {
        switch (event) {
            case MpvEvent.StartFile s -> update(st -> st.status() == PlayerState.Status.LOADING ? st
                    : PlayerState.loading(st.title()));
            case MpvEvent.FileLoaded f -> update(st -> st.withStatus(PlayerState.Status.PLAYING));
            case MpvEvent.EndFile e -> update(st -> switch (e.reason()) {
                case MpvEvent.END_ERROR -> st.failed(mpv.errorString(e.error()));
                case MpvEvent.END_EOF -> st.withStatus(PlayerState.Status.ENDED);
                case MpvEvent.END_STOP, MpvEvent.END_QUIT -> PlayerState.IDLE;
                default -> st;
            });
            case MpvEvent.PropertyChange p -> update(st -> propertyChanged(st, p));
            default -> LOG.debug("mpv event {}", event);
        }
    }
    // --8<-- [end:handle]

    static PlayerState propertyChanged(PlayerState st, MpvEvent.PropertyChange p) {
        Object v = p.value();
        if (p.id() == TIME) return st.withPosition(v instanceof Double d ? d : -1);
        if (p.id() == DURATION) return st.withDuration(v instanceof Double d ? d : -1);
        if (p.id() == PAUSE) return st.withPaused(Boolean.TRUE.equals(v));
        if (p.id() == CHAPTER) return st.withChapter(v instanceof Long l ? l.intValue() : -1);
        if (p.id() == CHAPTERS) return st.withChapters(v instanceof Long l ? l.intValue() : 0);
        if (p.id() == TITLE && v instanceof String t && !t.isBlank()) return st.withTitle(t);
        if (p.id() == EOF) {
            // keep-open: at the end mpv pauses on the last picture instead of ending the file
            if (Boolean.TRUE.equals(v) && st.status() == PlayerState.Status.PLAYING) return st.withStatus(PlayerState.Status.ENDED);
            if (Boolean.FALSE.equals(v) && st.status() == PlayerState.Status.ENDED) return st.withStatus(PlayerState.Status.PLAYING);
        }
        return st;
    }

    // --8<-- [start:update]
    private void update(UnaryOperator<PlayerState> change) {
        synchronized (stateLock) {
            PlayerState next = change.apply(state);
            if (next.equals(state)) return;
            state = next;
            for (Consumer<PlayerState> l : listeners) {
                try {
                    l.accept(next);
                } catch (RuntimeException e) {
                    LOG.warn("Player listener failed", e);
                }
            }
        }
    }
    // --8<-- [end:update]

    // ---- helpers ---------------------------------------------------------------------------------

    private void command(String... args) {
        mpv.command(args);
    }
}
