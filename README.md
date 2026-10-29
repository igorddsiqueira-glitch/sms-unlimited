# SMS Fácil — projeto inicial Android

Projeto Kotlin/Jetpack Compose para abrir no Android Studio ou enviar ao GitHub e compilar no Codemagic.

## O que inclui
- Campo para colar números, um por linha.
- Deduplicação básica dos números.
- Identificação dos SIMs ativos e seleção manual por assinatura quando o Android disponibiliza esses dados.
- Mensagem de texto editável.
- Confirmação de consentimento antes do envio.
- Confirmação explícita antes de iniciar a fila.
- Sem teto diário fixo definido pelo app.
- Status local básico, pausa/parada e indicação de que solicitação não equivale a entrega.

## Limitações importantes
- A compilação/execução deve ser testada em aparelho Android físico com SIM ativo. Emuladores normalmente não enviam SMS pela rede móvel.
- O Android e a operadora podem restringir, tarifar, limitar ou bloquear o envio. Este app não contorna tais restrições.
- O status apresentado não confirma entrega; a implementação inicial não inclui callbacks de entrega.
- Para mensagens comerciais, use somente contatos que autorizaram receber SMS e respeite pedidos de cancelamento.
- Este projeto é um ponto de partida e precisa de testes antes de uso real ou revenda.
- A política da Google Play para permissões de SMS pode impedir publicação sem cumprir os requisitos aplicáveis. APK privado e publicação na loja são processos diferentes.

## Compilar localmente
1. Abra a pasta no Android Studio recente.
2. Deixe o Gradle sincronizar e baixar dependências.
3. Conecte um celular Android com depuração USB habilitada.
4. Execute o app no aparelho e conceda as permissões solicitadas.

## GitHub + Codemagic
1. Crie um repositório vazio no GitHub.
2. Envie todos os arquivos desta pasta para a raiz do repositório.
3. Entre no Codemagic, conecte o GitHub e selecione o repositório.
4. Escolha o workflow `android-debug` de `codemagic.yaml`.
5. Inicie o build. O artefato esperado é `app/build/outputs/apk/debug/app-debug.apk`.

Se o ambiente do Codemagic não disponibilizar o comando `gradle`, abra o projeto no Android Studio e gere/adicione o Gradle Wrapper compatível com a versão do Gradle usada pela imagem de build, atualizando o workflow para usar `./gradlew assembleDebug`.
