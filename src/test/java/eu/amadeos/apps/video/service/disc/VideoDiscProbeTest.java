package eu.amadeos.apps.video.service.disc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class VideoDiscProbeTest {
    private static final int SECTOR = VideoDiscProbe.SECTOR;

    /** A tiny ISO 9660 image: PVD in sector 16, root directory in sector 18 with the given entries. */
    private static Path image(Path dir, String name, boolean folder) throws IOException {
        ByteBuffer b = ByteBuffer.allocate(20 * SECTOR).order(ByteOrder.LITTLE_ENDIAN);
        int pvd = 16 * SECTOR;
        b.put(pvd, (byte) 1);
        b.put(pvd + 1, "CD001".getBytes(StandardCharsets.US_ASCII));
        b.putInt(pvd + 156 + 2, 18);
        b.putInt(pvd + 156 + 10, SECTOR);
        int pos = 18 * SECTOR;
        pos = record(b, pos, "\0", true);
        pos = record(b, pos, "\1", true);
        record(b, pos, name, folder);
        Path file = dir.resolve("disc.iso");
        Files.write(file, b.array());
        return file;
    }

    private static int record(ByteBuffer b, int pos, String name, boolean folder) {
        byte[] n = name.getBytes(StandardCharsets.US_ASCII);
        int length = 33 + n.length + (n.length % 2 == 0 ? 1 : 0);
        b.put(pos, (byte) length);
        b.put(pos + 25, (byte) (folder ? 2 : 0));
        b.put(pos + 32, (byte) n.length);
        b.put(pos + 33, n);
        return pos + length;
    }

    @Test
    void aVideoDvdHasAVideoTsFolder(@TempDir Path dir) throws IOException {
        assertTrue(VideoDiscProbe.isVideoDvd(image(dir, "VIDEO_TS", true)));
    }

    @Test
    void otherDiscsAreNot(@TempDir Path dir) throws IOException {
        assertFalse(VideoDiscProbe.isVideoDvd(image(dir, "README.TXT;1", false)));
        assertFalse(VideoDiscProbe.isVideoDvd(image(dir, "PHOTOS", true)));
        // a file called VIDEO_TS is not the folder
        assertFalse(VideoDiscProbe.isVideoDvd(image(dir, "VIDEO_TS", false)));
    }

    @Test
    void notAnImageOrMissing(@TempDir Path dir) throws IOException {
        Path junk = dir.resolve("junk.bin");
        Files.write(junk, new byte[40 * SECTOR]);
        assertFalse(VideoDiscProbe.isVideoDvd(junk));
        assertFalse(VideoDiscProbe.isVideoDvd(dir.resolve("missing.iso")));
        assertFalse(VideoDiscProbe.hasMedia(dir.resolve("missing")));
    }
}
