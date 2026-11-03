package eu.amadeos.apps.video.service.disc;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;

/**
 * Tells a video DVD from other discs: it has a {@code VIDEO_TS} folder at the top. Video DVDs are UDF with an
 * ISO 9660 bridge, so the ISO 9660 root directory is enough; this works for a drive ({@code /dev/sr0}) and an
 * ISO image alike, without mounting anything.
 */
public final class VideoDiscProbe {
    static final int SECTOR = 2048;
    /** The Primary Volume Descriptor sits in sector 16. */
    private static final int PVD = 16;

    private VideoDiscProbe() {
    }

    /** True if the disc or image has a VIDEO_TS folder; false for other discs, an empty drive or an error. */
    public static boolean isVideoDvd(Path device) {
        try (FileChannel ch = FileChannel.open(device, StandardOpenOption.READ)) {
            return hasVideoTs(ch);
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    static boolean hasVideoTs(FileChannel ch) throws IOException {
        ByteBuffer pvd = read(ch, (long) PVD * SECTOR, SECTOR);
        // type 1, "CD001"
        if (pvd.get(0) != 1 || !"CD001".equals(text(pvd, 1, 5))) return false;
        // the root directory record at 156: extent (LBA) at +2, size at +10, both-endian; little end first
        long extent = Integer.toUnsignedLong(pvd.getInt(156 + 2));
        int size = Math.min(pvd.getInt(156 + 10), 64 * SECTOR);
        if (size <= 0) return false;
        ByteBuffer dir = read(ch, extent * SECTOR, size);
        int pos = 0;
        while (pos < size) {
            int length = Byte.toUnsignedInt(dir.get(pos));
            if (length == 0) {
                // records do not cross sectors: go on with the next one
                pos = (pos / SECTOR + 1) * SECTOR;
                continue;
            }
            if (pos + 33 > size) break;
            boolean folder = (dir.get(pos + 25) & 0x02) != 0;
            int nameLength = Byte.toUnsignedInt(dir.get(pos + 32));
            if (folder && pos + 33 + nameLength <= size
                    && text(dir, pos + 33, nameLength).toUpperCase(Locale.ROOT).equals("VIDEO_TS")) return true;
            pos += length;
        }
        return false;
    }

    private static ByteBuffer read(FileChannel ch, long position, int length) throws IOException {
        ByteBuffer b = ByteBuffer.allocate(length).order(ByteOrder.LITTLE_ENDIAN);
        while (b.hasRemaining()) {
            if (ch.read(b, position + b.position()) < 0) throw new IOException("short read");
        }
        return b.flip();
    }

    private static String text(ByteBuffer b, int offset, int length) {
        byte[] bytes = new byte[length];
        b.get(offset, bytes);
        return new String(bytes, StandardCharsets.US_ASCII);
    }

    /** Whether a drive holds a disc: Linux shows the size of what is in it under /sys/block; false elsewhere. */
    public static boolean hasMedia(Path device) {
        try {
            Path name = device.toRealPath().getFileName();
            if (name == null) return false;
            Path size = Path.of("/sys/block", name.toString(), "size");
            return Files.exists(size) && Long.parseLong(Files.readString(size).strip()) > 0;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }
}
