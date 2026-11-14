package com.roberto.entregas.dados

import android.content.Context
import com.roberto.entregas.core.DadosApp
import com.roberto.entregas.core.Operacoes
import com.roberto.entregas.core.Serializacao
import java.io.File
import java.util.UUID

fun novoId(): String = UUID.randomUUID().toString()

/** Guarda tudo num arquivo JSON dentro do app. Escreve num temporário e renomeia, para nunca corromper. */
class Armazem(context: Context) {

    private val pasta: File = context.filesDir
    private val arquivo = File(pasta, "dados.json")

    fun carregar(): DadosApp {
        if (arquivo.exists()) {
            try {
                return Serializacao.deJson(arquivo.readText())
            } catch (e: Exception) {
                // Não apaga: guarda o arquivo ruim ao lado, para dar para recuperar depois.
                arquivo.renameTo(File(pasta, "dados-com-erro-${System.currentTimeMillis()}.json"))
            }
        }
        return DadosApp(atual = Operacoes.novaRota(novoId(), System.currentTimeMillis()))
    }

    @Synchronized
    fun salvar(dados: DadosApp) {
        val tmp = File(pasta, "dados.json.tmp")
        tmp.writeText(Serializacao.paraJson(dados))
        if (!tmp.renameTo(arquivo)) {
            arquivo.delete()
            tmp.renameTo(arquivo)
        }
    }
}
