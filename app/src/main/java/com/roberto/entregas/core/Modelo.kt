package com.roberto.entregas.core

/** Ponto geográfico em graus decimais. */
data class Coordenada(val lat: Double, val lon: Double)

enum class Status { PENDENTE, ENTREGUE, FALHA }

data class Entrega(
    val id: String,
    val endereco: String,
    val lat: Double? = null,
    val lon: Double? = null,
    val destinatario: String = "",
    val telefone: String = "",
    val observacao: String = "",
    val pedido: String = "",
    val urgente: Boolean = false,
    val status: Status = Status.PENDENTE,
    /** Motivo da última tentativa sem sucesso (fica guardado mesmo se voltar para o fim da rota). */
    val motivoFalha: String = "",
    /** Quantas vezes o entregador tentou e não conseguiu entregar. */
    val tentativas: Int = 0,
    /** Momento em que foi marcada como entregue ou não entregue (epoch ms). */
    val concluidaEm: Long? = null
) {
    val coordenada: Coordenada?
        get() = if (lat != null && lon != null) Coordenada(lat, lon) else null

    val temLocal: Boolean get() = lat != null && lon != null
}

data class Rota(
    val id: String,
    val criadaEm: Long,
    val iniciadaEm: Long? = null,
    val finalizadaEm: Long? = null,
    /** Partida fixa. Sem ela, a otimização usa a localização atual do aparelho. */
    val partidaRotulo: String = "",
    val partidaLat: Double? = null,
    val partidaLon: Double? = null,
    val voltarAoInicio: Boolean = false,
    val otimizada: Boolean = false,
    val kmEstimado: Double = 0.0,
    val minutosEstimados: Int = 0,
    /** A ordem da lista é a ordem de visita. */
    val entregas: List<Entrega> = emptyList()
) {
    val partida: Coordenada?
        get() = if (partidaLat != null && partidaLon != null) Coordenada(partidaLat, partidaLon) else null

    val pendentes: List<Entrega> get() = entregas.filter { it.status == Status.PENDENTE }
    val entregues: Int get() = entregas.count { it.status == Status.ENTREGUE }
    val falhas: Int get() = entregas.count { it.status == Status.FALHA }

    /** Próxima parada: a primeira pendente na ordem da lista. */
    val proxima: Entrega? get() = entregas.firstOrNull { it.status == Status.PENDENTE }

    val vazia: Boolean get() = entregas.isEmpty()
}

data class DadosApp(
    val atual: Rota,
    val historico: List<Rota> = emptyList()
)
