package eu.amadeos.apps.video.service.disc;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

// --8<-- [start:decryption]
/**
 * Whether libdvdcss is installed, the library libdvdnav uses to decrypt bought (CSS-encrypted) DVDs.
 * amadeOS does not ship it, because getting round copy protection is not allowed everywhere; on Debian the
 * user builds it with the helper package libdvd-pkg. libdvdread loads it by name at run time, so looking
 * for the file in the library folders is enough.
 */
public final class DvdDecryption {
    /** The commands that install libdvdcss on Debian; the second one downloads and builds it. */
    public static final String INSTALL_COMMANDS = "sudo apt install libdvd-pkg\nsudo dpkg-reconfigure libdvd-pkg";

    private static final List<Path> LIBRARY_FOLDERS = List.of(Path.of("/usr/lib/x86_64-linux-gnu"),
            Path.of("/usr/lib/aarch64-linux-gnu"), Path.of("/usr/lib64"), Path.of("/usr/lib"),
            Path.of("/usr/local/lib"));

    private final List<Path> folders;

    public DvdDecryption() {
        this(LIBRARY_FOLDERS);
    }

    DvdDecryption(List<Path> folders) {
        this.folders = folders;
    }

    /** True if some libdvdcss.so* is in one of the library folders. */
    public boolean isInstalled() {
        for (Path folder : folders) {
            if (!Files.isDirectory(folder)) continue;
            try (DirectoryStream<Path> files = Files.newDirectoryStream(folder, "libdvdcss.so*")) {
                if (files.iterator().hasNext()) return true;
            } catch (IOException e) {
                // an unreadable folder holds nothing we can load
            }
        }
        return false;
    }
}
// --8<-- [end:decryption]
