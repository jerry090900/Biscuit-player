# Advanced music menu

Branch: `library-vlc-style`. Open music using the existing Audio tab, expand the
compact music bar, then tap the new three-dot button beside Stop. The existing
layout, compact/swipe behavior and transport buttons are retained.

## Architecture and preservation

- `MusicSettingsMenu` presents eleven scrollable menu entries and responsive
  Material dialogs. It neither launches another player nor takes audio focus.
- `MusicAdvancedController` operates through a small `MusicEngine` adapter over
  the same process-global mpv session owned by `HeadlessPlaybackController`.
  New file-loaded and session-end hooks are limited to music settings. The
  existing music/video presentation flag is now published before synchronous
  session reuse, so first loading after stopped/video playback is classified
  correctly. Starting a headless video clears only this menu's music overrides.
- No engine/JNI, dependency, identity, video player, scanning, external-audio
  association, or existing navigation changes. Latest folder long-press commits
  through `1c8aecd1` were preserved before implementation.
- With no user music overrides, no speed/pitch/filter changes are made. Explicit
  music overrides use new preference keys. Original speed/pitch are restored and
  only the `biscuit_music_eq` filter is removed before handing playback to video.
- Timers use an application-scoped coroutine and monotonic elapsed time, not the
  music sheet lifecycle. Cancel, stop, service destruction and handoff cancel
  timers. No polling task runs when no timer is active. No extra audio session,
  alarm permission or continuously running new service is introduced.

## Features

1. **Sleep Timer:** 5/10/15/30/45/60 minute presets, custom whole minutes 1–1440,
   remaining countdown and cancellation. Expiry sets mpv pause; it does not close
   playback or discard the queue. Continues across track changes and sheet
   dismissal. A countdown is intentionally session-only: it cannot unexpectedly
   pause unrelated playback after process death or restart.
2. **Playback Speed:** 0.5–2× slider and seven requested presets. Optional pitch
   correction uses mpv's existing property. Explicit music speed/pitch persist
   independently of existing video preferences.
3. **Jump to Time:** separate hours/minutes/seconds, validated against known track
   duration. Uses absolute exact mpv seeking; rejects unseekable streams.
4. **Equalizer:** ±12 dB bass, treble and five octave bands (60/230/910/3600/14000 Hz),
   Normal/Rock/Pop/Classical/Jazz presets and freely editable Custom state. Apply
   uses a labeled mpv lavfi chain, leaving all unrelated `af` filters intact.
   Positive gains include conservative preamp headroom. Reset disables/removes
   this filter and stores flat settings. Enabled settings restore on file load.
   Passthrough output is rejected; missing/rejected filters report an error and
   do not save a successful setting. Actual output support depends on bundled
   mpv/FFmpeg and the device route; no independent Android Equalizer is created.
5. **Bookmarks:** add current position with optional name; jump, rename and delete.
   Durable identity reuses `primaryAudioIdentity` / `MediaAudioIdentity` (SAF
   document IDs, canonical file URIs, MediaStore/provider IDs, durable network
   locators). Same filenames in different locations remain independent.
   Transient FD/proxy sources cannot create bookmarks. No files are modified.
6. **A–B Repeat:** set A/B at the current position after seeking as needed; rejects
   B ≤ A or points outside duration. Native mpv loops playback. Clear and track
   changes/session end disable this menu's points; no permanent numeric mpv IDs.
7. **Save Playlist:** snapshots the existing headless queue in its original order,
   including deliberate repeated entries. New `PlaylistDao.saveMusicQueue`
   transaction rejects blank/overlong/duplicate names (trimmed/case-insensitive)
   and transient transport locators. Existing PlaylistEntity/PlaylistItemEntity
   tables store the result; no second playlist system or original file changes.
8. **Share the Track Info:** Android text/plain chooser, current mpv title/artist/
   album and duration where available. No media files are sent.
9. **Control Settings:** reuses the existing `disableMediaButtons` preference,
   explicitly explains shared headset/Bluetooth/notification/lock-screen scope,
   offers existing shuffle/repeat behavior and Android notification settings.
   Android determines the controls shown by its system UI. No fake independent
   switches are provided for capabilities the current media session shares.
10. **Audio Player Tips:** compact/expanded behavior, seeking, speed, EQ, timer,
    repeat, bookmarks, system controls and stopping.
11. **Playlist Tips:** queue creation, next/previous, shuffle/repeat, saving,
    You → Playlists and original-file access requirements.

## Data

Existing playback/history/association Room database **remains version 19**, with
its schema unchanged. Bookmarks use a separate new Room database,
`biscuit_music_bookmarks.db`, version 1, with one indexed `music_bookmarks` table.
Its explicit initial schema is checked in. There is no destructive migration or
legacy data rewrite. Preferences use `biscuit_music_*` primitive keys only.
Playlists are saved atomically in the existing database, preventing partial
playlists and repeated-tap duplicates. Storage failures are surfaced in dialogs.

## Automated and device verification

Run the full existing command and `git diff --check`, plus
`python3 scripts/verify_music_settings.py`. New controller tests cover background
countdown expiry/cancel/replacement/track switch, speed/pitch persistence and
handoff cleanup, exact seeking and invalid input, A–B validation/clear, EQ label
isolation/reset/persistence/unsupported/passthrough, and stale post-stop actions.
Pure tests cover names, durable queue sources, source identity and EQ validation.
The SQLite check uses Room's exported schema and verifies bookmark persistence,
scoped rename/delete, source isolation, and existing data preservation.

Real-device checks (not replaceable by JVM tests):

1. Install the new debug APK over the existing debug build. Confirm library,
   playlists/history and remembered external-audio associations remain intact.
2. Play audio, expand the compact bar, open the overflow and scroll to all eleven
   items. Close each dialog: seek, pause/play, previous/next, shuffle/repeat and
   repeated sheet up/down swipes must still work. Rotate and use large text.
3. Start a custom one-minute sleep timer, lock the screen/background the app,
   switch tracks, and confirm expiry pauses once. Test Cancel and replacement.
   Stop/restart playback and verify no old timer fires.
4. Test every speed preset and slider; compare pitch correction on/off. Restart
   the app, reopen music, and confirm chosen music speed/pitch are restored.
   Play video afterward and verify its previous speed/pitch behavior.
5. Jump to 00:00:00 and a known later time; reject negative, 60-second fields and
   times past duration. Repeat with an unseekable stream (clear error).
6. Apply each EQ preset, bass/treble and custom bands; audibly confirm the output.
   Restart/reopen music to verify persistence, then Reset. Verify any existing
   normalization/custom filters still work. Test passthrough/unsupported output;
   failure must report an error without stopping playback.
7. Add two named bookmarks, jump, rename and delete; restart and reopen the same
   original file. Test identical filenames in different directories and a SAF
   source. Temporary network proxy URLs must explain why saving is unavailable.
8. Set A, seek forward, set B; confirm native looping. Reject reversed B; clear.
   Switch tracks and verify the old loop no longer applies.
9. Save a multi-track queue; find it in You → Playlists and play it. Double-tap
   Save or repeat the name with different case/spacing; no duplicate should appear.
   Try read-only/missing media after saving: original playlist behavior remains.
10. Share title/artist/album/duration from tagged and untagged files; verify chooser
    output. Nothing about the media file itself should change.
11. Toggle shared external controls; test headset/Bluetooth, notification and lock
    screen while enabled/disabled; re-enable. Test shuffle/repeat and Android
    notification settings. Read both Tips dialogs.
12. Check notification/background playback during screen off, track switches and
    incoming audio-focus interruptions. Run normal video/subtitle/gesture/PiP and
    external audio + per-track delay restoration regression checks.

No connected device/emulator is available in this executor. A passing build and
JVM suite do not establish audible DSP, touch, Bluetooth, leak/profiling or
background-device correctness. These require the checks above on a phone.

## Changed files

- `app/src/main/kotlin/xyz/mpv/rex/database/dao/PlaylistDao.kt`
- `app/src/main/kotlin/xyz/mpv/rex/di/DomainModule.kt`
- `app/src/main/kotlin/xyz/mpv/rex/ui/browser/miniplayer/MusicPlayerScreen.kt`
- `app/src/main/kotlin/xyz/mpv/rex/ui/player/HeadlessPlaybackController.kt`
- `app/src/main/kotlin/xyz/mpv/rex/ui/player/MediaPlaybackService.kt`
- `app/src/main/kotlin/xyz/mpv/rex/utils/media/MediaUtils.kt`
- `app/src/main/kotlin/xyz/mpv/rex/ui/browser/miniplayer/settings/MusicSettingsModel.kt`
- `app/src/main/kotlin/xyz/mpv/rex/ui/browser/miniplayer/settings/MusicAdvancedController.kt`
- `app/src/main/kotlin/xyz/mpv/rex/ui/browser/miniplayer/settings/MusicSettingsStorage.kt`
- `app/src/main/kotlin/xyz/mpv/rex/ui/browser/miniplayer/settings/MusicSettingsMenu.kt`
- `app/src/main/res/values/strings_music_settings.xml`
- `app/schemas/xyz.mpv.rex.ui.browser.miniplayer.settings.MusicBookmarksDatabase/1.json`
- `app/src/test/kotlin/xyz/mpv/rex/ui/browser/miniplayer/settings/MusicAdvancedControllerTest.kt`
- `app/src/test/kotlin/xyz/mpv/rex/ui/browser/miniplayer/settings/MusicSettingsModelTest.kt`
- `app/src/test/kotlin/xyz/mpv/rex/ui/browser/miniplayer/settings/MusicPreferencesTest.kt`
- `scripts/verify_music_settings.py`
- `docs/advanced_music_settings.md`

## Local verification result

- `GRADLE_OPTS=-Dorg.gradle.workers.max=1 ./gradlew assembleDebug test --console=plain`:
  BUILD SUCCESSFUL; 479 tests, 0 failures, 0 errors, 0 skipped (27 new tests).
- `git diff --check`: passed.
- `python3 scripts/verify_music_settings.py`: passed.
- `python3 scripts/verify_audio_migration.py`: passed; existing version 19 unchanged.
- Host FFmpeg accepted the exact Rock lavfi graph and produced a changed test
  signal (910 Hz sine RMS: flat 0.088369, Rock with headroom 0.045199). This is
  supplemental DSP syntax/output validation, not an Android runtime test.
- Bundled ARM64 `libavfilter.so` contains bass, treble and equalizer filter names;
  actual device output still requires audible verification.
- No new dependencies. No native/JNI, build configuration or app ID changes.
