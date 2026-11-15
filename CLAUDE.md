# Na Rota — otimizador de rotas de entrega (app Android; nome provisório)

O entregador cadastra as entregas com endereço, o app otimiza a ordem pela localização, ele marca "entregue" e segue para a próxima abrindo o GPS de sua escolha (Waze, Maps…).
Foco: entregador autônomo.
Repositório: github.com/robertobegood-jpg/Na-Rota-app-Android

## Decisões
- Otimização **híbrida calculada no próprio aparelho**; trânsito é só opcional.
- A otimização **sempre parte da origem do entregador** (mostrar por onde começar).

## Como trabalhar comigo
- Responda e escreva tudo em **português do Brasil**.
- **Antes de começar, rode `git pull`.**
- **Teste antes de entregar.** Compile (`./gradlew assembleDebug`, no Windows `gradlew.bat assembleDebug`) e corrija todos os erros antes de dizer que terminou. Não deixe o Roberto achar os erros.
- Explique o que mudou em linguagem simples.
- Peça confirmação antes de: apagar arquivos, mudar o `applicationId`, mudar o banco de dados, mexer na assinatura ou dar `git push`.

## Padrão dos apps do Roberto
- Kotlin nativo + Jetpack Compose (Material 3), JDK 17, minSdk 26.
- Visual no **design system Oficina** (o mesmo de Em Dia, Corrida Particular etc.). Use os tokens do tema, não cores soltas.
- Botão **"cafezinho"** (doação via Pix ao desenvolvedor).
- GitHub Actions gera o APK assinado a cada push na `main`; tags `v*` publicam Release (notas em `notas/<tag>.md`, se existir).
- Secrets KEYSTORE_BASE64 e KEYSTORE_PASSWORD no GitHub (mesma keystore de todos os apps).
- **NUNCA** commitar `*.jks`, `*.keystore`, `keystore.properties`, `app/assinatura/`, `local.properties`.
- Mudança no banco: migração que preserva os dados de quem já usa.
- Ao lançar versão: aumente `versionCode` e `versionName`.
- O app aparece no site **Kit do Motorista** (repo `apps-site`): ao lançar versão nova, lembre de atualizar o site.
