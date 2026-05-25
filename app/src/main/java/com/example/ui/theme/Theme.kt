package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
      primary = HighDensityPrimary,
      secondary = HighDensitySecondary,
      tertiary = HighDensityTertiary,
      background = Color(0xFF110F15),
      surface = Color(0xFF1D1B20),
      onPrimary = Color.White,
      onSecondary = Color(0xFF21005D),
      onBackground = Color(0xFFFDF7FF),
      onSurface = Color(0xFFFDF7FF)
  )

private val LightColorScheme =
  lightColorScheme(
      primary = HighDensityPrimary,
      secondary = HighDensitySecondary,
      tertiary = HighDensityTertiary,
      background = HighDensityBG,
      surface = HighDensitySurface,
      onPrimary = Color.White,
      onSecondary = Color(0xFF21005D),
      onBackground = HighDensityText,
      onSurface = HighDensityText
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Set default to false to allow our gorgeous custom theme to shine through
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
