package eu.amadeos.apps.video.service.mpv;

import com.google.inject.Singleton;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import eu.amadeos.apps.video.domain.MediaSource;
import eu.amadeos.apps.video.domain.PlayerState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * The player: libmpv rendering into a native window of the UI ({@link #attach}), decoding and playing the
 * sound itself. Commands can be sent from any thread; state changes are published to the listeners on the
 * player's event thread.
 *
 * <p>mpv options can be overridden with system properties {@code mpv.<option>}, e.g. {@code -Dmpv.ao=null}.
 */
@Singleton
public final class MpvPlayer implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(MpvPlayer.class);
    private static final String OVERRIDE_PREFIX = "mpv.";
    // reply_userdata of the observed properties
    private static final long TIME = 1, DURATION = 2, PAUSE = 3, CHAPTER = 4, CHAPTERS = 5, EOF = 6, TITLE = 7;

    private final LibMpv mpv;
    private final String unavailableReason;
    private final List<Consumer<PlayerState>> listeners = new CopyOnWriteArrayList<>();
    /** Guards state updates; not the player's monitor, which close() holds while joining the event thread. */
    private final Object stateLock = new Object();
    private volatile PlayerState state = PlayerState.IDLE;
    private volatile Pointer handle;
    private volatile boolean running;
    private volatile boolean fastProfile;
    private Thread events;

    // --8<-- [start:load]
    public MpvPlayer() {
        LibMpv lib = null;
        StringBuilder why = new StringBuilder();
        for (String name : LibMpv.NAMES) {
            try {
                lib = Native.load(name, LibMpv.class);
                break;
            } catch (UnsatisfiedLinkError e) {
                why.append(name).append(": ").append(e.getMessage()).append('\n');
            }
        }
        this.mpv = lib;
        this.unavailableReason = lib == null ? why.toString().strip() : "";
        if (lib == null) LOG.warn("libmpv is not installed:\n{}", unavailableReason);
    }
    // --8<-- [end:load]

    /** False if libmpv is not installed; nothing can be played then. */
    public boolean isAvailable() {
        return mpv != null;
    }

    public String unavailableReason() {
        return unavailableReason;
    }

    /** libmpv's client API version, e.g. "2.5". */
    public String apiVersion() {
        long v = mpv.mpv_client_api_version();
        return (v >> 16) + "." + (v & 0xFFFF);
    }

    // --8<-- [start:attach]
    /**
     * Creates the player inside the given native window (X11 window id, HWND). The window must exist,
     * i.e. its component must be displayable.
     */
    public synchronized void attach(long windowId) {
        if (mpv == null) throw new IllegalStateException("libmpv is not installed");
        if (handle != null) throw new IllegalStateException("already attached");
        // libmpv refuses to start unless LC_NUMERIC is "C"; the JVM sets the user's locale.
        if (!System.getProperty("os.name", "").startsWith("Windows")) {
            Native.load("c", LibMpv.LibC.class).setlocale(LibMpv.LibC.LC_NUMERIC, "C");
        }
        Pointer h = mpv.mpv_create();
        if (h == null) throw new IllegalStateException("mpv_create failed");
        handle = h;
        option("wid", Long.toString(windowId));
        Map<String, String> output = new LinkedHashMap<>(VideoOutput.forThisMachine());
        Map<String, String> overrides = overrides();
        output.keySet().removeAll(overrides.keySet());
        fastProfile = "fast".equals(output.get("profile"));
        output.forEach(this::outputOption);
        option("keep-open", "yes");
        option("idle", "yes");
        option("input-default-bindings", "no");
        option("input-vo-keyboard", "no");
        option("osd-level", "1");
        option("ytdl", "yes");
        overrides.forEach(this::option);
        check(mpv.mpv_initialize(h), "mpv_initialize");
        mpv.mpv_request_log_messages(h, LOG.isDebugEnabled() ? "info" : "warn");
        mpv.mpv_observe_property(h, TIME, "time-pos", LibMpv.FORMAT_DOUBLE);
        mpv.mpv_observe_property(h, DURATION, "duration", LibMpv.FORMAT_DOUBLE);
        mpv.mpv_observe_property(h, PAUSE, "pause", LibMpv.FORMAT_FLAG);
        mpv.mpv_observe_property(h, CHAPTER, "chapter", LibMpv.FORMAT_INT64);
        mpv.mpv_observe_property(h, CHAPTERS, "chapters", LibMpv.FORMAT_INT64);
        mpv.mpv_observe_property(h, EOF, "eof-reached", LibMpv.FORMAT_FLAG);
        mpv.mpv_observe_property(h, TITLE, "media-title", LibMpv.FORMAT_STRING);
        running = true;
        events = Thread.ofPlatform().daemon().name("mpv-events").start(this::eventLoop);
        LOG.info("libmpv {} attached to window 0x{}", apiVersion(), Long.toHexString(windowId));
    }
    // --8<-- [end:attach]

    public PlayerState state() {
        return state;
    }

    /** Called on the player's event thread with every new state. */
    public void addListener(Consumer<PlayerState> listener) {
        listeners.add(listener);
    }

    public void open(MediaSource source) {
        if (handle == null) return;
        if (source instanceof MediaSource.Dvd dvd) mpv.mpv_set_property_string(handle, "dvd-device", dvd.device());
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

    // --8<-- [start:close]
    @Override
    public synchronized void close() {
        Pointer h = handle;
        if (h == null) return;
        running = false;
        // The event thread must leave mpv_wait_event before the handle is destroyed.
        mpv.mpv_wakeup(h);
        try {
            if (events != null) events.join(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        handle = null;
        mpv.mpv_terminate_destroy(h);
    }
    // --8<-- [end:close]

    // ---- events ----------------------------------------------------------------------------------

    // --8<-- [start:event-loop]
    private void eventLoop() {
        while (running) {
            Pointer h = handle;
            if (h == null) break;
            handle(MpvEvent.read(mpv.mpv_wait_event(h, 1.0)));
        }
    }
    // --8<-- [end:event-loop]

    // --8<-- [start:handle]
    void handle(MpvEvent event) {
        switch (event) {
            case MpvEvent.None n -> {
            }
            case MpvEvent.Shutdown s -> running = false;
            case MpvEvent.StartFile s -> update(st -> st.status() == PlayerState.Status.LOADING ? st
                    : PlayerState.loading(st.title()));
            case MpvEvent.FileLoaded f -> update(st -> st.withStatus(PlayerState.Status.PLAYING));
            case MpvEvent.EndFile e -> update(st -> switch (e.reason()) {
                case MpvEvent.END_ERROR -> st.failed(mpv.mpv_error_string(e.error()));
                case MpvEvent.END_EOF -> st.withStatus(PlayerState.Status.ENDED);
                case MpvEvent.END_STOP, MpvEvent.END_QUIT -> PlayerState.IDLE;
                default -> st;
            });
            case MpvEvent.PropertyChange p -> update(st -> propertyChanged(st, p));
            case MpvEvent.Log l -> log(l);
            case MpvEvent.Other o -> LOG.debug("mpv event {}", mpv.mpv_event_name(o.id()));
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

    private void log(MpvEvent.Log l) {
        if (isSoftwareRenderer(l) && !fastProfile) {
            // The renderer found no GPU (QEMU, Xvfb) although /dev/dri suggested one: the high-quality
            // scalers would leave the picture blank, so switch to the simple ones while running.
            fastProfile = true;
            LOG.info("Software rendering: switching mpv to the fast profile");
            command("apply-profile", "fast");
        }
        // Probing hardware decoders fails normally on machines without them (no VAAPI driver, ...).
        boolean probing = l.prefix().contains("vaapi") || l.prefix().contains("vdpau");
        switch (probing ? "debug" : l.level()) {
            case "fatal", "error" -> LOG.error("mpv {}: {}", l.prefix(), l.text());
            case "warn" -> LOG.warn("mpv {}: {}", l.prefix(), l.text());
            default -> LOG.debug("mpv {}: {}", l.prefix(), l.text());
        }
    }

    /** mpv's warning when its OpenGL renderer runs without a GPU (llvmpipe). */
    static boolean isSoftwareRenderer(MpvEvent.Log l) {
        return l.prefix().startsWith("vo/gpu") && l.text().contains("software renderer");
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
        Pointer h = handle;
        if (h == null) return;
        int r = mpv.mpv_command(h, args);
        if (r < 0) LOG.warn("mpv {}: {}", String.join(" ", args), mpv.mpv_error_string(r));
    }

    private void option(String name, String value) {
        check(mpv.mpv_set_option_string(handle, name, value), "option " + name + "=" + value);
    }

    /** The {@code -Dmpv.<option>=<value>} system properties, which replace the defaults of the same name. */
    static Map<String, String> overrides() {
        Map<String, String> overrides = new TreeMap<>();
        System.getProperties().stringPropertyNames().stream().filter(k -> k.startsWith(OVERRIDE_PREFIX))
                .forEach(k -> overrides.put(k.substring(OVERRIDE_PREFIX.length()), System.getProperty(k)));
        return overrides;
    }

    // --8<-- [start:output-option]
    /**
     * Sets a video output option. These depend on the mpv version (older ones know fewer GPU contexts), so
     * a value this libmpv rejects is logged and its own default stays, instead of no player at all.
     */
    private void outputOption(String name, String value) {
        int result = mpv.mpv_set_option_string(handle, name, value);
        if (result < 0) {
            LOG.warn("libmpv {} rejects {}={} ({}); using its default", apiVersion(), name, value,
                    mpv.mpv_error_string(result));
        }
    }
    // --8<-- [end:output-option]

    private void check(int result, String what) {
        if (result < 0) throw new IllegalStateException(what + ": " + mpv.mpv_error_string(result));
    }
}
