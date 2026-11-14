package com.roberto.entregas.ui.telas

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.roberto.entregas.core.Entrega
import com.roberto.entregas.core.Operacoes
import com.roberto.entregas.core.Rota
import com.roberto.entregas.core.Texto
import com.roberto.entregas.ui.componentes.Botao
import com.roberto.entregas.ui.componentes.CabecalhoAba
import com.roberto.entregas.ui.componentes.Campo
import com.roberto.entregas.ui.componentes.Celula
import com.roberto.entregas.ui.componentes.Dialogo
import com.roberto.entregas.ui.componentes.FaixaIndicadores
import com.roberto.entregas.ui.componentes.Hud
import com.roberto.entregas.ui.componentes.TipoBotao
import com.roberto.entregas.ui.componentes.Titulo
import com.roberto.entregas.ui.componentes.Vazio
import com.roberto.entregas.ui.tema.Oficina

private val MOTIVOS = listOf("Ausente", "Endereço não encontrado", "Recusou", "Fechado", "Outro")

@Composable
fun EntregarTela(
    rota: Rota,
    onNavegar: (Entrega) -> Unit,
    onLigar: (Entrega) -> Unit,
    onWhatsApp: (Entrega) -> Unit,
    onEntregue: (Entrega) -> Unit,
    onFalha: (Entrega, String, Boolean) -> Unit,
    onEditar: (String) -> Unit,
    onIrParaRota: () -> Unit,
    onFinalizar: () -> Unit
) {
    val c = Oficina.cores
    val proxima = rota.proxima
    var falhaDe by remember { mutableStateOf<Entrega?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        val total = rota.entregas.size
        val feitas = rota.entregues + rota.falhas

        if (rota.vazia) {
            CabecalhoAba("02/Entregar", "Nada para *entregar*.")
            Vazio("", "Monte a rota na aba Rota para começar.")
            Botao("Ir para a rota", onIrParaRota, Modifier.fillMaxWidth().padding(16.dp), tipo = TipoBotao.SECUNDARIO)
            return@Column
        }

        if (proxima == null) {
            CabecalhoAba("02/Entregar · $feitas de $total", "Rota *concluída*.")
            FaixaIndicadores(
                listOf(
                    Triple("${rota.entregues}", "Entregues", c.verde),
                    Triple("${rota.falhas}", "Não entregues", if (rota.falhas > 0) c.perigo else null),
                    Triple(Operacoes.duracaoMinutos(rota)?.let { Texto.duracao(it) } ?: "—", "Duração", null)
                ),
                Modifier.padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(16.dp))
            Botao("Finalizar e guardar no histórico", onFinalizar, Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            return@Column
        }

        val numero = rota.entregas.indexOf(proxima) + 1
        CabecalhoAba("02/Entregar · parada $numero de $total", "Próxima *parada*.")

        // Barra de progresso fina
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(2.dp).background(c.line)) {
            if (feitas > 0) Spacer(Modifier.weight(feitas.toFloat()).height(2.dp).background(c.accent))
            if (total - feitas > 0) Spacer(Modifier.weight((total - feitas).toFloat()))
        }
        Spacer(Modifier.height(12.dp))

        if (!rota.otimizada) {
            Text(
                "A rota ainda não foi otimizada. A ordem é a da lista.",
                style = Oficina.tipos.bodySm, color = c.ambar,
                modifier = Modifier.padding(horizontal = 16.dp).clickable(onClick = onIrParaRota)
            )
            Spacer(Modifier.height(8.dp))
        }

        Celula(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Row {
                Hud(
                    listOfNotNull(
                        if (proxima.urgente) "Urgente" else null,
                        if (proxima.tentativas > 0) "Tentativa ${proxima.tentativas + 1}" else null,
                        proxima.pedido.ifBlank { null }?.let { "Pedido $it" }
                    ).joinToString(" · ").ifBlank { "Endereço" },
                    cor = if (proxima.urgente) c.ambar else c.muted
                )
                Spacer(Modifier.weight(1f))
                Text("EDITAR", style = Oficina.tipos.botao, color = c.accentInk, modifier = Modifier.clickable { onEditar(proxima.id) })
            }
            Spacer(Modifier.height(8.dp))
            Titulo(proxima.endereco, estilo = Oficina.tipos.heading, cor = c.inkForte)
            if (proxima.destinatario.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(proxima.destinatario, style = Oficina.tipos.bodyLg, color = c.ink)
            }
            if (proxima.observacao.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(proxima.observacao, style = Oficina.tipos.body, color = c.muted)
            }
            if (proxima.tentativas > 0 && proxima.motivoFalha.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text("Última tentativa: ${proxima.motivoFalha}", style = Oficina.tipos.bodySm, color = c.perigo)
            }
            if (!proxima.temLocal) {
                Spacer(Modifier.height(8.dp))
                Text("Sem local no mapa. Toque em Editar e busque o endereço.", style = Oficina.tipos.bodySm, color = c.perigo)
            }
        }

        Spacer(Modifier.height(12.dp))
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Botao(
                "Navegar até lá", { onNavegar(proxima) }, Modifier.fillMaxWidth(),
                tipo = TipoBotao.DESTAQUE, habilitado = proxima.temLocal, altura = 56.dp
            )
            if (proxima.telefone.isNotBlank()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Botao("Ligar", { onLigar(proxima) }, Modifier.weight(1f), tipo = TipoBotao.SECUNDARIO)
                    Botao("WhatsApp", { onWhatsApp(proxima) }, Modifier.weight(1f), tipo = TipoBotao.SECUNDARIO)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Botao("Não entregue", { falhaDe = proxima }, Modifier.weight(1f), tipo = TipoBotao.PERIGO, altura = 64.dp)
                Botao("Entregue", { onEntregue(proxima) }, Modifier.weight(1f), altura = 64.dp)
            }
        }

        val seguintes = rota.pendentes.filter { it.id != proxima.id }.take(3)
        if (seguintes.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            Hud("Depois", Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(4.dp))
            seguintes.forEach { e ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Text(
                        (rota.entregas.indexOf(e) + 1).toString().padStart(2, '0'),
                        style = Oficina.tipos.hud, color = c.faint, modifier = Modifier.padding(end = 12.dp, top = 3.dp)
                    )
                    Text(e.endereco, style = Oficina.tipos.bodySm, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    falhaDe?.let { e ->
        DialogoFalha(
            onFechar = { falhaDe = null },
            onConfirmar = { motivo, depois ->
                falhaDe = null
                onFalha(e, motivo, depois)
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DialogoFalha(onFechar: () -> Unit, onConfirmar: (String, Boolean) -> Unit) {
    val c = Oficina.cores
    var motivo by remember { mutableStateOf(MOTIVOS.first()) }
    var outro by remember { mutableStateOf("") }
    val texto = if (motivo == "Outro") outro.trim().ifBlank { "Outro" } else motivo

    Dialogo(
        titulo = "Não *entregue*.",
        onFechar = onFechar,
        confirmar = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Botao("Tentar no fim da rota", { onConfirmar(texto, true) }, Modifier.fillMaxWidth())
                Botao("Encerrar sem entregar", { onConfirmar(texto, false) }, Modifier.fillMaxWidth(), tipo = TipoBotao.PERIGO)
                Botao("Cancelar", onFechar, Modifier.fillMaxWidth(), tipo = TipoBotao.FANTASMA)
            }
        }
    ) {
        Hud("Motivo")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            MOTIVOS.forEach { m ->
                val ativo = m == motivo
                Text(
                    m.uppercase(),
                    style = Oficina.tipos.botao,
                    color = if (ativo) c.invertFg else c.ink,
                    modifier = Modifier
                        .background(if (ativo) c.invertBg else Color.Transparent)
                        .border(1.dp, if (ativo) c.invertBg else c.lineForte)
                        .clickable { motivo = m }
                        .padding(horizontal = 10.dp, vertical = 10.dp)
                )
            }
        }
        if (motivo == "Outro") Campo(outro, { outro = it }, "Descreva")
    }
}
