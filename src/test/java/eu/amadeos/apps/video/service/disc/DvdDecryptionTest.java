package eu.amadeos.apps.video.service.disc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DvdDecryptionTest {
    @TempDir
    Path dir;

    @Test
    void missingWithoutTheLibrary() throws Exception {
        Files.createFile(dir.resolve("libdvdnav.so.4"));
        assertFalse(new DvdDecryption(List.of(dir, dir.resolve("absent"))).isInstalled());
    }

    @Test
    void installedWhenTheLibraryIsInAFolder() throws Exception {
        Path lib = Files.createDirectory(dir.resolve("lib"));
        Files.createFile(lib.resolve("libdvdcss.so.2"));
        assertTrue(new DvdDecryption(List.of(dir.resolve("absent"), lib)).isInstalled());
    }
}
