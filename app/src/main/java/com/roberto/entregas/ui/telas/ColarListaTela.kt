package com.roberto.entregas.ui.telas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.unit.dp
import com.roberto.entregas.core.Texto
import com.roberto.entregas.ui.ItemColado
import com.roberto.entregas.ui.componentes.Botao
import com.roberto.entregas.ui.componentes.CabecalhoInterno
import com.roberto.entregas.ui.componentes.Campo
import com.roberto.entregas.ui.componentes.Hud
import com.roberto.entregas.ui.componentes.Ponto
import com.roberto.entregas.ui.componentes.TipoBotao
import com.roberto.entregas.ui.componentes.linhaBase
import com.roberto.entregas.ui.tema.Oficina
import kotlinx.coroutines.launch

@Composable
fun ColarListaTela(
    geocodificar: suspend (List<String>, (Int) -> Unit) -> List<ItemColado>,
    onAdicionar: (List<ItemColado>) -> Unit,
    onVoltar: () -> Unit
) {
    val c = Oficina.cores
    val escopo = rememberCoroutineScope()
    val area = LocalClipboardManager.current
    var texto by remember { mutableStateOf("") }
    var processando by remember { mutableStateOf(false) }
    var feitos by remember { mutableIntStateOf(0) }
    var resultado by remember { mutableStateOf<List<ItemColado>?>(null) }

    val linhas = remember(texto) { Texto.separarEnderecos(texto) }

    Column(Modifier.fillMaxSize().imePadding()) {
        CabecalhoInterno("Colar lista", "Vários *endereços* de uma vez.", onVoltar)

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val r = resultado
            if (r == null) {
                Text(
                    "Cole um endereço por linha. Numeração como \"1.\" ou \"- \" é removida sozinha.",
                    style = Oficina.tipos.body, color = c.muted
                )
                Botao("Colar da área de transferência", {
                    area.getText()?.text?.let { texto = if (texto.isBlank()) it else texto + "\n" + it }
                }, Modifier.fillMaxWidth(), tipo = TipoBotao.SECUNDARIO, habilitado = !processando)
                Campo(texto, { texto = it }, "Endereços", umaLinha = false, minLinhas = 8, dica = "Rua Augusta, 1500 - Consolação, São Paulo\nAv. Paulista, 1000 - Bela Vista, São Paulo")
                Hud("${linhas.size} endereços reconhecidos")
                if (processando) {
                    LinearProgressIndicator(
                        progress = { if (linhas.isEmpty()) 0f else feitos / linhas.size.toFloat() },
                        modifier = Modifier.fillMaxWidth().height(2.dp),
                        color = c.accent, trackColor = c.line
                    )
                    Hud("Buscando $feitos de ${linhas.size}")
                }
            } else {
                val ok = r.count { it.lugar != null }
                Text(
                    "$ok de ${r.size} encontrados." + if (ok < r.size) " Os outros entram sem local: depois é só editar e buscar de novo." else "",
                    style = Oficina.tipos.body, color = c.ink
                )
                Column {
                    r.forEach { item ->
                        Row(Modifier.fillMaxWidth().linhaBase(c.line).padding(vertical = 10.dp)) {
                            Ponto(if (item.lugar != null) c.verde else c.perigo, Modifier.padding(top = 6.dp))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(item.texto, style = Oficina.tipos.bodySm, color = c.ink)
                                Text(
                                    item.lugar?.rotulo ?: "Não encontrado",
                                    style = Oficina.tipos.bodySm, color = if (item.lugar != null) c.muted else c.perigo
                                )
                            }
                        }
                    }
                }
            }
        }

        val r = resultado
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (r == null) {
                Botao(
                    if (processando) "Buscando…" else "Buscar ${linhas.size} endereços",
                    {
                        processando = true
                        feitos = 0
                        escopo.launch {
                            resultado = geocodificar(linhas) { feitos = it }
                            processando = false
                        }
                    },
                    Modifier.fillMaxWidth(),
                    habilitado = linhas.isNotEmpty() && !processando
                )
            } else {
                Botao("Adicionar ${r.size} à rota", { onAdicionar(r) }, Modifier.fillMaxWidth())
                Botao("Voltar e editar a lista", { resultado = null }, Modifier.fillMaxWidth(), tipo = TipoBotao.FANTASMA)
            }
        }
    }
}
