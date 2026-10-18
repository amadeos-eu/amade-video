package eu.amadeos.video.i18n;

import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.i18n.Mnemonics;
import eu.amadeos.commons.i18n.TranslationCheck;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TranslationTest {
    @Test
    void everyLanguageHasEveryText() {
        assertEquals(Map.of(), TranslationCheck.problems(Texts.class, Texts.BUNDLE));
    }

    @Test
    void mnemonicsAreUniqueWithinEachMenu() {
        List<List<String>> menus = List.of(
                List.of("menu.file", "menu.playback", "menu.help"),
                List.of("menu.open-file", "menu.open-url", "menu.open-dvd", "menu.quit"),
                List.of("menu.play-pause", "menu.stop", "menu.back", "menu.forward", "menu.previous-chapter",
                        "menu.next-chapter"));
        for (String language : List.of("en", "de", "fr", "es", "it")) {
            Messages m = Messages.load(Texts.class, Texts.BUNDLE, Locale.of(language));
            for (List<String> menu : menus) {
                Set<Character> letters = new HashSet<>();
                for (String key : menu) {
                    Mnemonics.Text t = Mnemonics.parse(m.get(key));
                    assertTrue(t.index() >= 0, language + " " + key + " has no mnemonic");
                    char c = Character.toLowerCase(t.text().charAt(t.index()));
                    assertTrue(letters.add(c), language + " " + key + ": '" + c + "' used twice");
                }
            }
        }
    }
}
