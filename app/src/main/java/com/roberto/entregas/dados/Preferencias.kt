package com.roberto.entregas.dados

import android.content.Context

enum class AppGps(val rotulo: String) {
    PERGUNTAR("Perguntar"),
    GOOGLE_MAPS("Google Maps"),
    WAZE("Waze")
}

enum class Tema(val rotulo: String) { ESCURO("Escuro"), CLARO("Claro"), SISTEMA("Sistema") }

data class Config(
    val gps: AppGps = AppGps.PERGUNTAR,
    val tema: Tema = Tema.ESCURO,
    val mostrarMapa: Boolean = true,
    val mensagemWhatsApp: String = MENSAGEM_PADRAO
) {
    companion object {
        const val MENSAGEM_PADRAO = "Olá! Sou o entregador e estou a caminho com o seu pedido."
    }
}

class Preferencias(context: Context) {
    private val sp = context.getSharedPreferences("config", Context.MODE_PRIVATE)

    fun ler(): Config = Config(
        gps = runCatching { AppGps.valueOf(sp.getString("gps", null) ?: "") }.getOrDefault(AppGps.PERGUNTAR),
        tema = runCatching { Tema.valueOf(sp.getString("tema", null) ?: "") }.getOrDefault(Tema.ESCURO),
        mostrarMapa = sp.getBoolean("mostrarMapa", true),
        mensagemWhatsApp = sp.getString("mensagemWhatsApp", null) ?: Config.MENSAGEM_PADRAO
    )

    fun gravar(c: Config) {
        sp.edit()
            .putString("gps", c.gps.name)
            .putString("tema", c.tema.name)
            .putBoolean("mostrarMapa", c.mostrarMapa)
            .putString("mensagemWhatsApp", c.mensagemWhatsApp)
            .apply()
    }
}
