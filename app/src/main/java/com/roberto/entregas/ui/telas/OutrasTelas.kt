package com.roberto.entregas.ui.telas

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.roberto.entregas.BuildConfig
import com.roberto.entregas.core.Cafezinho
import com.roberto.entregas.core.Operacoes
import com.roberto.entregas.core.Pix
import com.roberto.entregas.core.Rota
import com.roberto.entregas.core.Status
import com.roberto.entregas.core.Texto
import com.roberto.entregas.dados.AppGps
import com.roberto.entregas.dados.Config
import com.roberto.entregas.dados.Tema
import com.roberto.entregas.servicos.Lugar
import com.roberto.entregas.ui.componentes.Botao
import com.roberto.entregas.ui.componentes.CabecalhoAba
import com.roberto.entregas.ui.componentes.CabecalhoInterno
import com.roberto.entregas.ui.componentes.Campo
import com.roberto.entregas.ui.componentes.Dialogo
import com.roberto.entregas.ui.componentes.FaixaIndicadores
import com.roberto.entregas.ui.componentes.Hud
import com.roberto.entregas.ui.componentes.Interruptor
import com.roberto.entregas.ui.componentes.Ponto
import com.roberto.entregas.ui.componentes.Segmentado
import com.roberto.entregas.ui.componentes.TipoBotao
import com.roberto.entregas.ui.componentes.Titulo
import com.roberto.entregas.ui.componentes.Vazio
import com.roberto.entregas.ui.componentes.linhaBase
import com.roberto.entregas.ui.tema.Oficina
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dataHora = SimpleDateFormat("dd/MM/yyyy · HH:mm", Locale("pt", "BR"))
private val hora = SimpleDateFormat("HH:mm", Locale("pt", "BR"))

// =================================================================== partida

@Composable
fun PartidaTela(
    rota: Rota,
    buscar: suspend (String) -> List<Lugar>,
    minhaPosicao: suspend () -> Lugar?,
    onDefinir: (Lugar?) -> Unit,
    onVoltar: () -> Unit
) {
    val c = Oficina.cores
    val inicial = remember { rota.partida?.let { Lugar(rota.partidaRotulo, it) } }
    var texto by remember { mutableStateOf(rota.partidaRotulo) }
    var lugar by remember { mutableStateOf(inicial) }
    var fixandoAqui by remember { mutableStateOf(false) }
    var erro by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(fixandoAqui) {
        if (!fixandoAqui) return@LaunchedEffect
        val l = minhaPosicao()
        fixandoAqui = false
        if (l != null) { lugar = l; texto = l.rotulo; erro = null } else erro = "Não deu para ler a localização."
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        CabecalhoInterno("Partida", "De onde você *sai*?", onVoltar)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Sem partida fixa, o app usa a sua localização no momento de otimizar. Fixe um endereço se você sempre sai do mesmo lugar (loja, depósito, casa).",
                style = Oficina.tipos.body, color = c.muted
            )
            Botao("Usar minha localização atual", { onDefinir(null); onVoltar() }, Modifier.fillMaxWidth(),
                tipo = if (rota.partida == null) TipoBotao.PRIMARIO else TipoBotao.SECUNDARIO)
            Spacer(Modifier.height(8.dp))
            Hud("Ou fixe um endereço")
            Column { BuscaEndereco(texto, { texto = it }, lugar, { lugar = it; texto = it.rotulo }, buscar, "Endereço de partida") }
            Botao(if (fixandoAqui) "Lendo…" else "Fixar onde estou agora", { fixandoAqui = true }, Modifier.fillMaxWidth(),
                tipo = TipoBotao.FANTASMA, habilitado = !fixandoAqui)
            erro?.let { Text(it, style = Oficina.tipos.bodySm, color = c.perigo) }
        }
        val l = lugar
        Botao(
            "Fixar esta partida", { if (l != null) { onDefinir(l); onVoltar() } },
            Modifier.fillMaxWidth().padding(16.dp),
            habilitado = l != null && l.rotulo == texto
        )
    }
}

// =================================================================== histórico

@Composable
fun HistoricoTela(historico: List<Rota>, onAbrir: (String) -> Unit) {
    val c = Oficina.cores
    Column(Modifier.fillMaxSize()) {
        CabecalhoAba("03/Histórico · ${historico.size} rotas", "Rotas *feitas*.")
        if (historico.isEmpty()) {
            Vazio("", "Quando você finalizar uma rota, ela aparece aqui com o resumo do dia.")
            return@Column
        }
        val totalEntregues = historico.sumOf { it.entregues }
        val totalFalhas = historico.sumOf { it.falhas }
        FaixaIndicadores(
            listOf(
                Triple("$totalEntregues", "Entregues", c.verde),
                Triple("$totalFalhas", "Não entregues", null),
                Triple(
                    if (totalEntregues + totalFalhas == 0) "—" else "${totalEntregues * 100 / (totalEntregues + totalFalhas)}%",
                    "Sucesso", null
                )
            ),
            Modifier.padding(horizontal = 16.dp)
        )
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp)) {
            items(historico, key = { it.id }) { r ->
                Row(
                    Modifier.fillMaxWidth().linhaBase(c.line).clickable { onAbrir(r.id) }.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Hud(dataHora.format(Date(r.iniciadaEm ?: r.criadaEm)))
                        Text(
                            "${r.entregues} entregues" + (if (r.falhas > 0) " · ${r.falhas} não entregues" else ""),
                            style = Oficina.tipos.body, color = c.ink
                        )
                        val partes = listOfNotNull(
                            Operacoes.duracaoMinutos(r)?.let { Texto.duracao(it) },
                            if (r.kmEstimado > 0) "~" + Texto.km(r.kmEstimado) + " previstos" else null
                        )
                        if (partes.isNotEmpty()) Text(partes.joinToString(" · "), style = Oficina.tipos.bodySm, color = c.muted)
                    }
                    Text("→", style = Oficina.tipos.body, color = c.muted)
                }
            }
        }
    }
}

@Composable
fun DetalheHistoricoTela(rota: Rota, onApagar: () -> Unit, onVoltar: () -> Unit) {
    val c = Oficina.cores
    var confirmar by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        CabecalhoInterno(dataHora.format(Date(rota.iniciadaEm ?: rota.criadaEm)), "Resumo da *rota*.", onVoltar)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp)) {
            item {
                FaixaIndicadores(
                    listOf(
                        Triple("${rota.entregues}", "Entregues", c.verde),
                        Triple("${rota.falhas}", "Não entregues", if (rota.falhas > 0) c.perigo else null),
                        Triple(Operacoes.duracaoMinutos(rota)?.let { Texto.duracao(it) } ?: "—", "Duração", null)
                    )
                )
                Spacer(Modifier.height(12.dp))
                if (rota.partidaRotulo.isNotBlank()) {
                    Hud("Partida")
                    Text(rota.partidaRotulo, style = Oficina.tipos.bodySm, color = c.ink)
                    Spacer(Modifier.height(12.dp))
                }
            }
            itemsIndexed(rota.entregas, key = { _, e -> e.id }) { i, e ->
                Row(Modifier.fillMaxWidth().linhaBase(c.line).padding(vertical = 10.dp)) {
                    Text((i + 1).toString().padStart(2, '0'), style = Oficina.tipos.hud, color = c.faint, modifier = Modifier.width(32.dp).padding(top = 3.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Ponto(when (e.status) { Status.ENTREGUE -> c.verde; Status.FALHA -> c.perigo; else -> c.lineForte })
                            Spacer(Modifier.width(6.dp))
                            Hud(
                                when (e.status) {
                                    Status.ENTREGUE -> "Entregue"
                                    Status.FALHA -> "Não entregue · ${e.motivoFalha}"
                                    else -> "Pendente"
                                } + (e.concluidaEm?.let { " · " + hora.format(Date(it)) } ?: "")
                            )
                        }
                        Text(e.endereco, style = Oficina.tipos.bodySm, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        val extra = listOf(e.destinatario, if (e.pedido.isBlank()) "" else "Pedido ${e.pedido}").filter { it.isNotBlank() }
                        if (extra.isNotEmpty()) Text(extra.joinToString(" · "), style = Oficina.tipos.bodySm, color = c.muted)
                    }
                }
            }
            item {
                Spacer(Modifier.height(16.dp))
                Botao("Apagar do histórico", { confirmar = true }, Modifier.fillMaxWidth(), tipo = TipoBotao.PERIGO)
            }
        }
    }
    if (confirmar) {
        Dialogo(
            titulo = "Apagar esta *rota*?",
            onFechar = { confirmar = false },
            confirmar = { Botao("Apagar", { confirmar = false; onApagar() }, tipo = TipoBotao.PERIGO) },
            cancelar = { Botao("Cancelar", { confirmar = false }, tipo = TipoBotao.FANTASMA) }
        ) { Text("Ela some do histórico e dos totais.", style = Oficina.tipos.body, color = c.ink) }
    }
}

// =================================================================== mais

@Composable
fun MaisTela(
    config: Config,
    onConfig: (Config) -> Unit,
    onExportar: () -> Unit,
    onImportar: () -> Unit,
    onCafezinho: () -> Unit
) {
    val c = Oficina.cores
    var mensagem by remember(config.mensagemWhatsApp) { mutableStateOf(config.mensagemWhatsApp) }
    var confirmarImportar by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        CabecalhoAba("04/Mais", "Do seu *jeito*.")
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Hud("GPS para navegar")
            Segmentado(AppGps.entries.toList(), config.gps, { it.rotulo }, { onConfig(config.copy(gps = it)) })
            Text(
                when (config.gps) {
                    AppGps.PERGUNTAR -> "O Android mostra a lista de apps de mapa a cada parada."
                    AppGps.GOOGLE_MAPS -> "Abre direto no Google Maps em modo navegação."
                    AppGps.WAZE -> "Abre direto no Waze em modo navegação."
                },
                style = Oficina.tipos.bodySm, color = c.muted
            )

            Spacer(Modifier.height(8.dp))
            Hud("Tema")
            Segmentado(Tema.entries.toList(), config.tema, { it.rotulo }, { onConfig(config.copy(tema = it)) })

            Interruptor("Mostrar mapa na rota", config.mostrarMapa, { onConfig(config.copy(mostrarMapa = it)) })

            Spacer(Modifier.height(8.dp))
            Hud("Mensagem do WhatsApp")
            Campo(mensagem, { mensagem = it }, "Texto enviado ao cliente", umaLinha = false, minLinhas = 2)
            if (mensagem != config.mensagemWhatsApp) {
                Botao("Salvar mensagem", { onConfig(config.copy(mensagemWhatsApp = mensagem.trim().ifBlank { Config.MENSAGEM_PADRAO })) },
                    Modifier.fillMaxWidth(), tipo = TipoBotao.SECUNDARIO)
            }

            Spacer(Modifier.height(8.dp))
            Hud("Backup")
            Text("Salva a rota atual e o histórico num arquivo. Use para trocar de celular ou guardar uma cópia.", style = Oficina.tipos.bodySm, color = c.muted)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Botao("Salvar backup", onExportar, Modifier.weight(1f), tipo = TipoBotao.SECUNDARIO)
                Botao("Restaurar", { confirmarImportar = true }, Modifier.weight(1f), tipo = TipoBotao.SECUNDARIO)
            }

            if (Cafezinho.configurado) {
                Spacer(Modifier.height(8.dp))
                Botao("Pagar um cafezinho ao dev", onCafezinho, Modifier.fillMaxWidth())
            }

            Spacer(Modifier.height(16.dp))
            Hud("Sobre")
            Text(
                "Na Rota ${BuildConfig.VERSION_NAME}. Código aberto sob a licença GPL-3.0. " +
                    "Mapas © colaboradores do OpenStreetMap. A otimização é feita no próprio celular, sem enviar a sua rota para ninguém.",
                style = Oficina.tipos.bodySm, color = c.muted
            )
            Spacer(Modifier.height(16.dp))
        }
    }

    if (confirmarImportar) {
        Dialogo(
            titulo = "Restaurar *backup*?",
            onFechar = { confirmarImportar = false },
            confirmar = { Botao("Escolher arquivo", { confirmarImportar = false; onImportar() }) },
            cancelar = { Botao("Cancelar", { confirmarImportar = false }, tipo = TipoBotao.FANTASMA) }
        ) {
            Text("A rota atual e o histórico deste celular serão trocados pelos do arquivo.", style = Oficina.tipos.body, color = c.ink)
        }
    }
}

// =================================================================== cafezinho

private enum class Oferta(val rotulo: String, val centavos: Long?) {
    CAFE("R$ 5", 500), CAFE_PAO("R$ 10", 1000), ALMOCO("R$ 25", 2500), LIVRE("Outro", null)
}

private fun qrBitmap(texto: String): Bitmap? = runCatching {
    val m = QRCodeWriter().encode(
        texto, BarcodeFormat.QR_CODE, 0, 0,
        mapOf(EncodeHintType.MARGIN to 0, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M)
    )
    val bmp = Bitmap.createBitmap(m.width, m.height, Bitmap.Config.ARGB_8888)
    for (x in 0 until m.width) for (y in 0 until m.height) {
        bmp.setPixel(x, y, if (m.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
    }
    bmp
}.getOrNull()

@Composable
fun CafezinhoTela(onVoltar: () -> Unit) {
    val c = Oficina.cores
    val area = LocalClipboardManager.current
    var oferta by rememberSaveable { mutableStateOf(Oferta.CAFE) }
    var copiado by remember { mutableStateOf(false) }
    val payload = remember(oferta) { Pix.payload(Cafezinho.CHAVE_PIX, Cafezinho.NOME, Cafezinho.CIDADE, oferta.centavos, "Cafezinho") }
    val qr = remember(payload) { qrBitmap(payload) }

    LaunchedEffect(copiado) {
        if (copiado) { delay(2500); copiado = false }
    }

    Column(Modifier.fillMaxSize()) {
        CabecalhoInterno("Apoie o projeto", "Um *cafezinho* para o dev.", onVoltar)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "O app é gratuito, sem anúncios e de código aberto. Se ele ajuda no seu dia, um Pix de qualquer valor ajuda a manter o projeto.",
                style = Oficina.tipos.bodyLg, color = c.muted, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(24.dp))
            Segmentado(Oferta.entries.toList(), oferta, { it.rotulo }, { oferta = it })
            Spacer(Modifier.height(24.dp))
            if (qr != null) {
                Box(Modifier.border(1.dp, c.line).background(Color.White).padding(16.dp)) {
                    Image(
                        qr.asImageBitmap(), contentDescription = "QR Code Pix",
                        modifier = Modifier.size(220.dp), filterQuality = FilterQuality.None
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Hud(Cafezinho.NOME)
            Spacer(Modifier.height(24.dp))
            Botao(if (copiado) "Copiado" else "Copiar Pix copia e cola", {
                area.setText(AnnotatedString(payload))
                copiado = true
            }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Botao("Copiar só a chave", {
                area.setText(AnnotatedString(Cafezinho.CHAVE_PIX))
                copiado = true
            }, Modifier.fillMaxWidth(), tipo = TipoBotao.FANTASMA)
            Spacer(Modifier.height(16.dp))
            Titulo("Obrigado.", estilo = Oficina.tipos.heading)
        }
    }
}
