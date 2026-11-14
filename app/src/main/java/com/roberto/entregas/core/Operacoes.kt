package com.roberto.entregas.core

import kotlin.math.roundToInt

/** Operações puras sobre a rota. Não tocam em Android, então podem ser testadas no computador. */
object Operacoes {

    fun novaRota(id: String, agora: Long, base: Rota? = null): Rota = Rota(
        id = id,
        criadaEm = agora,
        partidaRotulo = base?.partidaRotulo ?: "",
        partidaLat = base?.partidaLat,
        partidaLon = base?.partidaLon,
        voltarAoInicio = base?.voltarAoInicio ?: false
    )

    /**
     * Reordena as pendentes com local. As já concluídas ficam no topo (na ordem em que foram feitas)
     * e as pendentes sem local vão para o final, para o entregador corrigir o endereço.
     *
     * @param origemAtual localização do aparelho, usada quando a rota não tem partida fixa
     */
    fun otimizar(rota: Rota, origemAtual: Coordenada?): Rota {
        val concluidas = rota.entregas.filter { it.status != Status.PENDENTE }
            .sortedBy { it.concluidaEm ?: Long.MAX_VALUE }
        val comLocal = rota.entregas.filter { it.status == Status.PENDENTE && it.temLocal }
        val semLocal = rota.entregas.filter { it.status == Status.PENDENTE && !it.temLocal }

        val partida = rota.partida ?: origemAtual
        val voltar = rota.voltarAoInicio && partida != null
        val ordem = Otimizador.ordenar(
            partida = partida,
            pontos = comLocal.map { it.coordenada!! },
            urgentes = comLocal.map { it.urgente },
            voltarAoInicio = voltar
        )
        val ordenadas = ordem.map { comLocal[it] }
        return comEstimativa(
            rota.copy(entregas = concluidas + ordenadas + semLocal, otimizada = true),
            origemAtual
        )
    }

    /** Recalcula km e tempo das pendentes na ordem atual da lista. */
    fun comEstimativa(rota: Rota, origemAtual: Coordenada?): Rota {
        val pend = rota.pendentes.mapNotNull { it.coordenada }
        val partida = rota.partida ?: origemAtual
        val km = Otimizador.comprimentoKm(partida, pend, rota.voltarAoInicio && partida != null) * Otimizador.FATOR_RUAS
        val minutos = (km / Otimizador.VELOCIDADE_MEDIA_KMH * 60.0).roundToInt() + pend.size * Otimizador.MINUTOS_POR_PARADA
        return rota.copy(kmEstimado = km, minutosEstimados = minutos)
    }

    fun adicionar(rota: Rota, novas: List<Entrega>): Rota =
        rota.copy(entregas = rota.entregas + novas, otimizada = rota.otimizada && novas.isEmpty())

    fun salvar(rota: Rota, entrega: Entrega): Rota {
        val existe = rota.entregas.any { it.id == entrega.id }
        if (!existe) return adicionar(rota, listOf(entrega))
        val antiga = rota.entregas.first { it.id == entrega.id }
        val mudouLocal = antiga.lat != entrega.lat || antiga.lon != entrega.lon || antiga.urgente != entrega.urgente
        return rota.copy(
            entregas = rota.entregas.map { if (it.id == entrega.id) entrega else it },
            otimizada = rota.otimizada && !mudouLocal
        )
    }

    fun remover(rota: Rota, id: String): Rota =
        rota.copy(entregas = rota.entregas.filterNot { it.id == id })

    /** Sobe (-1) ou desce (+1) uma entrega na lista. Ajuste manual tira o selo de "otimizada". */
    fun mover(rota: Rota, id: String, direcao: Int): Rota {
        val lista = rota.entregas.toMutableList()
        val i = lista.indexOfFirst { it.id == id }
        val j = i + direcao
        if (i < 0 || j !in lista.indices) return rota
        val tmp = lista[i]; lista[i] = lista[j]; lista[j] = tmp
        return rota.copy(entregas = lista, otimizada = false)
    }

    fun marcarEntregue(rota: Rota, id: String, agora: Long): Rota = iniciar(rota, agora).copy(
        entregas = rota.entregas.map {
            if (it.id == id) it.copy(status = Status.ENTREGUE, concluidaEm = agora) else it
        }
    )

    /**
     * @param tentarDepois true = volta para o fim da rota como pendente; false = encerra como não entregue
     */
    fun marcarFalha(rota: Rota, id: String, motivo: String, tentarDepois: Boolean, agora: Long): Rota {
        val alvo = rota.entregas.firstOrNull { it.id == id } ?: return rota
        val base = iniciar(rota, agora)
        return if (tentarDepois) {
            val atualizada = alvo.copy(motivoFalha = motivo, tentativas = alvo.tentativas + 1)
            base.copy(entregas = rota.entregas.filterNot { it.id == id } + atualizada)
        } else {
            base.copy(entregas = rota.entregas.map {
                if (it.id == id) it.copy(
                    status = Status.FALHA, motivoFalha = motivo,
                    tentativas = it.tentativas + 1, concluidaEm = agora
                ) else it
            })
        }
    }

    /** Desfaz entregue/não entregue: volta a pendente na mesma posição. */
    fun reabrir(rota: Rota, id: String): Rota = rota.copy(
        entregas = rota.entregas.map {
            if (it.id == id) it.copy(status = Status.PENDENTE, concluidaEm = null) else it
        }
    )

    fun iniciar(rota: Rota, agora: Long): Rota =
        if (rota.iniciadaEm == null) rota.copy(iniciadaEm = agora) else rota

    /**
     * Arquiva a rota atual no histórico e abre uma nova, mantendo a partida e as pendentes
     * (o que não foi feito hoje continua na próxima rota).
     */
    fun finalizar(dados: DadosApp, novoId: String, agora: Long, levarPendentes: Boolean): DadosApp {
        val atual = dados.atual
        val feitas = atual.entregas.filter { it.status != Status.PENDENTE }
        val pendentes = atual.pendentes
        val arquivada = atual.copy(
            finalizadaEm = agora,
            entregas = if (levarPendentes) feitas else atual.entregas
        )
        val nova = novaRota(novoId, agora, atual).copy(
            entregas = if (levarPendentes) pendentes.map { it.copy(concluidaEm = null) } else emptyList()
        )
        val historico = if (arquivada.entregas.isEmpty()) dados.historico else listOf(arquivada) + dados.historico
        return DadosApp(atual = nova, historico = historico)
    }

    fun duracaoMinutos(rota: Rota): Int? {
        val ini = rota.iniciadaEm ?: return null
        val fim = rota.finalizadaEm ?: rota.entregas.mapNotNull { it.concluidaEm }.maxOrNull() ?: return null
        return ((fim - ini) / 60000L).toInt().coerceAtLeast(0)
    }
}

/** Utilidades de texto. */
object Texto {

    private val numeracao = Regex("""^\s*(\d{1,3}\s*[.)\-:]|[-*•·]+)\s*""")

    /**
     * Separa uma lista colada (WhatsApp, planilha, bloco de notas) em endereços, um por linha.
     * Remove numeração do tipo "1.", "2)", "- " e linhas vazias ou repetidas.
     */
    fun separarEnderecos(texto: String): List<String> =
        texto.lines()
            .map { it.replace('\t', ' ').replace(numeracao, "").trim().replace(Regex("\\s{2,}"), " ") }
            .filter { it.length >= 4 }
            .distinctBy { it.lowercase() }

    /** Só os dígitos; acrescenta 55 em números brasileiros de 10 ou 11 dígitos (para o wa.me). */
    fun telefoneWhatsApp(telefone: String): String? {
        val d = telefone.filter { it.isDigit() }.trimStart('0')
        return when {
            d.length in 10..11 -> "55$d"
            d.length in 12..13 && d.startsWith("55") -> d
            else -> null
        }
    }

    fun km(valor: Double): String =
        if (valor < 10) String.format(java.util.Locale("pt", "BR"), "%.1f km", valor)
        else "${valor.roundToInt()} km"

    fun duracao(minutos: Int): String = when {
        minutos < 60 -> "$minutos min"
        minutos % 60 == 0 -> "${minutos / 60} h"
        else -> "${minutos / 60} h ${minutos % 60} min"
    }
}
