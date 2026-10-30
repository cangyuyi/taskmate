package com.edy.taskmate.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val Accent = Color(0xFF4C68EC)
val AccentLight = Color(0xFF6D80F5)
val Red = Color(0xFFFF3B30)
val Orange = Color(0xFFFF9500)
val Green = Color(0xFF34C759)
val Indigo = Color(0xFF5856D6)

val HeroGradient = Brush.linearGradient(listOf(Color(0xFF665CEB), Color(0xFF3D78F2)))

private val lightColors = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE5EAFF),
    onPrimaryContainer = Color(0xFF1B2A6B),
    background = Color(0xFFF2F2F7),
    onBackground = Color.Black,
    surface = Color.White,
    onSurface = Color.Black,
    surfaceVariant = Color(0xFFF2F2F7),
    onSurfaceVariant = Color(0xFF3C3C43),
    outline = Color(0x4D3C3C43),
    error = Red,
    onError = Color.White
)

private val darkColors = darkColorScheme(
    primary = AccentLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF2B3670),
    onPrimaryContainer = Color(0xFFDCE2FF),
    background = Color.Black,
    onBackground = Color.White,
    surface = Color(0xFF1C1C1E),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF2C2C2E),
    onSurfaceVariant = Color(0xFFE5E5EA),
    outline = Color(0x66EBEBF5),
    error = Color(0xFFFF453A),
    onError = Color.White
)

private val appShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun TaskMateTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) darkColors else lightColors,
        shapes = appShapes,
        content = content
    )
}

@Composable
fun accentColor(): Color = MaterialTheme.colorScheme.primary

@Composable
fun cardColor(): Color = MaterialTheme.colorScheme.surface

@Composable
fun secondaryLabel(): Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)

@Composable
fun tertiaryLabel(): Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.32f)

@Composable
fun hairline(): Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)

@Composable
fun subtleFill(): Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)

@Composable
fun redColor(): Color = if (isSystemInDarkTheme()) Color(0xFFFF453A) else Red

@Composable
fun greenColor(): Color = if (isSystemInDarkTheme()) Color(0xFF30D158) else Green

@Composable
fun orangeColor(): Color = if (isSystemInDarkTheme()) Color(0xFFFF9F0A) else Orange