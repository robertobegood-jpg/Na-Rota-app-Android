package com.roberto.entregas.ui.telas

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.roberto.entregas.core.Entrega
import com.roberto.entregas.core.Rota
import com.roberto.entregas.core.Status
import com.roberto.entregas.core.Texto
import com.roberto.entregas.ui.MapaRota
import com.roberto.entregas.ui.MarcadorMapa
import com.roberto.entregas.ui.componentes.Botao
import com.roberto.entregas.ui.componentes.CabecalhoAba
import com.roberto.entregas.ui.componentes.Dialogo
import com.roberto.entregas.ui.componentes.FaixaIndicadores
import com.roberto.entregas.ui.componentes.Hud
import com.roberto.entregas.ui.componentes.Interruptor
import com.roberto.entregas.ui.componentes.Ponto
import com.roberto.entregas.ui.componentes.TipoBotao
import com.roberto.entregas.ui.componentes.Vazio
import com.roberto.entregas.ui.componentes.linhaBase
import com.roberto.entregas.ui.tema.Oficina

@Composable
fun RotaTela(
    rota: Rota,
    mostrarMapa: Boolean,
    otimizando: Boolean,
    onMostrarMapa: (Boolean) -> Unit,
    onOtimizar: () -> Unit,
    onAdicionar: () -> Unit,
    onColar: () -> Unit,
    onEditar: (String) -> Unit,
    onMover: (String, Int) -> Unit,
    onPartida: () -> Unit,
    onVolta: (Boolean) -> Unit,
    onFinalizar: (levarPendentes: Boolean) -> Unit,
    onLimpar: () -> Unit
) {
    val c = Oficina.cores
    var confirmarFinalizar by remember { mutableStateOf(false) }
    var confirmarLimpar by remember { mutableStateOf(false) }

    val proximaId = rota.proxima?.id
    val marcadores = rota.entregas.mapIndexedNotNull { i, e ->
        e.coordenada?.let { MarcadorMapa(i + 1, it, e.status, e.urgente, e.id == proximaId) }
    }

    Column(Modifier.fillMaxSize()) {
        CabecalhoAba(
            eyebrow = "01/Rota · ${if (rota.otimizada) "otimizada" else "não otimizada"}",
            titulo = if (rota.vazia) "Monte a sua *rota*." else "Sua *rota*."
        )

        if (!rota.vazia) {
            FaixaIndicadores(
                listOf(
                    Triple("${rota.pendentes.size}", "Pendentes", null),
                    Triple("${rota.entregues}", "Entregues", c.verde),
                    Triple(Texto.km(rota.kmEstimado), "Km est.", null),
                    Triple(Texto.duracao(rota.minutosEstimados), "Tempo est.", null)
                ),
                Modifier.padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(8.dp))
        }

        // Partida e volta
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onPartida).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Hud("Partida")
                Text(
                    rota.partidaRotulo.ifBlank { "Minha localização atual" },
                    style = Oficina.tipos.bodySm, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            Text("TROCAR", style = Oficina.tipos.botao, color = c.accentInk)
        }
        Interruptor(
            "Voltar ao ponto de partida", rota.voltarAoInicio, onVolta,
            Modifier.padding(horizontal = 16.dp)
        )

        if (mostrarMapa && marcadores.isNotEmpty()) {
            Box(Modifier.fillMaxWidth().height(220.dp).padding(horizontal = 16.dp).border(1.dp, c.line)) {
                MapaRota(rota.partida, marcadores, Modifier.fillMaxSize())
            }
            Spacer(Modifier.height(8.dp))
        }

        // Ações
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (otimizando) {
                Box(Modifier.weight(1f).height(48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp), color = c.ink, strokeWidth = 2.dp)
                }
            } else {
                Botao(
                    "Otimizar rota", onOtimizar, Modifier.weight(1f),
                    tipo = if (rota.otimizada) TipoBotao.SECUNDARIO else TipoBotao.PRIMARIO,
                    habilitado = rota.pendentes.any { it.temLocal }
                )
            }
            Botao("Adicionar", onAdicionar, Modifier.weight(1f), tipo = TipoBotao.SECUNDARIO)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Botao("Colar lista", onColar, Modifier.weight(1f), tipo = TipoBotao.SECUNDARIO)
            if (marcadores.isNotEmpty()) {
                Botao(if (mostrarMapa) "Ocultar mapa" else "Ver mapa", { onMostrarMapa(!mostrarMapa) }, Modifier.weight(1f), tipo = TipoBotao.FANTASMA)
            }
        }
        Spacer(Modifier.height(8.dp))

        if (rota.vazia) {
            Vazio(
                "Nenhuma *entrega* ainda.",
                "Adicione os endereços um a um ou cole uma lista inteira, um endereço por linha. Depois toque em Otimizar rota."
            )
        } else {
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)
            ) {
                itemsIndexed(rota.entregas, key = { _, e -> e.id }) { i, e ->
                    LinhaEntrega(
                        posicao = i + 1, e = e, proxima = e.id == proximaId,
                        podeSubir = i > 0, podeDescer = i < rota.entregas.size - 1,
                        onClick = { onEditar(e.id) },
                        onSubir = { onMover(e.id, -1) },
                        onDescer = { onMover(e.id, 1) }
                    )
                }
                item {
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Botao("Finalizar rota", { confirmarFinalizar = true }, Modifier.weight(1f), tipo = TipoBotao.SECUNDARIO)
                        Botao("Limpar tudo", { confirmarLimpar = true }, Modifier.weight(1f), tipo = TipoBotao.PERIGO)
                    }
                }
            }
        }
    }

    if (confirmarFinalizar) {
        val pend = rota.pendentes.size
        Dialogo(
            titulo = "Finalizar a *rota*?",
            onFechar = { confirmarFinalizar = false },
            confirmar = {
                Botao(if (pend > 0) "Finalizar e levar pendentes" else "Finalizar", {
                    confirmarFinalizar = false
                    onFinalizar(true)
                })
            },
            cancelar = { Botao("Cancelar", { confirmarFinalizar = false }, tipo = TipoBotao.FANTASMA) }
        ) {
            Text(
                "A rota vai para o histórico com ${rota.entregues} entregues e ${rota.falhas} não entregues." +
                    if (pend > 0) " As $pend pendentes continuam na próxima rota." else "",
                style = Oficina.tipos.body, color = c.ink
            )
        }
    }

    if (confirmarLimpar) {
        Dialogo(
            titulo = "Apagar todas as *entregas*?",
            onFechar = { confirmarLimpar = false },
            confirmar = { Botao("Apagar", { confirmarLimpar = false; onLimpar() }, tipo = TipoBotao.PERIGO) },
            cancelar = { Botao("Cancelar", { confirmarLimpar = false }, tipo = TipoBotao.FANTASMA) }
        ) {
            Text("A lista desta rota será apagada sem ir para o histórico.", style = Oficina.tipos.body, color = c.ink)
        }
    }
}

@Composable
private fun LinhaEntrega(
    posicao: Int,
    e: Entrega,
    proxima: Boolean,
    podeSubir: Boolean,
    podeDescer: Boolean,
    onClick: () -> Unit,
    onSubir: () -> Unit,
    onDescer: () -> Unit
) {
    val c = Oficina.cores
    val corStatus = when (e.status) {
        Status.ENTREGUE -> c.verde
        Status.FALHA -> c.perigo
        Status.PENDENTE -> if (proxima) c.accent else c.lineForte
    }
    val concluida = e.status != Status.PENDENTE
    Row(
        Modifier.fillMaxWidth().linhaBase(c.line).clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            posicao.toString().padStart(2, '0'), style = Oficina.tipos.numero.copy(fontSize = Oficina.tipos.hud.fontSize * 1.5f),
            color = if (concluida) c.faint else c.ink, modifier = Modifier.width(36.dp)
        )
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Ponto(corStatus)
                Spacer(Modifier.width(6.dp))
                Hud(
                    when {
                        proxima -> "Próxima"
                        e.status == Status.ENTREGUE -> "Entregue"
                        e.status == Status.FALHA -> "Não entregue · ${e.motivoFalha}"
                        !e.temLocal -> "Sem local"
                        e.tentativas > 0 -> "Nova tentativa · ${e.motivoFalha}"
                        else -> "Pendente"
                    },
                    cor = when {
                        !e.temLocal && !concluida -> c.perigo
                        proxima -> c.accentInk
                        else -> c.muted
                    }
                )
                if (e.urgente && !concluida) {
                    Spacer(Modifier.width(6.dp))
                    Hud("· Urgente", cor = c.ambar)
                }
            }
            Text(
                e.endereco, style = Oficina.tipos.bodySm, color = if (concluida) c.muted else c.ink,
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            val extra = listOf(e.destinatario, e.pedido.let { if (it.isBlank()) "" else "Pedido $it" }).filter { it.isNotBlank() }
            if (extra.isNotEmpty()) Text(extra.joinToString(" · "), style = Oficina.tipos.bodySm, color = c.muted, maxLines = 1)
        }
        if (!concluida) {
            Column {
                Seta("▲", podeSubir, onSubir)
                Seta("▼", podeDescer, onDescer)
            }
        }
    }
}

@Composable
private fun Seta(simbolo: String, habilitada: Boolean, onClick: () -> Unit) {
    val c = Oficina.cores
    Box(
        Modifier.size(width = 40.dp, height = 28.dp).clickable(enabled = habilitada, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(simbolo, style = Oficina.tipos.bodySm, color = if (habilitada) c.muted else c.faint.copy(alpha = 0.2f))
    }
}
