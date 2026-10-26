package xyz.mpv.rex.ui.browser.home

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import xyz.mpv.rex.domain.media.model.Video

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun BiscuitHeroCarousel(items: List<HomeMedia>, videos: Map<String, Video>, visible: Boolean,
  configuration: HeroConfiguration = HeroConfiguration(), onPlay: (HomeMedia) -> Unit, onInfo: (Video) -> Unit) {
  val pager = rememberPagerState { items.size }
  val dragging by pager.interactionSource.collectIsDraggedAsState()
  var touching by remember { mutableStateOf(false) }
  var interaction by remember { mutableIntStateOf(0) }
  val lifecycle = LocalLifecycleOwner.current.lifecycle
  var resumed by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
  DisposableEffect(lifecycle) {
    val observer = LifecycleEventObserver { _, _ -> resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
    lifecycle.addObserver(observer)
    onDispose { lifecycle.removeObserver(observer) }
  }
  // settledPage changes only after an animation completes; manual touch cancels
  // the pending timer immediately, including while held without dragging.
  LaunchedEffect(pager.settledPage, dragging, touching, interaction, resumed, visible, items.size, configuration) {
    if (shouldRotateHero(items.size, visible, resumed, touching, dragging)) {
      delay(configuration.intervalMillis)
      pager.animateScrollToPage(configuration.nextPage(pager.settledPage, items.size))
    }
  }
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    HorizontalPager(state = pager, key = { items[it].key }, beyondViewportPageCount = 0,
      modifier = Modifier.fillMaxWidth().pointerInput(Unit) {
        awaitEachGesture {
          awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
          touching = true
          interaction++
          try {
            var pressed = true
            while (pressed) { pressed = awaitPointerEvent(PointerEventPass.Initial).changes.any { it.pressed } }
          } finally { touching = false }
        }
      }) { page ->
      val hero = items[page]
      BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(BiscuitCinema.cardShape)) {
        // Minimum rather than fixed height allows large type to grow the content.
        val height = if (maxWidth > 700.dp) 360.dp else 440.dp
        Box(Modifier.fillMaxWidth().heightIn(min = height).background(BiscuitCinema.surface)) {
          HomeThumbnail(videos[hero.key], Modifier.matchParentSize(), true)
          Box(Modifier.matchParentSize().background(Brush.verticalGradient(BiscuitCinema.scrim)))
          Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 140.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("RECENTLY ADDED", color = BiscuitCinema.accentInk, fontSize = 11.sp, letterSpacing = 2.sp)
            Text(hero.title.substringBeforeLast('.'), color = BiscuitCinema.ink, fontSize = 30.sp, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text("${hero.folder} · ${hero.durationMs / 60000} min", color = BiscuitCinema.muted)
            if (hero.progress > 0) LinearProgressIndicator(progress = { hero.progress }, modifier = Modifier.fillMaxWidth(), color = BiscuitCinema.accent)
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
              Button(onClick = { onPlay(hero) }, colors = ButtonDefaults.buttonColors(containerColor = BiscuitCinema.accent, contentColor = BiscuitCinema.ink), modifier = Modifier.heightIn(min = 48.dp)) { Text(if (hero.resumable) "Continue" else "Play") }
              OutlinedButton(onClick = { videos[hero.key]?.let(onInfo) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("More Info", color = BiscuitCinema.ink) }
            }
          }
        }
      }
    }
    if (items.size > 1) {
      // Constant-size indicator even for thousands of items; no thousands of dots.
      Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        LinearProgressIndicator(progress = { (pager.currentPage + 1f) / items.size }, modifier = Modifier.weight(1f), color = BiscuitCinema.accent, trackColor = BiscuitCinema.surface)
        Text("${pager.currentPage + 1} / ${items.size}", color = BiscuitCinema.muted, fontSize = 12.sp)
      }
    }
  }
}
