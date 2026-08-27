# AI Brain

Aplicativo Android para descoberta e organização de ferramentas de inteligência artificial, com catálogo pesquisável, favoritos, biblioteca de prompts, assistente de curadoria e navegador interno.

## Requisitos

- JDK 17 ou superior;
- Android SDK com API 34;
- acesso à internet apenas para baixar dependências e, durante o uso do aplicativo, para sincronizar o catálogo e acessar os serviços selecionados.

## Build e testes

O projeto inclui Gradle Wrapper. Em um clone limpo, execute:

```bash
./gradlew test
./gradlew lint
./gradlew assembleDebug
./gradlew assembleRelease
```

O APK de debug é gerado em `app/build/outputs/apk/debug/`. A versão release possui minificação habilitada; valide-a em um dispositivo ou emulador antes de distribuir.

## Catálogos

O catálogo base fica em `app/src/main/assets/ia_catalogo.json`. Os arquivos de publicação em `para-subir-no-github/` devem ser mantidos sincronizados com os assets. Antes de publicar uma nova versão, incremente `versao`, valide o JSON e confira IDs, URLs HTTPS, categorias, idiomas e notas.

## API key da Groq

A chave é opcional e usada somente no Assistente de IA. Nunca inclua chaves no código-fonte, nos assets, em logs ou em issues. Em produção, prefira um backend com autenticação e controle de uso. No aplicativo, a chave deve ser tratada como segredo do usuário e armazenada no Android Keystore.

## Segurança do navegador

O navegador interno foi restringido a HTTPS, com bloqueio de conteúdo misto, arquivos locais e domínios fora da allowlist configurada. Capacidades como geolocalização, cookies de terceiros, downloads e upload devem ser habilitadas apenas quando necessárias para o domínio atual.

## Estrutura

- `app/src/main/java/com/aibrain/app/model`: modelos de domínio;
- `app/src/main/java/com/aibrain/app/repository`: leitura, sincronização e composição do catálogo;
- `app/src/main/java/com/aibrain/app/viewmodel`: estado de tela e filtros;
- `app/src/main/java/com/aibrain/app/browser`: abas e WebView;
- `app/src/test`: testes unitários;
- `docs`: changelog e roadmap.

## Contribuição

Antes de abrir um pull request, execute testes, lint e build. Mudanças no catálogo devem incluir validação do JSON. Mudanças de segurança ou no WebView devem incluir testes de regressão e uma descrição do impacto.
