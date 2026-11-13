package dev.calendarquiet.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val DayColors = lightColorScheme(
    primary = Color(0xFF263D31), onPrimary = Color(0xFFF7F8F2), primaryContainer = Color(0xFFE5EDDA), onPrimaryContainer = Color(0xFF203329),
    secondary = Color(0xFF5F715F), secondaryContainer = Color(0xFFE9EDE3), onSecondaryContainer = Color(0xFF28382D),
    tertiary = Color(0xFF817045), tertiaryContainer = Color(0xFFF0E8D5),
    background = Color(0xFFF7F6F0), onBackground = Color(0xFF1D2B23),
    surface = Color(0xFFF7F6F0), onSurface = Color(0xFF1D2B23), surfaceVariant = Color(0xFFEFEEE6), onSurfaceVariant = Color(0xFF5F6D61),
    outline = Color(0xFFA8B0A3), outlineVariant = Color(0xFFDFE2D6), surfaceContainer = Color(0xFFEFEEE6),
)
private val NightColors = darkColorScheme(
    primary = Color(0xFFD2E8AF), onPrimary = Color(0xFF213323), primaryContainer = Color(0xFF2F4232), onPrimaryContainer = Color(0xFFE5F0D7),
    secondary = Color(0xFFB9CBB2), secondaryContainer = Color(0xFF293B2F), onSecondaryContainer = Color(0xFFE1EBDD),
    tertiary = Color(0xFFD8C696), tertiaryContainer = Color(0xFF443D2A),
    background = Color(0xFF111A15), onBackground = Color(0xFFEEF2E6),
    surface = Color(0xFF111A15), onSurface = Color(0xFFEEF2E6), surfaceVariant = Color(0xFF202D23), onSurfaceVariant = Color(0xFFACB9A9),
    outline = Color(0xFF6F7F6C), outlineVariant = Color(0xFF344334), surfaceContainer = Color(0xFF1B281F),
)
private val type = Typography(
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.8).sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.5).sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 10.sp, lineHeight = 14.sp),
)

@Composable
fun TimeLeftTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) NightColors else DayColors, typography = type,
        shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(24.dp)), content = content)
}
