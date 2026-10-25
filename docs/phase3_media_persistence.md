# Phase 3: remembered external audio

## Primary identity

The new audio-association tables use `audio-media-v1:` plus SHA-256 of a small canonical
locator, never a media-content hash or a filename alone. Files use canonical paths (including
symlink/dot resolution). SAF documents use authority plus document ID, so tree/direct routes
for the same document share a key. MediaStore keys retain volume/collection/item IDs and
ignore access-hint queries. Other content providers retain their URI query. Known network
connections can use connection ID plus remote path; other network media use normalized
scheme/host/default port plus path and meaningful query. URL credentials are not stored in
primary keys: the locator is hashed. Unknown loopback proxies and fd/proc descriptor locators
are not persisted. No multi-gigabyte file is read to compute an identity.

Locator identity intentionally treats a renamed/moved file as a new primary and a replacement
at the exact same locator as the same primary. Signed network URLs that change their query
may produce new keys without a separate durable connection identifier. File and unrelated
content-provider aliases are not guessed to be the same object.

This identity is scoped to Phase 3 audio associations. Existing filename-based playback,
resume, watched-state and chronological-history records/keys remain untouched for compatibility.
Their ambiguous filename-only audio choices, delays and external-source strings are retained
in the old table but are no longer used to restore audio. Re-select old Phase 2 sources once to establish a safe
locator-keyed association. This avoids importing Dune's audio into another `movie.mkv`.

## Room version 19

Migration 18→19 only creates:

- `external_audio_sources`: primary key + durable source URI, display name, persisted-read
  capability, last successful attachment time, failure marker, and last chosen stream key.
- `audio_track_preferences`: primary + source + stream fingerprint, stream metadata and signed
  millisecond delay. Empty source denotes embedded audio; it is not a URI.
- `media_audio_selection`: intended source/stream (or audio off), plus last embedded stream key.

No mpv numeric `aid`, external filename, temporary fd URI, or descriptor is stored in these
new tables. Stream fingerprints use source demux stream index/source stream ID, audio ordinal,
language/title/codec/channels/sample rate; these are distinct from mpv's runtime track IDs.
The existing legacy playback table may retain its old aid values for compatibility;
Phase 3 never trusts those numbers and does not write new runtime IDs into that legacy field.

An application-lifetime FIFO writer serializes transactional snapshots and reads, so reopening
waits for already queued saves and exiting a ViewModel does not cancel them. Existing tables
are not deleted/rebuilt. Destructive migration fallback has been removed. Storage errors are
logged; as with any app, force-killing before a queued disk write finishes can lose that last edit.

## Restore and delay behavior

Video loading and default track selection finish normally first. Associations load separately,
then the selected external source is attached with the Phase 2 controller. Confirmation still
requires matching mpv track-list entries. The stream matcher prefers exact fingerprints, then
confident metadata/index matches, then same-language/unknown-language fallback. Known conflicting
languages are excluded; if all candidates clearly conflict, original audio remains selected.
A matching title outranks a reused numeric stream index. Unknown-language saved streams can
fall back to the first usable audio stream.

Only the selected remembered source is auto-attached. Other remembered sources are available
through Use in the audio sheet and are attached lazily. Successful selection restores that
track's delay; unseen external tracks start at 0 ms. Original tracks keep their own remembered
offsets, falling back to the existing global embedded-audio default. Live delay controls are
unchanged and changes are captured via `audio-delay` observation, before switches, and before
exit-state saves. A low-confidence stream fallback does not inherit an unrelated track's offset.

Missing files, revoked URI access and unavailable providers do not gate initial video playback.
Original audio/its delay remain usable, a concise message is shown, and the failed association
is retained for retry. Successful reselection clears the failure marker. Newer user actions and
primary-file changes cancel/supersede old restores; stale stream IDs are checked against the
current source before selection. Phase 2 read-only/persistable-grant handling is reused.

## Minimal management UI

The existing audio sheet lists remembered sources, including unavailable/temporary-access
status, with Use, Replace and Forget. Replace keeps the old association until the new source's
stream is successfully selected; cancelling/failing does not delete the old association.
Forget removes that association and its track preferences; if active, it returns to embedded
audio (or audio off if there is none). Original files are never deleted by these controls.

## Verification and device tests

Run the requested single-worker debug build/unit tests, `git diff --check`, and
`python scripts/verify_audio_migration.py`. The Python check executes the actual migration SQL
against exported Room 18/19 schemas with stdlib SQLite, validates all columns/types/nullability/
primary keys, and preserves seeded legacy playback/history and all original tables. This is not
an Android-instrumented Room MigrationTestHelper test; real on-device upgrade remains necessary.

1. Install over the Phase 2 app using the same signing key; preserve its data. Confirm old
   history/playlists/settings still exist. Re-select an old external source once for Phase 3.
2. Play `/Movies/Dune/movie.mkv`, choose a Hindi EAC3 5.1 stream from a multi-track video, set
   +420 ms, exit normally, reopen and verify exact source/stream/delay and unchanged picture.
3. Force-stop/relaunch after allowing a moment for saving; repeat step 2 through SAF and MediaStore.
4. Open `/Downloads/movie.mkv` with the same filename; verify Dune's association does not leak.
5. Set original English 0 ms, Hindi +420 ms and Japanese -180 ms. Switch repeatedly, including
   two tracks in one external container, and reopen. Verify each delay and selected stream.
6. Use original embedded audio or turn audio off, then reopen. Verify it does not auto-select
   a previously used external source against your new choice.
7. Test file/SD/SAF sources, opaque document IDs, non-ASCII names and temporary-grant providers.
   Revoke permission, delete/move a source or unmount SD; reopen and confirm video continues,
   concise feedback appears, and the remembered row offers retry/replacement.
8. Restore a multi-track source whose stream ordering/IDs changed. Verify metadata-based
   language/title matching; with all saved-language streams removed, verify no silent switch
   to a clearly different language. Check fallback offset behavior explicitly.
9. Use/Replace/Forget remembered sources; cancel/fail replacement and verify old setup survives.
   Forget active and inactive sources; ensure both media files remain intact.
10. Change primary videos while restore is pending, and make a manual audio choice during restore.
    Verify no stale source, delay, message or association is applied to the new video.
11. Exercise pause/resume, PiP, background/miniplayer, rotation and return. Headless-only library
    audio launches and dynamically changing web URLs are not new persistence integrations here.
12. Compare original input sizes/checksums before and after; no media rewrite/export is performed.
