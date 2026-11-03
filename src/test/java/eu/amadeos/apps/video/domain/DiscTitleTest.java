package eu.amadeos.apps.video.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class DiscTitleTest {
    @Test
    void fromMpvsEditionTitle() {
        assertEquals(Optional.of(new DiscTitle(3, 6130.48)), DiscTitle.parse("title: 3 (01:42:10.480)"));
        assertEquals(Optional.of(new DiscTitle(1, 95)), DiscTitle.parse("title: 1 (00:01:35)"));
        assertEquals(Optional.empty(), DiscTitle.parse("Director's Cut"));
        assertEquals(Optional.empty(), DiscTitle.parse(""));
        assertEquals(Optional.empty(), DiscTitle.parse(null));
    }

    @Test
    void theMainFilmIsTheLongestTitle() {
        PlayerState s = PlayerState.loading("DVD").withDiscTitles(List.of(new DiscTitle(1, 30),
                new DiscTitle(2, 5400), new DiscTitle(4, 600)));
        assertEquals(1, s.mainDiscTitle());
        assertEquals(-1, PlayerState.IDLE.mainDiscTitle());
        assertTrue(PlayerState.loading("next").discTitles().isEmpty(), "a new file forgets the titles");
    }
}
