# Step 1 spike: libmpv in a Swing window

> Historical notes. The spike code (`MpvSpike`) was replaced by the application in step 2; its findings
> live on in `MpvPlayer`, `VideoOutput` and `MediaSource`. The commands below refer to the spike.

Question: can amade-video use libmpv (via JNA) for playback, with the picture inside a Swing window,
under the amadeOS window manager, for files, URL streams and DVDs (with libdvdcss)?

**Answer: yes.** `MpvSpike` embeds mpv into an AWT `Canvas` through mpv's `wid` option; mpv decodes,
renders and plays the audio itself. Everything else (buttons, time, chapters) is Swing.

## Running it

```bash
sudo apt-get install libmpv2          # brings libdvdnav, libbluray, FFmpeg
sudo apt-get install libdvd-pkg && sudo dpkg-reconfigure libdvd-pkg   # libdvdcss, only for encrypted DVDs

./gradlew run --args="$HOME/Videos/film.mp4"
./gradlew run --args="https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
./gradlew run --args="dvd://" -Dmpv.dvdDevice=/dev/sr0      # or a VIDEO_TS folder or an ISO image
```

System properties: `mpv.vo` (default `gpu,xv,x11`), `mpv.hwdec` (default `auto-safe`), `mpv.profile`
(e.g. `fast`), `mpv.ao` (e.g. `null`), `mpv.dvdDevice`, and for automated tests `mpv.capture=shot.png`
with `mpv.captureAfter=<seconds>` (screenshot of the window, then exit).
File names with spaces do not survive `--args`; use a link or run the class directly.

## Results (2026-10-14, libmpv 0.41.0, client API 2.5, Ubuntu "resolute" packages)

| Test | Result |
|------|--------|
| Local file (H.264/AAC MP4) | plays in the window, `vo gpu` |
| HLS stream (`.m3u8`, 1080p) | plays, position and duration reported |
| DVD from ISO image, `dvd://` | plays; 4 chapters with times in `chapter-list`, current chapter follows playback; libdvdcss is loaded and libdvdread runs its CSS key step |
| Under the amadeOS window manager and front panel | normal Motif frame, video inside, panel unaffected |

Not tested yet: clicking the buttons (only automated runs so far), an encrypted commercial DVD (needs a drive; this machine has none), a real GPU (all
runs were on Xvfb), audio output (`ao=null` in the tests), window resize, workspace switching,
fullscreen, YouTube-style URLs (needs `yt-dlp`), Windows.

## Findings for the real application

- **Locale:** libmpv refuses to start unless `LC_NUMERIC` is `C`; the JVM sets the user's locale, so
  call `setlocale(LC_NUMERIC, "C")` (libc via JNA) before `mpv_create`.
- **Video output without a GPU** (QEMU, Xvfb): `vo=gpu` must be limited to X11 contexts
  (`gpu-context=x11egl,x11vk`; mpv 0.41 has no GLX context any more), otherwise mpv tries DRM and
  the console. On software OpenGL the high-quality scalers fail to compile and the picture stays
  blank when downscaling (e.g. 1080p into a small window); `profile=fast` or `vo=x11` fix it. The
  application should detect a software renderer and switch to `profile=fast`.
- **DVD titles:** mpv 0.41 has no `disc-titles` property; a DVD title is an *edition*
  (`editions`, `current-edition`, `edition-list`), chapters are in `chapter-list`. `dvd://N` opens
  title N. No DVD menus (dvdnav runs in non-menu mode).
- **Threading:** one thread waits on `mpv_wait_event`; commands can be sent from the EDT. On exit,
  wake the event thread and join it before `mpv_terminate_destroy`, or the JVM can crash.
- **Struct access:** events are read with fixed offsets from `mpv/client.h` (see `LibMpv`); no JNA
  `Structure` classes needed.
- **Swing on top of video is not possible** (mpv owns the native child window): controls go around
  the picture, subtitles and on-screen messages come from mpv.

## Next

Turn the spike into the application skeleton (amade-commons, Guice, an `MpvPlayer` service with the
event thread, properties as observable state), then the CDE-style UI, then DVD title/chapter lists,
tracks and subtitles, fullscreen, packaging (`libmpv2` dependency, `yt-dlp` recommended).
