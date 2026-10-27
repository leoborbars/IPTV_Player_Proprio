# Le&Sil Progress Sync no OpenMediaVault

Este serviço próprio mantém o progresso de reprodução do Le&Sil Stream sincronizado entre dispositivos na rede local. Ele roda em Docker Compose, usa SQLite num volume persistente e não recebe usuário/senha Xtream nem URLs dos vídeos.

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
- `PUT /api/v1/progress`: grava/atualiza um item; exige o mesmo cabeçalho.
- Banco: `/data/progress.sqlite3` dentro do container, persistido pelo volume `progress-data`.

O serviço salva ID da mídia, título, pôster, posição, duração, data de atualização e estado concluído. A API devolve até 500 registros mais recentes por perfil. Os logs HTTP não registram URLs nem conteúdo assistido.

## Segurança

Esta configuração usa HTTP na LAN, não criptografa o tráfego e depende do token Bearer. Use um token aleatório de pelo menos 32 caracteres, restrinja o serviço à rede de casa e não exponha a porta `8787` à internet. Para acesso remoto futuro, use uma VPN ou configure HTTPS e firewall antes de publicar qualquer endpoint.
