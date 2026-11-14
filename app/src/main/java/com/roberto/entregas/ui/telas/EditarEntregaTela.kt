package com.roberto.entregas.ui.telas

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.roberto.entregas.core.Coordenada
import com.roberto.entregas.core.Entrega
import com.roberto.entregas.dados.novoId
import com.roberto.entregas.servicos.Lugar
import com.roberto.entregas.ui.componentes.Botao
import com.roberto.entregas.ui.componentes.CabecalhoInterno
import com.roberto.entregas.ui.componentes.Campo
import com.roberto.entregas.ui.componentes.Dialogo
import com.roberto.entregas.ui.componentes.Hud
import com.roberto.entregas.ui.componentes.Interruptor
import com.roberto.entregas.ui.componentes.TipoBotao
import com.roberto.entregas.ui.componentes.linhaBase
import com.roberto.entregas.ui.tema.Oficina
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Busca de endereço reaproveitada no cadastro de entrega e na escolha da partida.
 * Busca sozinha depois que a pessoa para de digitar.
 */
@Composable
fun BuscaEndereco(
    texto: String,
    onTexto: (String) -> Unit,
    escolhido: Lugar?,
    onEscolher: (Lugar) -> Unit,
    buscar: suspend (String) -> List<Lugar>,
    rotulo: String = "Endereço"
) {
    val c = Oficina.cores
    var resultados by remember { mutableStateOf<List<Lugar>>(emptyList()) }
    var buscando by remember { mutableStateOf(false) }
    var semResultado by remember { mutableStateOf(false) }

    LaunchedEffect(texto) {
        if (escolhido != null && escolhido.rotulo == texto) return@LaunchedEffect
        resultados = emptyList()
        semResultado = false
        if (texto.trim().length < 5) return@LaunchedEffect
        delay(700)
        buscando = true
        resultados = runCatching { buscar(texto) }.getOrDefault(emptyList())
        semResultado = resultados.isEmpty()
        buscando = false
    }

    Campo(texto, onTexto, rotulo, umaLinha = false, dica = "Rua, número, bairro, cidade")
    if (buscando) {
        Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(14.dp), color = c.muted, strokeWidth = 1.5.dp)
            Spacer(Modifier.size(8.dp))
            Hud("Buscando")
        }
    }
    if (semResultado) {
        Text(
            "Nada encontrado. Confira o número e inclua bairro e cidade.",
            style = Oficina.tipos.bodySm, color = c.ambar, modifier = Modifier.padding(vertical = 6.dp)
        )
    }
    resultados.forEach { l ->
        Column(
            Modifier.fillMaxWidth().linhaBase(c.line).clickable {
                onEscolher(l)
                resultados = emptyList()
            }.padding(vertical = 10.dp)
        ) {
            Text(l.rotulo, style = Oficina.tipos.bodySm, color = c.ink)
        }
    }
    if (escolhido != null && escolhido.rotulo == texto) {
        Hud(
            "Local confirmado · %.5f, %.5f".format(java.util.Locale.ROOT, escolhido.coordenada.lat, escolhido.coordenada.lon),
            Modifier.padding(top = 6.dp), cor = c.verde
        )
    }
}

@Composable
fun EditarEntregaTela(
    original: Entrega?,
    buscar: suspend (String) -> List<Lugar>,
    minhaPosicao: suspend () -> Lugar?,
    onSalvar: (Entrega) -> Unit,
    onExcluir: (String) -> Unit,
    onReabrir: (String) -> Unit,
    onVoltar: () -> Unit
) {
    val c = Oficina.cores
    val escopo = rememberCoroutineScope()
    val inicial = remember(original) {
        original?.coordenada?.let { Lugar(original.endereco, it) }
    }
    var endereco by remember { mutableStateOf(original?.endereco ?: "") }
    var lugar by remember { mutableStateOf(inicial) }
    var destinatario by remember { mutableStateOf(original?.destinatario ?: "") }
    var telefone by remember { mutableStateOf(original?.telefone ?: "") }
    var pedido by remember { mutableStateOf(original?.pedido ?: "") }
    var observacao by remember { mutableStateOf(original?.observacao ?: "") }
    var urgente by remember { mutableStateOf(original?.urgente ?: false) }
    var lendoPosicao by remember { mutableStateOf(false) }
    var confirmarExcluir by remember { mutableStateOf(false) }
    var avisoPosicao by remember { mutableStateOf<String?>(null) }

    val confirmado = lugar != null && lugar?.rotulo == endereco

    fun salvar() {
        val coord: Coordenada? = if (confirmado) lugar?.coordenada else null
        val base = original ?: Entrega(id = novoId(), endereco = "")
        onSalvar(
            base.copy(
                endereco = endereco.trim(),
                lat = coord?.lat,
                lon = coord?.lon,
                destinatario = destinatario.trim(),
                telefone = telefone.trim(),
                pedido = pedido.trim(),
                observacao = observacao.trim(),
                urgente = urgente
            )
        )
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        CabecalhoInterno(
            eyebrow = if (original == null) "Nova entrega" else "Editar entrega",
            titulo = if (original == null) "Para onde *vai*?" else "Ajuste a *entrega*.",
            onVoltar = onVoltar
        )
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column {
                BuscaEndereco(endereco, { endereco = it }, lugar, { lugar = it; endereco = it.rotulo }, buscar)
            }
            if (lendoPosicao) {
                Hud("Lendo a sua localização")
            } else {
                Botao("Usar minha localização aqui", {
                    escopo.launch {
                        lendoPosicao = true
                        val l = minhaPosicao()
                        lendoPosicao = false
                        if (l != null) { lugar = l; endereco = l.rotulo; avisoPosicao = null }
                        else avisoPosicao = "Não deu para ler a localização. Confira se o GPS está ligado e a permissão foi dada."
                    }
                }, Modifier.fillMaxWidth(), tipo = TipoBotao.FANTASMA)
            }
            avisoPosicao?.let { Text(it, style = Oficina.tipos.bodySm, color = c.perigo) }
            if (!confirmado && endereco.isNotBlank()) {
                Text(
                    "Escolha um resultado da busca para marcar o local. Sem isso, a entrega fica fora da otimização.",
                    style = Oficina.tipos.bodySm, color = c.muted
                )
            }

            Campo(destinatario, { destinatario = it }, "Destinatário")
            Campo(telefone, { telefone = it }, "Telefone", teclado = KeyboardType.Phone, dica = "(11) 98765-4321")
            Campo(pedido, { pedido = it }, "Nº do pedido")
            Campo(observacao, { observacao = it }, "Observação", umaLinha = false, minLinhas = 2, dica = "Portão azul, deixar com o porteiro…")
            Interruptor("Urgente", urgente, { urgente = it }, detalhe = "Entra antes das outras na otimização.")

            if (original != null && original.status != com.roberto.entregas.core.Status.PENDENTE) {
                Botao("Voltar para pendente", { onReabrir(original.id); onVoltar() }, Modifier.fillMaxWidth(), tipo = TipoBotao.SECUNDARIO)
            }
            if (original != null) {
                Botao("Excluir entrega", { confirmarExcluir = true }, Modifier.fillMaxWidth(), tipo = TipoBotao.PERIGO)
            }
            Spacer(Modifier.height(8.dp))
        }
        Botao(
            "Salvar entrega", { salvar() },
            Modifier.fillMaxWidth().padding(16.dp),
            habilitado = endereco.isNotBlank()
        )
    }

    if (confirmarExcluir && original != null) {
        Dialogo(
            titulo = "Excluir a *entrega*?",
            onFechar = { confirmarExcluir = false },
            confirmar = { Botao("Excluir", { confirmarExcluir = false; onExcluir(original.id) }, tipo = TipoBotao.PERIGO) },
            cancelar = { Botao("Cancelar", { confirmarExcluir = false }, tipo = TipoBotao.FANTASMA) }
        ) {
            Text(original.endereco, style = Oficina.tipos.body, color = c.ink)
        }
    }
}
