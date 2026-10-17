# Integração Xtream Codes — LunaStream

## Endpoints usados na Home

- `player_api.php` sem `action`: autenticação existente.
- `action=get_vod_categories`: nomes e IDs das categorias de filmes.
- `action=get_vod_streams`: catálogo de filmes; a data `added`, arte e rating são opcionais.
- `action=get_vod_streams&category_id=...`: carregamento de uma categoria quando a listagem completa não contém aquele grupo.
- `action=get_vod_info&vod_id=...`: consulta sob demanda somente para o filme escolhido como hero.
- `action=get_series_categories` e `action=get_series`: categorias e cartões da fileira de séries; temporadas/episódios ainda não são consultados.

## Mapeamento

`CatalogEntry` mantém ID, título, extensão, URL HTTP(S) do pôster, ID da categoria, `added` e rating. `SeriesEntry` mantém ID, título, capa, categoria, data e rating. `MovieDetails` contém apenas campos apresentados pelo endpoint de detalhes: título, imagens, sinopse, gênero, ano, duração e rating.

IDs e datas numéricos aceitam string ou número JSON. `added` numérico em segundos é convertido para milissegundos; timestamps em milissegundos e ISO-8601 são aceitos. Datas ausentes ficam nulas; a ordem de chegada do servidor é preservada para o fallback do hero e da fileira recente.

O nome da categoria para “Filmes 4K” é normalizado quanto a caixa, acentos e espaços, aceitando somente `Filmes 4K` ou `Filmes UHD`. O rótulo comercial não é tratado como confirmação de resolução ou HDR.

## Segurança e limites

- Operações Xtream acontecem no executor de rede já usado pela Activity.
- URLs de arte aceitam apenas esquemas HTTP/HTTPS. A imagem é carregada em executor separado, com timeout, limite de bytes e cache LRU.
- As mensagens apresentadas não incluem URL, payload ou credenciais.
- Não são feitas consultas de detalhes para cada item da Home, sondagens de mídia, inferência por título, conexão com TMDB ou uso de `direct_source`.
- A listagem completa depende do comportamento do provedor Xtream; a interface limita os cartões renderizados e as imagens simultâneas.
- O acesso a filmes continua usando o cliente e a URL de mídia existentes. Séries ainda não iniciam reprodução nesta etapa.
