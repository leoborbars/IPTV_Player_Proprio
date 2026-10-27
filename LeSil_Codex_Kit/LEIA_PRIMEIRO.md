# Como usar este kit com Codex no VS Code

1. Abra o projeto Android **atual e funcional** do Le&Sil Stream/IPTV Player no VS Code. Faça um backup ou commit de segurança antes de mudanças grandes.
2. Copie para a **raiz desse projeto** as pastas `referencias_visuais` e `documentacao`, e o arquivo `PROMPT_CODEX_LESIL.md`. Não substitua outros arquivos do projeto.
3. No painel Codex, use uma nova conversa **com o workspace do seu aplicativo aberto** e envie: 

   > Leia `PROMPT_CODEX_LESIL.md`, as três imagens de `referencias_visuais/` e `documentacao/API_XTREAM_CODES.md`. Faça uma auditoria do projeto atual e implemente o **Marco 1** descrito no prompt (Home cinematográfica escolhida), sem prejudicar a reprodução atual. Compile, corrija erros e detalhe as alterações.

4. Após o Marco 1 e testes de navegação na TCL, solicite os marcos seguintes conforme o prompt. Evita-se mudar player e UI ao mesmo tempo sem validação.
5. As imagens são **referências de design**, não devem ser usadas como "screenshots de fundo" da aplicação. O Codex deve criar uma interface funcional e alimentar os cards com metadados reais do Xtream.
6. Nunca envie usuário/senha reais do Xtream para chats, commits ou screenshots. Informe-os somente no app local para os testes necessários.

Os exemplos de imagens são conceituais e usam títulos fictícios. A documentação da API Xtream é comunitária e pode variar conforme o provedor.
