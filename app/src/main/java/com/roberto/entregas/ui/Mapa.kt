package com.roberto.entregas.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.roberto.entregas.core.Coordenada
import com.roberto.entregas.core.Status
import com.roberto.entregas.ui.tema.CoresOficina
import com.roberto.entregas.ui.tema.Oficina
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.TilesOverlay
import java.io.File

data class MarcadorMapa(
    val numero: Int,
    val coordenada: Coordenada,
    val status: Status,
    val urgente: Boolean,
    val proxima: Boolean
)

/** Configura o osmdroid uma vez (chamar no onCreate da Activity). */
fun configurarMapa(ctx: Context) {
    val conf = Configuration.getInstance()
    conf.load(ctx, ctx.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
    conf.userAgentValue = ctx.packageName
    conf.osmdroidBasePath = File(ctx.filesDir, "osmdroid")
    conf.osmdroidTileCache = File(ctx.cacheDir, "osmdroid-tiles")
}

/** Guarda o que já foi enquadrado, para não ficar dando zoom a cada mudança de status. */
private class EstadoMapa { var ultimaChave: String = "" }

/**
 * Mapa OpenStreetMap com as paradas numeradas e a linha (reta) na ordem de visita das pendentes.
 * No tema escuro os blocos do mapa são invertidos para combinar com o Oficina.
 */
@Composable
fun MapaRota(partida: Coordenada?, marcadores: List<MarcadorMapa>, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val c = Oficina.cores
    val estado = remember { EstadoMapa() }
    val mapa = remember {
        MapView(ctx).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            isTilesScaledToDpi = true
            minZoomLevel = 4.0
            maxZoomLevel = 19.0
            controller.setZoom(11.0)
            controller.setCenter(GeoPoint(-23.5505, -46.6333))
        }
    }

    DisposableEffect(mapa) {
        mapa.onResume()
        onDispose {
            mapa.onPause()
            mapa.onDetach()
        }
    }

    AndroidView(
        factory = { mapa },
        modifier = modifier,
        update = { m -> desenhar(m, ctx, c, partida, marcadores, estado) }
    )
}

private fun desenhar(
    m: MapView,
    ctx: Context,
    c: CoresOficina,
    partida: Coordenada?,
    marcadores: List<MarcadorMapa>,
    estado: EstadoMapa
) {
    m.overlays.clear()
    m.overlayManager.tilesOverlay.setColorFilter(if (c.escuro) TilesOverlay.INVERT_COLORS else null)

    val dens = ctx.resources.displayMetrics.density
    val pend = marcadores.filter { it.status == Status.PENDENTE }

    val pontosLinha = listOfNotNull(partida?.let { GeoPoint(it.lat, it.lon) }) +
        pend.map { GeoPoint(it.coordenada.lat, it.coordenada.lon) }
    if (pontosLinha.size >= 2) {
        val linha = Polyline(m)
        linha.setPoints(pontosLinha)
        linha.outlinePaint.color = c.accent.toArgb()
        linha.outlinePaint.strokeWidth = 3f * dens
        linha.outlinePaint.isAntiAlias = true
        m.overlays.add(linha)
    }

    // Concluídas por baixo, a próxima por cima de todas.
    val ordenados = marcadores.sortedWith(compareBy({ it.status == Status.PENDENTE }, { it.proxima }))
    for (mk in ordenados) {
        val (fundo, texto) = when {
            mk.proxima -> c.accent to Color.White
            mk.status == Status.ENTREGUE -> c.verde to Color.Black
            mk.status == Status.FALHA -> c.perigo to Color.White
            else -> c.invertBg to c.invertFg
        }
        val marcador = Marker(m)
        marcador.position = GeoPoint(mk.coordenada.lat, mk.coordenada.lon)
        marcador.icon = BitmapDrawable(
            ctx.resources,
            icone(mk.numero.toString(), fundo.toArgb(), texto.toArgb(), if (mk.urgente) c.ambar.toArgb() else null, dens, mk.proxima)
        )
        marcador.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        marcador.setOnMarkerClickListener { _, _ -> true }
        m.overlays.add(marcador)
    }

    if (partida != null) {
        val p = Marker(m)
        p.position = GeoPoint(partida.lat, partida.lon)
        p.icon = BitmapDrawable(ctx.resources, icone("P", c.bg.toArgb(), c.ink.toArgb(), c.ink.toArgb(), dens, false))
        p.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        p.setOnMarkerClickListener { _, _ -> true }
        m.overlays.add(p)
    }

    m.overlays.add(CopyrightOverlay(ctx))

    // Reenquadra só quando o conjunto de pontos muda.
    val todos = marcadores.map { GeoPoint(it.coordenada.lat, it.coordenada.lon) } +
        listOfNotNull(partida?.let { GeoPoint(it.lat, it.lon) })
    val chave = todos.joinToString(";") { "%.5f,%.5f".format(it.latitude, it.longitude) }
    if (chave != estado.ultimaChave && todos.isNotEmpty()) {
        estado.ultimaChave = chave
        m.post {
            if (todos.size == 1) {
                m.controller.setZoom(15.0)
                m.controller.setCenter(todos.first())
            } else if (m.width > 0 && m.height > 0) {
                m.zoomToBoundingBox(BoundingBox.fromGeoPoints(todos), false, (40 * dens).toInt())
            }
        }
    }
    m.invalidate()
}

/** Quadrado com número (cantos retos, como no Oficina). */
private fun icone(texto: String, fundo: Int, frente: Int, borda: Int?, dens: Float, grande: Boolean): Bitmap {
    val lado = ((if (grande) 30 else 24) * dens).toInt()
    val bmp = Bitmap.createBitmap(lado, lado, Bitmap.Config.ARGB_8888)
    val cv = Canvas(bmp)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.color = fundo
    cv.drawRect(0f, 0f, lado.toFloat(), lado.toFloat(), p)
    if (borda != null) {
        p.style = Paint.Style.STROKE
        p.strokeWidth = 2f * dens
        p.color = borda
        val m = p.strokeWidth / 2
        cv.drawRect(m, m, lado - m, lado - m, p)
        p.style = Paint.Style.FILL
    }
    p.color = frente
    p.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    p.textAlign = Paint.Align.CENTER
    p.textSize = (if (texto.length > 2) 9f else 11f) * dens * (if (grande) 1.15f else 1f)
    val y = lado / 2f - (p.descent() + p.ascent()) / 2f
    cv.drawText(texto, lado / 2f, y, p)
    return bmp
}
