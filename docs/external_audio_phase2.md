# Phase 2: external audio playback

Use the player audio-track sheet's existing add button to select an audio or video file.
The custom file picker accepts both sets of media extensions; its system-picker button
uses Android OpenDocument for audio/video MIME types and common application container MIME types.
A single confirmed audio stream is selected automatically. Multiple streams open a chooser
showing track numbers and available title/language/codec metadata. Cancelling that chooser
leaves the prior audio selected. All confirmed tracks remain in the existing audio sheet,
alongside the original embedded audio.

## Implementation boundaries

- Playback stays in the existing mpv instance. `audio-add` uses `cached` so discovery does
  not replace the selected audio. Matching external audio entries in `track-list` must
  stabilize before selection via `aid` or a success message. Existing `audio-delay` is untouched.
- Content URI strings remain the durable source identity; no real-path conversion is used.
  Persistable read permission is attempted, with temporary-grant fallback for providers that
  do not support it. The source is opened read-only and its descriptor is explicitly owned.
  `fd://` values are transient transport paths and are never saved as associations.
- A mutex serializes local attachments. Repeated normalized identities reuse the existing
  source and track IDs. Local file dot/symlink aliases normalize to canonical file URIs;
  unrelated provider URIs are not guessed to be the same physical file.
- File-start, file-replacement and exit paths invalidate pending operations and chooser tokens.
  Confirmed URIs survive pending-operation cancellation for the existing exit-state save.
  Full session reset/release closes owned descriptors. Unconfirmed/stale tracks are removed
  by exact transient source path; embedded tracks are never removed.
- Native attachment runs off the UI thread without holding the state lock during demuxer IO.
  Kotlin cancellation cannot interrupt synchronous native/provider IO already executing;
  generation/path checks prevent a late completion from selecting audio in a different video.
- No database migration, engine/JNI change, export or video rewriting is involved.

## Current limits

- Actual compatibility depends on the bundled mpv decoders and the provider's read/seek support.
  Discovery has an eight-second polling timeout after the native add command returns; a
  blocking native/provider open itself cannot be interrupted by that timeout.
- Switching can require buffering and is not guaranteed gapless. A constant delay cannot
  correct differing edits, playback rates or progressive drift between two sources.
- Multiple distinct content URIs may refer to the same bytes; deduplication is by URI identity,
  not an expensive media-content hash.
- Existing persistence remains unchanged: it saves source URI lists and one audio delay per
  primary media record. A silent restore picks the first audio stream from the last selected
  restored source; exact stream/offset associations are Phase 3 work.
- Revoked or temporary permissions can prevent later restoration. The user can reselect the
  source. Confirmed track presence does not prove a damaged stream will decode fully.
- Existing remote yt-dlp alternate-audio behavior is retained; this change focuses on local
  file/document selection. New English strings currently use Android's translation fallback.
- Device/provider/multi-track playback must be verified on ARM64 hardware; JVM tests use a
  fake mpv port and cannot establish native decoding or Android permission behavior.

## ARM64 manual checks

1. Play Video A with embedded audio. Add MP3, FLAC, AAC/M4A and a WAV separately. Verify the
   image, position and original embedded tracks remain available; switch back to each original.
2. Pick Video B with one audio stream (MP4, then MKV). Verify only B's audio is used with A's
   picture. Seek, pause/resume, adjust speed, and check A's duration/end behavior with B both
   shorter and longer than A. Verify no second video replaces A.
3. Pick a video/MKA with two or more distinctly labeled/language tracks. Confirm the chooser,
   select each stream, repeat the same selection, cancel a chooser, and switch among all audio
   entries. Verify no duplicated entries and no unexpected mute on repeated source selection.
4. Use both custom filesystem and system document pickers. Include internal storage, SD card,
   spaces/non-ASCII filenames and an opaque content URI. Reopen the video after force-stop
   with a persistable provider; repeat after revoking access and confirm graceful recovery.
5. Add a silent video, a corrupt file renamed .mp4, an unreadable/deleted file and a source
   whose provider denies access. Verify a failure message, no crash and no false success.
6. Start a slow provider attachment and switch A to C immediately. Also change the primary
   video while the picker/track chooser is open. Verify no late track selection/toast for C.
7. Set positive and negative audio delay, reset it, and switch original/external tracks.
   Verify existing controls work and the current video-level offset behavior remains intact.
8. Rotate, lock/unlock, use PiP, background/miniplayer and return, then exit and reopen.
   Verify playback/lifecycle continuity and record provider-specific failures.
9. Compare source file size and SHA-256 before and after the above. Neither input is written
   by this feature; no output/remux should be created.
