package eu.amadeos.apps.video.domain;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A title of a DVD, as mpv lists it among the editions: {@code title: 3 (01:42:10.480)}.
 *
 * @param number the title's number on the disc, 1-based (what {@link MediaSource.Dvd#title()} takes)
 * @param length seconds, -1 if unknown
 */
public record DiscTitle(int number, double length) {
    private static final Pattern MPV = Pattern.compile(
            "title:\\s*(\\d+)\\s*\\((?:(\\d+):)?(\\d+):(\\d+(?:\\.\\d+)?)");

    /** From mpv's {@code edition-list/N/title}; empty for an edition that is not a DVD title. */
    public static Optional<DiscTitle> parse(String editionTitle) {
        if (editionTitle == null) return Optional.empty();
        Matcher m = MPV.matcher(editionTitle);
        if (!m.find()) return Optional.empty();
        int hours = m.group(2) == null ? 0 : Integer.parseInt(m.group(2));
        double length = hours * 3600 + Integer.parseInt(m.group(3)) * 60 + Double.parseDouble(m.group(4));
        return Optional.of(new DiscTitle(Integer.parseInt(m.group(1)), length));
    }
}
