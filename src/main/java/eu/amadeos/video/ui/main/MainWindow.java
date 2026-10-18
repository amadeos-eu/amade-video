package eu.amadeos.video.ui.main;

import static eu.amadeos.video.i18n.Texts.tr;

import eu.amadeos.commons.i18n.Mnemonics;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

/** The picture with the control bar below and the menu bar above; the window manager draws the frame. */
final class MainWindow extends JFrame {
    final VideoCanvas video = new VideoCanvas();
    final ControlBar controls;

    MainWindow(MainPresenter p) {
        super(tr("app.title"));
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        java.net.URL icon = getClass().getResource("/icons/amade-video.png");
        if (icon != null) setIconImage(new ImageIcon(icon).getImage());
        controls = new ControlBar(p);

        JMenuBar menus = new JMenuBar();
        JMenu file = menu("menu.file");
        file.add(item("menu.open-file", KeyStroke.getKeyStroke(KeyEvent.VK_O, KeyEvent.CTRL_DOWN_MASK), p::openFile));
        file.add(item("menu.open-url", KeyStroke.getKeyStroke(KeyEvent.VK_L, KeyEvent.CTRL_DOWN_MASK), p::openUrl));
        file.add(item("menu.open-dvd", KeyStroke.getKeyStroke(KeyEvent.VK_D, KeyEvent.CTRL_DOWN_MASK), p::openDvd));
        file.addSeparator();
        file.add(item("menu.quit", KeyStroke.getKeyStroke(KeyEvent.VK_Q, KeyEvent.CTRL_DOWN_MASK), p::quit));
        JMenu playback = menu("menu.playback");
        playback.add(item("menu.play-pause", KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), p::togglePause));
        playback.add(item("menu.stop", KeyStroke.getKeyStroke(KeyEvent.VK_S, 0), p::stop));
        playback.addSeparator();
        playback.add(item("menu.back", KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), () -> p.seekBy(-10)));
        playback.add(item("menu.forward", KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), () -> p.seekBy(10)));
        playback.add(item("menu.previous-chapter", KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_UP, 0), () -> p.chapter(-1)));
        playback.add(item("menu.next-chapter", KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_DOWN, 0), () -> p.chapter(1)));
        JMenu help = menu("menu.help");
        help.add(item("menu.about", null, p::about));
        menus.add(file);
        menus.add(playback);
        menus.add(Box.createHorizontalGlue());
        menus.add(help);
        setJMenuBar(menus);

        JPanel content = new JPanel(new BorderLayout());
        content.add(video, BorderLayout.CENTER);
        content.add(controls, BorderLayout.SOUTH);
        setContentPane(content);
        pack();
        setLocationRelativeTo(null);
    }

    private static JMenu menu(String key) {
        return Mnemonics.apply(new JMenu(), tr(key));
    }

    private static JMenuItem item(String key, KeyStroke accelerator, Runnable action) {
        JMenuItem item = Mnemonics.apply(new JMenuItem(), tr(key));
        if (accelerator != null) item.setAccelerator(accelerator);
        item.addActionListener((ActionEvent e) -> action.run());
        return item;
    }
}
