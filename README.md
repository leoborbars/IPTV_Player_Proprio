# Le&Sil Stream — IPTV Player Android

Aplicativo VOD para Android TV/Google TV e Android, com catálogo Xtream Codes, navegação por controle remoto e reprodução pelo Media3/ExoPlayer. A interface usa Views nativas e mantém o cliente Xtream e o pipeline de reprodução Media3 existentes.

**Repositório:** [leoborbars/IPTV_Player_Proprio](https://github.com/leoborbars/IPTV_Player_Proprio)

## Estado atual

- Home com as fileiras **Filmes recém adicionados** e **Séries recém adicionadas**, sem destaque superior nem fileira de Continuar assistindo. O catálogo de filmes tem categorias, busca, arte de fundo e navegação por D-pad. Na TV, o painel de categorias desliza sem deslocar o cabeçalho do filme, e a grade mantém o tamanho das capas durante a navegação.
- Filmes e séries aparecem do mais recente para o mais antigo nas categorias do provedor, conforme a data/hora informada por ele; itens sem data ficam ao final. Em **Favoritos**, filmes e séries ficam sempre em ordem A–Z. O menu Lançamentos foi removido; as demais categorias mantêm as opções de ordenação manual.
- Séries usam a mesma grade e navegação dos filmes, com sinopse e número de temporadas da série selecionada. Ao abrir uma série, o app mostra apenas temporadas com episódios, que reproduzem no Media3.
- **Atualizar catálogo ao iniciar** fica desativado por padrão em Configurações. Nesse modo, o app abre apenas o último catálogo salvo no aparelho, sem consultar o Xtream na inicialização. Se ainda não houver uma cópia, use **Atualizar** no menu principal para fazer a primeira carga. Ao ativar a opção ou selecionar **Atualizar**, a tela de atualização ocupa toda a tela até o catálogo carregar.
- Player Media3 existente com controles e diagnóstico de reprodução.
- Progresso local de filmes e episódios. A barra azul permanece visível também na capa selecionada e acompanha filmes nas fileiras da Home e no catálogo, e episódios na tela de temporadas. Ao entrar nos últimos 10 segundos (ou no tempo escolhido em Configurações), o item é marcado como concluído, a barra fica verde e aparece **Ir para o Próximo** quando houver outro título. Episódios ainda avançam automaticamente ao terminar. Ao rever um item concluído, a barra volta a azul e acompanha a nova reprodução.
- **Favoritos** ficam nas categorias de Filmes e Séries. Os botões **Trocar lista** e **Voltar** aparecem ao lado do nome da lista; nas demais categorias, **Voltar** fica ao lado do nome da categoria. Segure uma capa por 3 segundos no controle ou na tela para adicionar/remover; a estrela aparece no título da capa.
- A busca abre uma tela única com teclado navegável pelo controle à esquerda e resultados de filmes e séries atualizados a cada tecla à direita. As capas continuam verticais; a busca consulta o catálogo já carregado no aparelho e ignora acentos nos títulos. As setas percorrem e rolam todas as linhas de resultados; segure **OK** por 3 segundos sobre uma capa para adicionar o filme ou a série a uma lista.
- Sincronização opcional do progresso, das listas e dos favoritos entre TV e celular por um serviço próprio no NAS/OpenMediaVault.
- Em **Configurações**, o app mostra o estado da conexão com o NAS e permite escolher quando exibir **Ir para o Próximo** (5, 10, 15, 20, 30 ou 60 segundos antes do fim). A preferência é salva no aparelho e sincronizada pelo NAS entre os dispositivos da mesma conta Xtream após atualizar o serviço.
- O item **Filmes 4K** foi removido do menu lateral. Na lista de categorias de Filmes, a categoria 4K do provedor aparece logo abaixo de **Favoritos**, quando disponível. **[XXX] Bella da Semana** fica por último e **[XXX] LGBT +18** não aparece no menu de categorias. O cabeçalho distingue **Categorias / Filmes** de **Categorias / Séries**, e os rótulos omitem o prefixo repetido.
- **Sobre**, abaixo de Configurações, mostra o hash das fontes usadas na compilação, a revisão Git, a data/hora da compilação e informações do dispositivo. O hash das fontes identifica o código e os recursos do app; não é o checksum do APK.

A versão Android é `0.3-lesil` (APK Debug). O ícone Le&Sil integrado ao app está em `app/src/main/res/drawable-nodpi/lesil_icon.png` e usa a arte de `Fotos_iptvplayer/icone2.png` no launcher, no banner da TV e nas telas internas. Confirme a navegação e a reprodução 4K HDR/áudio multicanal nos equipamentos; a sinalização informada pelo app não substitui a validação da imagem na TV nem do formato recebido pelo receiver. Consulte [TESTE_NA_TCL.md](TESTE_NA_TCL.md) e [testes_4k_audio.csv](testes_4k_audio.csv).

O serviço no NAS respondeu `{"status":"ok"}` em `/health`. A sincronização entre os dois aparelhos também foi exercitada após a migração do perfil antigo: o celular recuperou registros que já estavam salvos pela TV.

Em 02/11/2026, o `nas-progress/server.py` atualizado foi copiado para o NAS e o container foi reconstruído. O endpoint `/health` respondeu `{"status":"ok"}` e a rota de preferências de reprodução respondeu `401` sem token, confirmando que está disponível e protegida. Para repetir a atualização em versões futuras, siga o [procedimento habitual](#atualizar-o-serviço-no-nas-procedimento-habitual).

Em um teste anterior no mesmo dia, o APK Debug compilou e foi instalado no Motorola Edge 70 Pro. No celular, a Home carregou, a barra azul permaneceu visível na capa selecionada em **Continuar assistindo** (fileira removida da Home na revisão atual), e Configurações mostrou **NAS: conectado**. A opção **Mostrar Próximo** foi alterada de 10 para 15 segundos e restaurada para 10; o NAS devolveu o valor final de 10 segundos. O aviso nos últimos segundos e a passagem para o próximo episódio ainda precisam de teste com vídeo real.

Na revisão atual, o APK Debug compilou e foi instalado no Motorola Edge 70 Pro e na Smart TV Pro. A instalação na TV não abriu o aplicativo. A tela de Listas permite remover **Leandro** quando há outra lista ativa; para essa remoção sincronizar com os demais aparelhos, ainda é necessário copiar o `nas-progress/server.py` desta revisão para o NAS e reconstruir o container.

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

### Atualizar o serviço no NAS (procedimento habitual)

Abra o **CMD do Windows**. Se o prompt estiver em `pi@nas:~$`, digite `exit` primeiro: o caminho `D:\` só existe no Windows. No prompt `C:\...>` ou `D:\...>`, execute:

```cmd
cd /d D:\Projetos\IPTV_Player_P0
scp nas-progress\server.py pi@192.168.3.47:/home/pi/nas-progress/server.py
ssh pi@192.168.3.47
```

Informe a senha SSH quando solicitada. Depois que o prompt mudar para `pi@nas:~$`, execute **no NAS**:

```sh
cd ~/nas-progress
sudo docker compose up -d --build
curl http://127.0.0.1:8787/health
```

A resposta esperada é `{"status":"ok"}`. Se o `curl` falhar logo após o container iniciar, aguarde alguns segundos e repita o teste; se continuar falhando, consulte `sudo docker compose ps` e `sudo docker compose logs --tail=80 progress-sync`. Esse procedimento mantém o `.env` e o volume com os progressos. Quando `Dockerfile` ou `compose.yaml` também mudarem, copie esses arquivos para `/home/pi/nas-progress/` antes de reconstruir. Veja [as instruções completas do serviço](nas-progress/README.md).

### 4. Configurar TV e celular

Compile o APK com os padrões descritos acima e instale-o nos dois aparelhos, ou abra **Configurações → Sincronização NAS** em cada um e informe:

- Endereço: `http://192.168.3.47:8787`
- Token: o mesmo valor de `PROGRESS_SYNC_TOKEN` no `.env` do NAS.

O endpoint e o token salvos manualmente ficam criptografados no armazenamento Android Keystore de cada dispositivo. Em **Configurações**, o indicador mostra se a API respondeu. Para sincronizar o catálogo de progresso, TV e celular precisam usar a mesma conta Xtream (mesmo nome de usuário) e o mesmo NAS/token. O endereço do servidor e a senha Xtream não são enviados ao serviço de sincronização.

O perfil de sincronização é derivado por hash do nome de usuário Xtream, permitindo que o mesmo usuário funcione em aparelhos que configuraram o servidor com endereços diferentes. A versão atual também tenta migrar os registros dos perfis antigos. O progresso é salvo localmente e sincronizado quando o serviço está acessível; se o NAS estiver indisponível, a reprodução e o salvamento local continuam. Usar o mesmo nome de usuário em provedores diferentes pode juntar os progressos desses provedores no mesmo perfil.

O serviço armazena ID da mídia, título, pôster, posição, duração, data de atualização e estado concluído. Ele não armazena credenciais Xtream nem URLs dos streams. A API devolve até 5000 registros de progresso ou favoritos e até 20000 itens de listas por perfil.

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
   Na tela de Filmes ou Séries, use a lupa para abrir a busca. Selecione as letras com as setas e **OK**; pressione **→** na última coluna do teclado para ir aos resultados e **←** na primeira coluna de capas para voltar ao teclado.
3. Abra um filme ou episódio para reproduzir no player Media3. Pressione **OK** para abrir os controles; use **Reiniciar** para voltar ao início e **Próximo** para avançar ao item seguinte. Pressione **baixo** a partir dos botões centrais para focar a barra de tempo e use **esquerda/direita** para saltos de 5 segundos. A barra e os botões somem juntos após a inatividade. Ao sair do player com **Voltar**, o foco retorna ao filme ou episódio reproduzido na tela de origem. O progresso aparece nas capas dos filmes e dos episódios.
4. Abra **Listas** no menu principal para criar, selecionar ou remover listas. **Criar lista** fica no topo e cada lista aparece abaixo com seu botão de remoção. A lista **Leandro** reúne os favoritos existentes e também pode ser removida quando houver outra lista; a última lista ativa não pode ser removida. **Favoritos** em Filmes e Séries mostra os itens da lista selecionada; use **Trocar lista**, ao lado do nome da lista, para mudar a seleção e atualizar os títulos exibidos. Mantenha **OK** pressionado por 3 segundos sobre uma capa ou episódio, ou segure o dedo no celular, para abrir **Adicionar a uma lista**. Para retirar um item da lista selecionada, use **Remover de [nome da lista]** no mesmo menu.
   Na tela de temporadas, **Adicionar a uma lista** abre diretamente a seleção das listas para salvar a série.
5. Use **Atualizar** no menu principal para buscar novamente o catálogo do servidor. Para sincronizar progresso e listas entre TV e celular, configure o NAS nos dois dispositivos com o mesmo nome de usuário Xtream, endereço do NAS e token e atualize `nas-progress/server.py` no NAS conforme o procedimento acima. As tabelas de listas são criadas automaticamente, preservando o volume.
6. Abra **Sobre** no menu principal para conferir o hash, a revisão Git e a data da compilação instalada.

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
