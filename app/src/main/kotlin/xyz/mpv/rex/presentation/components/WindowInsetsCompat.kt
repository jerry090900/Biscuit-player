package xyz.mpv.rex.presentation.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only as composeOnly

/** Keeps PlayerSheet source compact while delegating to Compose's WindowInsets.only extension. */
internal fun WindowInsets.only(sides: WindowInsetsSides): WindowInsets = this.composeOnly(sides)
