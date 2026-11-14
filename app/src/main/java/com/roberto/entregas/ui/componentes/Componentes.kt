package com.roberto.entregas.ui.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.roberto.entregas.ui.tema.Oficina

/** Rótulo HUD: mono, maiúsculas, espaçado. */
@Composable
fun Hud(texto: String, modifier: Modifier = Modifier, cor: Color = Oficina.cores.muted, linhas: Int = 1) {
    Text(
        texto.uppercase(), modifier = modifier, style = Oficina.tipos.hud, color = cor,
        maxLines = linhas, overflow = TextOverflow.Ellipsis
    )
}

/**
 * Título display. A palavra entre asteriscos sai em serif itálico: "Sua *rota*."
 */
@Composable
fun Titulo(texto: String, modifier: Modifier = Modifier, estilo: TextStyle = Oficina.tipos.display, cor: Color = Oficina.cores.inkForte) {
    Text(comItalico(texto, Oficina.tipos.serifItalico.toSpanStyle()), modifier = modifier, style = estilo, color = cor)
}

fun comItalico(texto: String, serif: SpanStyle): AnnotatedString = buildAnnotatedString {
    texto.split('*').forEachIndexed { i, parte ->
        if (i % 2 == 1) withStyle(serif.copy(fontStyle = FontStyle.Italic)) { append(parte) } else append(parte)
    }
}

enum class TipoBotao { PRIMARIO, SECUNDARIO, FANTASMA, PERIGO, DESTAQUE }

/** Botão Oficina: 48dp, cantos retos, texto mono em maiúsculas começando por verbo. */
@Composable
fun Botao(
    texto: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tipo: TipoBotao = TipoBotao.PRIMARIO,
    habilitado: Boolean = true,
    altura: Dp = 48.dp
) {
    val c = Oficina.cores
    val (fundo, frente, borda) = when (tipo) {
        TipoBotao.PRIMARIO -> Triple(c.invertBg, c.invertFg, Color.Transparent)
        TipoBotao.DESTAQUE -> Triple(c.accent, Color.White, Color.Transparent)
        TipoBotao.SECUNDARIO -> Triple(Color.Transparent, c.ink, c.lineForte)
        TipoBotao.FANTASMA -> Triple(Color.Transparent, c.ink, Color.Transparent)
        TipoBotao.PERIGO -> Triple(Color.Transparent, c.perigo, c.perigo)
    }
    Box(
        modifier
            .heightIn(min = altura)
            .background(if (habilitado) fundo else fundo.copy(alpha = fundo.alpha * 0.4f))
            .then(if (borda != Color.Transparent) Modifier.border(1.dp, borda) else Modifier)
            .clickable(enabled = habilitado, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            texto.uppercase(), style = Oficina.tipos.botao,
            color = if (habilitado) frente else frente.copy(alpha = 0.5f),
            textAlign = TextAlign.Center, maxLines = 2
        )
    }
}

/** Célula com hairline de 1px. */
@Composable
fun Celula(modifier: Modifier = Modifier, preenchimento: Dp = 16.dp, conteudo: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.border(1.dp, Oficina.cores.line).padding(preenchimento),
        content = conteudo
    )
}

/** Linha fina horizontal no topo do elemento. */
fun Modifier.linhaTopo(cor: Color): Modifier = drawBehind {
    drawLine(cor, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx())
}

fun Modifier.linhaBase(cor: Color): Modifier = drawBehind {
    drawLine(cor, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
}

@Composable
fun Campo(
    valor: String,
    onValor: (String) -> Unit,
    rotulo: String,
    modifier: Modifier = Modifier,
    teclado: KeyboardType = KeyboardType.Text,
    umaLinha: Boolean = true,
    minLinhas: Int = 1,
    dica: String? = null
) {
    val c = Oficina.cores
    OutlinedTextField(
        value = valor,
        onValueChange = onValor,
        modifier = modifier.fillMaxWidth(),
        label = { Text(rotulo.uppercase(), style = Oficina.tipos.hud) },
        placeholder = dica?.let { { Text(it, style = Oficina.tipos.body, color = c.faint) } },
        singleLine = umaLinha,
        minLines = minLinhas,
        textStyle = Oficina.tipos.body,
        shape = RectangleShape,
        keyboardOptions = KeyboardOptions(keyboardType = teclado),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = c.ink,
            unfocusedTextColor = c.ink,
            focusedContainerColor = c.fundo,
            unfocusedContainerColor = c.fundo,
            cursorColor = c.accent,
            focusedBorderColor = c.ink,
            unfocusedBorderColor = c.lineForte,
            focusedLabelColor = c.ink,
            unfocusedLabelColor = c.muted
        )
    )
}

@Composable
fun Interruptor(rotulo: String, marcado: Boolean, onMudar: (Boolean) -> Unit, modifier: Modifier = Modifier, detalhe: String? = null) {
    val c = Oficina.cores
    Row(
        modifier.fillMaxWidth().clickable { onMudar(!marcado) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(rotulo, style = Oficina.tipos.body, color = c.ink)
            if (detalhe != null) Text(detalhe, style = Oficina.tipos.bodySm, color = c.muted)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = marcado,
            onCheckedChange = onMudar,
            colors = SwitchDefaults.colors(
                checkedThumbColor = c.invertFg,
                checkedTrackColor = c.invertBg,
                checkedBorderColor = c.invertBg,
                uncheckedThumbColor = c.muted,
                uncheckedTrackColor = c.fundo,
                uncheckedBorderColor = c.lineForte
            )
        )
    }
}

/** Seletor em blocos lado a lado; o escolhido fica invertido. */
@Composable
fun <T> Segmentado(
    opcoes: List<T>,
    selecionada: T,
    rotulo: (T) -> String,
    onSelecionar: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = Oficina.cores
    Row(modifier.fillMaxWidth().border(1.dp, c.lineForte)) {
        opcoes.forEachIndexed { i, op ->
            val ativo = op == selecionada
            Box(
                Modifier
                    .weight(1f)
                    .height(44.dp)
                    .background(if (ativo) c.invertBg else Color.Transparent)
                    .clickable { onSelecionar(op) }
                    .then(if (i > 0) Modifier.drawBehind {
                        drawLine(c.lineForte, Offset(0f, 0f), Offset(0f, size.height), 1.dp.toPx())
                    } else Modifier),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    rotulo(op).uppercase(), style = Oficina.tipos.botao,
                    color = if (ativo) c.invertFg else c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}

@Composable
fun Ponto(cor: Color, modifier: Modifier = Modifier, tamanho: Dp = 8.dp) {
    Box(modifier.size(tamanho).background(cor, CircleShape))
}

/** Cabeçalho das telas internas: voltar + eyebrow + título. */
@Composable
fun CabecalhoInterno(eyebrow: String, titulo: String, onVoltar: () -> Unit, acao: (@Composable RowScope.() -> Unit)? = null) {
    val c = Oficina.cores
    Column(Modifier.fillMaxWidth().linhaBase(c.line).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.clickable(onClick = onVoltar).padding(vertical = 12.dp, horizontal = 0.dp)) {
                Text("← VOLTAR", style = Oficina.tipos.botao, color = c.ink)
            }
            Spacer(Modifier.weight(1f))
            if (acao != null) acao()
        }
        Hud(eyebrow)
        Spacer(Modifier.height(6.dp))
        Titulo(titulo, estilo = Oficina.tipos.heading)
    }
}

/** Cabeçalho das abas principais. */
@Composable
fun CabecalhoAba(eyebrow: String, titulo: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp)) {
        Hud(eyebrow)
        Spacer(Modifier.height(8.dp))
        Titulo(titulo)
    }
}

/** Número grande com rótulo HUD embaixo, para a faixa de indicadores. */
@Composable
fun Indicador(valor: String, rotulo: String, modifier: Modifier = Modifier, cor: Color = Oficina.cores.ink) {
    Column(modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
        Text(valor, style = Oficina.tipos.numero, color = cor, maxLines = 1)
        Hud(rotulo)
    }
}

/** Faixa de indicadores com divisórias de 1px entre eles. */
@Composable
fun FaixaIndicadores(itens: List<Triple<String, String, Color?>>, modifier: Modifier = Modifier) {
    val c = Oficina.cores
    Row(modifier.fillMaxWidth().border(1.dp, c.line)) {
        itens.forEachIndexed { i, (valor, rotulo, cor) ->
            Indicador(
                valor, rotulo,
                Modifier.weight(1f).then(if (i > 0) Modifier.drawBehind {
                    drawLine(c.line, Offset(0f, 0f), Offset(0f, size.height), 1.dp.toPx())
                } else Modifier),
                cor ?: c.ink
            )
        }
    }
}

@Composable
fun Dialogo(
    titulo: String,
    onFechar: () -> Unit,
    confirmar: @Composable () -> Unit,
    cancelar: (@Composable () -> Unit)? = null,
    conteudo: @Composable ColumnScope.() -> Unit
) {
    val c = Oficina.cores
    AlertDialog(
        onDismissRequest = onFechar,
        shape = RectangleShape,
        containerColor = c.elevado,
        titleContentColor = c.inkForte,
        textContentColor = c.ink,
        title = { Titulo(titulo, estilo = Oficina.tipos.heading) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp), content = conteudo) },
        confirmButton = confirmar,
        dismissButton = cancelar
    )
}

/** Barra de abas inferior: rótulos HUD numerados, a aba ativa em bloco invertido. */
@Composable
fun BarraInferior(abas: List<String>, selecionada: Int, onSelecionar: (Int) -> Unit) {
    val c = Oficina.cores
    Row(
        Modifier
            .fillMaxWidth()
            .background(c.bg)
            .linhaTopo(c.line)
            .navigationBarsPadding()
            .height(56.dp)
    ) {
        abas.forEachIndexed { i, nome ->
            val ativo = i == selecionada
            Column(
                Modifier
                    .weight(1f)
                    .height(56.dp)
                    .background(if (ativo) c.invertBg else Color.Transparent)
                    .clickable { onSelecionar(i) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("0${i + 1}", style = Oficina.tipos.hud, color = if (ativo) c.invertFg.copy(alpha = 0.6f) else c.faint)
                Text(nome.uppercase(), style = Oficina.tipos.botao, color = if (ativo) c.invertFg else c.muted, maxLines = 1)
            }
        }
    }
}

/** Estado vazio com título e uma frase. */
@Composable
fun Vazio(titulo: String, texto: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(24.dp)) {
        Titulo(titulo, estilo = Oficina.tipos.heading)
        Spacer(Modifier.height(8.dp))
        Text(texto, style = Oficina.tipos.body, color = Oficina.cores.muted)
    }
}
