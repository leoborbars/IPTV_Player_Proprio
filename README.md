# LunaStream — IPTV Player Android

Aplicativo Android para Google TV/Android TV, desenvolvido para a TCL C7K. O projeto reproduz conteúdo VOD autorizado por Xtream Codes e mantém o player Media3 existente. Não oferece canais ao vivo, EPG, gravação ou retransmissão.

**Repositório:** [leoborbars/IPTV_Player_Proprio](https://github.com/leoborbars/IPTV_Player_Proprio)

## Estado do projeto

O Marco 1 entrega a Home cinematográfica em Views nativas, navegação por controle remoto, catálogo real de filmes e séries, categorias e busca de filmes. A arte e os metadados vêm do provedor Xtream quando disponíveis. O player Media3/ExoPlayer e sua seleção de faixas foram mantidos.

A versão atual é `0.2-lunastream` (APK Debug). A compilação foi verificada; navegação na TV e reprodução 4K HDR/áudio multicanal ainda precisam de validação física na TCL e no Denon. Consulte [TESTE_NA_TCL.md](TESTE_NA_TCL.md) e [testes_4k_audio.csv](testes_4k_audio.csv).

## Requisitos

- JDK 17 e Android SDK Platform 36.
- Android Studio para instalar/atualizar o SDK; o desenvolvimento também pode ser feito pelo VS Code.
- Uma conta Xtream Codes com acesso autorizado a VOD.

O Gradle Wrapper baixa a versão do Gradle configurada pelo projeto. Não versione `local.properties`: ele deve apontar para o SDK instalado neste computador e já está excluído pelo `.gitignore`.

## Compilar

Na raiz do projeto, no PowerShell:

```powershell
.\gradlew.bat --no-daemon :app:assembleDebug
```

O APK fica em:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Instalar na TV

Conecte a TV e o computador à mesma rede Wi-Fi e habilite **Opções do desenvolvedor → Depuração sem fio** na TV. Para parear usando código, deixe a tela **Parear dispositivo com código** aberta e use o IP e a porta de pareamento mostrados nela:

```powershell
adb pair 192.168.3.72:43275
```

Quando `adb pair` pedir, digite o código temporário exibido pela TV. No teste da TCL, a porta de pareamento foi `43275`. Depois volte à tela principal de **Depuração sem fio** e use o endereço IP e a porta que aparecem em **Endereço IP e porta**. No teste, a conexão foi feita com `192.168.3.72:37487`; essa porta é diferente da porta de pareamento. O comando `adb connect` não pede o código:

```powershell
adb connect 192.168.3.72:37487
adb devices
```

Confirme que a TV aparece como `device` e instale o APK:

```powershell
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
```

O pareamento e a conexão sem fio exigem que os dois dispositivos estejam na mesma rede; redes de convidados podem bloquear a comunicação. Também é possível instalar por USB com a depuração USB habilitada.

## Usar o aplicativo

1. Abra **LunaStream** e informe o servidor Xtream (incluindo `https://` ou `http://` e a porta, se necessária), usuário e senha.
2. A Home apresenta títulos do catálogo autorizado. O destaque escolhe o filme com a data `added` mais recente válida; sem datas confiáveis, usa o primeiro item retornado.
3. Use o menu lateral para navegar com o D-pad, abrir **Filmes**, selecionar categorias ou pesquisar títulos. **Filmes 4K** depende de uma categoria do provedor chamada “Filmes 4K” ou “Filmes UHD”; o nome da categoria não comprova a resolução do conteúdo.
4. Séries aparecem no catálogo. Temporadas e episódios ainda não são reproduzidos nesta etapa. Histórico e favoritos também serão implementados em marcos posteriores.
5. Abra um filme para iniciar o player Media3. As informações de codec, resolução, HDR e áudio reportadas pelo app descrevem o stream/decoder; confirme separadamente o modo de imagem na TCL e o formato recebido pelo Denon.

O app só mostra sinopse, backdrop e outros metadados quando o provedor os fornece. Sem progresso salvo, a fileira “Continuar assistindo” permanece oculta.

## Segurança e limites

- Use apenas serviços e conteúdo aos quais você tem autorização de acesso.
- Prefira HTTPS. A API Xtream pode incluir credenciais em URLs; servidores, bibliotecas e intermediários podem registrá-las. Não compartilhe capturas ou logs sem remover usuários, senhas e URLs.
- Se ativar **Lembrar neste dispositivo**, as credenciais são armazenadas localmente de forma criptografada com Android Keystore e podem ser removidas em **Esquecer dados salvos**.
- `android:usesCleartextTraffic="true"` permite testar servidores antigos em HTTP; HTTP não protege credenciais em trânsito.
- Detecção de HEVC 4K, E-AC-3 5.1 ou sinalização HDR não comprova o formato efetivamente exibido pela TV ou recebido pelo receiver. ARC convencional também limita o transporte de áudio lossless.
- Esta é uma versão de desenvolvimento. Teste compatibilidade e reprodução no equipamento antes de qualquer distribuição.

## Documentação

- [Roteiro de teste na TCL e no Denon](TESTE_NA_TCL.md)
- [Matriz de cenários 4K e áudio](testes_4k_audio.csv)
- [Decisões visuais da Home](docs/UI_DESIGN.md)
- [Integração Xtream Codes](docs/XTREAM_INTEGRATION.md)
- [Documentação da API Xtream](API_XTREAM_CODES.md)

## Próximos marcos

1. Validar Home, navegação e player na TCL/Denon.
2. Implementar a tela de detalhes do conteúdo.
3. Implementar temporadas e episódios de séries.
4. Adicionar progresso de reprodução e favoritos persistentes.
5. Evoluir os controles e diagnósticos do player sem substituir o pipeline Media3.