# Phase 3.5 correction pass

All work stays on phase3.5-cinematic-home; no master merge/release/tag.

## Navigation and discovery

Fixed primary destinations: Home, Library, Audio, You. Old optional/reorderable
bottom-tab settings are intentionally replaced by this product structure; stored
legacy preferences and the internal Shorts code remain intact. Shorts is not
linked from navigation, You, Settings, or settings search. Library keeps the
existing folder browser and its features. You links directly to existing Network
Storage / Streaming, Playlists, Recently Played, Appearance, Settings and About.
Networking, including its protocols and Jellyfin integration, is unchanged.

Audio is a read-only MediaStore.Audio projection into the existing Video model,
using existing MediaUtils playback (including the user's mini-player setting).
It requests READ_MEDIA_AUDIO on Android 13+, legacy read permission on older
versions, and handles denial/revocation without blocking video use. Audio-only
folders are included regardless of the video browser's show-audio preference.
Recently Added is a bounded preview; All Audio is lazy and unrestricted. Folder
chips filter by full parent path, not display name. Titles, duration, format and
folder are shown with a music fallback icon; no artwork/metadata is invented.
Explicit refresh and activity resume re-query off the UI thread. No metadata
frame decoding is needed for this list. Android/provider indexing determines
which audio types appear: MP3/AAC/M4A/FLAC/WAV/OGG/Opus/AC3/EAC3/DTS can play via
mpv when indexed/supported. Unindexed and SAF-only audio stays available through
the existing file browser/picker; this is not a recursive new scanner.

## Home, source policy and image budgets

BiscuitCinema tokens: accent #BD2946, background #09090B, surface #1C1C21,
primary text #F7F7FA, secondary #AEAEB8, accessible red text #F07A90. The red is distinct from Netflix red.
Home's header contains only BISCUIT and a Checkroom icon opening Appearance
directly. Main navigation uses the same red; player seek bars use this accent.
Launcher image and splash from the preceding branding commit are unchanged.

HeroConfiguration isolates source (RecentlyAdded) and interval (6000ms).
HomeCatalogBuilder sorts all unique available videos by date added, tie-breaking
by durable presentation key. It does not truncate the hero to row preview limits.
The pager uses stable media keys and saveable PagerState, lazy page composition,
zero additional offscreen pages, bounded 768x432 hero / 320x200 card requests to
the shared thumbnail cache, and a shared two-request semaphore. This keeps a
metadata list for all hero candidates, not all decoded frames. Home still uses
the existing read-only library/history projections and background dispatchers.

Settled page, touch, drag, visibility, lifecycle and collection changes govern
the timer. User touch cancels the pending timer immediately; release/settle
starts a full new interval. Rotation pauses offscreen/in background and for
zero/one item. Last page loops to first. The page-count/progress indicator has
constant size even for thousands of candidates. Hero height is a minimum so
large text can expand; action buttons wrap at narrow widths. Pager state survives
rotation through Compose's saved state. Hero source and timer remain easy to
replace without rewriting visual layout.

Continue Watching uses existing unfinished playback state. Recently Added row
omits those continuing items to reduce repeated cards. Movies/TV classification
remains conservative. Empty rows are omitted; the redundant Recently Played and
folder shelves have moved to You / Library. Empty-library actions remain useful.

## Player presentation, preserved capabilities

Center controls use existing seekBy(-10), pause/play and seekBy(10). The compact
primary row opens existing Speed, conditional Playlist, Audio & Subtitles, More.
The Audio & Subtitles menu links audio tracks/external source management, audio
delay, subtitle tracks, subtitle delay and subtitle settings. While the menu is
open, its controls timeout is refreshed so auto-hide does not dismiss the menu.

Advanced controls move into More's Controls tab, grouped Playback, Video, Audio,
Subtitles and Advanced through RenderPlayerButton (single existing action
implementation). It includes all applicable buttons regardless of old on-screen
row preferences so stale layout customization cannot hide a moved control.
More's Aesthetics, Gestures, Settings and Interaction tabs remain available,
including video filters, tone mapping/HDR, decoder/player settings, frame seek,
zoom/aspect/crop/rotation, chapters, repeat, background playback and PiP. Legacy
custom row arrangements are superseded by the concise primary row; their stored
preferences are retained, and More ordering remains honored. Custom script
controls and all gesture handlers are unchanged. Prev/next media remain in the
existing playlist/session/gesture paths; center buttons now explicitly seek time.

No changes to Phase 2 attachment controller, Phase 3 association/delay managers,
media identity, Room schema 19, migrations, media sessions, playback service,
networking implementations, dependencies, native engine or JNI. No new playback,
export, AI or metadata services are added. Existing branding/credits are retained.

## Verification boundary and phone checks

JVM tests cover hero source coverage/order/dedup, wrapping and pause eligibility,
row membership, plus absence of Shorts from normal settings search. Existing
playback/audio/persistence tests are run with the full suite. No emulator/device
is available, so interactive behavior and decoder performance need real-device
validation; static/build checks are not presented as device testing.

Build the ARM64 Test APK workflow from phase3.5-cinematic-home. Test:

1. Exactly Home / Library / Audio / You, including with old custom tab preferences.
   No Shorts in You, Settings or search; Network only through You (empty-library
   network action is also retained). Open each You destination and SMB/WebDAV/
   FTP/SFTP/Jellyfin/URL playback as applicable.
2. Empty video library and absent/denied/revoked audio permission; allow audio,
   deny permanently then use App permissions, resume and refresh after file changes.
   Test audio-only folders, same-named folders, formats above where indexed, and
   the user's normal player/mini-player/background choice.
3. Red Home/nav/seek accents; no header text shortcuts; shirt opens Appearance
   directly. Test light/dark system modes, large type, portrait/tablet/rotation.
4. Add at least 20 videos: all participate in date order. Swipe immediately,
   wait six seconds, hold touch for >6 seconds, drag/fling, check full timer reset,
   final-page looping, one-video case, scroll hero offscreen and background app.
   Revisit and rotate to check pager position; watch thumbnail memory/scrolling.
5. Hero Continue/Play/More Info; existing resume prompt and unfinished progress.
   Home rows must remain useful without duplicate unfinished cards in Recently Added.
6. Portrait/landscape player, notch/cutout, rounded corners, large type, ±10 seeks,
   Play/Pause, red seek dragging, time display, Speed and conditional Playlist.
7. Audio & Subtitles: attach audio and a multitrack video; switch embedded/external
   tracks; set different delays, exit/reopen, verify exact track/offset restored;
   replace/forget and missing-source fallback. Check subtitle selection/delay/style.
8. More groups/tabs: frame seeking, chapters, aspect, zoom/pan, crop, rotation,
   repeat, decoder, HDR/tone mapping, gestures, brightness/volume, custom controls,
   PiP transitions, background/media session and network playback.
9. Existing supplied Biscuit launcher icon under round/square masks and task icon;
   force-stop/cold launch, warm launch and Recents return: black Biscuit splash,
   no inherited REX/MPV branding flash, then cinematic Home.
