# Xtream Codes Player API — documentação de apoio ao projeto LunaStream

**Escopo:** apenas filmes e séries VOD. Os endpoints abaixo são padrões comuns encontrados em implementações Xtream Codes/XUI, documentados principalmente por projetos da comunidade (não há garantia de que todos existam ou tenham respostas idênticas no seu provedor). Validar com o próprio serviço autorizado, com credenciais configuradas localmente no aplicativo e **sem registrar URLs secretas em logs**.

## 1. Entrada da API e autenticação

Base: `{BASE_URL}/player_api.php`

Autenticação:
`GET {BASE_URL}/player_api.php?username={USER}&password={PASS}`

Campos comuns no retorno inicial: `user_info.auth`, `user_info.status`, `user_info.max_connections`, `user_info.allowed_output_formats`, `server_info.url`, `server_info.port`, `server_info.server_protocol`. Alguns servidores retornam esses campos como string, não como número.

**Segurança:** credenciais em query string e URLs de vídeo são sensíveis. Não logar URLs, payloads inteiros nem usar screenshot do retorno não sanitizado. Priorizar HTTPS, quando o servidor permitir. Evitar encaminhar `Authorization`/credenciais para hosts de redirecionamento desconhecidos. HTTP legado requer aviso de risco.

## 2. Endpoints de filmes

| Ação | Parâmetros adicionais | Retorno típico |
|---|---|---|
| `get_vod_categories` | nenhum | array com `category_id`, `category_name`, `parent_id` |
| `get_vod_streams` | `category_id` opcional | array `stream_id`, `name`, `stream_icon`, `category_id`, `container_extension`, `added`, `rating`, `direct_source` |
| `get_vod_info` | `vod_id={stream_id}` | objeto `info` + `movie_data` |

`get_vod_info.info` pode incluir `movie_image`, `backdrop_path` (array, texto ou ausente), `plot`, `genre`, `cast`, `director`, `releasedate`, `duration_secs`, `duration`, `rating`, `tmdb_id`, `youtube_trailer`. Às vezes tem dados `video`/`audio`, porém não é obrigatório nem prova a saída real de áudio/HDR.

## 3. Endpoints de séries

| Ação | Parâmetros adicionais | Retorno típico |
|---|---|---|
| `get_series_categories` | nenhum | categorias de série |
| `get_series` | `category_id` opcional | array com `series_id`, `name`, `cover`, `plot`, `backdrop_path`, `genre`, `rating`, etc. |
| `get_series_info` | `series_id={series_id}` | `info`, `seasons[]`, `episodes{}` |

Exemplo simples: `episodes` pode ser um MAPA de listas indexadas por temporada, como `{"1":[{"id":"9876","episode_num":1,"title":"Ep. 1","container_extension":"mkv"}]}`. Não trate como lista plana. O ID do episódio é diferente do ID da série.

## 4. Playback (padrões, não universais)

Filme: `{BASE_URL}/movie/{USER}/{PASS}/{stream_id}.{ext}`

Episódio: `{BASE_URL}/series/{USER}/{PASS}/{episode_id}.{ext}`

**Não há garantia** de formato único: respeitar `container_extension`, `direct_source` confiável e comportamento do provedor; `mp4`, `mkv`, `ts`, `m3u8` e suas capacidades reais dependem de servidor e player. A presença de `direct_source` exige validação e saneamento de URL, não conexão indiscriminada a hosts arbitrários.

## 5. Mapeamento sugerido para o app

- `Category(categoryId, name, type)` — `type` é `MOVIE` ou `SERIES`.
- `Movie(streamId, title, posterUrl?, categoryId?, extension?, addedAt?, providerRating?)`.
- `MovieDetails(movieId, plot?, backdropUrl?, cast?, director?, durationSeconds?, year?, tmdbId?, trailers?)`.
- `Series(seriesId, title, coverUrl?, categoryId?, plot?, ...)`.
- `Season(number, name?, episodes)`.
- `Episode(episodeId, seasonNumber, episodeNumber, title, extension?, durationSeconds?, posterUrl?, plot?)`.
- `TechnicalInfo(videoCodec?, width?, height?, fps?, hdr?, audioTracks[], subtitleTracks[], source: XTREAM|PROBED, checkedAt?)`.

DTOs resilientes para `null`, string/number, datas incoerentes, arrays vazios e campos inconsistentes. Campos ausentes não autorizam inventar valores. "Filmes 4K" é um nome de categoria comercial e por si só não prova resolução 4K.

## 6. Estratégia eficiente de carregamento

- Primeiro categorias e lista curta/cacheada de filmes e séries.
- Buscar detalhes **on demand** por item; não consultar centenas de `get_vod_info` na Home simultaneamente.
- Miniaturas carregadas com cache, tamanho adequado e LazyRow/LazyColumn.
- Atualizações controladas e tratamento de perda de rede.
- Examinar stream sob demanda, após checar metadados já existentes. Sondagens Media3 podem abrir conexão e consumir o limite de simultâneas do provedor.
- Guardar metadados/cache/progresso no Room. Progresso individual de cada episódio com ID único e provedor.

## 7. Fontes da comunidade para Xtream

1. Visão geral do Player API: https://github.com/worldofiptvcom/xtream-codes-api/blob/master/docs/player-api/overview.md
2. Referência rápida de endpoints: https://github.com/worldofiptvcom/xtream-codes-api-documentation/blob/master/ENDPOINT_REFERENCE.md
3. Documentação com exemplos JSON de filmes e séries: https://github.com/worldofiptvcom/xtream-codes-api-documentation/blob/master/XTREAM_CODES_API_DOCUMENTATION.md
4. Cliente em Go para comparação de ações: https://github.com/tellytv/go.xtream-codes/blob/master/xtream-codes.go

## 8. Fontes oficiais complementares

- ExoPlayer: https://developer.android.com/media/media3/exoplayer/hello-world
- Formatos e HDR: https://developer.android.com/media/media3/exoplayer/supported-formats
- Seleção de trilhas: https://developer.android.com/media/media3/exoplayer/track-selection
- Player e controle remoto na TV: https://developer.android.com/media/media3/ui/androidtv
- Navegação com D-pad: https://developer.android.com/training/tv/get-started/navigation
- Catálogos na TV: https://developer.android.com/training/tv/playback/compose/lists
- TMDB documentação: https://developer.themoviedb.org/docs/getting-started
- TMDB termos e atribuição: https://developer.themoviedb.org/docs/faq

## 9. Diagnóstico de formatos

Sempre diferenciar `formato descrito pelo servidor` → `faixa detectada pelo Media3` → `modo HDR efetivamente ativado na TCL` → `formato realmente recebido pelo Denon AVR-S920W`. Essa última confirmação depende do hardware. HDMI ARC convencional não transporta Dolby TrueHD nem DTS-HD MA lossless. Não apresentar formato suposto como confirmado.
