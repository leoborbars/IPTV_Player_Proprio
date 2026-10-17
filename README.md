# LunaStream — IPTV Player Android (TCL C7K / Denon AVR-S920W)

**Objetivo:** reproduzir filmes VOD autorizados via Xtream Codes e evoluir a interface para Google TV. A Home exibe filmes e séries retornados pelo catálogo; reprodução de episódios, histórico e favoritos serão implementados em marcos posteriores. Não há canais ao vivo, EPG, gravação nem retransmissão.

## Status deste pacote

O Marco 1 implementa Home cinematográfica em Views nativas, navegação lateral por controle remoto, fileiras com artes reais do provedor, busca/categorias de filmes e catálogo de séries. O player Media3/ExoPlayer existente permanece ativo com a mesma superfície e seleção de faixas. O APK Debug é gerado em `app/build/outputs/apk/debug/app-debug.apk`. A interface ainda precisa ser validada fisicamente na TCL.

## Requisitos

- PC com Android Studio atualizado; JDK 17 e Android SDK API 36 instalados.
- Plugin Android Gradle 8.13.2 + Gradle 8.13 + Kotlin 2.2.0.
- TV em rede local, opções do desenvolvedor e depuração disponíveis para instalar o APK.
- Conta Xtream Codes com acesso permitido a filmes/séries VOD. **Não envie usuário nem senha em prints ou mensagens.**

## Preparação e compilação no Windows

1. Instale JDK 17 e Android SDK Platform API 36. Android Studio pode ser usado apenas para instalar e atualizar o SDK; o desenvolvimento pode ser feito no VS Code.
2. Abra esta pasta no VS Code e confira `local.properties`. Ele deve apontar para o SDK local e não deve ser versionado.
3. No terminal integrado do VS Code, execute:

   ```powershell
   .\gradlew.bat --no-daemon assembleDebug
   ```

4. O APK resultante fica em `app/build/outputs/apk/debug/app-debug.apk`.
5. Com a TV conectada por ADB, instale-o com:

   ```powershell
   adb install -r .\app\build\outputs\apk\debug\app-debug.apk
   ```

Consulte `TESTE_NA_TCL.md` para a preparação da TV, ARC e validação real. Este é código experimental: eventuais ajustes de compatibilidade ainda podem ser necessários.
Decisões visuais estão em `docs/UI_DESIGN.md`; o mapeamento dos endpoints está em `docs/XTREAM_INTEGRATION.md`.

## Operação

1. Na TCL, coloque a saída de áudio para receiver/ARC e experimente **Auto / Pass Through** no menu de saída digital, de acordo com as opções reais do firmware. No Denon, use a saída **HDMI MONITOR 1 (ARC)** e habilite **HDMI Control / ARC**.
2. Abra `LunaStream` na Google TV.
3. Digite servidor (incluindo `https://` ou `http://` e porta, se aplicável), usuário e senha Xtream Codes.
4. Na Home, o destaque usa o filme com data `added` mais recente válida; sem datas confiáveis, usa o primeiro item retornado. Sinopse, backdrop e metadados só aparecem quando `get_vod_info` os fornece.
5. Use o menu lateral **Filmes** para abrir categorias e pesquisar. **Filmes 4K** aparece apenas quando o provedor retorna uma categoria chamada “Filmes 4K” ou “Filmes UHD”; esse nome não comprova resolução. A fileira **Séries em destaque** mostra dados de `get_series`; temporadas e episódios ainda não são reproduzidos.
6. **Continuar assistindo** fica oculto sem progresso salvo. Favoritos e configurações estão no menu; favoritos ainda não persistem.
7. Ao abrir o filme, `Assistir` inicia o Media3 atual. Durante a reprodução, `Diagnóstico` mostra codec, dimensões, FPS, sinalização HDR, codec de áudio e canais; `Áudio` seleciona faixa; `Legendas` seleciona faixa, quando reconhecida.
8. No Denon, verifique o **formato de áudio efetivamente recebido** pelo painel frontal ou tela de informação do receiver, e registre esse resultado separadamente.

### Limites do diagnóstico

- Detectar vídeo HEVC 3840×2160 ou faixa E-AC3 6 canais **não comprova** que a saída HDMI realmente está reproduzindo esse formato.
- HDR marcado como **PQ/ST2084** pode ser HDR10 ou HDR10+; o P0 não valida metadados dinâmicos.
- Dolby Vision sinalizado no stream **não garante** que a TV tenha ativado o modo DV. Confirmar usando o indicador de modo da TCL.
- O player não decodifica Dolby TrueHD/DTS-HD em uma saída ARC lossless: ARC é o limitador físico.
- Sem transcoding; codecs não compatíveis podem não reproduzir som/imagem. O P0 não implementa fallback de FFmpeg.
- Alguns arquivos não permitem buscar/avançar por limitações do servidor HTTP.

## Segurança

A API Xtream Codes costuma enviar credenciais como parâmetros de URL e também pode embuti-las na URL de vídeo. O app não inclui credenciais no código, nos relatórios ou nos logs. Se o usuário marcar **Lembrar neste dispositivo** depois de uma autenticação válida, elas são armazenadas localmente em forma criptografada com uma chave do Android Keystore e podem ser removidas por **Esquecer dados salvos**. Bibliotecas, intermediários e o servidor ainda podem registrar URLs. **Prefira HTTPS**. A configuração `android:usesCleartextTraffic="true"` existe apenas para permitir testar servidores Xtream antigos que usam HTTP. **Não distribua esta versão** nem envie logs completos sem revisar URLs e credenciais.

## Próximos incrementos

- Validar navegação e foco na TCL e executar a matriz de áudio/vídeo em `testes_4k_audio.csv`, seguindo `TESTE_NA_TCL.md`.
- Marco 2: tela de detalhes completa com metadados Xtream sob demanda.
- Marco 3: temporadas e episódios da API Xtream.
- Marco 4: progresso local e Favoritos.
- Marco 5: overlay do player e diagnóstico ampliado sem alterar o pipeline Media3.
