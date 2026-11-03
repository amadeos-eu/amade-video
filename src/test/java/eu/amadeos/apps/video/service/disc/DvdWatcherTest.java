package eu.amadeos.apps.video.service.disc;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class DvdWatcherTest {
    private final AtomicBoolean media = new AtomicBoolean();
    private final AtomicBoolean video = new AtomicBoolean();
    private final AtomicInteger reads = new AtomicInteger();
    private final DvdWatcher watcher = new DvdWatcher(Path.of("/dev/sr0"), d -> media.get(), d -> {
        reads.incrementAndGet();
        return video.get();
    });

    @Test
    void aDiscAlreadyInAtStartDoesNotCount() {
        media.set(true);
        video.set(true);
        assertFalse(watcher.look());
        assertFalse(watcher.look());
        assertEquals(0, reads.get());
    }

    @Test
    void aVideoDvdPutInIsReportedOnce() {
        assertFalse(watcher.look());
        media.set(true);
        video.set(true);
        assertTrue(watcher.look());
        assertFalse(watcher.look(), "still the same disc");
        media.set(false);
        assertFalse(watcher.look());
        media.set(true);
        assertTrue(watcher.look(), "the next disc");
    }

    @Test
    void aDiscThatSpinsUpIsReadAgain() {
        assertFalse(watcher.look());
        media.set(true);
        assertFalse(watcher.look());
        assertFalse(watcher.look());
        video.set(true);
        assertTrue(watcher.look());
    }

    @Test
    void otherDiscsAreReadAFewTimesOnly() {
        assertFalse(watcher.look());
        media.set(true);
        for (int i = 0; i < 10; i++) assertFalse(watcher.look());
        assertEquals(DvdWatcher.READ_ATTEMPTS, reads.get());
    }
}
