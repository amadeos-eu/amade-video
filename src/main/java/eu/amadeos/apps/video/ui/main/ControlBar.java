package eu.amadeos.apps.video.ui.main;

import static eu.amadeos.apps.video.i18n.Texts.tr;

import eu.amadeos.apps.video.domain.PlayerState;
import eu.amadeos.apps.video.ui.widget.TransportIcon;

import javax.swing.*;
import java.awt.*;

/** Below the picture: transport buttons, position slider, time and chapter, and a status line. */
final class ControlBar extends JPanel {
    /** Slider steps over the whole duration. */
    static final int SCALE = 10_000;

    final JButton previous;
    final JButton rewind;
    final JButton playPause;
    final JButton forward;
    final JButton next;
    final JButton stop;
    final JSlider seek = new JSlider(0, SCALE, 0);
    final JLabel time = new JLabel("--:--");
    final JLabel chapter = new JLabel(" ");
    final JLabel status = new JLabel(" ");
    private final Icon playIcon = new TransportIcon(TransportIcon.Kind.PLAY);
    private final Icon pauseIcon = new TransportIcon(TransportIcon.Kind.PAUSE);
    private double duration = -1;
    /** Set while the slider follows the player, so that does not count as the user seeking. */
    private boolean following;

    ControlBar(MainPresenter p) {
        super(new BorderLayout(0, 4));
        setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        previous = TransportIcon.button(TransportIcon.Kind.PREVIOUS, () -> p.chapter(-1));
        rewind = TransportIcon.button(TransportIcon.Kind.REWIND, () -> p.seekBy(-10));
        playPause = TransportIcon.button(TransportIcon.Kind.PLAY, p::togglePause);
        forward = TransportIcon.button(TransportIcon.Kind.FORWARD, () -> p.seekBy(10));
        next = TransportIcon.button(TransportIcon.Kind.NEXT, () -> p.chapter(1));
        stop = TransportIcon.button(TransportIcon.Kind.STOP, p::stop);

        seek.setOpaque(false);
        seek.setFocusable(false);
        seek.getAccessibleContext().setAccessibleName(tr("transport.position"));
        // Seek when the knob is let go, not on every pixel of a drag.
        seek.addChangeListener(e -> {
            if (!following && !seek.getValueIsAdjusting() && duration > 0) {
                p.seekTo(duration * seek.getValue() / SCALE);
            }
        });

        time.setFont(new Font(Font.MONOSPACED, Font.BOLD, 13));
        JPanel clock = new JPanel(new BorderLayout(10, 0));
        clock.setOpaque(false);
        clock.add(time, BorderLayout.WEST);
        clock.add(chapter, BorderLayout.EAST);

        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setOpaque(false);
        row.add(TransportIcon.bar(previous, rewind, playPause, forward, next, stop), BorderLayout.WEST);
        row.add(seek, BorderLayout.CENTER);
        row.add(clock, BorderLayout.EAST);
        add(row, BorderLayout.NORTH);
        add(status, BorderLayout.SOUTH);
        show(PlayerState.IDLE);
    }

    void show(PlayerState s) {
        boolean media = s.hasMedia();
        boolean running = s.status() == PlayerState.Status.PLAYING && !s.paused();
        playPause.setIcon(running ? pauseIcon : playIcon);
        String label = tr(running ? "transport.pause" : "transport.play");
        playPause.setToolTipText(label);
        playPause.getAccessibleContext().setAccessibleName(label);
        playPause.setEnabled(media);
        rewind.setEnabled(media);
        forward.setEnabled(media);
        stop.setEnabled(media || s.status() == PlayerState.Status.LOADING);
        previous.setEnabled(media && s.chapters() > 1);
        next.setEnabled(media && s.chapters() > 1 && s.chapter() < s.chapters() - 1);

        duration = s.duration();
        if (!seek.getValueIsAdjusting()) {
            following = true;
            seek.setEnabled(media && duration > 0);
            seek.setValue(duration > 0 && s.position() >= 0 ? (int) Math.round(SCALE * Math.min(1, s.position() / duration)) : 0);
            following = false;
        }
        time.setText(TimeFormat.position(s.position(), s.duration()));
        chapter.setText(s.chapters() > 0 && s.chapter() >= 0 ? tr("transport.chapter", s.chapter() + 1, s.chapters()) : " ");
        status.setText(statusText(s));
    }

    static String statusText(PlayerState s) {
        return switch (s.status()) {
            case IDLE -> tr("status.idle");
            case LOADING -> tr("status.loading", s.title());
            case PLAYING -> s.paused() ? tr("status.paused", s.title()) : s.title();
            case ENDED -> tr("status.ended", s.title());
            case FAILED -> tr("status.failed", s.title(), s.error());
        };
    }
}
