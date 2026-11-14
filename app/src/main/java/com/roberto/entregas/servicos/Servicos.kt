package com.roberto.entregas.servicos

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.roberto.entregas.core.Coordenada
import com.roberto.entregas.core.Texto
import com.roberto.entregas.dados.AppGps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.coroutines.resume

data class Lugar(val rotulo: String, val coordenada: Coordenada)

/**
 * Busca de endereços sem chave de API:
 * 1) Geocoder do próprio Android (bom com números de casa no Brasil);
 * 2) se não achar nada ou o aparelho não tiver o serviço, Photon (OpenStreetMap).
 */
class Geocodificacao(context: Context) {

    private val ctx = context.applicationContext
    private val ptBR = Locale("pt", "BR")

    suspend fun buscar(consulta: String, perto: Coordenada?): List<Lugar> = withContext(Dispatchers.IO) {
        val q = consulta.trim()
        if (q.length < 3) return@withContext emptyList()
        val doAndroid = runCatching { geocoder(q) }.getOrDefault(emptyList())
        doAndroid.ifEmpty { runCatching { photon(q, perto) }.getOrDefault(emptyList()) }
    }

    suspend fun endereco(c: Coordenada): String? = withContext(Dispatchers.IO) {
        runCatching { reversoAndroid(c) }.getOrNull() ?: runCatching { reversoPhoton(c) }.getOrNull()
    }

    @Suppress("DEPRECATION")
    private fun geocoder(q: String): List<Lugar> {
        if (!Geocoder.isPresent()) return emptyList()
        val lista = Geocoder(ctx, ptBR).getFromLocationName(q, 5) ?: return emptyList()
        return lista.filter { it.hasLatitude() && it.hasLongitude() }
            .map { Lugar(rotulo(it), Coordenada(it.latitude, it.longitude)) }
            .distinctBy { it.rotulo }
    }

    @Suppress("DEPRECATION")
    private fun reversoAndroid(c: Coordenada): String? {
        if (!Geocoder.isPresent()) return null
        return Geocoder(ctx, ptBR).getFromLocation(c.lat, c.lon, 1)?.firstOrNull()?.let { rotulo(it) }
    }

    private fun rotulo(a: Address): String {
        val linha = a.getAddressLine(0)
        if (!linha.isNullOrBlank()) return linha.removeSuffix(", Brasil").removeSuffix(", Brazil")
        return listOfNotNull(
            listOfNotNull(a.thoroughfare, a.subThoroughfare).joinToString(", ").ifBlank { null },
            a.subLocality, a.locality ?: a.subAdminArea, a.adminArea
        ).joinToString(" - ")
    }

    private fun photon(q: String, perto: Coordenada?): List<Lugar> {
        val url = buildString {
            append("https://photon.komoot.io/api/?limit=5&q=")
            append(URLEncoder.encode(q, "UTF-8"))
            if (perto != null) append("&lat=${perto.lat}&lon=${perto.lon}")
        }
        val json = baixar(url) ?: return emptyList()
        val feats = json.optJSONArray("features") ?: return emptyList()
        val res = ArrayList<Lugar>()
        for (i in 0 until feats.length()) {
            val f = feats.optJSONObject(i) ?: continue
            val coords = f.optJSONObject("geometry")?.optJSONArray("coordinates") ?: continue
            val p = f.optJSONObject("properties") ?: continue
            res.add(Lugar(rotuloPhoton(p), Coordenada(coords.getDouble(1), coords.getDouble(0))))
        }
        return res.distinctBy { it.rotulo }
    }

    private fun reversoPhoton(c: Coordenada): String? {
        val json = baixar("https://photon.komoot.io/reverse?lat=${c.lat}&lon=${c.lon}") ?: return null
        val p = json.optJSONArray("features")?.optJSONObject(0)?.optJSONObject("properties") ?: return null
        return rotuloPhoton(p)
    }

    private fun rotuloPhoton(p: JSONObject): String {
        fun s(k: String) = p.optString(k, "").trim().ifEmpty { null }
        val rua = s("street")?.let { r -> s("housenumber")?.let { "$r, $it" } ?: r }
        val nome = s("name")
        val primeiro = when {
            rua != null && nome != null && nome != s("street") -> "$nome, $rua"
            rua != null -> rua
            else -> nome
        }
        return listOfNotNull(primeiro, s("district") ?: s("locality"), s("city"), s("state"))
            .distinct().joinToString(" - ")
    }

    private fun baixar(url: String): JSONObject? {
        val con = URL(url).openConnection() as HttpURLConnection
        return try {
            con.connectTimeout = 8000
            con.readTimeout = 8000
            con.setRequestProperty("User-Agent", "RotaEntregas/1.0 (Android; app de rotas de entrega)")
            if (con.responseCode != 200) null
            else JSONObject(con.inputStream.bufferedReader().use { it.readText() })
        } finally {
            con.disconnect()
        }
    }
}

/** Localização atual pelo LocationManager do Android (sem Google Play Services, compatível com F-Droid). */
object Localizacao {

    fun temPermissao(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    suspend fun atual(context: Context): Coordenada? {
        val ctx = context.applicationContext
        if (!temPermissao(ctx)) return null
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val provedores = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        if (provedores.isEmpty()) return null

        fun ultimas() = provedores.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }

        val recente = ultimas().filter { System.currentTimeMillis() - it.time < 2 * 60_000 }.maxByOrNull { it.time }
        if (recente != null) return Coordenada(recente.latitude, recente.longitude)

        var fresca: Location? = null
        for (p in provedores) {
            fresca = withTimeoutOrNull(10_000) { umaLeitura(ctx, lm, p) }
            if (fresca != null) break
        }
        val loc = fresca ?: ultimas().maxByOrNull { it.time } ?: return null
        return Coordenada(loc.latitude, loc.longitude)
    }

    @SuppressLint("MissingPermission")
    private suspend fun umaLeitura(ctx: Context, lm: LocationManager, provedor: String): Location? =
        suspendCancellableCoroutine { cont ->
            if (Build.VERSION.SDK_INT >= 30) {
                val sinal = android.os.CancellationSignal()
                lm.getCurrentLocation(provedor, sinal, ctx.mainExecutor) { loc ->
                    if (cont.isActive) cont.resume(loc)
                }
                cont.invokeOnCancellation { sinal.cancel() }
            } else {
                val ouvinte = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        lm.removeUpdates(this)
                        if (cont.isActive) cont.resume(location)
                    }

                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {}

                    @Deprecated("Obrigatório em Android 9 ou anterior")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                }
                @Suppress("DEPRECATION")
                lm.requestSingleUpdate(provedor, ouvinte, Looper.getMainLooper())
                cont.invokeOnCancellation { lm.removeUpdates(ouvinte) }
            }
        }
}

/** Abre o GPS, o discador e o WhatsApp. */
object Acoes {

    private fun fmt(v: Double) = String.format(Locale.ROOT, "%.6f", v)

    fun navegar(ctx: Context, destino: Coordenada, rotulo: String, app: AppGps) {
        val lat = fmt(destino.lat)
        val lon = fmt(destino.lon)
        val geo = Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lon?q=$lat,$lon(${Uri.encode(rotulo)})"))
        val escolher = Intent.createChooser(geo, "Navegar com")
        val intent = when (app) {
            AppGps.GOOGLE_MAPS -> Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$lat,$lon"))
                .setPackage("com.google.android.apps.maps")
            AppGps.WAZE -> Intent(Intent.ACTION_VIEW, Uri.parse("https://waze.com/ul?ll=$lat,$lon&navigate=yes"))
                .setPackage("com.waze")
            AppGps.PERGUNTAR -> escolher
        }
        if (!abrir(ctx, intent)) {
            if (app != AppGps.PERGUNTAR) {
                Toast.makeText(ctx, "${app.rotulo} não encontrado. Escolha outro app.", Toast.LENGTH_SHORT).show()
            }
            if (!abrir(ctx, escolher)) Toast.makeText(ctx, "Nenhum app de mapa instalado.", Toast.LENGTH_LONG).show()
        }
    }

    fun ligar(ctx: Context, telefone: String) {
        abrir(ctx, Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(telefone))))
    }

    fun whatsapp(ctx: Context, telefone: String, mensagem: String) {
        val numero = Texto.telefoneWhatsApp(telefone)
        if (numero == null) {
            Toast.makeText(ctx, "Telefone inválido para WhatsApp. Use DDD + número.", Toast.LENGTH_LONG).show()
            return
        }
        val ok = abrir(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$numero?text=${Uri.encode(mensagem)}")))
        if (!ok) Toast.makeText(ctx, "WhatsApp não encontrado.", Toast.LENGTH_SHORT).show()
    }

    private fun abrir(ctx: Context, intent: Intent): Boolean = try {
        ctx.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }
}
