package eu.amadeos.video.bootstrap;

import eu.amadeos.commons.app.AmadeApplication;
import eu.amadeos.commons.app.AppInfo;
import eu.amadeos.video.ui.main.MainPresenter;

import java.util.List;

/** Entry point. The first argument is a video file, a URL or {@code dvd://[title]}, as passed by the desktop entry. */
public final class AmadeVideoApplication {
    public static final AppInfo APP = new AppInfo("amade-video", "Video Player", "video");

    private AmadeVideoApplication() {
    }

    public static void main(String[] args) {
        AmadeApplication.launch(APP, injector -> injector.getInstance(MainPresenter.class).start(List.of(args)),
                new VideoModule());
    }
}
