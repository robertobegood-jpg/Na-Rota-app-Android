package com.roberto.entregas.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.roberto.entregas.core.Coordenada
import com.roberto.entregas.core.DadosApp
import com.roberto.entregas.core.Entrega
import com.roberto.entregas.core.Operacoes
import com.roberto.entregas.core.Rota
import com.roberto.entregas.core.Serializacao
import com.roberto.entregas.dados.Armazem
import com.roberto.entregas.dados.Config
import com.roberto.entregas.dados.Preferencias
import com.roberto.entregas.dados.novoId
import com.roberto.entregas.servicos.Geocodificacao
import com.roberto.entregas.servicos.Localizacao
import com.roberto.entregas.servicos.Lugar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Ação que pode ser desfeita (entregue / não entregue). */
data class Desfazer(val texto: String, val rotaAntes: Rota)

/** Resultado de uma linha da lista colada. */
data class ItemColado(val texto: String, val lugar: Lugar?)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val armazem = Armazem(app)
    private val preferencias = Preferencias(app)
    val geo = Geocodificacao(app)

    private val _dados = MutableStateFlow(armazem.carregar())
    val dados: StateFlow<DadosApp> = _dados.asStateFlow()

    private val _config = MutableStateFlow(preferencias.ler())
    val config: StateFlow<Config> = _config.asStateFlow()

    private val _posicao = MutableStateFlow<Coordenada?>(null)
    val posicao: StateFlow<Coordenada?> = _posicao.asStateFlow()

    private val _otimizando = MutableStateFlow(false)
    val otimizando: StateFlow<Boolean> = _otimizando.asStateFlow()

    private val _aviso = MutableStateFlow<String?>(null)
    val aviso: StateFlow<String?> = _aviso.asStateFlow()

    private val _desfazer = MutableStateFlow<Desfazer?>(null)
    val desfazer: StateFlow<Desfazer?> = _desfazer.asStateFlow()

    private val travaSalvar = Mutex()

    // ------------------------------------------------------------ estado

    private fun alterar(f: (DadosApp) -> DadosApp) {
        _dados.value = f(_dados.value)
        viewModelScope.launch(Dispatchers.IO) {
            travaSalvar.withLock {
                // Sempre grava o estado mais novo, mesmo que outro salvamento tenha entrado antes.
                runCatching { armazem.salvar(_dados.value) }
            }
        }
    }

    private fun alterarRota(f: (Rota) -> Rota) = alterar { d ->
        d.copy(atual = Operacoes.comEstimativa(f(d.atual), _posicao.value))
    }

    fun avisar(texto: String) { _aviso.value = texto }
    fun limparAviso() { _aviso.value = null }

    fun mudarConfig(c: Config) {
        _config.value = c
        preferencias.gravar(c)
    }

    // ------------------------------------------------------------ localização

    suspend fun lerPosicao(): Coordenada? {
        val p = Localizacao.atual(getApplication())
        if (p != null) _posicao.value = p
        return p ?: _posicao.value
    }

    fun atualizarPosicao() {
        viewModelScope.launch {
            lerPosicao()
            alterarRota { it }
        }
    }

    // ------------------------------------------------------------ rota

    fun otimizar() {
        if (_otimizando.value) return
        viewModelScope.launch {
            _otimizando.value = true
            try {
                val antes = _dados.value.atual
                val origem = if (antes.partida == null) lerPosicao() else _posicao.value
                val nova = withContext(Dispatchers.Default) { Operacoes.otimizar(antes, origem) }
                val atual = _dados.value.atual
                if (atual.id == antes.id && atual.entregas == antes.entregas && atual.partida == antes.partida &&
                    atual.voltarAoInicio == antes.voltarAoInicio
                ) {
                    alterar { it.copy(atual = nova) }
                    val semLocal = nova.pendentes.count { !it.temLocal }
                    avisar(
                        when {
                            semLocal > 0 -> "Rota otimizada. $semLocal sem local ficaram no fim: corrija o endereço."
                            antes.partida == null && origem == null -> "Rota otimizada sem a sua localização: começa pela melhor parada."
                            else -> "Rota otimizada."
                        }
                    )
                } else {
                    avisar("A lista mudou durante o cálculo. Toque em otimizar de novo.")
                }
            } finally {
                _otimizando.value = false
            }
        }
    }

    fun salvarEntrega(e: Entrega) = alterarRota { Operacoes.salvar(it, e) }

    fun removerEntrega(id: String) = alterarRota { Operacoes.remover(it, id) }

    fun mover(id: String, direcao: Int) = alterarRota { Operacoes.mover(it, id, direcao) }

    fun adicionarVarias(itens: List<ItemColado>) {
        val novas = itens.map {
            Entrega(
                id = novoId(),
                endereco = it.lugar?.rotulo ?: it.texto,
                lat = it.lugar?.coordenada?.lat,
                lon = it.lugar?.coordenada?.lon,
                observacao = if (it.lugar != null && !it.lugar.rotulo.equals(it.texto, true)) "Digitado: ${it.texto}" else ""
            )
        }
        alterarRota { Operacoes.adicionar(it, novas) }
    }

    fun marcarEntregue(id: String) {
        val antes = _dados.value.atual
        alterarRota { Operacoes.marcarEntregue(it, id, System.currentTimeMillis()) }
        _desfazer.value = Desfazer("Marcada como entregue.", antes)
    }

    fun marcarFalha(id: String, motivo: String, tentarDepois: Boolean) {
        val antes = _dados.value.atual
        alterarRota { Operacoes.marcarFalha(it, id, motivo, tentarDepois, System.currentTimeMillis()) }
        _desfazer.value = Desfazer(if (tentarDepois) "Movida para o fim da rota." else "Marcada como não entregue.", antes)
    }

    fun desfazer() {
        val d = _desfazer.value ?: return
        alterar { dados -> if (dados.atual.id == d.rotaAntes.id) dados.copy(atual = d.rotaAntes) else dados }
        _desfazer.value = null
    }

    fun esquecerDesfazer() { _desfazer.value = null }

    fun reabrir(id: String) = alterarRota { Operacoes.reabrir(it, id) }

    fun iniciarRota() = alterarRota { Operacoes.iniciar(it, System.currentTimeMillis()) }

    /** @param lugar null = usar a localização atual do aparelho como partida */
    fun definirPartida(lugar: Lugar?) = alterarRota {
        it.copy(
            partidaRotulo = lugar?.rotulo ?: "",
            partidaLat = lugar?.coordenada?.lat,
            partidaLon = lugar?.coordenada?.lon,
            otimizada = false
        )
    }

    fun definirVolta(voltar: Boolean) = alterarRota { it.copy(voltarAoInicio = voltar, otimizada = false) }

    fun finalizarRota(levarPendentes: Boolean) {
        alterar { Operacoes.finalizar(it, novoId(), System.currentTimeMillis(), levarPendentes) }
        _desfazer.value = null
        avisar("Rota guardada no histórico.")
    }

    fun limparRota() = alterarRota { it.copy(entregas = emptyList(), otimizada = false, iniciadaEm = null) }

    fun apagarDoHistorico(id: String) = alterar { d -> d.copy(historico = d.historico.filterNot { it.id == id }) }

    // ------------------------------------------------------------ lista colada

    suspend fun geocodificarLista(linhas: List<String>, progresso: (Int) -> Unit): List<ItemColado> {
        val perto = _dados.value.atual.partida ?: _posicao.value ?: lerPosicao()
        val res = ArrayList<ItemColado>()
        linhas.forEachIndexed { i, linha ->
            val lugar = geo.buscar(linha, perto).firstOrNull()
            res.add(ItemColado(linha, lugar))
            progresso(i + 1)
            delay(250) // respeita o limite dos serviços gratuitos de busca
        }
        return res
    }

    // ------------------------------------------------------------ backup

    fun exportar(uri: Uri) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver.openOutputStream(uri, "wt")!!.use {
                        it.write(Serializacao.paraJson(_dados.value).toByteArray(Charsets.UTF_8))
                    }
                }.isSuccess
            }
            avisar(if (ok) "Backup salvo." else "Não deu para salvar o backup.")
        }
    }

    fun importar(uri: Uri) {
        viewModelScope.launch {
            val lido = withContext(Dispatchers.IO) {
                runCatching {
                    val texto = getApplication<Application>().contentResolver.openInputStream(uri)!!
                        .bufferedReader(Charsets.UTF_8).use { it.readText() }
                    Serializacao.deJson(texto)
                }
            }
            lido.onSuccess { d ->
                alterar { d }
                _desfazer.value = null
                avisar("Backup restaurado: ${d.atual.entregas.size} entregas na rota, ${d.historico.size} rotas no histórico.")
            }.onFailure {
                avisar("Arquivo inválido: ${it.message ?: "não é um backup do app"}")
            }
        }
    }
}
