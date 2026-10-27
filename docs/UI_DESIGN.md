# Le&Sil Stream UI — Marco 1

## Identidade e escopo

A Home segue `LeSil_Codex_Kit/referencias_visuais/01_home_escolhida.png` como referência de hierarquia, espaçamento e paleta. A imagem é apenas uma referência: a interface é composta por Views reais e as artes vêm do catálogo Xtream. Os exemplos fictícios das referências não aparecem no app.

Paleta central deste marco: fundo `#050A11`, superfícies `#0B1724`/`#101D2A`, texto `#F4F7FB`, secundário `#A9BDD0` e ciano `#2EA9FF`. A sidebar e os cartões têm contorno/escala ao receber foco. A animação de foco dura 150 ms.

## Home

- Sidebar fixa: Início, Filmes, Séries, Filmes 4K, Continuar, Favoritos e Configurações.
- Hero escolhe o item com `added` válido mais recente; sem data válida, usa o primeiro item retornado pelo servidor. O critério é determinístico e não representa recomendação.
- `get_vod_info` é consultado apenas para esse item do hero. Sinopse, ano, duração, gênero e backdrop só são mostrados quando o provedor os informa.
- Seções: Filmes 4K, Adicionados recentemente e Séries em destaque. “Continuar assistindo” fica oculta enquanto não existir histórico persistente.
- A categoria 4K é localizada por nome normalizado `Filmes 4K` ou `Filmes UHD`. Nenhum selo técnico é inferido do nome.
- As linhas mostram até 16 cartões e as telas de catálogo renderizam até 80 itens por busca; imagens são carregadas sob demanda e limitadas por cache/memória.

## Navegação por controle remoto

Todos os cartões e ações são focáveis. D-pad move o foco entre sidebar, ações e cartões; OK abre o item/ação; Voltar retorna da lista à categoria e da categoria à Home. Ao sair do player, o app retorna à tela de onde o filme foi iniciado.

## Estados

- Sem imagem: fundo de superfície como placeholder, sem arte fictícia.
- Sem categoria 4K: mensagem de categoria não identificada.
- Sem títulos: estado vazio da fileira/lista.
- Falha inicial de catálogo: estado de erro com retorno ao login.
- Séries são listadas; temporadas e episódios permanecem para o Marco 3.
- Histórico e Favoritos são estados vazios até sua implementação nos Marcos 4.

## Player

O Marco 1 não altera `ExoPlayer`, `PlayerView`, superfície de vídeo, seleção de áudio/legendas nem diagnóstico. O overlay visual dedicado continuará para o Marco 5.
