package com.roberto.entregas.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class NucleoTest {

    private val sp = Coordenada(-23.5505, -46.6333)

    private fun perto(dLatKm: Double, dLonKm: Double) =
        Coordenada(sp.lat + dLatKm / 111.0, sp.lon + dLonKm / (111.0 * Math.cos(Math.toRadians(sp.lat))))

    private fun permutacoes(n: Int): List<List<Int>> {
        if (n == 0) return listOf(emptyList())
        val res = ArrayList<List<Int>>()
        fun gera(atual: MutableList<Int>, usados: BooleanArray) {
            if (atual.size == n) { res.add(atual.toList()); return }
            for (i in 0 until n) if (!usados[i]) {
                usados[i] = true; atual.add(i); gera(atual, usados); atual.removeAt(atual.size - 1); usados[i] = false
            }
        }
        gera(ArrayList(), BooleanArray(n))
        return res
    }

    private fun otimoForcaBruta(partida: Coordenada?, pts: List<Coordenada>, voltar: Boolean): Double =
        permutacoes(pts.size).minOf { p -> Otimizador.comprimentoKm(partida, p.map { pts[it] }, voltar) }

    @Test
    fun pontosEmLinhaSaemEmOrdem() {
        val pts = listOf(5.0, 1.0, 4.0, 2.0, 3.0).map { perto(0.0, it) }
        val ordem = Otimizador.ordenar(sp, pts)
        assertEquals(listOf(1, 3, 4, 2, 0), ordem)
    }

    @Test
    fun ordemEhUmaPermutacaoCompleta() {
        val r = Random(7)
        repeat(50) {
            val n = r.nextInt(1, 40)
            val pts = List(n) { perto(r.nextDouble(-10.0, 10.0), r.nextDouble(-10.0, 10.0)) }
            val urg = List(n) { r.nextInt(5) == 0 }
            val ordem = Otimizador.ordenar(if (r.nextBoolean()) sp else null, pts, urg, voltarAoInicio = r.nextBoolean())
            assertEquals(n, ordem.size)
            assertEquals((0 until n).toSet(), ordem.toSet())
        }
    }

    @Test
    fun ficaPertoDoOtimoComPartida() {
        val r = Random(42)
        var pior = 1.0
        var soma = 0.0
        val casos = 150
        repeat(casos) {
            val n = r.nextInt(3, 8)
            val pts = List(n) { perto(r.nextDouble(-8.0, 8.0), r.nextDouble(-8.0, 8.0)) }
            val voltar = r.nextBoolean()
            val ordem = Otimizador.ordenar(sp, pts, voltarAoInicio = voltar)
            val nosso = Otimizador.comprimentoKm(sp, ordem.map { pts[it] }, voltar)
            val otimo = otimoForcaBruta(sp, pts, voltar)
            val razao = nosso / otimo
            pior = maxOf(pior, razao)
            soma += razao
        }
        println("com partida: média ${"%.4f".format(soma / casos)}, pior ${"%.4f".format(pior)}")
        assertTrue("pior caso ${"%.3f".format(pior)} acima de 5%", pior <= 1.05)
    }

    @Test
    fun ficaPertoDoOtimoSemPartida() {
        val r = Random(99)
        var pior = 1.0
        repeat(150) {
            val n = r.nextInt(3, 8)
            val pts = List(n) { perto(r.nextDouble(-8.0, 8.0), r.nextDouble(-8.0, 8.0)) }
            val ordem = Otimizador.ordenar(null, pts)
            val nosso = Otimizador.comprimentoKm(null, ordem.map { pts[it] }, false)
            pior = maxOf(pior, nosso / otimoForcaBruta(null, pts, false))
        }
        println("sem partida: pior ${"%.4f".format(pior)}")
        assertTrue("pior caso ${"%.3f".format(pior)} acima de 5%", pior <= 1.05)
    }

    @Test
    fun urgentesVemPrimeiro() {
        val r = Random(3)
        repeat(30) {
            val n = 15
            val pts = List(n) { perto(r.nextDouble(-8.0, 8.0), r.nextDouble(-8.0, 8.0)) }
            val urg = List(n) { it % 4 == 0 }
            val ordem = Otimizador.ordenar(sp, pts, urg)
            val qtd = urg.count { it }
            assertTrue(ordem.take(qtd).all { urg[it] })
            assertTrue(ordem.drop(qtd).none { urg[it] })
        }
    }

    @Test
    fun melhoraMuitoUmaOrdemAleatoria() {
        val r = Random(5)
        val pts = List(80) { perto(r.nextDouble(-15.0, 15.0), r.nextDouble(-15.0, 15.0)) }
        val aleatoria = Otimizador.comprimentoKm(sp, pts, false)
        val ordem = Otimizador.ordenar(sp, pts)
        val otimizada = Otimizador.comprimentoKm(sp, ordem.map { pts[it] }, false)
        println("80 paradas: aleatória ${"%.1f".format(aleatoria)} km -> otimizada ${"%.1f".format(otimizada)} km")
        assertTrue(otimizada < aleatoria * 0.35)
    }

    @Test
    fun duzentasParadasRodamRapido() {
        val r = Random(11)
        val pts = List(200) { perto(r.nextDouble(-20.0, 20.0), r.nextDouble(-20.0, 20.0)) }
        val ini = System.nanoTime()
        val ordem = Otimizador.ordenar(null, pts, voltarAoInicio = false)
        val ms = (System.nanoTime() - ini) / 1_000_000
        println("200 paradas sem partida: $ms ms")
        assertEquals(200, ordem.toSet().size)
        assertTrue("demorou $ms ms", ms < 5000)
    }

    // ---------------------------------------------------------------- operações

    private fun entrega(id: String, km: Double?, status: Status = Status.PENDENTE, urgente: Boolean = false, em: Long? = null) =
        Entrega(
            id = id, endereco = "Rua $id",
            lat = km?.let { perto(0.0, it).lat }, lon = km?.let { perto(0.0, it).lon },
            status = status, urgente = urgente, concluidaEm = em
        )

    @Test
    fun otimizarMantemConcluidasNoTopoESemLocalNoFim() {
        val rota = Rota(
            id = "r", criadaEm = 0, partidaLat = sp.lat, partidaLon = sp.lon,
            entregas = listOf(
                entrega("c", 3.0), entrega("semLocal", null), entrega("feita2", 9.0, Status.ENTREGUE, em = 20),
                entrega("a", 1.0), entrega("feita1", 8.0, Status.FALHA, em = 10), entrega("b", 2.0)
            )
        )
        val o = Operacoes.otimizar(rota, null)
        assertEquals(listOf("feita1", "feita2", "a", "b", "c", "semLocal"), o.entregas.map { it.id })
        assertTrue(o.otimizada)
        assertEquals("a", o.proxima?.id)
        // 3 km em linha reta x fator de ruas
        assertEquals(3.0 * Otimizador.FATOR_RUAS, o.kmEstimado, 0.05)
        assertTrue(o.minutosEstimados >= 3 * Otimizador.MINUTOS_POR_PARADA)
    }

    @Test
    fun otimizarSemPartidaUsaLocalizacaoAtual() {
        val rota = Rota(id = "r", criadaEm = 0, entregas = listOf(entrega("longe", 10.0), entrega("perto", 1.0)))
        val o = Operacoes.otimizar(rota, sp)
        assertEquals(listOf("perto", "longe"), o.entregas.map { it.id })
    }

    @Test
    fun falhaComTentarDepoisVaiParaOFim() {
        var rota = Rota(id = "r", criadaEm = 0, entregas = listOf(entrega("a", 1.0), entrega("b", 2.0), entrega("c", 3.0)))
        rota = Operacoes.marcarFalha(rota, "a", "Ausente", tentarDepois = true, agora = 100)
        assertEquals(listOf("b", "c", "a"), rota.entregas.map { it.id })
        assertEquals(Status.PENDENTE, rota.entregas.last().status)
        assertEquals(1, rota.entregas.last().tentativas)
        assertEquals("b", rota.proxima?.id)
        assertEquals(100L, rota.iniciadaEm)

        rota = Operacoes.marcarFalha(rota, "b", "Recusou", tentarDepois = false, agora = 200)
        assertEquals(Status.FALHA, rota.entregas.first { it.id == "b" }.status)
        assertEquals(100L, rota.iniciadaEm) // não reinicia
        rota = Operacoes.marcarEntregue(rota, "c", 300)
        rota = Operacoes.marcarEntregue(rota, "a", 400)
        assertEquals(null, rota.proxima)
        assertEquals(2, rota.entregues)
        assertEquals(1, rota.falhas)

        rota = Operacoes.reabrir(rota, "c")
        assertEquals("c", rota.proxima?.id)
    }

    @Test
    fun moverTrocaPosicaoETiraOtimizada() {
        val rota = Rota(id = "r", criadaEm = 0, otimizada = true, entregas = listOf(entrega("a", 1.0), entrega("b", 2.0)))
        val m = Operacoes.mover(rota, "b", -1)
        assertEquals(listOf("b", "a"), m.entregas.map { it.id })
        assertTrue(!m.otimizada)
        assertEquals(m, Operacoes.mover(m, "b", -1)) // já está no topo
    }

    @Test
    fun finalizarArquivaELevaPendentes() {
        val atual = Rota(
            id = "r1", criadaEm = 0, iniciadaEm = 1000, partidaRotulo = "Base", partidaLat = 1.0, partidaLon = 2.0,
            voltarAoInicio = true,
            entregas = listOf(entrega("a", 1.0, Status.ENTREGUE, em = 61000), entrega("b", 2.0))
        )
        val d = Operacoes.finalizar(DadosApp(atual), "r2", 121000, levarPendentes = true)
        assertEquals(1, d.historico.size)
        assertEquals(listOf("a"), d.historico[0].entregas.map { it.id })
        assertEquals(2, Operacoes.duracaoMinutos(d.historico[0]))
        assertEquals("r2", d.atual.id)
        assertEquals(listOf("b"), d.atual.entregas.map { it.id })
        assertEquals("Base", d.atual.partidaRotulo)
        assertTrue(d.atual.voltarAoInicio)

        // rota vazia não entra no histórico
        val d2 = Operacoes.finalizar(DadosApp(Rota("x", 0)), "y", 1, true)
        assertEquals(0, d2.historico.size)
    }

    // ---------------------------------------------------------------- texto, json, pix

    @Test
    fun separaListaColada() {
        val texto = """
            1. Rua Augusta, 1500 - Consolação
            2) Av. Paulista 1000
            - Rua da Consolação, 200

            • Rua Augusta, 1500 - Consolação
            ok
            	Alameda Santos,   45
        """.trimIndent()
        assertEquals(
            listOf("Rua Augusta, 1500 - Consolação", "Av. Paulista 1000", "Rua da Consolação, 200", "Alameda Santos, 45"),
            Texto.separarEnderecos(texto)
        )
    }

    @Test
    fun telefoneParaWhatsApp() {
        assertEquals("5511987654321", Texto.telefoneWhatsApp("(11) 98765-4321"))
        assertEquals("5511987654321", Texto.telefoneWhatsApp("+55 11 98765-4321"))
        assertEquals("551133334444", Texto.telefoneWhatsApp("011 3333-4444"))
        assertEquals(null, Texto.telefoneWhatsApp("1234"))
    }

    @Test
    fun formataKmETempo() {
        assertEquals("45 min", Texto.duracao(45))
        assertEquals("2 h", Texto.duracao(120))
        assertEquals("1 h 5 min", Texto.duracao(65))
        assertEquals("12 km", Texto.km(12.4))
        assertEquals("3,5 km", Texto.km(3.46))
    }

    @Test
    fun jsonIdaEVolta() {
        val dados = DadosApp(
            atual = Rota(
                id = "r1", criadaEm = 1731546000000, partidaRotulo = "Base \"central\"\nZona Sul",
                partidaLat = -23.5, partidaLon = -46.6, voltarAoInicio = true, otimizada = true,
                kmEstimado = 12.75, minutosEstimados = 48,
                entregas = listOf(
                    Entrega(
                        "e1", "Rua São João, 12 — ap. 3\\B", -23.55, -46.63, "José Ação", "(11) 9", "portão\tazul",
                        "#123", true, Status.FALHA, "Ausente", 2, 1731546100000
                    ),
                    Entrega("e2", "Sem local")
                )
            ),
            historico = listOf(Rota("r0", 5, iniciadaEm = 6, finalizadaEm = 7))
        )
        val texto = Serializacao.paraJson(dados)
        assertEquals(dados, Serializacao.deJson(texto))
        // tolera campos novos e ausentes
        val parcial = """{"app":"rota-entregas","versao":9,"novo":[1,2,{"x":null}],"atual":{"id":"a","entregas":[{"id":"z","endereco":"X","lat":-1.5e1}]}}"""
        val lido = Serializacao.deJson(parcial)
        assertEquals(-15.0, lido.atual.entregas[0].lat!!, 0.0)
        assertEquals(Status.PENDENTE, lido.atual.entregas[0].status)
    }

    @Test(expected = IllegalArgumentException::class)
    fun recusaArquivoQueNaoEhBackup() {
        Serializacao.deJson("""{"foo":1}""")
    }

    @Test
    fun crcPix() {
        // Valor de verificação padrão do CRC-16/CCITT-FALSE
        assertEquals("29B1", Pix.crc16("123456789"))
        val p = Pix.payload(Cafezinho.CHAVE_PIX, Cafezinho.NOME, Cafezinho.CIDADE, 500, "Cafezinho")
        assertTrue(p.startsWith("000201"))
        assertTrue(p.contains("5404" + "5.00"))
        assertTrue(p.contains("5303986"))
        assertEquals(Pix.crc16(p.dropLast(4)), p.takeLast(4))
        val livre = Pix.payload(Cafezinho.CHAVE_PIX, Cafezinho.NOME, Cafezinho.CIDADE, null)
        assertTrue(!livre.contains("5404"))
    }
}
