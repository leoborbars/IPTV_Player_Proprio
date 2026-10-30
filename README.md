# Le&Sil Stream — IPTV Player Android

Aplicativo VOD para Android TV/Google TV e Android, com catálogo Xtream Codes, navegação por controle remoto e reprodução pelo Media3/ExoPlayer. A interface usa Views nativas e mantém o cliente Xtream e o pipeline de reprodução Media3 existentes.

**Repositório:** [leoborbars/IPTV_Player_Proprio](https://github.com/leoborbars/IPTV_Player_Proprio)

## Estado atual

- Home com destaque compacto e fileiras de capas, além do catálogo de filmes com categorias, busca, arte de fundo e navegação por D-pad. Na TV, o painel de categorias desliza sem deslocar o cabeçalho do filme, e a grade mantém o tamanho das capas durante a navegação.
- Filmes e séries aparecem do mais recente para o mais antigo em todas as categorias, conforme a data/hora informada pelo provedor. Itens sem data ficam ao final. O menu Lançamentos foi removido; as opções de ordenação manual continuam disponíveis.
- Séries usam a mesma grade e navegação dos filmes, com sinopse e número de temporadas da série selecionada. Ao abrir uma série, o app mostra apenas temporadas com episódios, que reproduzem no Media3.
- Ao iniciar ou selecionar **Atualizar** no menu principal, uma tela de atualização ocupa toda a tela até o catálogo carregar. **Continuar assistindo** permanece na Home.
- Player Media3 existente com controles e diagnóstico de reprodução.
- Progresso local de filmes e episódios. Episódios concluídos aparecem em verde; os em andamento mostram a porcentagem e retomam do ponto salvo.
- **Favoritos** ficam nas categorias de Filmes e Séries. Segure uma capa por 3 segundos no controle ou na tela para adicionar/remover; a estrela aparece no título da capa.
- Sincronização opcional do progresso e dos favoritos entre TV e celular por um serviço próprio no NAS/OpenMediaVault.
- Em **Configurações**, o app mostra o estado da conexão com o NAS.
- O item **Filmes 4K** foi removido do menu lateral. Conteúdos 4K ainda podem aparecer conforme as categorias e os dados fornecidos pelo catálogo.

A versão Android é `0.3-lesil` (APK Debug). O ícone Le&Sil integrado ao app está em `app/src/main/res/drawable-nodpi/lesil_icon.png`. Confirme a navegação e a reprodução 4K HDR/áudio multicanal nos equipamentos; a sinalização informada pelo app não substitui a validação da imagem na TV nem do formato recebido pelo receiver. Consulte [TESTE_NA_TCL.md](TESTE_NA_TCL.md) e [testes_4k_audio.csv](testes_4k_audio.csv).

O serviço no NAS respondeu `{"status":"ok"}` em `/health`. A sincronização entre os dois aparelhos também foi exercitada após a migração do perfil antigo: o celular recuperou registros que já estavam salvos pela TV.

Para sincronizar **favoritos e episódios**, atualize o `nas-progress/server.py` no NAS e reconstrua o container conforme [as instruções do serviço](nas-progress/README.md). A versão antiga da API não aceita esses novos registros; até a atualização, eles ficam salvos no aparelho.

Em 27/10/2026, o APK Debug foi instalado na TCL e a navegação dos filmes foi testada por ADB: 20 linhas percorridas, movimento lateral entre capas, retorno da primeira coluna para **Categorias** e entrada/saída da categoria **DC**. O app permaneceu aberto e o log não mostrou exceção fatal nesse percurso. Esse teste não incluiu reprodução de vídeo. Os vídeos `Fotos_iptvplayer/video1.mp4` e `video4.mp4` serviram de referência para o movimento dos painéis.

## Requisitos

- JDK 17 e Android SDK Platform 36.
- Android Studio para instalar/atualizar o SDK; o desenvolvimento também pode ser feito pelo VS Code.
- ADB para instalar por USB ou depuração sem fio.
- Conta Xtream Codes com acesso autorizado a VOD.
- Para sincronizar progresso: Raspberry Pi/NAS com OpenMediaVault, Docker Compose e dispositivos na mesma rede local.

O Gradle Wrapper baixa a versão do Gradle configurada pelo projeto. `local.properties` contém o caminho local do Android SDK e pode conter os padrões de conexão ao NAS; o arquivo é ignorado pelo Git e não deve ser publicado.

## Compilar o APK

Na raiz do projeto, no PowerShell:

```powershell
.\gradlew.bat --no-daemon :app:assembleDebug
```

O APK de depuração fica em `app/build/outputs/apk/debug/app-debug.apk`.

### Definir o NAS como padrão de compilação

O app permite configurar o endereço e o token em **Configurações → Sincronização NAS**. Para já deixar os valores preenchidos nas novas instalações feitas com um APK local, acrescente estas propriedades ao `local.properties` da raiz, preservando qualquer linha `sdk.dir` que já exista:

```properties
lesil.progressSyncEndpoint=http://192.168.3.47:8787
lesil.progressSyncToken=COLE_AQUI_O_MESMO_TOKEN_DO_NAS
```

Edite localmente com `notepad .\local.properties`, sem compartilhar o conteúdo. Faça uma nova compilação depois de alterar as propriedades. Na primeira inicialização, o app salva os padrões no armazenamento criptografado pelo Android Keystore; depois, os campos podem ser alterados nas configurações do app. O token fica incorporado ao APK construído com esse arquivo, então proteja o APK e não envie `local.properties` nem o token ao GitHub.

## Configurar a sincronização no NAS/OpenMediaVault

O serviço Le&Sil Progress Sync roda na Raspberry Pi, usa SQLite em um volume persistente do Docker e atende pela porta TCP `8787`. No ambiente instalado, o NAS usa `192.168.3.47`. Reserve esse endereço no roteador ou ajuste o endereço padrão acima se ele mudar. Mantenha o serviço acessível apenas pela rede local: não configure encaminhamento da porta `8787` para a internet.

### 1. Copiar os arquivos do serviço

Copie a pasta `nas-progress` do projeto para `/home/pi/nas-progress` no NAS. No Windows, pode usar WinSCP. Se o cliente OpenSSH estiver instalado, também é possível executar:

```powershell
scp -r "D:\Projetos\IPTV_Player_P0\nas-progress" pi@192.168.3.47:/home/pi/
```

Se o PowerShell disser que `scp` não é reconhecido, instale o recurso **OpenSSH Client** do Windows ou transfira a pasta pelo WinSCP. Se o usuário SSH não for `pi`, substitua o usuário e o caminho.

### 2. Criar o token secreto no NAS

Conecte por SSH e gere um segredo aleatório de 64 caracteres hexadecimais:

```sh
ssh pi@192.168.3.47
cd ~/nas-progress
cp .env.example .env
openssl rand -hex 32
```

Copie o resultado e coloque-o no arquivo `.env`, substituindo o valor de exemplo:

```sh
nano .env
chmod 600 .env
```

O formato deve ser `PROGRESS_SYNC_TOKEN=` seguido do segredo, sem aspas. Não publique nem envie esse valor em mensagens ou capturas. Use exatamente o mesmo token no `local.properties` de cada build e em qualquer aparelho configurado manualmente.

### 3. Iniciar o serviço

Ainda dentro de `~/nas-progress`, execute:

```sh
docker compose up -d --build
docker compose ps
docker compose logs --tail=30 progress-sync
curl http://127.0.0.1:8787/health
```

Se o Docker responder `permission denied` para `/var/run/docker.sock`, execute os comandos Docker com `sudo`, por exemplo `sudo docker compose up -d --build`. A resposta do teste de saúde deve ser `{"status":"ok"}`. De outro aparelho na mesma rede, teste também `http://192.168.3.47:8787/health` no navegador.

O `compose.yaml` publica a porta `8787` e guarda o banco em `progress-data`, que é um volume persistente do Docker. A política `restart: unless-stopped` inicia o container novamente após a reinicialização do NAS, desde que o Docker esteja ativo e o serviço não tenha sido parado manualmente. Para parar e iniciar novamente, use `docker compose down` e `docker compose up -d`; não use `docker compose down -v`, pois `-v` apaga o volume com os dados. Para atualizar o serviço após mudanças em `server.py` ou nos arquivos do NAS, copie os arquivos atualizados e rode `docker compose up -d --build`.

### 4. Configurar TV e celular

Compile o APK com os padrões descritos acima e instale-o nos dois aparelhos, ou abra **Configurações → Sincronização NAS** em cada um e informe:

- Endereço: `http://192.168.3.47:8787`
- Token: o mesmo valor de `PROGRESS_SYNC_TOKEN` no `.env` do NAS.

O endpoint e o token salvos manualmente ficam criptografados no armazenamento Android Keystore de cada dispositivo. Em **Configurações**, o indicador mostra se a API respondeu. Para sincronizar o catálogo de progresso, TV e celular precisam usar a mesma conta Xtream (mesmo nome de usuário) e o mesmo NAS/token. O endereço do servidor e a senha Xtream não são enviados ao serviço de sincronização.

O perfil de sincronização é derivado por hash do nome de usuário Xtream, permitindo que o mesmo usuário funcione em aparelhos que configuraram o servidor com endereços diferentes. A versão atual também tenta migrar os registros dos perfis antigos. O progresso é salvo localmente e sincronizado quando o serviço está acessível; se o NAS estiver indisponível, a reprodução e o salvamento local continuam. Usar o mesmo nome de usuário em provedores diferentes pode juntar os progressos desses provedores no mesmo perfil.

O serviço armazena ID da mídia, título, pôster, posição, duração, data de atualização e estado concluído. Ele não armazena credenciais Xtream nem URLs dos streams. A lista da API é limitada aos 500 registros mais recentes por perfil.

## Instalar na TV por depuração sem fio

Conecte a TV e o computador à mesma rede e habilite **Opções do desenvolvedor → Depuração sem fio**. Deixe aberta a tela **Parear dispositivo com código** e use o IP e a porta de pareamento que a TV mostrar:

```powershell
adb pair IP_DA_TV:PORTA_DE_PAREAMENTO
```

Digite o código temporário exibido pela TV. Em seguida, volte à tela principal de **Depuração sem fio** e conecte usando a porta indicada em **Endereço IP e porta**. A porta de conexão é diferente da porta de pareamento. No teste da TCL, os endereços foram `192.168.3.72:43275` para parear e `192.168.3.72:37487` para conectar; os números podem mudar:

```powershell
adb connect IP_DA_TV:PORTA_DE_CONEXAO
adb devices
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
```

Confirme que o dispositivo aparece como `device` antes de instalar. Para o Motorola, repita o pareamento usando o IP e as portas exibidos pelo próprio celular. Se `adb devices` estiver vazio após o pareamento, ainda é necessário executar `adb connect` na porta de depuração sem fio.

## Usar o aplicativo

1. Abra o Le&Sil Stream e configure o servidor Xtream, usuário e senha. Informe o esquema (`https://` ou `http://`) e a porta quando necessário.
2. Navegue com o controle remoto/D-pad pelo menu lateral e pelas fileiras do catálogo. A disponibilidade de capas, sinopses, backdrops e metadados depende do provedor.
3. Abra um filme ou episódio para reproduzir no player Media3. O progresso do filme aparece em **Continuar assistindo** na Home; o do episódio aparece na tela de temporadas.
4. Em **Filmes** ou **Séries → Categorias → Favoritos**, veja os itens salvos. Mantenha **OK** pressionado por 3 segundos sobre uma capa, ou segure o dedo no celular, para adicionar ou remover o favorito.
5. Use **Atualizar** no menu principal para buscar novamente o catálogo do servidor. Para sincronizar progresso e favoritos entre TV e celular, configure o NAS nos dois dispositivos com o mesmo nome de usuário Xtream, endereço do NAS e token e atualize o serviço no NAS.

### Navegar pelo catálogo na TV

- Em **Filmes**, percorra as categorias com ↑/↓, pressione **OK** para selecionar a destacada e depois → para entrar na grade.
- Na grade, ↑/↓ percorrem as linhas de capas e ←/→ mudam de filme. O título, a sinopse e o plano de fundo acompanham o filme selecionado enquanto a parte superior da tela permanece fixa.
- Na primeira capa de uma linha, pressione ← para abrir **Categorias**; pressione ← novamente, já com uma categoria em foco, para abrir o menu principal. Pressione → para voltar à grade.
- As capas são carregadas conforme a rolagem. A disponibilidade da arte depende dos dados enviados pelo servidor Xtream.

## Segurança e limites

- Use somente serviços e conteúdo aos quais você tem autorização de acesso.
- Prefira HTTPS para Xtream. APIs podem incluir credenciais em URLs; não compartilhe URLs, capturas ou logs sem remover usuário e senha.
- `android:usesCleartextTraffic="true"` permite compatibilidade com servidores Xtream antigos em HTTP; HTTP não protege credenciais em trânsito.
- O serviço NAS desta configuração usa HTTP e um token Bearer dentro da LAN. Não exponha a porta `8787` à internet, use token aleatório forte e proteja cópias do APK que o contém.
- `local.properties`, `.env`, APKs e dados locais estão excluídos do Git. Verifique o `git status` antes de criar commits.
- Detecção de HEVC 4K, E-AC-3 5.1 ou sinalização HDR não comprova o formato efetivamente exibido pela TV ou recebido pelo receiver. ARC convencional também limita o transporte de áudio lossless.
- Esta é uma versão de desenvolvimento. Valide navegação, sincronização e reprodução no equipamento antes de qualquer distribuição.

## Documentação relacionada

- [Configuração do serviço no NAS](nas-progress/README.md)
- [Roteiro de teste na TCL e no Denon](TESTE_NA_TCL.md)
- [Matriz de cenários 4K e áudio](testes_4k_audio.csv)
- [Decisões visuais da Home](docs/UI_DESIGN.md)
- [Integração Xtream Codes](docs/XTREAM_INTEGRATION.md)
- [Documentação da API Xtream](API_XTREAM_CODES.md)
- [Logos e formatos de codecs](docs/CODEC_LOGOS.md)

## Próximas etapas

- Tela de detalhes do conteúdo.
- Temporadas e episódios de séries.
- Histórico e melhorias de favoritos.
- Continuar refinando os controles e diagnósticos do player sem substituir o pipeline Media3.
