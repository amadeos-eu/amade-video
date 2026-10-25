# amade-video

**Video Player** for [amadeOS](https://github.com/datazuul/amadeos): plays video files, internet video
streams and DVDs in a Motif/CDE-styled window. Playback is done by [libmpv](https://mpv.io) (FFmpeg,
libdvdnav), embedded into the Swing window; the rest is Java on
[amade-commons](https://github.com/datazuul/amade-commons) like the other amadeOS applications.

Status: **skeleton** (step 2). Opening and playing works; the CDE-style interface, DVD title lists,
audio/subtitle tracks, fullscreen and volume come next (see [Roadmap](#roadmap)).

## Features

| Source | How | Notes |
|--------|-----|-------|
| **Files** | *File → Open File...*, command line, desktop entry | everything FFmpeg decodes: MP4, MKV, WebM, AVI, MOV, MPEG-2, ... |
| **Streams** | *File → Open URL...* | HTTP(S) files, HLS (`.m3u8`), DASH; YouTube and other sites with `yt-dlp` installed |
| **DVDs** | *File → Open DVD...*, `dvd://[title]`, an `.iso` image or a `VIDEO_TS` folder | titles and chapters, no DVD menus; encrypted DVDs need libdvdcss (see below) |

Controls: play/pause (Space), stop (S), back/forward 10 s (←/→), previous/next chapter (Page Up/Down),
position slider. Texts in English, German, French, Spanish and Italian, following the desktop language.

### Encrypted DVDs

Commercial DVDs are encrypted with CSS. libdvdnav decrypts them with **libdvdcss** if it is installed;
amade-video neither ships nor installs it. On Debian/Ubuntu:

```bash
sudo apt install libdvd-pkg && sudo dpkg-reconfigure libdvd-pkg
```

Whether you may use libdvdcss depends on where you live (in some countries, e.g. Germany, circumventing
copy protection is not allowed). Unencrypted DVDs (home videos, many TV recordings) play without it.

## Architecture

Java 25, Swing, Guice, JNA; same structure as [amade-audio](https://github.com/datazuul/amade-audio).

| Package | Content |
|---------|---------|
| `bootstrap` | `AmadeVideoApplication` (entry point), `VideoModule` |
| `domain` | `MediaSource` (file, URL, DVD; what mpv's `loadfile` gets), `PlayerState` (immutable, what the UI shows) |
| `service.mpv` | `MpvPlayer`: libmpv via JNA (`LibMpv`), event thread, observed properties → `PlayerState`; `MpvEvent` reads `mpv_event` structs; `VideoOutput` picks the video output |
| `ui.main` | `MainWindow` (menu bar, `VideoCanvas`, `ControlBar`), `MainPresenter` |
| `ui.widget` | `TransportIcon` (as in amade-audio) |

mpv renders into the native window of an AWT `Canvas` (its `wid` option) and plays the sound itself.
Swing cannot paint over the picture, so the controls sit around it. Findings from the first spike
(locale, video output without a GPU, DVD titles as editions, shutdown order) are in
[docs/spike.md](docs/spike.md).

Without a GPU (QEMU, Xvfb) mpv's high-quality scalers do not compile on software OpenGL and the picture
stays blank; `MpvPlayer` switches to mpv's `fast` profile when there is no `/dev/dri` render node or
when mpv reports a software renderer.

## Building and testing

Requirements: Java 25 JDK; `libmpv2` to play; `dpkg-deb` for the Debian package.

`amade-commons` is taken from JitPack. If it is checked out next to this repository
(`../amade-commons`), Gradle builds that checkout instead.

```bash
./gradlew test                         # unit tests (no libmpv or display needed)
./gradlew run                          # start the Video Player
./gradlew run --args="dvd://"          # play the DVD in /dev/sr0 (or /dev/dvd)
./gradlew run --args=/path/film.iso    # a DVD image (paths with spaces: start the jar directly)
./gradlew run -Dmpv.ao=null            # any mpv option as -Dmpv.<option>=<value>
./gradlew deb                          # build/distributions/amade-video_0.0.0~dev.0_all.deb
./gradlew installToAmadeOs             # copy the .deb into ../amadeos/config/packages.chroot/
```

The Debian package depends on `libmpv2`, recommends `yt-dlp` and suggests `libdvd-pkg`. It installs
`amade-video.desktop` (`Categories=AudioVideo;Video;Player;`), so the amadeOS front panel lists it in the
Sound & Video drawer.

## Roadmap

1. ~~Spike: libmpv in a Swing window, files, streams, DVD~~ (done, [docs/spike.md](docs/spike.md))
2. ~~Skeleton: application, player service, window, packaging, CI~~ (this version)
3. CDE-style interface: monitor frame, transport and LCD display in the amade-audio style
4. DVD title and chapter lists, recent files and URLs, playlist, drag and drop
5. Audio and subtitle tracks, fullscreen, volume, remembering the position per file
6. Windows: bundled `libmpv-2.dll`, jpackage installer

## License

amade-video is licensed under the [European Union Public Licence v1.2](LICENSE) (EUPL-1.2).

Third-party libraries keep their licences: Guice (Apache-2.0), JNA (Apache-2.0 or LGPL-2.1), SLF4J
(MIT), Logback (EPL-1.0 / LGPL-2.1); at runtime libmpv (GPL-2.0-or-later as built by Debian; LGPL
builds exist), FFmpeg, libdvdnav and, if installed by the user, libdvdcss (GPL-2.0-or-later), loaded
only when installed.
