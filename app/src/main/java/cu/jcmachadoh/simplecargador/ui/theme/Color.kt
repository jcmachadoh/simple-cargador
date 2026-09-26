package cu.jcmachadoh.simplecargador.ui.theme

import androidx.compose.ui.graphics.Color

// ── Acento de identidad ────────────────────────────────────────────────
// Verde energía: carga, electricidad, progreso. Un solo acento, usado
// solo en acciones primarias, selección y estados de carga.
val VerdeEnergia = Color(0xFF2BD97C)
val AmbarCarga = Color(0xFFE8A13C)
val RojoCritico = Color(0xFFD9573F)
val AzulInfo = Color(0xFF5B8DEF)

// ── Esquema claro: neutros con tinte verde sutil (nunca blanco puro) ──
val md_light_primary = Color(0xFF146C43)
val md_light_onPrimary = Color(0xFFF8FBF8)
val md_light_primaryContainer = Color(0xFFB7EFCD)
val md_light_onPrimaryContainer = Color(0xFF0B2917)
val md_light_secondary = Color(0xFF4B6355)
val md_light_onSecondary = Color(0xFFF8FBF8)
val md_light_secondaryContainer = Color(0xFFDCE8DF)
val md_light_onSecondaryContainer = Color(0xFF0C1F15)
val md_light_tertiary = Color(0xFF3D6371)
val md_light_onTertiary = Color(0xFFF8FBF8)
val md_light_error = Color(0xFFB3271E)
val md_light_onError = Color(0xFFF8FBF8)
val md_light_surface = Color(0xFFF7FAF5)
val md_light_onSurface = Color(0xFF181D18)
val md_light_surfaceVariant = Color(0xFFE3E8E0)
val md_light_onSurfaceVariant = Color(0xFF3F473F)
val md_light_outline = Color(0xFF6E776E)
val md_light_outlineVariant = Color(0xFFC6CEC4)

// ── Esquema oscuro: superficies profundas con tinte verde (nunca #000) ─
val md_dark_primary = Color(0xFF7EDC9F)
val md_dark_onPrimary = Color(0xFF00391D)
val md_dark_primaryContainer = Color(0xFF10522D)
val md_dark_onPrimaryContainer = Color(0xFFB7EFCD)
val md_dark_secondary = Color(0xFFB3CCBA)
val md_dark_onSecondary = Color(0xFF1E3326)
val md_dark_secondaryContainer = Color(0xFF34493B)
val md_dark_onSecondaryContainer = Color(0xFFDCE8DF)
val md_dark_tertiary = Color(0xFFA5CCD9)
val md_dark_error = Color(0xFFFFB4A9)
val md_dark_surface = Color(0xFF101510)
val md_dark_onSurface = Color(0xFFDFE4DC)
val md_dark_surfaceVariant = Color(0xFF232922)
val md_dark_onSurfaceVariant = Color(0xFFBFC9BE)
val md_dark_outline = Color(0xFF899288)
val md_dark_outlineVariant = Color(0xFF3C443B)

// ── Estados semánticos estandarizados (producto) ──────────────────────
val state_charging_light = Color(0xFF146C43)
val state_charging_dark = Color(0xFF7EDC9F)
val state_low_light = Color(0xFFB3271E)
val state_low_dark = Color(0xFFFFB4A9)
val state_mid_light = Color(0xFF9A6200)
val state_mid_dark = Color(0xFFF3B95D)

// ── Color del anillo por nivel de batería (con degradado para notar el progreso) ──
// Rojo < 25%, naranja 25–75%, verde 75–100%, con tono más vivo a medida que sube.
val batt_red_deep = Color(0xFF7A1305)
val batt_red_bright = Color(0xFFE5482F)
val batt_orange_deep = Color(0xFFC75B12)
val batt_orange_bright = Color(0xFFF2A93B)
val batt_green_deep = Color(0xFF9BC400)
val batt_green_bright = Color(0xFF2BD97C)
