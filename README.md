# Na Rota

App Android de rotas para entregador autônomo. Você cadastra as entregas, o app calcula a melhor ordem de visita no próprio celular e, na rua, mostra uma parada por vez: navegar, ligar, WhatsApp, entregue ou não entregue.

Gratuito, sem anúncios, sem conta e sem chave de API. Visual no sistema de design **Oficina**.

## O que já faz (versão 0.2)

**Cadastro**
- Endereço com busca automática enquanto você digita (Geocoder do Android; se não achar, usa o Photon/OpenStreetMap)
- "Usar minha localização aqui" para endereços difíceis
- Destinatário, telefone, nº do pedido, observação e marcação de urgente
- **Colar lista**: vários endereços de uma vez, um por linha (numeração "1." ou "- " é removida). Os não encontrados entram sem local para corrigir depois.

**Otimização**
- Calculada no celular, sem internet: vizinho mais próximo + 2-opt + Or-opt
- Nos testes fica, em média, 0,02% acima da rota perfeita (pior caso 2,3%); 200 paradas em ~55 ms no computador
- Sempre parte de onde você está (ou de uma partida fixa); sem localização, o app pede antes de otimizar
- Mostra no mapa de onde a rota sai e qual a primeira parada, com a distância
- Urgentes primeiro e opção de voltar ao ponto de partida (começando pelo lado mais perto)
- Estimativa de km e tempo (linha reta × 1,35 de fator de ruas, 25 km/h, 3 min por parada)
- Mapa OpenStreetMap monocromático (só ruas e nomes), com as paradas numeradas e a ordem de visita; a tela da rota rola inteira e o mapa abre em tela cheia
- Ajuste manual da ordem com ▲ ▼

**Modo entrega**
- Card grande da próxima parada, barra de progresso e as três seguintes
- Navegar no GPS escolhido (perguntar, Google Maps ou Waze)
- Ligar e WhatsApp com mensagem pronta (editável em Mais)
- Entregue / Não entregue com motivo; "tentar no fim da rota" ou "encerrar sem entregar"
- Desfazer a última marcação

**Histórico e extras**
- Rota finalizada vai para o histórico com entregues, não entregues, duração e horários; pendentes passam para a próxima rota
- Backup e restauração em arquivo JSON
- Tema escuro, claro ou do sistema
- Cafezinho via Pix com QR Code

## Fontes do Oficina

Copie as quatro fontes para `app/src/main/assets/fonts/` (passo a passo em `LEIA-ME.txt` na mesma pasta). Sem elas o app funciona com as fontes do sistema.

## Como compilar

**GitHub Actions:** cada push na `main` roda os testes e gera o APK de teste em *Actions → Build → Artifacts*.
Para o APK assinado, cadastre em *Settings → Secrets and variables → Actions*
`KEYSTORE_BASE64` e `KEYSTORE_PASSWORD` (a mesma chave dos outros apps). O alias é lido do arquivo;
`KEY_ALIAS` e `KEY_PASSWORD` só são necessários se a sua chave tiver alias ou senha diferentes.
Uma tag `v0.1.0` publica o APK assinado em *Releases*.

**Android Studio:** *Open* nesta pasta, espere o Gradle sincronizar e clique em *Run*.

## Estrutura

```
core/       lógica pura, testada no computador (modelo, otimizador, operações, JSON, Pix)
dados/      arquivo de dados e preferências
servicos/   busca de endereço, localização, GPS/telefone/WhatsApp
ui/         tema Oficina, componentes, mapa, telas e ViewModel
```

Testes: `./gradlew testDebugUnitTest` (21 testes do núcleo).

## Próximas etapas sugeridas
- Comprovante de entrega (foto, nome de quem recebeu, assinatura)
- Janela de horário na otimização
- Aviso automático ao cliente ("você é o próximo")
- Importar planilha CSV e ler código de barras da etiqueta
- Valor a receber na entrega e fechamento do dia
- Relatório em PDF

## Licença

GPL-3.0. Mapas © colaboradores do OpenStreetMap.
