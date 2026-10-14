package org.datazuul.amadeos.video.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlayerStateTest {
    @Test
    void loadingForgetsThePreviousMedia() {
        PlayerState playing = PlayerState.loading("a.mkv").withStatus(PlayerState.Status.PLAYING)
                .withPosition(12).withDuration(100).withChapters(5).withChapter(2).withPaused(true);
        PlayerState next = PlayerState.loading("b.mkv");
        assertEquals(PlayerState.Status.LOADING, next.status());
        assertEquals("b.mkv", next.title());
        assertEquals(-1, next.position());
        assertEquals(0, next.chapters());
        assertFalse(next.paused());
        assertTrue(playing.hasMedia());
        assertFalse(next.hasMedia());
    }

    @Test
    void failureKeepsTheTitleAndTheReason() {
        PlayerState failed = PlayerState.loading("http://x/y").failed("loading failed");
        assertEquals(PlayerState.Status.FAILED, failed.status());
        assertEquals("http://x/y", failed.title());
        assertEquals("loading failed", failed.error());
        assertEquals("", failed.withStatus(PlayerState.Status.LOADING).error(), "the reason goes with the failure");
    }
}
