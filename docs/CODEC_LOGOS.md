# Selos de imagem e áudio

Os selos aparecem pequenos, à direita dos metadados do filme, na mesma linha do gênero. A lista muda conforme os dados disponíveis para o título.

## Artes oficiais incluídas

| Asset no app | Origem oficial | Uso |
| --- | --- | --- |
| `dolby_vision_vertical.png` | [Dolby Vision Logos — Dolby Newsroom](https://news.dolby.com/en-WW/assets/228231/) | Dolby Vision |
| `dolby_atmos_vertical.png` | [Dolby Atmos Logos — Dolby Newsroom](https://news.dolby.com/en-WW/assets/228229/) | Dolby Atmos |
| `av1_logo_rgb.png` | [Logos — Alliance for Open Media](https://aomedia.org/resources/logo/) | AV1 |

Os três arquivos são as artes originais sem recoloração ou deformação. Os logos pretos da Dolby ficam sobre uma pequena base clara para manter o contraste sobre o banner; o AV1 usa sua arte colorida oficial.

## Outros formatos

Resolução, HDR10/HDR10+, HLG, HEVC/H.265, H.264/AVC, VP9, DTS:X, DTS-HD, TrueHD, Dolby Digital, AAC, FLAC e canais 5.1/7.1 aparecem como nomes curtos. Isso evita criar ou usar como selo de certificação uma marca cuja arte não está disponível para esse uso. Em particular, o selo HDR10+ é uma marca de certificação; a [página oficial de licenciamento e certificação](https://hdr10plus.org/) distingue o padrão do direito de usar a marca certificada. A [UHD Alliance](https://alliance.experienceuhd.com/logo-usage/) também reserva o logo Ultra HD Premium a conteúdos e produtos que cumpram seus critérios.

## De onde vêm os dados

O app verifica o nome do item e os campos opcionais `video` e `audio` que o provedor Xtream devolver em `movie_data`/`info`. A API Xtream nem sempre entrega esses campos e o catálogo nem sempre identifica codecs no nome; nesses casos, o selo não é exibido. A tela não inspeciona nem analisa o fluxo de vídeo antes da reprodução.
