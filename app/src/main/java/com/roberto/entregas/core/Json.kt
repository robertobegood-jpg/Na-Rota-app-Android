package com.roberto.entregas.core

/**
 * JSON mínimo, sem dependências, para salvar os dados e o backup.
 * (O org.json do Android não funciona nos testes de computador, por isso este.)
 *
 * Valores: null, Boolean, Double/Long/Int, String, List<Any?>, Map<String, Any?>.
 */
object Json {

    fun escrever(valor: Any?): String = StringBuilder().also { escrever(valor, it) }.toString()

    private fun escrever(v: Any?, sb: StringBuilder) {
        when (v) {
            null -> sb.append("null")
            is Boolean -> sb.append(v)
            is Int, is Long -> sb.append(v)
            is Double -> if (v.isNaN() || v.isInfinite()) sb.append("null")
                else if (v == Math.floor(v) && kotlin.math.abs(v) < 1e15) sb.append(v.toLong()) else sb.append(v)
            is Float -> escrever(v.toDouble(), sb)
            is String -> texto(v, sb)
            is Map<*, *> -> {
                sb.append('{')
                var primeiro = true
                for ((k, valor) in v) {
                    if (!primeiro) sb.append(',')
                    primeiro = false
                    texto(k.toString(), sb); sb.append(':'); escrever(valor, sb)
                }
                sb.append('}')
            }
            is Iterable<*> -> {
                sb.append('[')
                var primeiro = true
                for (item in v) {
                    if (!primeiro) sb.append(',')
                    primeiro = false
                    escrever(item, sb)
                }
                sb.append(']')
            }
            else -> texto(v.toString(), sb)
        }
    }

    private fun texto(s: String, sb: StringBuilder) {
        sb.append('"')
        for (c in s) when (c) {
            '"' -> sb.append("\\\"")
            '\\' -> sb.append("\\\\")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            '\t' -> sb.append("\\t")
            '\b' -> sb.append("\\b")
            '\u000C' -> sb.append("\\f")
            else -> if (c < ' ') sb.append(String.format("\\u%04x", c.code)) else sb.append(c)
        }
        sb.append('"')
    }

    fun ler(texto: String): Any? {
        val p = Leitor(texto)
        p.espacos()
        val v = p.valor()
        p.espacos()
        if (p.i != texto.length) throw IllegalArgumentException("JSON com sobra na posição ${p.i}")
        return v
    }

    private class Leitor(val s: String) {
        var i = 0

        fun espacos() { while (i < s.length && s[i].isWhitespace()) i++ }

        fun valor(): Any? {
            espacos()
            if (i >= s.length) throw IllegalArgumentException("JSON terminou antes da hora")
            return when (val c = s[i]) {
                '{' -> objeto()
                '[' -> lista()
                '"' -> texto()
                't' -> palavra("true", true)
                'f' -> palavra("false", false)
                'n' -> palavra("null", null)
                else -> if (c == '-' || c.isDigit()) numero() else throw IllegalArgumentException("Caractere inesperado '$c' em $i")
            }
        }

        private fun palavra(p: String, v: Any?): Any? {
            if (!s.startsWith(p, i)) throw IllegalArgumentException("Esperava $p em $i")
            i += p.length
            return v
        }

        private fun numero(): Any {
            val ini = i
            if (s[i] == '-') i++
            while (i < s.length && (s[i].isDigit() || s[i] in ".eE+-")) i++
            val t = s.substring(ini, i)
            return if (t.any { it in ".eE" }) t.toDouble() else t.toLongOrNull() ?: t.toDouble()
        }

        private fun texto(): String {
            i++ // "
            val sb = StringBuilder()
            while (true) {
                if (i >= s.length) throw IllegalArgumentException("Texto sem fim")
                val c = s[i++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        when (val e = s[i++]) {
                            '"' -> sb.append('"'); '\\' -> sb.append('\\'); '/' -> sb.append('/')
                            'n' -> sb.append('\n'); 'r' -> sb.append('\r'); 't' -> sb.append('\t')
                            'b' -> sb.append('\b'); 'f' -> sb.append('\u000C')
                            'u' -> { sb.append(s.substring(i, i + 4).toInt(16).toChar()); i += 4 }
                            else -> throw IllegalArgumentException("Escape inválido \\$e")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun objeto(): Map<String, Any?> {
            i++ // {
            val m = LinkedHashMap<String, Any?>()
            espacos()
            if (s[i] == '}') { i++; return m }
            while (true) {
                espacos()
                if (s[i] != '"') throw IllegalArgumentException("Esperava chave em $i")
                val k = texto()
                espacos()
                if (s[i++] != ':') throw IllegalArgumentException("Esperava ':' em ${i - 1}")
                m[k] = valor()
                espacos()
                when (s[i++]) {
                    ',' -> continue
                    '}' -> return m
                    else -> throw IllegalArgumentException("Esperava ',' ou '}' em ${i - 1}")
                }
            }
        }

        private fun lista(): List<Any?> {
            i++ // [
            val l = ArrayList<Any?>()
            espacos()
            if (s[i] == ']') { i++; return l }
            while (true) {
                l.add(valor())
                espacos()
                when (s[i++]) {
                    ',' -> continue
                    ']' -> return l
                    else -> throw IllegalArgumentException("Esperava ',' ou ']' em ${i - 1}")
                }
            }
        }
    }
}

/** Converte os dados do app de/para JSON. Campos desconhecidos são ignorados e os ausentes ganham padrão. */
object Serializacao {

    const val VERSAO = 1

    fun paraJson(dados: DadosApp): String = Json.escrever(
        mapOf(
            "app" to "rota-entregas",
            "versao" to VERSAO,
            "atual" to rota(dados.atual),
            "historico" to dados.historico.map { rota(it) }
        )
    )

    fun deJson(texto: String): DadosApp {
        val m = Json.ler(texto) as? Map<*, *> ?: throw IllegalArgumentException("Arquivo não é um backup do app")
        if (m["app"] != "rota-entregas") throw IllegalArgumentException("Arquivo não é um backup do app")
        val atual = (m["atual"] as? Map<*, *>)?.let { rota(it) } ?: throw IllegalArgumentException("Backup sem rota atual")
        val hist = (m["historico"] as? List<*>)?.mapNotNull { (it as? Map<*, *>)?.let { r -> rota(r) } } ?: emptyList()
        return DadosApp(atual, hist)
    }

    private fun rota(r: Rota): Map<String, Any?> = mapOf(
        "id" to r.id, "criadaEm" to r.criadaEm, "iniciadaEm" to r.iniciadaEm, "finalizadaEm" to r.finalizadaEm,
        "partidaRotulo" to r.partidaRotulo, "partidaLat" to r.partidaLat, "partidaLon" to r.partidaLon,
        "voltarAoInicio" to r.voltarAoInicio, "otimizada" to r.otimizada,
        "kmEstimado" to r.kmEstimado, "minutosEstimados" to r.minutosEstimados,
        "entregas" to r.entregas.map { entrega(it) }
    )

    private fun entrega(e: Entrega): Map<String, Any?> = mapOf(
        "id" to e.id, "endereco" to e.endereco, "lat" to e.lat, "lon" to e.lon,
        "destinatario" to e.destinatario, "telefone" to e.telefone, "observacao" to e.observacao,
        "pedido" to e.pedido, "urgente" to e.urgente, "status" to e.status.name,
        "motivoFalha" to e.motivoFalha, "tentativas" to e.tentativas, "concluidaEm" to e.concluidaEm
    )

    private fun rota(m: Map<*, *>): Rota = Rota(
        id = m.str("id").ifEmpty { "r" + System.nanoTime() },
        criadaEm = m.long("criadaEm") ?: 0L,
        iniciadaEm = m.long("iniciadaEm"),
        finalizadaEm = m.long("finalizadaEm"),
        partidaRotulo = m.str("partidaRotulo"),
        partidaLat = m.dbl("partidaLat"),
        partidaLon = m.dbl("partidaLon"),
        voltarAoInicio = m["voltarAoInicio"] == true,
        otimizada = m["otimizada"] == true,
        kmEstimado = m.dbl("kmEstimado") ?: 0.0,
        minutosEstimados = (m.long("minutosEstimados") ?: 0L).toInt(),
        entregas = (m["entregas"] as? List<*>)?.mapNotNull { (it as? Map<*, *>)?.let { e -> entrega(e) } } ?: emptyList()
    )

    private fun entrega(m: Map<*, *>): Entrega = Entrega(
        id = m.str("id").ifEmpty { "e" + System.nanoTime() },
        endereco = m.str("endereco"),
        lat = m.dbl("lat"),
        lon = m.dbl("lon"),
        destinatario = m.str("destinatario"),
        telefone = m.str("telefone"),
        observacao = m.str("observacao"),
        pedido = m.str("pedido"),
        urgente = m["urgente"] == true,
        status = runCatching { Status.valueOf(m.str("status")) }.getOrDefault(Status.PENDENTE),
        motivoFalha = m.str("motivoFalha"),
        tentativas = (m.long("tentativas") ?: 0L).toInt(),
        concluidaEm = m.long("concluidaEm")
    )

    private fun Map<*, *>.str(k: String): String = this[k] as? String ?: ""
    private fun Map<*, *>.dbl(k: String): Double? = (this[k] as? Number)?.toDouble()
    private fun Map<*, *>.long(k: String): Long? = (this[k] as? Number)?.toLong()
}
