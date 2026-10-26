package xyz.mpv.rex.ui.player.controls

import xyz.mpv.rex.preferences.PlayerButton

/** Grouping only; RenderPlayerButton remains the single action implementation. */
fun playerControlGroup(button: PlayerButton): String = when (button) {
  PlayerButton.AUDIO_TRACK -> "Audio"
  PlayerButton.SUBTITLES -> "Subtitles"
  PlayerButton.ASPECT_RATIO, PlayerButton.VIDEO_ZOOM, PlayerButton.SCREEN_ROTATION,
  PlayerButton.MIRROR, PlayerButton.VERTICAL_FLIP -> "Video"
  PlayerButton.DECODER, PlayerButton.VIDEO_QUALITY, PlayerButton.AMBIENT_MODE -> "Advanced"
  else -> "Playback"
}
