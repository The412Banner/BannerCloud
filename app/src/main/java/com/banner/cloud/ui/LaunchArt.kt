package com.banner.cloud.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay

/**
 * While a stream connects: the game's art as a slow slideshow (each picture drifts and zooms for a few
 * seconds, then cross-fades to the next), with the title over a dark gradient. Fades out when [visible]
 * turns false (the first video frame).
 */
@Composable
fun LaunchArt(images: List<String>, title: String, visible: Boolean) {
    AnimatedVisibility(visible, enter = fadeIn(tween(400)), exit = fadeOut(tween(700))) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            var index by remember { mutableIntStateOf(0) }
            LaunchedEffect(images.size) {
                while (true) {
                    delay(SLIDE_MS)
                    if (images.size > 1) index = (index + 1) % images.size
                }
            }
            Crossfade(targetState = images.getOrNull(index % images.size.coerceAtLeast(1)), animationSpec = tween(1400),
                label = "launch-art") { url ->
                if (url != null) Slide(url, index)
            }
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
                0f to Color(0x66000000), 0.45f to Color(0x22000000), 0.78f to Color(0xB3000000), 1f to Color(0xF2000000))))
            Column(Modifier.align(Alignment.BottomStart).padding(start = 40.dp, end = 40.dp, bottom = 34.dp)) {
                Text("XBOX CLOUD GAMING", style = MaterialTheme.typography.labelSmall, color = Accent)
                Text(title, style = MaterialTheme.typography.headlineSmall, color = Color.White, maxLines = 2,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

/** One picture: a slow zoom with a sideways drift (direction alternates per slide). */
@Composable
private fun Slide(url: String, index: Int) {
    val ctx = LocalContext.current
    val progress = remember(url) { Animatable(0f) }
    LaunchedEffect(url) { progress.animateTo(1f, tween((SLIDE_MS + 1600).toInt(), easing = LinearEasing)) }
    val dir = if (index % 2 == 0) 1f else -1f
    AsyncImage(
        model = ImageRequest.Builder(ctx).data(url).crossfade(500).build(),
        contentDescription = null, contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize().graphicsLayer {
            val p = progress.value
            val s = 1.06f + 0.10f * p
            scaleX = s; scaleY = s
            translationX = dir * (p - 0.5f) * size.width * 0.04f
        })
}

private const val SLIDE_MS = 5200L
