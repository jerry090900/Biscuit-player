# Biscuit cinematic Home

Home is a Compose presentation over the existing media repository, Room playback state/history,
shared thumbnail repository and MediaUtils.playFile launch path. It adds no playback/history table.
Library remains the existing folder/media browser. Existing configurable tabs (including Shorts,
You, recents and playlists) keep their numeric indices; the new Home is appended internally but
rendered first in navigation. Settings and Network are also direct Home actions. This intentionally
preserves existing shortcuts/features instead of replacing the entire navigation system.

The hero prioritizes meaningful unfinished progress, then recently added, recently played, then
any local item. Continue Watching excludes tiny, completed and watched entries; legacy filename
progress is accepted only for unambiguous filenames. Duplicate filenames are conservatively omitted
from Continue Watching rather than assigned another file's progress. Resume follows the existing
player's resume/ask preferences, not a new forced-seek implementation.

Movies uses explicit Movies/Films folders; episodic filenames use SxxExx or Episode-number hints.
TV and Anime are not inferred as genres. Unknown media remains in Local Library. Empty rows are
omitted; no indexed media shows browse/access/network actions. Local thumbnails are sufficient;
no internet metadata, posters or copyrighted sample artwork are required.

BiscuitCinema holds near-black/slate surfaces, warm amber accent, scrims, card shape and spacing.
Home has reusable media (portrait/landscape) and folder cards. A tall phone hero becomes shallower
on wide screens; rows remain horizontally scrollable. Status-bar appearance is restored on exit.
Player themes and global user appearance settings are unchanged.

Library discovery runs on IO initially, after explicit refresh/library-change events or permission
changes. Projection/sorting runs on Default. Room flows update progress/history without rescanning.
Rows are lazy, keyed and capped at 30 items; Library retains full browsing. Thumbnails share existing
caches with at most two Home requests at once and bounded 320/768-pixel requests. Failed thumbnails
retain a readable surface/title. No full-resolution frames are requested by Home.

Verification: build/unit suite, whitespace checks and protected-path diff checks. No Android emulator
or attached device is available in this workspace, so actual rendered screenshots, native decoder
behavior and scroll frame timing require device testing. New labels currently use English.

Device checklist:
- Empty library and denied/revoked access: browse/grant access/network actions recover correctly.
- Populated library: hero priority, Continue/Play, More Info, actual duration and progress.
- Duplicate filenames: no misattributed Continue Watching; original player resume policy preserved.
- Clearly named episodes, Movies folder and ambiguous files: conservative rows, no empty sections.
- Cards and collections launch actual media/folders; new files appear after refresh or library events.
- Every legacy tab, custom tab order, selection mode/back, settings, playlists, Jellyfin and network.
- Phase 2 external audio and Phase 3 source/stream/delay persistence from a Home-launched video.
- Phone portrait/landscape, tablet, large font, gesture navigation, light app theme and return.
- Long thumbnail rows, missing thumbnails/corrupt videos, repeated navigation, background/miniplayer.
- No original files modified; database remains 19 and dependencies/native libraries unchanged.
