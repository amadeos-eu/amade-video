package eu.amadeos.video.ui.main;

import eu.amadeos.video.domain.PlayerState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TimeFormatTest {
    @Test
    void clockLikeAVideoRecorder() {
        assertEquals("00:00", TimeFormat.clock(0));
        assertEquals("04:07", TimeFormat.clock(247.9));
        assertEquals("1:52:10", TimeFormat.clock(6730));
        assertEquals("--:--", TimeFormat.clock(-1));
        assertEquals("--:--", TimeFormat.clock(Double.NaN));
        assertEquals("04:07 / 1:52:10", TimeFormat.position(247, 6730));
        assertEquals("04:07", TimeFormat.position(247, -1), "live stream");
    }

    @Test
    void statusLine() {
        assertEquals("Open a video file, a URL or a DVD", ControlBar.statusText(PlayerState.IDLE));
        assertEquals("Opening film.mkv...", ControlBar.statusText(PlayerState.loading("film.mkv")));
        PlayerState playing = PlayerState.loading("film.mkv").withStatus(PlayerState.Status.PLAYING);
        assertEquals("film.mkv", ControlBar.statusText(playing));
        assertEquals("film.mkv (paused)", ControlBar.statusText(playing.withPaused(true)));
        assertEquals("Cannot play film.mkv: loading failed", ControlBar.statusText(playing.failed("loading failed")));
    }
}
