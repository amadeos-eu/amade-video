package eu.amadeos.apps.video.ui.main;

import static eu.amadeos.commons.i18n.Texts.tr;

import com.google.inject.Inject;
import com.sun.jna.Native;
import eu.amadeos.commons.i18n.Mnemonics;
import eu.amadeos.commons.swing.EdtExecutor;
import eu.amadeos.apps.video.domain.MediaSource;
import eu.amadeos.apps.video.domain.PlayerState;
import eu.amadeos.apps.video.service.mpv.MpvPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;

/** Connects the main window to the player. Runs on the EDT; player states arrive through the EDT executor. */
public final class MainPresenter {
    private static final Logger LOG = LoggerFactory.getLogger(MainPresenter.class);
    static final String[] VIDEO_EXTENSIONS = {"mp4", "m4v", "mkv", "webm", "avi", "mov", "mpg", "mpeg", "ts",
            "m2ts", "mts", "vob", "ogv", "wmv", "flv", "3gp", "iso"};

    private final MpvPlayer player;
    private final EdtExecutor edt;
    private final ExecutorService executor;
    private MainWindow window;
    private Path lastFolder;

    @Inject
    public MainPresenter(MpvPlayer player, EdtExecutor edt, ExecutorService executor) {
        this.player = player;
        this.edt = edt;
        this.executor = executor;
    }

    /** Shows the window and, once the player runs, opens the first argument (file, URL or dvd://). */
    public void start(List<String> args) {
        window = new MainWindow(this);
        window.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                quit();
            }
        });
        window.setVisible(true);
        player.addListener(state -> edt.execute(() -> show(state)));
        show(player.state());
        if (!player.isAvailable()) {
            window.video.setHint(tr("no-mpv.hint"));
            JOptionPane.showMessageDialog(window, tr("no-mpv.text", player.unavailableReason()), tr("app.title"),
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        // The canvas is displayable now, so it has a native window for mpv.
        long windowId = Native.getComponentID(window.video);
        executor.execute(() -> {
            try {
                // mpv_initialize can take a moment (GPU detection), so not on the EDT.
                player.attach(windowId);
                if (!args.isEmpty()) {
                    MediaSource source = MediaSource.parse(args.get(0));
                    edt.execute(() -> open(source));
                }
            } catch (RuntimeException e) {
                LOG.error("Cannot start the player", e);
                edt.execute(() -> JOptionPane.showMessageDialog(window, tr("player.failed", e.getMessage()),
                        tr("app.title"), JOptionPane.ERROR_MESSAGE));
            }
        });
    }

    private void show(PlayerState s) {
        window.controls.show(s);
        window.video.setHint(switch (s.status()) {
            case IDLE, LOADING, FAILED -> ControlBar.statusText(s);
            case PLAYING, ENDED -> "";
        });
        window.setTitle(s.title().isEmpty() ? tr("app.title") : s.title() + " - " + tr("app.title"));
    }

    // ---- open ------------------------------------------------------------------------------------

    void open(MediaSource source) {
        if (source instanceof MediaSource.File f && f.path().getParent() != null) lastFolder = f.path().getParent();
        player.open(source);
    }

    void openFile() {
        JFileChooser chooser = new JFileChooser(startFolder().toFile());
        chooser.setDialogTitle(Mnemonics.strip(tr("menu.open-file")));
        chooser.setFileFilter(new FileNameExtensionFilter(tr("open-file.filter"), VIDEO_EXTENSIONS));
        if (chooser.showOpenDialog(window) != JFileChooser.APPROVE_OPTION) return;
        // An ISO image is opened as a DVD.
        open(MediaSource.parse(chooser.getSelectedFile().getAbsolutePath()));
    }

    void openUrl() {
        String url = JOptionPane.showInputDialog(window, tr("open-url.prompt"), Mnemonics.strip(tr("menu.open-url")),
                JOptionPane.QUESTION_MESSAGE);
        if (url != null && !url.isBlank()) open(new MediaSource.Url(url));
    }

    void openDvd() {
        Object device = JOptionPane.showInputDialog(window, tr("open-dvd.prompt"), Mnemonics.strip(tr("menu.open-dvd")),
                JOptionPane.QUESTION_MESSAGE, null, null, MediaSource.defaultDvdDevice());
        if (device != null && !device.toString().isBlank()) open(new MediaSource.Dvd(device.toString(), 0));
    }

    private Path startFolder() {
        if (lastFolder != null && Files.isDirectory(lastFolder)) return lastFolder;
        Path home = Path.of(System.getProperty("user.home"));
        return Files.isDirectory(home.resolve("Videos")) ? home.resolve("Videos") : home;
    }

    // ---- transport -------------------------------------------------------------------------------

    void togglePause() {
        player.togglePause();
    }

    void stop() {
        player.stop();
    }

    void seekBy(double seconds) {
        if (player.state().hasMedia()) player.seekBy(seconds);
    }

    void seekTo(double seconds) {
        if (player.state().hasMedia()) player.seekTo(seconds);
    }

    void chapter(int delta) {
        if (player.state().chapters() > 1) player.chapter(delta);
    }

    // ---- window ----------------------------------------------------------------------------------

    void about() {
        String version = player.isAvailable() ? "libmpv " + player.apiVersion() : tr("no-mpv.short");
        Icon icon = window.getIconImages().isEmpty() ? null : new ImageIcon(window.getIconImages().get(0));
        JOptionPane.showMessageDialog(window, tr("about.text", version) + "\nhttps://github.com/datazuul/amade-video",
                Mnemonics.strip(tr("menu.about")), JOptionPane.INFORMATION_MESSAGE, icon);
    }

    void quit() {
        player.close();
        for (Window w : Window.getWindows()) w.dispose();
        executor.shutdownNow();
    }
}
