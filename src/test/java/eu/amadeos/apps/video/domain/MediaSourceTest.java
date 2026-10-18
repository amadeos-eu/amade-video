package eu.amadeos.apps.video.domain;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MediaSourceTest {
    @Test
    void argumentsAreFilesUrlsOrDvds() {
        assertEquals(new MediaSource.File(Path.of("/home/u/Videos/film.mkv")), MediaSource.parse("/home/u/Videos/film.mkv"));
        assertEquals(new MediaSource.File(Path.of("film with spaces.mp4")), MediaSource.parse(" film with spaces.mp4 "));
        assertEquals(new MediaSource.Url("https://example.org/live.m3u8"), MediaSource.parse("https://example.org/live.m3u8"));
        assertEquals(new MediaSource.Url("rtsp://camera/stream"), MediaSource.parse("rtsp://camera/stream"));
        assertInstanceOf(MediaSource.Dvd.class, MediaSource.parse("dvd://"));
        assertEquals(3, ((MediaSource.Dvd) MediaSource.parse("DVD://3")).title());
    }

    @Test
    void dvdImagesAndFoldersOpenAsDvd(@org.junit.jupiter.api.io.TempDir Path dir) throws Exception {
        assertEquals(new MediaSource.Dvd("/films/Ice Age.ISO", 0), MediaSource.parse("/films/Ice Age.ISO"));
        Path copy = dir.resolve("ICE_AGE");
        java.nio.file.Files.createDirectories(copy.resolve("VIDEO_TS"));
        assertEquals(new MediaSource.Dvd(copy.toString(), 0), MediaSource.parse(copy.toString()));
        assertEquals(new MediaSource.Dvd(copy.resolve("VIDEO_TS").toString(), 0), MediaSource.parse(copy.resolve("VIDEO_TS").toString()));
        // An ordinary folder is not a DVD
        assertInstanceOf(MediaSource.File.class, MediaSource.parse(dir.toString()));
    }

    @Test
    void mpvTargets() {
        assertEquals("https://example.org/a.mp4", new MediaSource.Url(" https://example.org/a.mp4 ").target());
        assertEquals("dvd://", new MediaSource.Dvd("/dev/sr0", 0).target());
        // mpv counts DVD titles from 0, people from 1
        assertEquals("dvd://0", new MediaSource.Dvd("/dev/sr0", 1).target());
        assertEquals("dvd://4", new MediaSource.Dvd("film.iso", 5).target());
        assertTrue(new MediaSource.File(Path.of("rel.mp4")).target().endsWith("rel.mp4"));
        assertTrue(Path.of(new MediaSource.File(Path.of("rel.mp4")).target()).isAbsolute());
        assertThrows(IllegalArgumentException.class, () -> new MediaSource.Dvd("/dev/sr0", -1));
    }

    @Test
    void displayNames() {
        assertEquals("film.mkv", new MediaSource.File(Path.of("/a/b/film.mkv")).displayName());
        assertEquals("DVD /dev/sr0", new MediaSource.Dvd("/dev/sr0", 0).displayName());
        assertEquals("DVD D: · 2", new MediaSource.Dvd("D:", 2).displayName());
    }
}
