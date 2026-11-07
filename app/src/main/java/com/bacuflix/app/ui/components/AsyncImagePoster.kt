package com.bacuflix.app.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.bacuflix.app.ui.theme.FlixColors
import com.bacuflix.app.util.ImageLoader

@Composable
fun AsyncImagePoster(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    alternateUrl: String? = null,
    contentScale: ContentScale = ContentScale.Crop,
    maxDimension: Int = 720
) {
    val context = LocalContext.current
    var bitmap by remember(url, alternateUrl) {
        mutableStateOf<Bitmap?>(ImageLoader.getCached(url))
    }
    var isLoading by remember(url, alternateUrl) {
        mutableStateOf(bitmap == null)
    }

    LaunchedEffect(url, alternateUrl) {
        if (bitmap == null) {
            isLoading = true
            bitmap = ImageLoader.loadBitmap(
                context = context,
                url = url,
                alternateUrl = alternateUrl,
                maxDimension = maxDimension
            )
            isLoading = false
        }
    }

    Box(
        modifier = modifier.background(FlixColors.BgCard),
        contentAlignment = Alignment.Center
    ) {
        val currentBitmap = bitmap
        if (currentBitmap != null) {
            Image(
                bitmap = currentBitmap.asImageBitmap(),
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize()
            )
        } else if (isLoading) {
            SkeletonShimmerBox(modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
fun SkeletonShimmerBox(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )
    val brush = Brush.linearGradient(
        colors = listOf(
            Color(0xFF161822),
            Color(0xFF262A3B),
            Color(0xFF161822)
        ),
        start = Offset(translateAnim - 300f, translateAnim - 300f),
        end = Offset(translateAnim, translateAnim)
    )
    Box(modifier = modifier.background(brush))
}
