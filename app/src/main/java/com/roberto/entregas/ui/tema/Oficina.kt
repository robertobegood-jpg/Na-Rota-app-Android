@file:OptIn(ExperimentalTextApi::class)

package com.roberto.entregas.ui.tema

import android.content.Context
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** Cores do sistema Oficina (tema escuro é o principal). */
@Immutable
data class CoresOficina(
    val escuro: Boolean,
    val bg: Color,
    val elevado: Color,
    val fundo: Color,
    val ink: Color,
    val inkForte: Color,
    val muted: Color,
    val faint: Color,
    val line: Color,
    val lineForte: Color,
    val accent: Color,
    val accentInk: Color,
    val invertBg: Color,
    val invertFg: Color,
    val perigo: Color,
    val verde: Color,
    val ambar: Color
) {
    companion object {
        val Escuro = CoresOficina(
            escuro = true,
            bg = Color(0xFF121316),
            elevado = Color(0xFF101014),
            fundo = Color(0xFF060607),
            ink = Color(0xFFF2F1EE),
            inkForte = Color(0xFFFFFFFF),
            muted = Color(0x99F2F1EE),
            faint = Color(0x61F2F1EE),
            line = Color(0x26F2F1EE),
            lineForte = Color(0x66F2F1EE),
            accent = Color(0xFF2D81FF),
            accentInk = Color(0xFF74AAFF),
            invertBg = Color(0xFFFFFFFF),
            invertFg = Color(0xFF0B0B0C),
            perigo = Color(0xFFFF6259),
            verde = Color(0xFF5EE089),
            ambar = Color(0xFFFFC14D)
        )

        val Claro = CoresOficina(
            escuro = false,
            bg = Color(0xFFF2F1EE),
            elevado = Color(0xFFFAF9F7),
            fundo = Color(0xFFE9E8E4),
            ink = Color(0xFF101014),
            inkForte = Color(0xFF000000),
            muted = Color(0x9E101014),
            faint = Color(0x6B101014),
            line = Color(0x29101014),
            lineForte = Color(0x66101014),
            accent = Color(0xFF2D81FF),
            accentInk = Color(0xFF1659C4),
            invertBg = Color(0xFF101014),
            invertFg = Color(0xFFF2F1EE),
            perigo = Color(0xFFB3261E),
            verde = Color(0xFF136938),
            ambar = Color(0xFF8A5800)
        )
    }
}

@Immutable
data class FamiliasOficina(
    val display: FontFamily,
    val serif: FontFamily,
    val corpo: FontFamily,
    val mono: FontFamily
)

@Immutable
data class TiposOficina(val familias: FamiliasOficina) {
    private val peso620 = FontWeight(620)

    val displayLg = TextStyle(fontFamily = familias.display, fontWeight = peso620, fontSize = 34.sp, lineHeight = 36.sp, letterSpacing = (-0.03).em)
    val display = TextStyle(fontFamily = familias.display, fontWeight = peso620, fontSize = 28.sp, lineHeight = 30.sp, letterSpacing = (-0.03).em)
    val heading = TextStyle(fontFamily = familias.display, fontWeight = peso620, fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = (-0.02).em)
    val bodyLg = TextStyle(fontFamily = familias.corpo, fontWeight = FontWeight.Normal, fontSize = 18.sp, lineHeight = 28.sp)
    val body = TextStyle(fontFamily = familias.corpo, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp)
    val bodySm = TextStyle(fontFamily = familias.corpo, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp)
    val hud = TextStyle(fontFamily = familias.mono, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.15.em)
    val botao = TextStyle(fontFamily = familias.mono, fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.12.em)
    val numero = TextStyle(fontFamily = familias.mono, fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 26.sp)
    val serifItalico = TextStyle(fontFamily = familias.serif, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Normal)
}

private val LocalCores = staticCompositionLocalOf { CoresOficina.Escuro }
private val LocalTipos = staticCompositionLocalOf {
    TiposOficina(FamiliasOficina(FontFamily.SansSerif, FontFamily.Serif, FontFamily.SansSerif, FontFamily.Monospace))
}

object Oficina {
    val cores: CoresOficina
        @Composable get() = LocalCores.current
    val tipos: TiposOficina
        @Composable get() = LocalTipos.current
}

/**
 * Carrega as fontes de assets/fonts/ (Archivo, Instrument Serif, Inter e Geist Mono).
 * Se algum arquivo não estiver lá, usa a fonte do sistema no lugar, sem travar o app.
 */
object Fontes {

    fun carregar(context: Context): FamiliasOficina {
        val am = context.assets
        val arquivos = runCatching { am.list("fonts")?.toList() }.getOrNull().orEmpty()
            .filter { it.endsWith(".ttf", true) || it.endsWith(".otf", true) }

        fun achar(prefixo: String, italico: Boolean): String? = arquivos
            .filter { it.lowercase().filter { ch -> ch.isLetterOrDigit() }.startsWith(prefixo) }
            .sortedBy { if (it.contains("italic", ignoreCase = true) == italico) 0 else 1 }
            .firstOrNull()
            ?.let { "fonts/$it" }

        val display = achar("archivo", false)?.let { caminho ->
            FontFamily(
                Font(
                    caminho, am, FontWeight(620), FontStyle.Normal,
                    FontVariation.Settings(FontVariation.weight(620), FontVariation.width(118f))
                )
            )
        } ?: FontFamily.SansSerif

        val corpo = achar("inter", false)?.let { caminho ->
            FontFamily(listOf(400, 500, 600, 700).map { w ->
                Font(caminho, am, FontWeight(w), FontStyle.Normal, FontVariation.Settings(FontVariation.weight(w)))
            })
        } ?: FontFamily.SansSerif

        val mono = achar("geistmono", false)?.let { caminho ->
            FontFamily(listOf(400, 500, 700).map { w ->
                Font(caminho, am, FontWeight(w), FontStyle.Normal, FontVariation.Settings(FontVariation.weight(w)))
            })
        } ?: FontFamily.Monospace

        val serif = achar("instrumentserif", true)?.let { caminho ->
            FontFamily(Font(caminho, am, FontWeight.Normal, FontStyle.Italic))
        } ?: FontFamily.Serif

        return FamiliasOficina(display = display, serif = serif, corpo = corpo, mono = mono)
    }
}

@Composable
fun TemaOficina(escuro: Boolean, conteudo: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val familias = remember { Fontes.carregar(ctx.applicationContext) }
    val tipos = remember(familias) { TiposOficina(familias) }
    val c = if (escuro) CoresOficina.Escuro else CoresOficina.Claro

    val esquema = if (escuro) {
        darkColorScheme(
            primary = c.ink, onPrimary = c.bg, secondary = c.accent, onSecondary = c.invertFg,
            background = c.bg, onBackground = c.ink, surface = c.bg, onSurface = c.ink,
            surfaceVariant = c.fundo, onSurfaceVariant = c.muted, surfaceContainerHigh = c.elevado,
            outline = c.lineForte, outlineVariant = c.line, error = c.perigo
        )
    } else {
        lightColorScheme(
            primary = c.ink, onPrimary = c.bg, secondary = c.accent, onSecondary = c.invertFg,
            background = c.bg, onBackground = c.ink, surface = c.bg, onSurface = c.ink,
            surfaceVariant = c.fundo, onSurfaceVariant = c.muted, surfaceContainerHigh = c.elevado,
            outline = c.lineForte, outlineVariant = c.line, error = c.perigo
        )
    }
    val reto = RoundedCornerShape(0.dp)

    CompositionLocalProvider(LocalCores provides c, LocalTipos provides tipos) {
        MaterialTheme(
            colorScheme = esquema,
            shapes = Shapes(extraSmall = reto, small = reto, medium = reto, large = reto, extraLarge = reto),
            typography = Typography(
                bodyLarge = tipos.body, bodyMedium = tipos.bodySm, bodySmall = tipos.bodySm,
                titleLarge = tipos.heading, titleMedium = tipos.body, labelLarge = tipos.botao,
                labelMedium = tipos.hud, labelSmall = tipos.hud
            ),
            content = conteudo
        )
    }
}
