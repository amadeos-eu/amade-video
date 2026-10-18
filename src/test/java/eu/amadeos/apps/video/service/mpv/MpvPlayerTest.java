package eu.amadeos.apps.video.service.mpv;

import eu.amadeos.apps.video.domain.PlayerState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The state machine behind the observed properties, without libmpv. */
class MpvPlayerTest {
    private static PlayerState change(PlayerState s, long id, Object value) {
        return MpvPlayer.propertyChanged(s, new MpvEvent.PropertyChange(id, "", value));
    }

    @Test
    void propertiesFillTheState() {
        PlayerState s = PlayerState.loading("film.mkv").withStatus(PlayerState.Status.PLAYING);
        s = change(s, 1, 61.5);
        s = change(s, 2, 5400.0);
        s = change(s, 3, true);
        s = change(s, 5, 12L);
        s = change(s, 4, 3L);
        s = change(s, 7, "Ice Age");
        assertEquals(61.5, s.position());
        assertEquals(5400.0, s.duration());
        assertTrue(s.paused());
        assertEquals(12, s.chapters());
        assertEquals(3, s.chapter());
        assertEquals("Ice Age", s.title());
        // Unavailable values reset, an empty title keeps the old one
        s = change(s, 2, null);
        s = change(s, 7, "");
        assertEquals(-1, s.duration());
        assertEquals("Ice Age", s.title());
    }

    @Test
    void endOfFileWithKeepOpen() {
        PlayerState playing = PlayerState.loading("a").withStatus(PlayerState.Status.PLAYING);
        PlayerState ended = change(playing, 6, true);
        assertEquals(PlayerState.Status.ENDED, ended.status());
        // Seeking back from the end
        assertEquals(PlayerState.Status.PLAYING, change(ended, 6, false).status());
        // eof-reached while loading does not end anything
        assertEquals(PlayerState.Status.LOADING, change(PlayerState.loading("b"), 6, true).status());
    }

    @Test
    void recognisesTheSoftwareRendererWarning() {
        assertTrue(MpvPlayer.isSoftwareRenderer(new MpvEvent.Log("warn", "vo/gpu/opengl",
                "Suspected software renderer or indirect context.")));
        assertFalse(MpvPlayer.isSoftwareRenderer(new MpvEvent.Log("warn", "vo/gpu/vaapi", "libva: vaGetDriverNames() failed")));
    }
}
