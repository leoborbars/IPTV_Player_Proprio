# PROMPT MESTRE PARA CODEX — LUNASTREAM, UI PREMIUM + XTREAM CODES

**Leia este documento inteiro e os três arquivos PNG da pasta `referencias_visuais/` antes de modificar qualquer código.** Responda e documente em português do Brasil. Você é um desenvolvedor Android sênior especializado em Google TV, Kotlin, UX para controle remoto, AndroidX Media3/ExoPlayer e APIs de catálogo Xtream Codes. Sua missão é EVOLUIR o aplicativo Android que JÁ ESTÁ FUNCIONANDO no meu workspace, preservando seu player e autenticação, e construir a interface LunaStream baseada nas referências fornecidas.

## 0. O QUE EXECUTAR NESTA SESSÃO

1. Examine o workspace e identifique a estrutura, dependências, ferramentas de build, o player atual, o cliente Xtream e funções já implementadas. NÃO substitua o projeto funcional pelo protótipo antigo; o workspace é a fonte da verdade.
2. Leia as imagens `referencias_visuais/01_home_escolhida.png`, `02_detalhes_filme.png` e `03_player_reproducao.png` e compare-as com a UI atual.
3. Crie um plano curto, em etapas incrementais; inicie imediatamente a implementação real de **Home + navegação lateral + cards + categorias**, com estados de loading/vazio/erro e ligação aos dados EXISTENTES do Xtream. Em seguida implemente/aperfeiçoe a **tela de detalhes** e o **overlay do player**, conforme o que o tempo e a compilação permitirem.
4. Execute `gradlew.bat :app:assembleDebug` (ou o comando Gradle apropriado ao workspace), corrija os erros que surgirem e relate honestamente qualquer dependência de SDK/rede que impeça compilar.
5. No encerramento, liste arquivos alterados, estado de cada tela, build executado, caminho real do APK se gerado, testes feitos e próximos passos. Não responda apenas com pseudocódigo, checklist ou proposta visual: **implemente código Kotlin funcional**.

## 1. CONTEXTO REAL E RESTRIÇÕES

- App de uso pessoal para uma **TCL 65C7K (65 pol., Google TV)**; receiver **Denon AVR-S920W** ligado à TV por **HDMI ARC convencional**.
- Serviço de catálogo Xtream Codes autorizado: endereço/base URL, usuário e senha fornecidos pelo usuário no próprio app. NUNCA peça credenciais nesta conversa, gere credenciais de exemplo como se fossem reais, nem grave senhas em código.
- Uso restrito a **filmes e séries VOD**. Não implementar canais ao vivo, EPG, gravação, catch-up, IPTV ao vivo ou backend de retransmissão. Mesmo que alguma arte conceitual use o nome 'Canais', **não criar menu Canais**.
- A versão preliminar já reproduz vídeos. Não destruir, refatorar indiscriminadamente, substituir nem degradar esse código. O sucesso significa manter a qualidade reproduzida e melhorar experiência visual e gestão do catálogo.
- O objetivo do player não é "melhorar" o sinal artificialmente: é **não limitar o stream original**. Preservar o pipeline Media3, renderização `SurfaceView`, seleção de faixas, HDR quando suportado e saída de áudio negociada pelo Android. Não transcodificar.
- A conexão ARC não transporta TrueHD/DTS-HD MA lossless. Dados da faixa de áudio e o que o Denon de fato recebe são informações diferentes. Jamais inferir passthrough real pela simples presença de um codec no arquivo.

## 2. REFERÊNCIA VISUAL APROVADA — NÃO CRIAR OUTRA IDENTIDADE

Os três PNGs da pasta `referencias_visuais` representam o **primeiro conjunto de telas aprovado**. Use-os como referência primária de composição, hierarquia, estilo, espaçamento e comportamento visual. Prioridade absoluta para `01_home_escolhida.png` (é a imagem escolhida pelo usuário).

### Linguagem visual

- Nome provisório: **LunaStream**; tudo em **português brasileiro**.
- Tela 16:9, visual de Google TV, fundo preto/azul muito escuro, gradientes suaves, detalhes azul/ciano, foco branco/azul luminoso; cantos discretamente arredondados; estética cinematográfica elegante.
- NUNCA usar um screenshot como plano de fundo estático para simular interface funcional. Criar elementos reais (textos, cards, foco, navegação, imagens remotas e controles).
- O banner/filmes fictícios "Eclipse 2049" e outros títulos nas imagens são **dados demonstrativos visuais**, não catálogos reais, não devem ser inventados nas consultas do provedor. Mostrar título/arte real que o servidor fornece; em modo de pré-visualização local pode haver mocks claramente indicados como fictícios.
- Padrão para controle remoto: foco visível em TODOS os elementos selecionáveis, `D-pad` (cima/baixo/esquerda/direita), OK, Voltar, sem mouse nem toque; restaurar foco ao voltar de detalhes/player; foco não deve desaparecer nem prender usuário; rolagem horizontal/vertical conforme foco.
- Legibilidade a 2–3 metros de distância, margens seguras, componentes adaptáveis a diferentes tamanhos de TV, loading/skeleton elegante, placeholder quando pôster/backdrop indisponível. Otimizar memória e evitar efeitos contínuos custosos na TCL.

### Tela 1 — HOME (`01_home_escolhida.png`)

Layout muito próximo do PNG: barra lateral fixa/expansível à esquerda com logo e itens **Início, Filmes, Séries, Filmes 4K, Continuar, Favoritos, Configurações**. Destaque inicial com backdrop cinematográfico ocupando topo à direita e degradê à esquerda para legibilidade; título, ano, duração, gêneros, sinopse curta, badges reais e botões **Assistir** e **Detalhes**.

Na área inferior, seções horizontais, preferencialmente nesta ordem:
1. **Continuar assistindo** — cards 16:9, progresso persistente em faixa inferior, para filmes e episódios, com título e posição restante; ocultar seção quando não houver conteúdo em andamento.
2. **Filmes 4K** — categoria identificada **pelo nome exato/normalizado vindo do servidor** (ou configuração/mapeamento do usuário), sem supor ID constante; foco em qualidade e badges reais.
3. **Adicionados recentemente** — ordenar pelo `added` válido fornecido pelo provedor (com fallback sem ordenar incorretamente datas não confiáveis).
4. **Séries em destaque** — capa/miniatura e metadados reais do Xtream.

Ao entrar em Filmes ou Séries, oferecer navegação por categorias do provedor, pesquisa de títulos e grids eficientes; não carregar tudo em memória/imagens simultaneamente. Hero pode usar o item em destaque por regra simples e documentada, sem escolher arbitrariamente um item como se fosse recomendação pessoal do provedor.

### Tela 2 — DETALHES (`02_detalhes_filme.png`)

Backdrop de grande largura, pôster vertical à esquerda, título e texto à direita, metadados, sinopse completa, botões **Assistir/Continuar**, **Trailer** (apenas se existir URL válida), **Favoritar**, e informações de elenco/direção quando disponíveis. Na parte inferior, **Títulos semelhantes** somente quando houver critério verdadeiro (mesma categoria/gênero, ou API externa opcional), nunca gerar falsos.

Adicionar painel discreto **Informações técnicas** com vídeo (codec), resolução, FPS, HDR, contêiner, faixas de áudio, canais/idiomas, legendas — preenchido por Xtream quando realmente informar e depois por sondagem técnica opcional. Mostrar **Não identificado** quando dado indisponível. NÃO inferir HDR10, Dolby Vision, Atmos nem TrueHD apenas por palavra no título do filme ou por pertencer à categoria "Filmes 4K".

Para séries, a mesma identidade visual mais seletor de temporada, lista de episódios com imagens/sinopses, ícones "assistido", indicador de progresso individual e botão continuar/próximo episódio.

### Tela 3 — PLAYER (`03_player_reproducao.png`)

Manter o atual Media3/ExoPlayer e vídeo fullscreen real; overlay semiptransparente acionado por OK/cima/baixo, desaparece automaticamente em reprodução. Barra de progresso com posição e duração; pausar/retomar, seek, seletores de áudio e legendas, informações técnicas opcionais, sair/voltar. Exibir próximo episódio só em séries e somente quando houver episódio seguinte — jamais sobrepor "próximo episódio" em filmes. `Back` durante controles fecha overlay; novo `Back` volta aos detalhes e preserva progresso. Evitar sobrepor controles na imagem durante reprodução normal.

No painel de diagnóstico diferenciar (a) stream declarado/API; (b) faixa selecionada e format detectado no Media3; (c) HDR real da TV confirmado externamente; (d) áudio real no Denon confirmado externamente. Mostrar "Não verificado no dispositivo" nos itens c/d.

### Sugestões de design system ajustáveis

- Preto/azul base aproximados: `#050A11`, `#091524`, `#0D2236`; texto `#F4F7FB`; texto secundário `#A9BDD0`; destaque `#2EA9FF`; brilho mais sutil; badges 4K dourado discreto.
- Esses valores são **referências**, não uma restrição pixel-perfect que prejudique contraste ou legibilidade.
- Design tokens centralizados; componentes reutilizáveis: `SidebarNav`, `HeroBanner`, `MediaPosterCard`, `LandscapeCard`, `ContentRow`, `TechnicalBadge`, `ProgressIndicator`, `DetailsSection`, `TechnicalInfoPanel`, `FocusHighlight`, `PlaybackOverlay`.
- Animações de foco curtas (aprox. 120–180 ms); escala moderada; borda/sombra consistente; **não** usar scroll infinito que trava em catálogos grandes.

## 3. ARQUITETURA/TECNOLOGIAS — PRESERVAR O QUE EXISTE

- Kotlin nativo para Android/Google TV.
- Preferência por **Jetpack Compose com suporte a UI de TV** para telas novas (`LazyRow`, `LazyColumn`, estado, foco D-pad), mas **não migrar cegamente** a Activity ou player existente se isso causar regressão. Se projeto usar Views, realizar integração incremental (ComposeView, Activity separada, ou caminho equivalente) com ponte para player existente.
- Media3/ExoPlayer para reprodução; `SurfaceView` onde aplicável; seleção de faixas pela API do player, evitando recriar motor de vídeo.
- Retrofit + OkHttp ou cliente já existente para API, com coroutines e timeout/retry moderados; Coil ou carregador de imagens compatível com a stack. Room para histórico/cache persistente, DataStore para preferências, se não houver alternativa local já implementada.
- Separação simples sugerida: `data/xtream`, `data/local`, `domain/models`, `ui/home`, `ui/details`, `ui/series`, `ui/components`, `player`, `diagnostics`. Não criar muitas camadas artificiais.
- Todas as bibliotecas AndroidX Media3 com versões coerentes; conferir compatibilidade Gradle/Kotlin/Compose antes de adicionar dependências. Nunca copiar versões de exemplos sem validar.

## 4. API XTREAM CODES — CONTRATO E DOCUMENTAÇÃO

**IMPORTANTE:** Xtream Codes possui clones e versões com campos inconsistentes. As fontes documentais a seguir são **referências de comunidade/reverse engineering**, não garantia de que o meu provedor retorna exatamente o mesmo JSON. Investigar adaptando o cliente existente. Leia também `documentacao/API_XTREAM_CODES.md` deste kit.

### Endpoint base e autenticação

`GET {BASE_URL}/player_api.php?username={USUARIO}&password={SENHA}`

- URL do servidor vem do cadastro do usuário e pode ter porta, HTTPS ou HTTP legado.
- No retorno consultar `user_info.auth`, `user_info.status`, `user_info.max_connections`, `user_info.allowed_output_formats`, quando disponíveis; servidor pode divergir.
- Nunca armazenar/salvar em logs URLs completas com credenciais. Nunca inferir legitimidade do serviço pelos metadados. Respeitar conexões simultâneas e autorização.

### Ações VOD — Filmes

- `action=get_vod_categories` → categorias: `category_id`, `category_name`, `parent_id` (opcional).
- `action=get_vod_streams` → todos os filmes, ou `&category_id={ID}` → filtrado; campos típicos: `stream_id`, `name`, `stream_icon`, `category_id`, `container_extension`, `added`, `rating`, `direct_source` (pode faltar/ser vazio).
- `action=get_vod_info&vod_id={stream_id}` → informações detalhadas: objeto `info` (podem existir `movie_image`, `backdrop_path[]`, `plot`, `genre`, `cast`, `director`, `rating`, `releasedate`, `duration`, `duration_secs`, `tmdb_id`, `youtube_trailer`) e `movie_data` (ID, nome, categoria, extensão, etc.). Campos de `video` e `audio` quando presentes podem estar ausentes/vazios/inconsistentes; tratá-los como informações declaradas, não como prova de decodificação.

### Ações VOD — Séries

- `action=get_series_categories` → categorias de séries.
- `action=get_series` → lista de séries; aceita `&category_id={ID}`; campos comuns: `series_id`, `name`, `cover`, `backdrop_path`, `plot`, `genre`, `rating`, `last_modified`, `category_id`, etc.
- `action=get_series_info&series_id={ID}` → objetos `info`, `seasons` e `episodes`; atenção: `episodes` pode ser **objeto indexado por número de temporada como string** (`"1": [...]`), não uma lista plana; episódios têm `id`, `episode_num`, `title`, `container_extension`, `info` com `duration`, `plot`, `movie_image` etc. `id` do episódio NÃO é necessariamente o `series_id`. Ordenar temporadas e episódios por seus números quando válidos.

### URL de mídia (somente se a implementação já precisar construir)

Formato habitual para filmes: `{BASE_URL}/movie/{USUARIO}/{SENHA}/{stream_id}.{container_extension}`.
Formato habitual para episódios: `{BASE_URL}/series/{USUARIO}/{SENHA}/{episode_id}.{container_extension}`.

São **padrões frequentes, não universais**. Respeitar URL de reprodução/direct_source reconhecida, extensão suportada, redirecionamento, fallback explícito e limitações específicas do provedor. Não tentar URLs aleatórias em massa, não contornar DRM e não transcodificar.

### Exemplo de JSON SIMULADO (não é resposta real do meu provedor)

Categorias:
```json
[{"category_id":"41","category_name":"Filmes 4K","parent_id":0}]
```
Filmes:
```json
[{"stream_id":12345,"name":"Título fictício","stream_icon":"https://exemplo.invalid/poster.jpg","category_id":"41","container_extension":"mkv","added":"1760000000"}]
```
Detalhes de filme:
```json
{"info":{"plot":"Sinopse fictícia","movie_image":"https://exemplo.invalid/poster.jpg","backdrop_path":["https://exemplo.invalid/backdrop.jpg"],"genre":"Ficção científica","cast":"Pessoa A, Pessoa B","duration_secs":7200,"tmdb_id":"123"},"movie_data":{"stream_id":12345,"container_extension":"mkv"}}
```
Série com temporadas e episódios:
```json
{"info":{"name":"Série fictícia","cover":"https://exemplo.invalid/serie.jpg"},"seasons":[{"season_number":1,"name":"Temporada 1"}],"episodes":{"1":[{"id":"98765","episode_num":1,"title":"Episódio 1","container_extension":"mp4","info":{"duration_secs":2500}}]}}
```

### Implementação correta da API

- API e catálogos podem vir em JSON com números como **string ou number**, objetos/arrays vazios, arrays de backdrop vazios, campos nulos, nomenclaturas inconsistentes `releaseDate`/`releasedate`, duração em texto/número e `episodes` como mapa.
- Implementar DTOs resilientes, mappers com null safety, testes de parsing com fixtures fictícias e fallback de placeholder de imagem.
- Efetuar operações de rede fora da UI thread. Cache local de categorias e listagens, invalidação/atualização manual, timeout, erro de autenticação, limite de conexões, sem resposta, categoria vazia, alterações do catálogo.
- Fazer `get_vod_info` e `get_series_info` sob demanda (ao abrir detalhes, ou pré-busca moderada), não disparar uma requisição por item durante renderização de 500 capas.
- Identificar categoria `Filmes 4K` por `category_name`, case-insensitive/trim/normalização, oferecer seleção manual se nome variar (ex.: `Filmes UHD`), nunca assumir `category_id=41`.
- Credenciais em URLs de API e mídia: tratar autenticação de modo compatível e proteger ao máximo. Preferir HTTPS quando disponível, usar armazenamento local seguro, mascarar URL nos erros, nos logs, em ferramentas de analytics, exceptions, screenshots e arquivos exportados. Em HTTP legado informar risco; não adicionar exceções amplas sem necessidade.

### Fontes da API (comunitárias, NÃO oficiais)

- Visão geral dos endpoints: https://github.com/worldofiptvcom/xtream-codes-api/blob/master/docs/player-api/overview.md
- Referência de parâmetros: https://github.com/worldofiptvcom/xtream-codes-api-documentation/blob/master/ENDPOINT_REFERENCE.md
- Exemplos de payloads `get_vod_info`, `get_series_info`: https://github.com/worldofiptvcom/xtream-codes-api-documentation/blob/master/XTREAM_CODES_API_DOCUMENTATION.md
- Implementação de cliente como referência adicional (sem copiar indiscriminadamente): https://github.com/tellytv/go.xtream-codes/blob/master/xtream-codes.go

## 5. METADADOS EXTERNOS (TMDB SOMENTE QUANDO NECESSÁRIO)

Prioridade: (1) dados Xtream reais; (2) cache Room de dados previamente obtidos; (3) **opcional**, API TMDB para complementar descrição, backdrop, elenco, nota e artes que faltarem. Não cadastrar chave TMDB fixa no código/repositório; perguntar ao usuário como deseja configurar a chave quando for implementar. Preferir `tmdb_id` confiável do Xtream; se indisponível, buscar por título e ano com verificação antes de associar para evitar misturar filmes/remakes. Respeitar atribuição, licença e termos do TMDB, incluindo créditos "This product uses the TMDB API but is not endorsed or certified by TMDB." se habilitado. Sem conexão com TMDB, o app deve continuar funcionando completamente com o Xtream.

Documentação oficial: https://developer.themoviedb.org/docs/getting-started e https://developer.themoviedb.org/docs/faq

## 6. MEDIAINFO SCANNER — CATEGORIA "FILMES 4K"

Criar um componente desacoplado `MediaInfoScanner`/`MediaTechnicalRepository` com estratégia escalonada:
1. Ler dados técnicos presentes em `get_vod_info` se vierem preenchidos, mostrando proveniência "Servidor".
2. Ao entrar nos detalhes de filme/episódio (ou quando solicitado) realizar **sondagem leve/opcional** via Media3, preparando mídia sem iniciar reprodução, dentro dos limites de tempo/tráfego e conexão do provedor; `onTracksChanged` revela formatos quando conhecidos. Nunca sondar todos os filmes de uma vez.
3. Persistir em cache local por ID do provedor + ID do item + data e proveniência, invalidar quando item/extensão mudarem; evitar sobrecarregar servidor e o limite de streams simultâneos.
4. Quando alguma informação como profile Dolby Vision, HDR10+ dinâmico, TrueHD/Atmos, DTS:X, profundidade de cor, contêiner ou tamanho **não puder ser identificada com segurança**, exibir "Não identificado". Não inventar metadata a partir do nome do título, categoria "Filmes 4K" ou arte. A detecção completa pode exigir ferramentas adicionais e não deve bloquear o app.
5. Sinalizar **codec/faixas presentes** versus **suporte de decoder na TCL** versus **formato realmente recebido pelo Denon**. O app não deve mostrar "Denon recebendo Atmos" sem confirmação.
6. No Home manter badges apenas de formatos devidamente identificados; distinguir badges verificadas de "informação do servidor" quando a procedência for incerta. Tamanho do arquivo e bitrate só se conhecidos.

## 7. CONTINUAR ASSISTINDO, FAVORITOS E SÉRIES

- Salvar progresso local persistente de cada filme/episódio em milissegundos no Room: chave estável incluindo identificador do provedor + tipo do conteúdo + stream_id ou episode_id, temporada/episódio, posição, duração conhecida, data, estado visto. Não confundir ID da série com ID do episódio.
- Atualizar em intervalos moderados (p.ex. 10–20 s durante reprodução) e sempre em pause, exit e mudança de episódio; cuidar de interrupção abrupta, seek e estados de erro.
- Exibir progresso visual no card; ao retornar, oferecer **Continuar** de onde parou ou **Começar do início**.
- Marcar concluído quando atingir limiar configurável próximo do fim (ex.: >=95%, com cuidado em vídeos curtos/créditos), permitir "marcar visto/não visto" manualmente e remover/ocultar dos em andamento quando concluído.
- Próximo episódio baseado nas temporadas e episódios reais retornados pelo Xtream; não pular episódios nem inventar sequência.
- Favoritos locais independentes do servidor; buscar/listar favoritos mesmo após reiniciar.
- Opcional: preferências de idioma/legendas salvas por usuário/dispositivo e ajustáveis por vídeo.

## 8. CRITÉRIOS DE ACEITAÇÃO DE UX E COMPILAÇÃO

A. Abra o APK na TCL e navegue do início até filme/episódio e retorne com o controle remoto sem precisar de mouse/touch.
B. A Home se parece inequivocamente com `01_home_escolhida.png`: menu lateral, hero, cores, foco e fileiras horizontais reais — sem print estático.
C. Filmes vêm de `get_vod_streams`, séries de `get_series`; cards e detalhes mostram imagens/sinopses verdadeiras quando disponíveis, placeholders corretos caso contrário.
D. Exibe `Filmes 4K` da categoria do servidor e não usa selo "4K" para arquivos sem evidência.
E. Playback que já funcionava segue funcionando; não remover os controles essenciais de áudio, legenda, diagnóstico, HDR e seleção de trilha.
F. Com progresso salvo, fechar/reabrir app oferece Continuar no ponto anterior; progressos de episódios diferentes não se misturam.
G. Trata catálogos grandes, rede lenta/sem rede e erros de credenciais sem travamento e sem vazamento de senha.
H. `:app:assembleDebug` compila se SDK instalado; se não compilar, relatar o erro exato e o que foi testado sem afirmar sucesso.
I. Arquivos documentados (`README.md`, `CHANGELOG.md`, `docs/XTREAM_INTEGRATION.md`, `docs/UI_DESIGN.md`, `TESTE_TCL.md`), com estado atual do projeto e roteiro de testes físicos na TCL/Denon.
J. Criar testes de DTO/parsing Xtream (incluindo `episodes` como mapa de temporada), estado vazio e progresso.

## 9. ORDEM PARA IMPLEMENTAR — MANTER O PROJETO USÁVEL EM TODAS AS ETAPAS

**Marco 1 — AGORA:** inventário do projeto e dependências; design tokens + menu lateral + Home cinematográfica + componentes de card/carrossel + conexão às categorias e filmes existentes. Compilar. **Prioridade para reproduzir fielmente a Home escolhida.**

**Marco 2:** tela de detalhes de filme com metadata Xtream, pôster/backdrop, ações e painel técnico (dados reais ou Não identificado). Compilar.

**Marco 3:** biblioteca de séries, temporadas/episódios, detalhes de série. Compilar.

**Marco 4:** Room, histórico/continuar assistindo, visto/não visto, favoritos, próximo episódio. Compilar.

**Marco 5:** player overlay igual à referência visual, diagnóstico e `MediaInfoScanner` leve para Filmes 4K, sem modificar pipeline comprovado de vídeo/áudio. Compilar.

**Marco 6:** refinamento visual, responsividade, focus restoration, cache/otimização de imagens, testes na TCL com o mesmo conteúdo do TiviMate, documentação final.

No primeiro ciclo, não tente construir todas as funcionalidades de uma vez se comprometer build, qualidade ou estabilidade. Ainda assim, implemente arquivos reais do Marco 1 e me entregue um APK/compilação quando possível.

## 10. DOCUMENTAÇÃO TÉCNICA OFICIAL PARA ANDROID

- Media3/ExoPlayer inicial: https://developer.android.com/media/media3/exoplayer/hello-world
- Media3/ExoPlayer formatos e HDR: https://developer.android.com/media/media3/exoplayer/supported-formats
- Seleção real de faixas: https://developer.android.com/media/media3/exoplayer/track-selection
- Integração do Media3 em TV / foco e controle: https://developer.android.com/media/media3/ui/androidtv
- Layouts/rolagem em TV: https://developer.android.com/training/tv/playback/compose/lists
- Navegação por D-pad: https://developer.android.com/training/tv/get-started/navigation
- Recomendações de controles de vídeo na TV: https://developer.android.com/training/tv/playback/controls
- Layouts legíveis na TV: https://developer.android.com/training/tv/playback/compose/layouts

## 11. RELATÓRIO DE ENTREGA OBRIGATÓRIO

Ao concluir cada marco apresente:

1. Resumo do que foi IMPLEMENTADO e do que ainda não foi.
2. Arquivos criados/alterados (caminhos relativos).
3. Comandos de build/testes que realmente executou, resultado e limitações.
4. APK gerado com caminho real, se existir; não invente APK.
5. Prints de preview/screenshot somente se forem resultados genuínos de app renderizado (nunca confundir a arte de referência com uma captura de implementação).
6. O que devo testar no controle da TCL, no HDR da TV e no painel do Denon.
7. Próximo marco sugerido, mantendo código funcional.

**Comece agora a leitura do código atual e das três referências. Implemente o Marco 1. Não recomece o aplicativo do zero.**
