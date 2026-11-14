package com.roberto.entregas.core

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Otimização da ordem de visita feita no próprio aparelho, sem internet.
 *
 * Usa a distância em linha reta (haversine) multiplicada por um fator de ruas,
 * monta uma rota inicial pelo vizinho mais próximo e melhora com 2-opt e Or-opt
 * até não haver mais ganho. Para até umas 200 paradas roda em menos de um segundo.
 */
object Otimizador {

    /** Ruas não são retas: em cidade, o caminho real fica em média ~35% maior que a linha reta. */
    const val FATOR_RUAS = 1.35

    /** Velocidade média urbana usada só para a estimativa de tempo. */
    const val VELOCIDADE_MEDIA_KMH = 25.0

    /** Tempo médio parado em cada entrega. */
    const val MINUTOS_POR_PARADA = 3

    private const val RAIO_TERRA_KM = 6371.0

    fun distanciaKm(a: Coordenada, b: Coordenada): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val h = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * RAIO_TERRA_KM * asin(min(1.0, sqrt(h)))
    }

    /**
     * Devolve a ordem de visita como índices de [pontos].
     *
     * @param partida ponto de saída (null = começa pela parada que der o menor percurso)
     * @param urgentes mesma ordem de [pontos]; as urgentes são visitadas antes das demais
     * @param voltarAoInicio inclui o retorno até a partida no cálculo (exige [partida])
     */
    fun ordenar(
        partida: Coordenada?,
        pontos: List<Coordenada>,
        urgentes: List<Boolean> = List(pontos.size) { false },
        voltarAoInicio: Boolean = false
    ): List<Int> {
        require(urgentes.size == pontos.size) { "urgentes e pontos com tamanhos diferentes" }
        if (pontos.isEmpty()) return emptyList()

        val grupoUrgente = pontos.indices.filter { urgentes[it] }
        val grupoNormal = pontos.indices.filter { !urgentes[it] }

        val resultado = ArrayList<Int>(pontos.size)
        var origem: Coordenada? = partida

        if (grupoUrgente.isNotEmpty()) {
            // O retorno só conta no último grupo.
            val voltaAqui = voltarAoInicio && grupoNormal.isEmpty()
            val ordem = resolverGrupo(origem, grupoUrgente.map { pontos[it] }, if (voltaAqui) partida else null)
            ordem.forEach { resultado.add(grupoUrgente[it]) }
            origem = pontos[resultado.last()]
        }
        if (grupoNormal.isNotEmpty()) {
            val ordem = resolverGrupo(origem, grupoNormal.map { pontos[it] }, if (voltarAoInicio) partida else null)
            ordem.forEach { resultado.add(grupoNormal[it]) }
        }
        return resultado
    }

    /** Comprimento em linha reta (km) do percurso na ordem dada, sem o fator de ruas. */
    fun comprimentoKm(partida: Coordenada?, ordem: List<Coordenada>, voltarAoInicio: Boolean): Double {
        if (ordem.isEmpty()) return 0.0
        var total = 0.0
        if (partida != null) total += distanciaKm(partida, ordem.first())
        for (i in 0 until ordem.size - 1) total += distanciaKm(ordem[i], ordem[i + 1])
        if (voltarAoInicio && partida != null) total += distanciaKm(ordem.last(), partida)
        return total
    }

    // ------------------------------------------------------------------ interno

    /**
     * Resolve um grupo. Os nós da matriz são: 0..n-1 = pontos, n = início (se houver), n+1 = fim (se houver).
     * Devolve a ordem como índices de [pontos].
     */
    private fun resolverGrupo(inicio: Coordenada?, pontos: List<Coordenada>, fim: Coordenada?): List<Int> {
        val n = pontos.size
        if (n == 1) return listOf(0)

        val nos = ArrayList<Coordenada>(pontos)
        val noInicio = if (inicio != null) { nos.add(inicio); nos.size - 1 } else -1
        val noFim = if (fim != null) { nos.add(fim); nos.size - 1 } else -1
        val d = Array(nos.size) { i -> DoubleArray(nos.size) { j -> distanciaKm(nos[i], nos[j]) } }

        val inicial: IntArray = if (noInicio >= 0) {
            vizinhoMaisProximo(noInicio, n, d)
        } else {
            // Sem partida: testa começar por cada parada e fica com a melhor.
            var melhor: IntArray? = null
            var melhorCusto = Double.MAX_VALUE
            for (s in 0 until n) {
                val c = vizinhoMaisProximo(s, n, d, incluirOrigem = true)
                val custo = custo(c, noInicio, noFim, d)
                if (custo < melhorCusto) { melhorCusto = custo; melhor = c }
            }
            melhor!!
        }

        // seq = [início?] + caminho + [fim?]; só o caminho pode mudar.
        val seq = ArrayList<Int>(n + 2)
        if (noInicio >= 0) seq.add(noInicio)
        inicial.forEach { seq.add(it) }
        if (noFim >= 0) seq.add(noFim)
        val primeiro = if (noInicio >= 0) 1 else 0
        val ultimo = seq.size - 1 - (if (noFim >= 0) 1 else 0)

        var melhorou = true
        var voltas = 0
        while (melhorou && voltas < 50) {
            melhorou = doisOpt(seq, primeiro, ultimo, d) or orOpt(seq, primeiro, ultimo, d)
            voltas++
        }
        return seq.subList(primeiro, ultimo + 1).toList()
    }

    private fun vizinhoMaisProximo(origem: Int, n: Int, d: Array<DoubleArray>, incluirOrigem: Boolean = false): IntArray {
        val visitado = BooleanArray(n)
        val ordem = IntArray(n)
        var k = 0
        var atual = origem
        if (incluirOrigem) { visitado[origem] = true; ordem[k++] = origem }
        while (k < n) {
            var prox = -1
            var melhor = Double.MAX_VALUE
            for (j in 0 until n) {
                if (!visitado[j] && d[atual][j] < melhor) { melhor = d[atual][j]; prox = j }
            }
            visitado[prox] = true
            ordem[k++] = prox
            atual = prox
        }
        return ordem
    }

    private fun custo(caminho: IntArray, noInicio: Int, noFim: Int, d: Array<DoubleArray>): Double {
        var total = 0.0
        if (noInicio >= 0) total += d[noInicio][caminho.first()]
        for (i in 0 until caminho.size - 1) total += d[caminho[i]][caminho[i + 1]]
        if (noFim >= 0) total += d[caminho.last()][noFim]
        return total
    }

    /** Custo da aresta entre posições de seq; posição fora da sequência = ponta aberta (custo 0). */
    private fun aresta(seq: List<Int>, a: Int, b: Int, d: Array<DoubleArray>): Double =
        if (a < 0 || b < 0 || a >= seq.size || b >= seq.size) 0.0 else d[seq[a]][seq[b]]

    private fun doisOpt(seq: ArrayList<Int>, primeiro: Int, ultimo: Int, d: Array<DoubleArray>): Boolean {
        var algum = false
        var melhorou = true
        while (melhorou) {
            melhorou = false
            for (i in primeiro until ultimo) {
                for (j in i + 1..ultimo) {
                    val antes = aresta(seq, i - 1, i, d) + aresta(seq, j, j + 1, d)
                    val depois = aresta(seq, i - 1, j, d) + aresta(seq, i, j + 1, d)
                    if (depois < antes - 1e-9) {
                        seq.subList(i, j + 1).reverse()
                        melhorou = true
                        algum = true
                    }
                }
            }
        }
        return algum
    }

    /** Move trechos de 1 a 3 paradas para outra posição (também invertidos). */
    private fun orOpt(seq: ArrayList<Int>, primeiro: Int, ultimo: Int, d: Array<DoubleArray>): Boolean {
        var algum = false
        for (tam in 1..3) {
            var i = primeiro
            while (i + tam - 1 <= ultimo) {
                val fimSeg = i + tam - 1
                val remocao = aresta(seq, i - 1, i, d) + aresta(seq, fimSeg, fimSeg + 1, d) -
                    aresta(seq, i - 1, fimSeg + 1, d)

                // Sequência sem o trecho, para testar onde reinserir.
                val resto = ArrayList<Int>(seq.size - tam)
                for (p in seq.indices) if (p < i || p > fimSeg) resto.add(seq[p])
                val restoPrimeiro = primeiro
                val restoUltimo = ultimo - tam

                val a = seq[i]
                val b = seq[fimSeg]
                var melhorGanho = 1e-9
                var melhorPos = -2
                var melhorInvertido = false

                // Inserir entre resto[k] e resto[k+1]; k = restoPrimeiro-1 significa logo após a partida
                // (ou no começo, sem partida) e k = restoUltimo significa no final do caminho.
                for (k in restoPrimeiro - 1..restoUltimo) {
                    val x = if (k >= 0) resto[k] else -1
                    val y = if (k + 1 < resto.size) resto[k + 1] else -1
                    val xy = if (x >= 0 && y >= 0) d[x][y] else 0.0
                    val normal = (if (x >= 0) d[x][a] else 0.0) + (if (y >= 0) d[b][y] else 0.0) - xy
                    val invertido = (if (x >= 0) d[x][b] else 0.0) + (if (y >= 0) d[a][y] else 0.0) - xy
                    if (remocao - normal > melhorGanho) { melhorGanho = remocao - normal; melhorPos = k; melhorInvertido = false }
                    if (tam > 1 && remocao - invertido > melhorGanho) { melhorGanho = remocao - invertido; melhorPos = k; melhorInvertido = true }
                }

                if (melhorPos != -2) {
                    val trecho = seq.subList(i, fimSeg + 1).toMutableList()
                    if (melhorInvertido) trecho.reverse()
                    resto.addAll(melhorPos + 1, trecho)
                    seq.clear()
                    seq.addAll(resto)
                    algum = true
                    // Não avança i: a posição agora tem outro conteúdo.
                } else {
                    i++
                }
            }
        }
        return algum
    }
}
