# PROMPT PARA CODEX — CONTINUAÇÃO DO PROJETO IPTV PLAYER

Atue como desenvolvedor Android sênior especializado em Google TV / Android TV, Kotlin, AndroidX Media3 / ExoPlayer, reprodução de vídeo HDR, áudio multicanal e integração com APIs Xtream Codes.

## 1. Contexto e objetivo

Estou desenvolvendo um aplicativo IPTV Player **para uso pessoal**, instalado diretamente em uma **TCL 65C7K (65", Google TV)**, conectada por **HDMI ARC** a um **receiver Denon AVR-S920W**. O provedor fornece acesso por **Xtream Codes (URL do servidor, usuário e senha)** e catálogo de filmes e séries sob minha autorização.

**Problema:** o TiviMate reproduz alguns conteúdos com qualidade de imagem/áudio superior à percebida no XCIPTV. Não quero melhorar artificialmente o sinal; quero evitar que o player **limite ou altere** a qualidade original. O aplicativo deve preservar o stream e reproduzi-lo no melhor formato realmente suportado pela TV, Android e caminho de áudio. O foco é exclusivamente **VOD: filmes e séries**. Não desenvolver canais ao vivo, EPG, gravação, catch-up ou serviços de retransmissão.

## 2. Equipamento e restrições de fidelidade

- TV: TCL 65C7K com Google TV e painel 4K, a ser testada com HEVC/H.265, HDR10, HDR10+, HLG e Dolby Vision conforme os perfis efetivamente suportados por firmware/MediaCodec.
- Áudio: o app roda **dentro da TV**; a saída da TV segue por **HDMI ARC** ao Denon AVR-S920W. ARC convencional não transporta Dolby TrueHD ou DTS-HD MA lossless. Dolby Digital 5.1, Dolby Digital Plus e certos formatos DTS dependem de passthrough/firmware/configuração. Não prometer TrueHD, DTS-HD MA, DTS:X ou Atmos lossless nesse caminho.
- **Não transcodificar** nem reencodar vídeo ou áudio. Evitar conversão HDR → SDR ou downmix arbitrário. Usar decodificação por hardware sempre que compatível; documentar incompatibilidades honestamente.
- Separar rigorosamente: **(a) metadados/formato do arquivo/stream**, **(b) decoder efetivamente utilizado**, **(c) modo HDR efetivamente ativado na TCL**, e **(d) codec/canais efetivamente recebidos pelo Denon**. O aplicativo pode medir (a) e parte de (b), mas não deve afirmar (c) ou (d) sem evidência do dispositivo; pedir confirmação manual na TV/receiver.

## 3. Código-base existente — NÃO COMEÇAR DO ZERO

Existe um protótipo chamado `IPTV_Player_P0_fontes.zip`. Use como ponto de partida. Caso o ZIP esteja no workspace, extraia-o e trabalhe em `IPTV_Player_P0/`. Se o projeto já estiver extraído, preserve e evolua o conteúdo; não recrie outro app paralelo. Se o arquivo não existir no ambiente, registre exatamente qual insumo está faltando.

Estrutura atual conhecida:

```text
IPTV_Player_P0/
├── README.md
├── testes_4k_audio.csv
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradle/wrapper/gradle-wrapper.properties
└── app/
    ├── build.gradle.kts
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/br/com/iptvlab/
        │   ├── MainActivity.kt
        │   └── XtreamClient.kt
        └── res/drawable/tv_banner.png
```

O P0 já tenta: autenticar na API Xtream (`player_api.php`), listar categorias de filmes (`get_vod_categories`), listar filmes (`get_vod_streams`), construir URLs `/movie/usuario/senha/id.extensao`, reproduzir com Media3/ExoPlayer e `PlayerView`, selecionar áudio/legendas e exibir diagnóstico de codec, resolução, FPS, HDR sinalizado, decoder, buffering e quadros perdidos.

**Importante:** o pacote anterior é experimental, **não foi compilado nem testado fisicamente na TCL**. Falta `gradlew`, `gradlew.bat` e `gradle/wrapper/gradle-wrapper.jar`; há apenas o `gradle-wrapper.properties`. Os arquivos citam Android Gradle Plugin 8.13.2, Gradle 8.13, Kotlin 2.2.0, compileSdk 36, targetSdk 35, minSdk 26 e Media3 1.11.1. Verifique se a combinação é real, disponível e compatível no ambiente; se algum número/API não existir ou for incompatível, ajuste para uma combinação estável e documente a troca. Não invente dependências nem APIs.

## 4. Sua missão agora — PRIMEIRO MARCO (P0 compilável + reprodução confiável)

Implemente integralmente o melhor P0 **executável e instalável** possível antes de iniciar a interface completa de séries ou histórico de reprodução.

### A. Auditoria e compilação

1. Leia todos os arquivos e descreva brevemente o que já funciona em código e os defeitos concretos que encontrou.
2. Corrija erros de Gradle, Kotlin, imports e APIs Media3. Gere/adapte **Gradle Wrapper completo**, quando as ferramentas permitirem, sem colocar binários fictícios no repositório. Se não houver SDK/JDK/rede, execute as verificações possíveis e informe objetivamente os bloqueios.
3. Garanta que o app possa aparecer na launcher do Google TV, seja controlável por D-pad/OK/Voltar e apresente foco visual, sem depender de touchscreen.
4. Gere `app-debug.apk` se houver toolchain disponível. Nunca declare uma compilação bem-sucedida sem ter executado o build.

### B. API Xtream Codes

1. Manter login por URL base, usuário e senha, com autenticação em `player_api.php` e suporte a catálogo VOD (`get_vod_categories`, `get_vod_streams`). Não incluir canais ao vivo.
2. Tratar URL com porta, barra final, JSON inconsistente, catálogos vazios, falhas HTTP, timeout, servidor indisponível e extensões/contêineres diferentes sem travamento.
3. Evitar trabalho de rede na thread principal. Encerrar/cancelar operações adequadamente ao sair da Activity. Não renderizar centenas de botões simultâneos para catálogos enormes: prefira listagem eficiente ou limitação/paginação acompanhada de pesquisa.
4. Tratar redirecionamentos de mídia de forma segura e compatível quando houver, sem repassar credenciais arbitrariamente para outros hosts. Não supor que todo provedor retorna exatamente o mesmo JSON ou formato de URL.
5. **Nunca colocar credenciais nos fontes, screenshots, testes, logs ou exceções.** Xtream frequentemente usa credenciais nas URLs; sanitizar URLs e mensagens de erro. Preferir HTTPS; se HTTP legado for permitido para teste, alertar claramente e restringir a exceção tanto quanto viável.
6. Não contornar DRM, bloqueios de assinatura, autenticação nem acesso a conteúdos não autorizados.

### C. Reprodução de vídeo e áudio

1. Usar **AndroidX Media3 / ExoPlayer** com renderização em `SurfaceView` (e não `TextureView`, salvo justificativa documentada), tamanho/aspect ratio correto e decoder por hardware quando possível.
2. Suportar progressivamente MP4, MKV, HLS/M3U8 e outros contêineres realmente atendidos pelo Android/Media3; tratar formato não suportado sem crash. Não afirmar suporte genérico a qualquer codec, perfil Dolby Vision ou arquivo MKV apenas pela extensão.
3. Identificar vídeo HEVC/H.265, AVC/H.264, resolução, FPS, transferência HDR PQ/ST2084, HLG, Dolby Vision sinalizado, color depth quando informado, decoder, bufferings, drops e erros.
4. Implementar seleção de faixa de áudio e legenda, com opção de desligar legendas. Tornar as ações navegáveis com controle remoto e manter a escolha somente quando tecnicamente possível.
5. Não forçar downmix ou áudio estéreo sem necessidade. Respeitar a negociação de formato do Android/TV/ARC. Se o formato não for suportado, oferecer informação clara e, quando possível, escolha de outra trilha compatível — **sem conversão lossless fictícia**.
6. Tratar play/pause, seek, Voltar, rotação fixa para TV se necessário, foreground/background, liberação de player/recursos, perda de rede e reconexão/retry controlado.
7. Não diminuir a qualidade de vídeo para fazer o stream "funcionar" sem indicação ao usuário. Buffering/adaptação HLS dependem das variantes disponibilizadas pelo provedor; se houver seleção de qualidade, indicar a variante ativa e permitir seleção quando viável.

### D. Painel diagnóstico do P0

Exibir opcionalmente, em overlay discreto que pode ser desligado pelo controle:

- Título e ID do item (sem URL contendo credenciais).
- Estado: carregando, pronto, reproduzindo, pausado, bufferizando, encerrado ou erro.
- Contêiner/protocolo quando confiável; codec de vídeo e `codecs` string; resolução; FPS.
- HDR **sinalizado**: SDR, PQ/ST2084, HLG ou Dolby Vision, indicando que HDR10+ dinâmico não pode ser comprovado apenas por PQ.
- Profundidade de bits somente se reportada (usar N/D caso contrário).
- Nome de decoder de vídeo e áudio quando disponível.
- Codec de áudio, quantidade de canais, taxa de amostragem e faixa selecionada.
- Contador de dropped frames, eventos de buffering, posição/duração e código de erro seguro.
- **Aviso fixo:** "Informações do stream/decoder; confirme o HDR na TCL e o sinal de entrada no Denon".

Se algum dado não puder ser obtido com segurança pela API, mostrar **N/D**; não deduzir formato de saída HDMI a partir do codec da faixa.

### E. Critérios de aceite e testes

Preserve e melhore `testes_4k_audio.csv` ou converta também para Markdown. Prever pelo menos:

1. SDR 1080p com AAC (baseline).
2. HEVC 3840×2160 HDR10, E-AC-3 5.1.
3. 4K Dolby Vision em perfil compatível, comparando indicador da TCL.
4. Dolby Digital 5.1 e Dolby Digital Plus 5.1 no Denon (verificar manualmente o formato de **entrada**, não apenas modo de som).
5. Dolby TrueHD/Atmos lossless e DTS-HD MA: documentar a limitação do ARC; testar seleção de trilha alternativa quando disponível.
6. DTS 5.1 convencional se houver conteúdo de teste apropriado.
7. Pausar, retomar, buscar 10/30 minutos, trocar áudio/legenda, sair/voltar e perda temporária de rede.
8. Comparar **o mesmo filme, mesma faixa e configurações** no TiviMate, XCIPTV e app próprio, registrando o que foi observado sem atribuir diferenças automaticamente a "compressão" do XCIPTV.

Criar um checklist para cada teste contendo: título genérico/ID redigido, dados do app, indicador real da TCL, sinal real mostrado no Denon, resultado, falha e próximos passos. A execução de testes físicos será feita por mim; não alegue ter testado os aparelhos remotamente.

## 5. Arquitetura e próximos marcos — NÃO desviar foco do P0

Organize o código para evoluir sem reescrever tudo (por exemplo: `data/xtream`, `player`, `ui`, `diagnostics`, `model`), mas evite arquitetura excessivamente complexa para um protótipo. Manter código Kotlin legível, pequenos métodos, null safety, uso correto do lifecycle e tratamento de erros.

Depois que o P0 estiver compilando e apto a teste, documente o backlog:

- **P1 — Séries e VOD:** endpoints `get_series_categories`, `get_series` e `get_series_info` quando fornecidos; temporadas, episódios, capas e busca.
- **P2 — Histórico local robusto:** Room; progresso por filme e episódio em milissegundos; checkpoint periódico e em pause/stop; Continue Assistindo; visto/não visto; próximo episódio; faixas favoritas; persistência independente do catálogo remoto; tratamento de IDs alterados; exportação/backup futuramente.
- **P3 — UX Google TV:** interface otimizada para D-pad, capas, paginação, filtros e navegação rápida.

**Não implemente P1–P3 agora se isso atrasar a validação de 4K/HDR/áudio.** Não criar backend, serviços externos, transcoding, anúncios ou monetização.

## 6. Como trabalhar e o que entregar

- Leia o projeto primeiro e apresente um plano curto; depois **modifique os arquivos diretamente** no workspace. Não responda somente com recomendações ou pseudocódigo.
- Use ferramentas de busca/documentação, se disponíveis, para confirmar compatibilidade de Android Gradle Plugin, Gradle, Kotlin, Media3 e APIs de diagnóstico antes de ajustar dependências.
- Compile e execute testes automatizados locais quando o ambiente permitir; crie testes unitários usando respostas fictícias da API Xtream. Não acesse meu provedor real sem que eu informe as credenciais em ambiente local apropriado.
- Sempre que alterar arquivo, mantenha README sincronizado. Crie `CHANGELOG.md` e `TESTE_NA_TCL.md` com instruções Windows/Android Studio/ADB, preparação HDMI ARC, configurações de áudio da TV/Denon e comparação com TiviMate.
- Ao final, informe: **arquivos alterados**, **build/testes realmente executados e seus resultados**, **APK produzido e caminho exato (se houver)**, **limitações conhecidas**, e **o que preciso testar na TCL**.
- Se ocorrer bloqueio de compilação, corrija o que for possível e forneça o erro específico e os comandos necessários, sem afirmar que "está pronto".
- Pergunte apenas pelo dado que realmente bloquear uma próxima ação. Nunca solicite que eu divulgue senha Xtream no chat ou comite credenciais no repositório.

**Comece agora pela auditoria do ZIP/projeto e avance até ter o P0 mais confiável e compilável possível, priorizando reprodução fiel do stream e diagnóstico realista de 4K/HDR/áudio via ARC.**
