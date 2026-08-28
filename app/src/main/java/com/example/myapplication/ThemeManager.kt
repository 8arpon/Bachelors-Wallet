package com.example.myapplication

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

data class AppThemeOption(
    val id: String,
    val name: String,
    val description: String,
    val isProOnly: Boolean,
    val previewColor: Color,
    val accentColor: Color,
    val isDarkTheme: Boolean,
    val bgColor: Color,
    val cardColor: Color,
    val headerGradient: Brush
)

object ThemeState {
    var isDark = mutableStateOf(false)
    var currentTheme = mutableStateOf("System")

    // Dynamic Reactive Colors for entire app
    var primaryAccent = mutableStateOf(Color(0xFF7B61FF))
    var background = mutableStateOf(Color(0xFFF8F9FA))
    var cardBackground = mutableStateOf(Color.White)
    var headerGradient = mutableStateOf(
        Brush.verticalGradient(listOf(Color(0xFF5E45DA), Color(0xFF7B61FF)))
    )

    val ALL_THEMES = listOf(
        AppThemeOption(
            id = "System",
            name = "System Default",
            description = "Follow device light/dark mode",
            isProOnly = false,
            previewColor = Color(0xFF7B61FF),
            accentColor = Color(0xFF7B61FF),
            isDarkTheme = false,
            bgColor = Color(0xFFF8F9FA),
            cardColor = Color.White,
            headerGradient = Brush.verticalGradient(listOf(Color(0xFF5E45DA), Color(0xFF7B61FF)))
        ),
        AppThemeOption(
            id = "Light",
            name = "Classic Light",
            description = "Clean, bright & crisp minimalism",
            isProOnly = false,
            previewColor = Color(0xFF007AFF),
            accentColor = Color(0xFF007AFF),
            isDarkTheme = false,
            bgColor = Color(0xFFF6F8FB),
            cardColor = Color.White,
            headerGradient = Brush.verticalGradient(listOf(Color(0xFF0062E0), Color(0xFF19B5FE)))
        ),
        AppThemeOption(
            id = "Dark",
            name = "Slate Dark",
            description = "Comfortable everyday dark mode",
            isProOnly = false,
            previewColor = Color(0xFF7B61FF),
            accentColor = Color(0xFF7B61FF),
            isDarkTheme = true,
            bgColor = Color(0xFF121215),
            cardColor = Color(0xFF1E1E24),
            headerGradient = Brush.verticalGradient(listOf(Color(0xFF4C3BA8), Color(0xFF6D57E6)))
        ),
        // 👑 PRO EXCLUSIVE THEMES
        AppThemeOption(
            id = "OLED_Midnight",
            name = "OLED Midnight Dark",
            description = "Pure pitch-black battery saver with electric violet",
            isProOnly = true,
            previewColor = Color(0xFF9D5CFF),
            accentColor = Color(0xFF9D5CFF),
            isDarkTheme = true,
            bgColor = Color(0xFF000000),
            cardColor = Color(0xFF0D0D12),
            headerGradient = Brush.verticalGradient(listOf(Color(0xFF220845), Color(0xFF53179E)))
        ),
        AppThemeOption(
            id = "Royal_Gold",
            name = "Royal Gold VIP",
            description = "Luxurious obsidian charcoal with gleaming metallic gold",
            isProOnly = true,
            previewColor = Color(0xFFFFD700),
            accentColor = Color(0xFFFFD700),
            isDarkTheme = true,
            bgColor = Color(0xFF0E0F12),
            cardColor = Color(0xFF18191E),
            headerGradient = Brush.verticalGradient(listOf(Color(0xFF332608), Color(0xFF735711), Color(0xFFB88E28)))
        ),
        AppThemeOption(
            id = "Ocean_Sapphire",
            name = "Ocean Sapphire",
            description = "Deep marine abyss with electric cyan neon",
            isProOnly = true,
            previewColor = Color(0xFF00E5FF),
            accentColor = Color(0xFF00E5FF),
            isDarkTheme = true,
            bgColor = Color(0xFF050B17),
            cardColor = Color(0xFF0C182E),
            headerGradient = Brush.verticalGradient(listOf(Color(0xFF061E42), Color(0xFF0C4A8A)))
        ),
        AppThemeOption(
            id = "Emerald_Forest",
            name = "Emerald Forest",
            description = "Deep soothing jungle with vibrant mint green",
            isProOnly = true,
            previewColor = Color(0xFF10B981),
            accentColor = Color(0xFF10B981),
            isDarkTheme = true,
            bgColor = Color(0xFF05120B),
            cardColor = Color(0xFF0B2115),
            headerGradient = Brush.verticalGradient(listOf(Color(0xFF082B1B), Color(0xFF145E3D)))
        ),
        AppThemeOption(
            id = "Sunset_Rose",
            name = "Sunset Neon Rose",
            description = "Midnight velvet plum infused with radiant rose",
            isProOnly = true,
            previewColor = Color(0xFFF43F5E),
            accentColor = Color(0xFFF43F5E),
            isDarkTheme = true,
            bgColor = Color(0xFF12051A),
            cardColor = Color(0xFF200B2E),
            headerGradient = Brush.verticalGradient(listOf(Color(0xFF3B0B3E), Color(0xFF821360)))
        )
    )

    fun applyTheme(context: Context, themeId: String, isSystemDark: Boolean) {
        var theme = ALL_THEMES.find { it.id == themeId } ?: ALL_THEMES[0]
        if (theme.isProOnly && !PremiumManager.isFeatureAccessible("custom_themes")) {
            theme = ALL_THEMES[0]
        }
        val effectiveDark = if (theme.id == "System") isSystemDark else theme.isDarkTheme

        isDark.value = effectiveDark
        currentTheme.value = theme.id
        primaryAccent.value = theme.accentColor

        if (theme.id == "System") {
            background.value = if (isSystemDark) Color(0xFF121212) else Color(0xFFF8F9FA)
            cardBackground.value = if (isSystemDark) Color(0xFF1E1E20) else Color.White
            headerGradient.value = if (isSystemDark) {
                Brush.verticalGradient(listOf(Color(0xFF4C3BA8), Color(0xFF6D57E6)))
            } else {
                Brush.verticalGradient(listOf(Color(0xFF5E45DA), Color(0xFF7B61FF)))
            }
        } else {
            background.value = theme.bgColor
            cardBackground.value = theme.cardColor
            headerGradient.value = theme.headerGradient
        }

        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("theme_mode", theme.id)
            .putBoolean("dark_mode", effectiveDark)
            .apply()
    }
}
