package com.roberto.entregas.core

import java.text.Normalizer
import java.util.Locale

/** Dados do Pix que recebe os cafezinhos. Com a chave vazia, a opção some do app. */
object Cafezinho {
    const val CHAVE_PIX = "robertobegood+itau@gmail.com"
    const val NOME = "Roberto Santos da Silva"
    const val CIDADE = "Sao Paulo"

    val configurado: Boolean get() = CHAVE_PIX.isNotBlank() && NOME.isNotBlank() && CIDADE.isNotBlank()
}

/** Código Pix "copia e cola" (BR Code estático, padrão EMV do Banco Central). */
object Pix {

    private fun campo(id: String, valor: String): String =
        id + valor.length.toString().padStart(2, '0') + valor

    private fun limpar(texto: String, max: Int): String =
        Normalizer.normalize(texto, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^A-Za-z0-9 ]"), "")
            .uppercase(Locale.ROOT)
            .trim()
            .take(max)

    /** @param centavos null = quem paga escolhe o valor no app do banco */
    fun payload(chave: String, nome: String, cidade: String, centavos: Long?, descricao: String = ""): String {
        val conta = buildString {
            append(campo("00", "br.gov.bcb.pix"))
            append(campo("01", chave.trim()))
            val desc = limpar(descricao, 40)
            if (desc.isNotEmpty()) append(campo("02", desc))
        }
        val semCrc = buildString {
            append(campo("00", "01"))
            append(campo("26", conta))
            append(campo("52", "0000"))
            append(campo("53", "986"))
            if (centavos != null && centavos > 0) {
                append(campo("54", String.format(Locale.ROOT, "%d.%02d", centavos / 100, centavos % 100)))
            }
            append(campo("58", "BR"))
            append(campo("59", limpar(nome, 25)))
            append(campo("60", limpar(cidade, 15)))
            append(campo("62", campo("05", "***")))
            append("6304")
        }
        return semCrc + crc16(semCrc)
    }

    /** CRC16-CCITT (polinômio 0x1021, início 0xFFFF), em 4 dígitos hexadecimais maiúsculos. */
    fun crc16(texto: String): String {
        var crc = 0xFFFF
        for (b in texto.toByteArray(Charsets.UTF_8)) {
            crc = crc xor ((b.toInt() and 0xFF) shl 8)
            repeat(8) {
                crc = if (crc and 0x8000 != 0) (crc shl 1) xor 0x1021 else crc shl 1
                crc = crc and 0xFFFF
            }
        }
        return String.format(Locale.ROOT, "%04X", crc)
    }
}
