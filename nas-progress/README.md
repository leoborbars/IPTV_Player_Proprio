# Le&Sil Progress Sync no OpenMediaVault

Este serviço próprio mantém o progresso de reprodução, as listas e os favoritos do Le&Sil Stream sincronizados entre dispositivos na rede local. Ele roda em Docker Compose, usa SQLite num volume persistente e não recebe usuário/senha Xtream nem URLs dos vídeos.

## Ambiente instalado

- NAS/Raspberry Pi com OpenMediaVault: `192.168.3.47`.
- Pasta no NAS: `/home/pi/nas-progress`.
- Porta da API: `8787`.
- Container: `lesil-progress`.
- Volume Docker: `nas-progress_progress-data` (o nome pode variar conforme a pasta/projeto Compose).

Reserve o endereço do NAS no roteador para que o IP não mude. Mantenha a porta `8787` acessível somente na rede local; não configure encaminhamento externo no roteador.

## Instalar ou atualizar os arquivos

Copie a pasta inteira `nas-progress` do projeto para `/home/pi/nas-progress`. No Windows, use WinSCP ou, com o cliente OpenSSH instalado, PowerShell:

```powershell
scp -r "D:\Projetos\IPTV_Player_P0\nas-progress" pi@192.168.3.47:/home/pi/
```

Se `scp` não for reconhecido, instale **OpenSSH Client** em Recursos opcionais do Windows ou transfira a pasta pelo WinSCP. Troque `pi` se o usuário SSH do NAS for outro. Ao atualizar, preserve o `.env` já criado no NAS: ele contém o segredo local e não faz parte dos arquivos do projeto.

## Atualizar o serviço nas próximas versões

Use este procedimento sempre que `server.py` mudar. Abra o **CMD no Windows**. Caso esteja conectado ao NAS e veja `pi@nas:~$`, digite `exit` antes: o NAS não consegue ler o caminho `D:\` do computador. Quando aparecer `C:\...>` ou `D:\...>`, execute:

```cmd
cd /d D:\Projetos\IPTV_Player_P0
scp nas-progress\server.py pi@192.168.3.47:/home/pi/nas-progress/server.py
ssh pi@192.168.3.47
```

Digite a senha SSH solicitada. No prompt `pi@nas:~$`, execute:

```sh
cd ~/nas-progress
sudo docker compose up -d --build
curl http://127.0.0.1:8787/health
```

O teste deve responder `{"status":"ok"}`. Se houver `Recv failure: Connection reset by peer` imediatamente após a reconstrução, espere alguns segundos e repita o `curl`. Se persistir, execute `sudo docker compose ps` e `sudo docker compose logs --tail=80 progress-sync`. Não recrie o `.env` nem use `docker compose down -v`: o token e os dados existentes devem ser preservados. Se `Dockerfile` ou `compose.yaml` também tiverem mudado, copie-os pelo mesmo método antes do `up -d --build`.

## Criar o token e iniciar o serviço

Conecte por SSH:

```sh
ssh pi@192.168.3.47
cd ~/nas-progress
cp .env.example .env
openssl rand -hex 32
nano .env
chmod 600 .env
```

Cole no `.env` o resultado aleatório gerado, substituindo o valor de exemplo:

```text
PROGRESS_SYNC_TOKEN=seu_segredo_aleatorio_de_64_caracteres
```

Não coloque aspas. Use o mesmo token nos builds locais do app ou digite-o nas configurações dos dispositivos. Mantenha `.env` privado e não o envie ao Git.

Inicie e confira o container:

```sh
docker compose up -d --build
docker compose ps
docker compose logs --tail=30 progress-sync
curl http://127.0.0.1:8787/health
```

Se houver `permission denied` para `/var/run/docker.sock`, execute com `sudo`, por exemplo `sudo docker compose up -d --build`. A verificação deve responder `{"status":"ok"}`. Em outro aparelho na mesma rede, abra `http://192.168.3.47:8787/health` para confirmar o acesso LAN.

Para reiniciar, use `docker compose restart`. Para reconstruir após atualizar `server.py` ou o `Dockerfile`, copie os arquivos e rode `docker compose up -d --build`. `docker compose down` mantém o volume; **não** use `docker compose down -v`, pois `-v` apaga o banco persistente.

Após atualizar para a versão com favoritos e episódios, copie `server.py` para `/home/pi/nas-progress/server.py` e execute no NAS:

```sh
cd ~/nas-progress
sudo docker compose up -d --build
curl http://127.0.0.1:8787/health
```

O banco SQLite cria a tabela de favoritos automaticamente. O volume e os progressos antigos são preservados. Sem essa atualização, os novos APKs ainda guardam os favoritos e episódios localmente, mas o NAS antigo responderá `404` para favoritos e `400` para progresso de episódios.

Após atualizar o app com a remoção de itens de **Continuar assistindo**, envie o `server.py` atual pelo PowerShell e reconstrua o serviço:

```powershell
scp ".\nas-progress\server.py" pi@192.168.3.47:/home/pi/nas-progress/server.py
ssh pi@192.168.3.47
```

No NAS, execute `cd ~/nas-progress && sudo docker compose up -d --build`. A migração adiciona a coluna `hidden_from_continue` ao banco existente sem apagar o progresso; só então a remoção passa a sincronizar entre TV e celular.

Para sincronizar também a preferência de tempo do botão **Ir para o Próximo**, copie a versão atual de `server.py` para o NAS e reconstrua o container com o mesmo comando. A nova tabela `playback_preferences` é criada automaticamente no volume existente; não use `down -v`. Até atualizar o NAS, a escolha continua salva localmente em cada aparelho, mas a API antiga responderá `404` para a preferência.

Para ativar **Listas**, atualize `server.py` pelo procedimento de CMD acima e reconstrua o container. O banco cria `media_lists` e `media_list_items` no mesmo volume. Na primeira abertura, os favoritos já gravados no NAS são copiados para a lista **Leandro**, inclusive as remoções pendentes; o app também importa os favoritos locais de cada aparelho. A lista Leandro pode ser removida quando existir outra lista ativa, e a remoção é sincronizada como estado inativo. Mantenha o volume e o `.env`. Até o servidor ser atualizado, as listas adicionais ficam apenas no aparelho e as novas rotas respondem `404`.

Se as rotas de Listas já funcionam, mas a remoção de **Leandro** não sincroniza, copie novamente o `server.py` desta revisão e reconstrua o container. A versão anterior da API rejeitava a remoção dessa lista; os dados do volume são preservados na atualização.

## Configurar o Le&Sil Stream

Há duas formas:

1. No app, abra **Configurações → Sincronização NAS** e informe o endereço `http://192.168.3.47:8787` e o token do `.env`.
2. Para preencher esses valores por padrão num APK local, acrescente ao `local.properties` da raiz do projeto, sem apagar `sdk.dir`:

   ```properties
   lesil.progressSyncEndpoint=http://192.168.3.47:8787
   lesil.progressSyncToken=O_MESMO_TOKEN_DO_ARQUIVO_ENV
   ```

   Compile novamente e instale o APK. O app importa os padrões e armazena as configurações com Android Keystore. `local.properties` é ignorado pelo Git; o token também fica incorporado ao APK produzido, então proteja o APK.

Configure TV e celular com a mesma conta Xtream (mesmo nome de usuário), endpoint e token. O identificador de perfil é derivado por hash do nome de usuário; não depende do endereço do servidor. Registros salvos no formato anterior são migrados durante a sincronização. O mesmo nome de usuário em provedores diferentes pode misturar os registros.

Em **Configurações**, o app mostra o estado de conexão com a API. Quando o NAS está indisponível, o progresso continua salvo localmente e será sincronizado quando a API voltar a responder.

## API e dados

- `GET /health`: teste sem autenticação, responde `{"status":"ok"}`.
- `GET /api/v1/progress?profile_id=<sha256>`: lista progresso; exige `Authorization: Bearer <token>`.
- `PUT /api/v1/progress`: grava/atualiza um filme ou episódio; exige o mesmo cabeçalho. Episódios usam `media_id` no formato `episode:<id>`.
- `GET /api/v1/favorites?profile_id=<sha256>`: lista filmes e séries favoritos, inclusive remoções pendentes; exige o mesmo token.
- `PUT /api/v1/favorites`: adiciona ou remove um favorito; exige o mesmo token. Remoções ficam registradas para sincronizar aparelhos que estavam offline.
- `GET /api/v1/lists?profile_id=<sha256>`: devolve listas e seus itens, inclusive remoções pendentes.
- `PUT /api/v1/lists`: cria, renomeia ou remove uma lista pelo estado `active` e `updated_at`.
- `PUT /api/v1/list-items`: adiciona ou remove filmes, séries e episódios de uma lista. Episódios incluem `series_id` para identificar a série correspondente.
- `GET /api/v1/playback-preferences?profile_id=<sha256>`: lê o tempo configurado para **Ir para o Próximo**.
- `PUT /api/v1/playback-preferences`: grava `profile_id`, `next_prompt_seconds` (5 a 300) e `updated_at`. A atualização mais recente prevalece entre dispositivos.
- Banco: `/data/progress.sqlite3` dentro do container, persistido pelo volume `progress-data`.

O serviço salva ID da mídia, título, pôster, posição, duração, data de atualização e estado concluído. Listas e seus itens usam tabelas próprias e estado ativo/removido para sincronização offline. A lista Leandro mantém compatibilidade com a antiga API de favoritos. Cada API devolve até 5000 registros de progresso ou favoritos e até 20000 itens de listas por perfil. Os logs HTTP não registram URLs nem conteúdo assistido.

## Segurança

Esta configuração usa HTTP na LAN, não criptografa o tráfego e depende do token Bearer. Use um token aleatório de pelo menos 32 caracteres, restrinja o serviço à rede de casa e não exponha a porta `8787` à internet. Para acesso remoto futuro, use uma VPN ou configure HTTPS e firewall antes de publicar qualquer endpoint.
