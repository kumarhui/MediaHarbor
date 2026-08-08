package com.mediaharbor.app.feature.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.mediaharbor.app.navigation.Screen
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun FloatingNavigationBar(
    currentTab: Screen,
    tabs: List<Screen>,
    onTabSelected: (Screen) -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    var barWidthPx by remember { mutableFloatStateOf(0f) }
    var barHeightPx by remember { mutableFloatStateOf(0f) }

    val selectedIndex = remember(currentTab, tabs) {
        tabs.indexOf(currentTab).coerceAtLeast(0)
    }

    val tabWidthPx = if (tabs.isNotEmpty() && barWidthPx > 0f) barWidthPx / tabs.size else 0f
    val targetOffsetX = selectedIndex * tabWidthPx

    val animatedOffsetX = remember { Animatable(targetOffsetX) }

    LaunchedEffect(selectedIndex, tabWidthPx) {
        if (tabWidthPx > 0f) {
            animatedOffsetX.animateTo(
                targetValue = selectedIndex * tabWidthPx,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 24.dp, vertical = 12.dp)
            .height(64.dp)
            .shadow(elevation = 16.dp, shape = RoundedCornerShape(32.dp), clip = false)
            .clip(RoundedCornerShape(32.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                shape = RoundedCornerShape(32.dp)
            )
            .onGloballyPositioned { coordinates ->
                barWidthPx = coordinates.size.width.toFloat()
                barHeightPx = coordinates.size.height.toFloat()
            }
            .pointerInput(tabs, tabWidthPx) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val initialIdx = (offset.x / tabWidthPx).toInt().coerceIn(0, tabs.size - 1)
                        onTabSelected(tabs[initialIdx])
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val newOffset = (animatedOffsetX.value + dragAmount.x).coerceIn(0f, (tabs.size - 1) * tabWidthPx)
                        coroutineScope.launch {
                            animatedOffsetX.snapTo(newOffset)
                        }
                        val hoverIdx = ((newOffset + tabWidthPx / 2f) / tabWidthPx).toInt().coerceIn(0, tabs.size - 1)
                        if (hoverIdx != selectedIndex) {
                            onTabSelected(tabs[hoverIdx])
                        }
                    },
                    onDragEnd = {
                        val targetIdx = ((animatedOffsetX.value + tabWidthPx / 2f) / tabWidthPx).toInt().coerceIn(0, tabs.size - 1)
                        onTabSelected(tabs[targetIdx])
                        coroutineScope.launch {
                            animatedOffsetX.animateTo(
                                targetValue = targetIdx * tabWidthPx,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                    stiffness = Spring.StiffnessMedium
                                )
                            )
                        }
                    }
                )
            }
    ) {
        if (tabWidthPx > 0f) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(animatedOffsetX.value.roundToInt(), 0) }
                    .width(with(density) { tabWidthPx.toDp() })
                    .fillMaxHeight()
                    .padding(4.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
            )
        }

        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEachIndexed { index, tab ->
                val isSelected = index == selectedIndex
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            onTabSelected(tab)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        CompositionLocalProvider(
                            LocalContentColor provides if (isSelected)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        ) {
                            tab.icon()
                            if (isSelected) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = tab.title,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}