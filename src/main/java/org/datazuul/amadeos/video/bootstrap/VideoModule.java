package org.datazuul.amadeos.video.bootstrap;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import org.datazuul.amadeos.video.service.mpv.MpvPlayer;
import org.datazuul.amadeos.video.ui.main.MainPresenter;

/**
 * Video Player bindings; executor, directories, theme and EDT executor come from
 * {@link org.datazuul.amadeos.commons.app.CommonsModule}. There is one player and one main window.
 */
public final class VideoModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(MpvPlayer.class).in(Scopes.SINGLETON);
        bind(MainPresenter.class).in(Scopes.SINGLETON);
    }
}
