# Teste na TCL e no Denon

Este roteiro valida o player em equipamento real. O diagnóstico do app descreve o stream e o decoder; confirme separadamente o modo de imagem na TCL e o formato de entrada no Denon.

## Preparação

1. Na TCL, habilite Opções do desenvolvedor e Depuração USB ou Depuração sem fio.
2. Conecte o computador e a TV à mesma rede quando usar ADB sem fio. Confirme a chave mostrada pela TV.
3. Confirme que a TV está ligada à entrada HDMI ARC do Denon e que ARC/HDMI Control está habilitado nos dois equipamentos.
4. Na TCL, anote a configuração de saída digital disponível (por exemplo, Auto ou Pass Through). No Denon, use HDMI MONITOR 1 (ARC).
5. Escolha somente VOD autorizado. Antes de registrar qualquer resultado, oculte URL, usuário, senha e outros dados da conta.

## Instalar o APK

No terminal do VS Code, na raiz do projeto:

```powershell
adb devices
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
```

O primeiro comando deve mostrar a TV como `device`. Se mostrar `unauthorized`, confirme a autorização na tela da TV.

## Validar a Home — Le&Sil Stream

1. Abra **Le&Sil Stream** e conecte ao catálogo Xtream autorizado. A Home deve mostrar apenas títulos/artes retornados pelo provedor; sem backdrop, deve usar o pôster ou placeholder.
2. Navegue por todos os itens da sidebar com D-pad, OK e Voltar. Confirme foco visível e que a página rola até todas as fileiras.
3. Nas fileiras de filmes e séries, mova o foco horizontalmente; o cartão em foco deve ficar evidente e rolar para dentro da tela.
4. Abra **Filmes**, selecione uma categoria real e pesquise um título. Confirme que a lista apresenta dados do catálogo e que OK abre as informações disponíveis.
5. Confira **Filmes 4K**: ela só deve listar itens quando o provedor retorna uma categoria com nome “Filmes 4K” ou “Filmes UHD”. O nome da categoria não prova resolução 4K.
6. Abra **Assistir** em um filme da Home e em um card. Confirme que o player existente inicia e que Voltar/Back retorna ao local de origem.
7. Sem progresso salvo, a seção “Continuar assistindo” deve ficar oculta. Séries aparecem como catálogo; episódios ainda não são reproduzidos neste marco.

Se não houver conexão ao provedor, a Home deve exibir uma mensagem de erro e oferecer retorno à tela de login. Não envie credenciais em capturas ou logs.

## Como registrar cada cenário

Use o CSV `testes_4k_audio.csv` como índice. Para cada cenário, registre:

- Título genérico ou ID redigido. Não inclua URL nem credenciais.
- Dados vistos no app: estado, codec, resolução, FPS, HDR sinalizado, decoder, faixa de áudio/legenda, buffering, frames descartados e erro seguro.
- Indicador real da TCL: SDR, HDR10/HDR10+, HLG ou Dolby Vision, conforme exibido pela TV.
- Formato de entrada mostrado em Denon → INFO. Registre também quando a tela indicar PCM ou formato desconhecido.
- Resultado observado, falha reproduzível e próximo passo.

Use `N/D` quando uma informação não estiver disponível. Não deduza HDR ativo na TV nem formato recebido pelo receiver a partir dos dados do app.

## Sequência de validação

1. **SDR 1080p com AAC:** confirme início, imagem, som, pausa, retomada e navegação por D-pad/OK/Voltar.
2. **HEVC 4K HDR10 com E-AC-3 5.1:** registre resolução e PQ/ST2084 no app, modo de imagem da TCL e entrada do Denon.
3. **Dolby Vision 4K:** confirme se a TCL indica Dolby Vision. O app só pode registrar a sinalização reportada pelo stream.
4. **Dolby Digital 5.1 e Dolby Digital Plus 5.1:** teste cada faixa separadamente e registre o formato de entrada indicado pelo Denon.
5. **Dolby TrueHD/Atmos lossless:** registre a faixa e o comportamento. ARC convencional não transporta TrueHD lossless; se houver faixa alternativa, selecione-a e repita.
6. **DTS 5.1 e DTS-HD MA:** teste separadamente, se disponíveis. Registre a faixa selecionada e o formato que o Denon efetivamente informa.
7. **Controles e estabilidade:** pause/retome, avance para 10 e 30 minutos, troque áudio e legenda, desligue legenda, saia e reabra o filme. Se viável, interrompa a rede por alguns segundos e registre a recuperação ou erro.
8. **Comparação entre players:** use o mesmo conteúdo, faixa de áudio/legenda e configurações de saída no Le&Sil Stream, TiviMate e XCIPTV. Anote somente observações reproduzíveis; não atribua diferenças a recompressão sem evidência.

## Relato de falha

Para cada falha, anote o cenário, a etapa, o resultado esperado, o resultado observado, se ocorreu novamente e o próximo passo. Remova dados da conta e URLs antes de compartilhar capturas ou logs. A execução física e a leitura dos indicadores da TCL/Denon precisam ser feitas no próprio equipamento.
