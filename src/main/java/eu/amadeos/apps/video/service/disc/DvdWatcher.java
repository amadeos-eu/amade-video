package eu.amadeos.apps.video.service.disc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Looks at the DVD drive every few seconds and reports when a video DVD has been put in. Linux only: the
 * drive's size in /sys/block tells whether a disc is in, and only then is the disc read once.
 */
public final class DvdWatcher implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(DvdWatcher.class);
    static final long PERIOD_MS = 3000;
    /** A disc just put in may not be readable for a few seconds while it spins up. */
    static final int READ_ATTEMPTS = 5;

    private final Path device;
    private final Predicate<Path> hasMedia;
    private final Predicate<Path> isVideoDvd;
    private ScheduledExecutorService timer;
    /** Whether there was a disc at the last look; the first look only takes note. */
    private Boolean hadMedia;
    /** Reads left for the disc just put in; 0 when it has been told apart. */
    private int attempts;

    public DvdWatcher(Path device) {
        this(device, VideoDiscProbe::hasMedia, VideoDiscProbe::isVideoDvd);
    }

    DvdWatcher(Path device, Predicate<Path> hasMedia, Predicate<Path> isVideoDvd) {
        this.device = device;
        this.hasMedia = hasMedia;
        this.isVideoDvd = isVideoDvd;
    }

    /** True where the watcher can work: Linux with the drive present. */
    public boolean isSupported() {
        return System.getProperty("os.name", "").startsWith("Linux") && Files.exists(device);
    }

    /** Calls {@code inserted} with the drive, on the watcher's own thread, for every video DVD put in. */
    public synchronized void start(Consumer<Path> inserted) {
        if (timer != null || !isSupported()) return;
        timer = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "dvd-watcher");
            t.setDaemon(true);
            return t;
        });
        timer.scheduleWithFixedDelay(() -> {
            try {
                if (look()) inserted.accept(device);
            } catch (RuntimeException e) {
                LOG.warn("DVD watcher", e);
            }
        }, 0, PERIOD_MS, TimeUnit.MILLISECONDS);
    }

    /** One look at the drive; true if a video DVD has just been put in. */
    boolean look() {
        boolean media = hasMedia.test(device);
        if (!media) attempts = 0;
        else if (Boolean.FALSE.equals(hadMedia)) attempts = READ_ATTEMPTS;
        hadMedia = media;
        if (attempts == 0) return false;
        attempts--;
        if (!isVideoDvd.test(device)) {
            if (attempts == 0) LOG.info("Disc put into {} is not a video DVD", device);
            return false;
        }
        attempts = 0;
        LOG.info("Video DVD put into {}", device);
        return true;
    }

    @Override
    public synchronized void close() {
        if (timer != null) timer.shutdownNow();
        timer = null;
    }
}
