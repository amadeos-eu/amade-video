package eu.amadeos.apps.video.domain;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

/** What to play: a video file, a URL (HTTP, HLS, ...) or a DVD (drive, VIDEO_TS folder or ISO image). */
public sealed interface MediaSource {
    /** What mpv's {@code loadfile} gets. */
    String target();

    /** Short name for the title bar and status line. */
    String displayName();

    record File(Path path) implements MediaSource {
        public File {
            Objects.requireNonNull(path, "path");
        }

        @Override
        public String target() {
            return path.toAbsolutePath().toString();
        }

        @Override
        public String displayName() {
            Path name = path.getFileName();
            return name == null ? path.toString() : name.toString();
        }
    }

    record Url(String url) implements MediaSource {
        public Url {
            Objects.requireNonNull(url, "url");
            url = url.strip();
        }

        @Override
        public String target() {
            return url;
        }

        @Override
        public String displayName() {
            return url;
        }
    }

    /**
     * @param device drive ({@code /dev/sr0}, {@code D:}), VIDEO_TS folder or ISO image
     * @param title  DVD title to play, 1-based; 0 = whichever title mpv starts with
     */
    record Dvd(String device, int title) implements MediaSource {
        public Dvd {
            Objects.requireNonNull(device, "device");
            device = device.strip();
            if (title < 0) throw new IllegalArgumentException("title " + title);
        }

        @Override
        public String target() {
            // mpv numbers titles from 0 (dvdnav logs "title 0" for the first one).
            return title == 0 ? "dvd://" : "dvd://" + (title - 1);
        }

        @Override
        public String displayName() {
            return "DVD " + device + (title == 0 ? "" : " · " + title);
        }
    }

    /**
     * From a command-line argument: {@code dvd://[title]} (drive from {@link #defaultDvdDevice()}), a URL
     * with a scheme, a DVD image ({@code .iso}) or folder (with or named {@code VIDEO_TS}), or a video file.
     */
    static MediaSource parse(String argument) {
        String a = argument.strip();
        String lower = a.toLowerCase(Locale.ROOT);
        if (lower.startsWith("dvd://")) {
            String number = a.substring(6);
            return new Dvd(defaultDvdDevice(), number.isEmpty() ? 0 : Integer.parseInt(number));
        }
        if (lower.matches("[a-z][a-z0-9+.-]+://.*")) return new Url(a);
        Path path = Path.of(a);
        if (isDvd(path)) return new Dvd(a, 0);
        return new File(path);
    }

    /** An ISO image, a VIDEO_TS folder or a folder containing one (as copied from a DVD). */
    static boolean isDvd(Path path) {
        Path name = path.getFileName();
        if (name == null) return false;
        String n = name.toString().toLowerCase(Locale.ROOT);
        if (n.endsWith(".iso")) return true;
        if (!java.nio.file.Files.isDirectory(path)) return false;
        return n.equals("video_ts") || java.nio.file.Files.isDirectory(path.resolve("VIDEO_TS"))
                || java.nio.file.Files.isDirectory(path.resolve("video_ts"));
    }

    /** The first optical drive's usual name on this system. */
    static String defaultDvdDevice() {
        if (System.getProperty("os.name", "").startsWith("Windows")) return "D:";
        return java.nio.file.Files.exists(Path.of("/dev/dvd")) ? "/dev/dvd" : "/dev/sr0";
    }
}
