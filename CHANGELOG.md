# Changelog

## 0.2-lunastream — 17/10/2026

- Implementada Home cinematográfica em Views nativas, com sidebar focável, hero alimentado por catálogo real, carrosséis de filmes recentes/Filmes 4K/séries e placeholders sem arte.
- Adicionada navegação por D-pad para categorias, busca de filmes e lista de títulos limitada a 80 itens renderizados por vez.
- Xtream agora lê pôsteres, categoria, data `added`, rating e catálogo de séries; consulta `get_vod_info` apenas para o filme escolhido como destaque.
- O card 4K depende do nome de categoria “Filmes 4K” ou “Filmes UHD”; nenhum formato técnico é deduzido desse nome.
- Mantido o player Media3/ExoPlayer, `PlayerView`, seleção de áudio/legendas e diagnóstico existentes. O retorno do player volta para a tela de origem.
- Atualizado nome/banner do app para LunaStream e versão para `0.2-lunastream`.
- Build `:app:assembleDebug` concluído; validação física na TCL/Denon ainda pendente.

## Continuação do P0 — 17/10/2026

- Expandido o roteiro de validação física para incluir Dolby Digital, Dolby Digital Plus, TrueHD/Atmos lossless, DTS, DTS-HD MA, controles de reprodução, interrupção de rede e comparação entre players.
- Atualizada a matriz CSV com 13 cenários e indicação separada do que deve ser observado no app, na TCL e no Denon.

## 0.1-test — 16/10/2026

- Adicionados Gradle Wrapper 8.13 (`gradlew`, `gradlew.bat` e `gradle-wrapper.jar`).
- Adicionado `local.properties` local para o Android SDK; o arquivo permanece ignorado pelo Git.
- Instalados localmente Android SDK Platform API 36 e Build Tools requeridos.
- Compilação Debug executada localmente, com APK em `app/build/outputs/apk/debug/app-debug.apk`.
- Nenhum teste foi realizado na TCL ou no receiver Denon.

## Em desenvolvimento — credenciais locais opcionais

- Adicionada a opção **Lembrar neste dispositivo** na tela de login.
- Após autenticação válida, os dados são criptografados com Android Keystore antes de serem guardados localmente.
- Adicionada a ação **Esquecer dados salvos**; nenhuma credencial é incluída em código-fonte, APK de referência, logs ou documentação.
