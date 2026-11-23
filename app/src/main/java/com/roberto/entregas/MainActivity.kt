package com.roberto.entregas

import android.Manifest
import android.graphics.Color as CorAndroid
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.roberto.entregas.core.Coordenada
import com.roberto.entregas.servicos.Acoes
import com.roberto.entregas.servicos.Localizacao
import com.roberto.entregas.servicos.Lugar
import com.roberto.entregas.dados.Tema
import com.roberto.entregas.ui.AppViewModel
import com.roberto.entregas.ui.componentes.BarraInferior
import com.roberto.entregas.ui.componentes.Botao
import com.roberto.entregas.ui.componentes.Dialogo
import com.roberto.entregas.ui.componentes.TipoBotao
import com.roberto.entregas.ui.componentes.linhaTopo
import com.roberto.entregas.ui.configurarMapa
import com.roberto.entregas.ui.tema.Oficina
import com.roberto.entregas.ui.tema.TemaOficina
import com.roberto.entregas.ui.telas.CafezinhoTela
import com.roberto.entregas.ui.telas.ColarListaTela
import com.roberto.entregas.ui.telas.DetalheHistoricoTela
import com.roberto.entregas.ui.telas.EditarEntregaTela
import com.roberto.entregas.ui.telas.EntregarTela
import com.roberto.entregas.ui.telas.HistoricoTela
import com.roberto.entregas.ui.telas.MaisTela
import com.roberto.entregas.ui.telas.MapaCheioTela
import com.roberto.entregas.ui.telas.PartidaTela
import com.roberto.entregas.ui.telas.RotaTela
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Telas que abrem por cima das abas. */
sealed interface Sobreposta {
    data class Editar(val id: String?) : Sobreposta
    data object Colar : Sobreposta
    data object Partida : Sobreposta
    data class Historico(val id: String) : Sobreposta
    data object Cafezinho : Sobreposta
    data object Mapa : Sobreposta
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configurarMapa(applicationContext)
        enableEdgeToEdge()
        setContent {
            val vm: AppViewModel = viewModel()
            val config by vm.config.collectAsState()
            val escuro = when (config.tema) {
                Tema.ESCURO -> true
                Tema.CLARO -> false
                Tema.SISTEMA -> isSystemInDarkTheme()
            }
            LaunchedEffect(escuro) {
                val estilo = if (escuro) SystemBarStyle.dark(CorAndroid.TRANSPARENT)
                else SystemBarStyle.light(CorAndroid.TRANSPARENT, CorAndroid.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = estilo, navigationBarStyle = estilo)
            }
            TemaOficina(escuro) { App(vm) }
        }
    }
}

@Composable
private fun App(vm: AppViewModel) {
    val ctx = LocalContext.current
    val c = Oficina.cores
    val dados by vm.dados.collectAsState()
    val config by vm.config.collectAsState()
    val otimizando by vm.otimizando.collectAsState()
    val aviso by vm.aviso.collectAsState()
    val desfazer by vm.desfazer.collectAsState()
    val posicao by vm.posicao.collectAsState()
    val semOrigem by vm.semOrigem.collectAsState()

    var aba by rememberSaveable { mutableStateOf(0) }
    var pilha by remember { mutableStateOf(listOf<Sobreposta>()) }
    fun abrir(s: Sobreposta) { pilha = pilha + s }
    fun fechar() { pilha = pilha.dropLast(1) }

    val rota = dados.atual

    // Permissão de localização logo na primeira abertura.
    val pedirLocal = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.any { it }) vm.atualizarPosicao()
        else vm.avisar("Sem a localização, a rota começa pela melhor parada. Dá para fixar uma partida na aba Rota.")
    }
    LaunchedEffect(Unit) {
        if (Localizacao.temPermissao(ctx)) vm.atualizarPosicao()
        else pedirLocal.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    val exportar = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.exportar(uri)
    }
    val importar = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importar(uri)
    }

    val buscar: suspend (String) -> List<Lugar> = { q -> vm.geo.buscar(q, rota.partida ?: posicao) }
    val minhaPosicao: suspend () -> Lugar? = {
        val p: Coordenada? = if (Localizacao.temPermissao(ctx)) vm.lerPosicao() else null
        p?.let { Lugar(vm.geo.endereco(it) ?: "Minha localização", it) }
    }

    BackHandler(enabled = pilha.isNotEmpty()) { fechar() }
    BackHandler(enabled = pilha.isEmpty() && aba != 0) { aba = 0 }

    LaunchedEffect(aviso) {
        if (aviso != null) { delay(3500); vm.limparAviso() }
    }
    LaunchedEffect(desfazer) {
        if (desfazer != null) { delay(6000); vm.esquecerDesfazer() }
    }

    Box(Modifier.fillMaxSize().background(c.bg)) {
        val topo = pilha.lastOrNull()
        if (topo == null) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).statusBarsPadding()) {
                    when (aba) {
                        0 -> RotaTela(
                            rota = rota,
                            mostrarMapa = config.mostrarMapa,
                            otimizando = otimizando,
                            onMostrarMapa = { vm.mudarConfig(config.copy(mostrarMapa = it)) },
                            onAbrirMapa = { abrir(Sobreposta.Mapa) },
                            onOtimizar = { vm.otimizar() },
                            onAdicionar = { abrir(Sobreposta.Editar(null)) },
                            onColar = { abrir(Sobreposta.Colar) },
                            onEditar = { abrir(Sobreposta.Editar(it)) },
                            onMover = { id, d -> vm.mover(id, d) },
                            onPartida = { abrir(Sobreposta.Partida) },
                            onVolta = { vm.definirVolta(it) },
                            onFinalizar = { vm.finalizarRota(it) },
                            onLimpar = { vm.limparRota() }
                        )
                        1 -> EntregarTela(
                            rota = rota,
                            onNavegar = { e ->
                                vm.iniciarRota()
                                e.coordenada?.let { Acoes.navegar(ctx, it, e.endereco, config.gps) }
                            },
                            onLigar = { Acoes.ligar(ctx, it.telefone) },
                            onWhatsApp = { Acoes.whatsapp(ctx, it.telefone, config.mensagemWhatsApp) },
                            onEntregue = { vm.marcarEntregue(it.id) },
                            onFalha = { e, motivo, depois -> vm.marcarFalha(e.id, motivo, depois) },
                            onEditar = { abrir(Sobreposta.Editar(it)) },
                            onIrParaRota = { aba = 0 },
                            onFinalizar = { vm.finalizarRota(true); aba = 0 }
                        )
                        2 -> HistoricoTela(dados.historico) { abrir(Sobreposta.Historico(it)) }
                        else -> MaisTela(
                            config = config,
                            onConfig = { vm.mudarConfig(it) },
                            onExportar = {
                                val nome = "na-rota-backup-" + SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.ROOT).format(Date()) + ".json"
                                exportar.launch(nome)
                            },
                            onImportar = { importar.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                            onCafezinho = { abrir(Sobreposta.Cafezinho) }
                        )
                    }
                }
                BarraInferior(listOf("Rota", "Entregar", "Histórico", "Mais"), aba) { aba = it }
            }
        } else {
            Box(Modifier.fillMaxSize().systemBarsPadding()) {
                when (topo) {
                    is Sobreposta.Editar -> {
                        val original = topo.id?.let { id -> rota.entregas.firstOrNull { it.id == id } }
                        EditarEntregaTela(
                            original = original,
                            buscar = buscar,
                            minhaPosicao = minhaPosicao,
                            onSalvar = { vm.salvarEntrega(it); fechar() },
                            onExcluir = { vm.removerEntrega(it); fechar() },
                            onReabrir = { vm.reabrir(it) },
                            onVoltar = { fechar() }
                        )
                    }
                    Sobreposta.Colar -> ColarListaTela(
                        geocodificar = { linhas, progresso -> vm.geocodificarLista(linhas, progresso) },
                        onAdicionar = { vm.adicionarVarias(it); fechar() },
                        onVoltar = { fechar() }
                    )
                    Sobreposta.Partida -> PartidaTela(
                        rota = rota,
                        buscar = buscar,
                        minhaPosicao = minhaPosicao,
                        onDefinir = { vm.definirPartida(it) },
                        onVoltar = { fechar() }
                    )
                    is Sobreposta.Historico -> {
                        val r = dados.historico.firstOrNull { it.id == topo.id }
                        if (r == null) LaunchedEffect(topo) { fechar() }
                        else DetalheHistoricoTela(r, onApagar = { vm.apagarDoHistorico(r.id); fechar() }, onVoltar = { fechar() })
                    }
                    Sobreposta.Cafezinho -> CafezinhoTela(onVoltar = { fechar() })
                    Sobreposta.Mapa -> MapaCheioTela(rota, onVoltar = { fechar() })
                }
            }
        }

        semOrigem?.let { motivo ->
            Dialogo(
                titulo = "De onde você *sai*?",
                onFechar = { vm.fecharSemOrigem() },
                confirmar = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        when (motivo) {
                            "permissao" -> Botao("Permitir localização", {
                                vm.fecharSemOrigem()
                                pedirLocal.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                            }, Modifier.fillMaxWidth())
                            "desligada" -> Botao("Ligar a localização", {
                                vm.fecharSemOrigem()
                                Acoes.configuracoesLocalizacao(ctx)
                            }, Modifier.fillMaxWidth())
                            else -> Botao("Tentar de novo", { vm.fecharSemOrigem(); vm.otimizar() }, Modifier.fillMaxWidth())
                        }
                        Botao("Fixar um endereço de partida", {
                            vm.fecharSemOrigem()
                            abrir(Sobreposta.Partida)
                        }, Modifier.fillMaxWidth(), tipo = TipoBotao.SECUNDARIO)
                        Botao("Cancelar", { vm.fecharSemOrigem() }, Modifier.fillMaxWidth(), tipo = TipoBotao.FANTASMA)
                    }
                }
            ) {
                Text(
                    when (motivo) {
                        "permissao" -> "A rota começa de onde você está, mas o app não tem permissão para ver a sua localização."
                        "desligada" -> "A rota começa de onde você está, mas a localização do celular está desligada."
                        else -> "Não deu para ler a sua localização agora. Vá para um lugar aberto e tente de novo, ou fixe o endereço de onde você sai."
                    },
                    style = Oficina.tipos.body, color = c.ink
                )
            }
        }

        // Aviso / desfazer na parte de baixo, acima da barra de abas.
        val d = desfazer
        val texto = d?.texto ?: aviso
        if (texto != null) {
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .systemBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, bottom = if (topo == null) 72.dp else 16.dp)
                    .fillMaxWidth()
                    .background(c.invertBg)
                    .linhaTopo(c.accent)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(texto, style = Oficina.tipos.bodySm, color = c.invertFg, modifier = Modifier.weight(1f))
                if (d != null) {
                    Spacer(Modifier.width(12.dp))
                    Text("DESFAZER", style = Oficina.tipos.botao, color = c.accent, modifier = Modifier.clickable { vm.desfazer() })
                }
            }
        }
    }
}
