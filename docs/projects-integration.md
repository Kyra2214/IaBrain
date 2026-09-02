# Integração de Contribuições

A Fase 2 transforma o workspace de Projetos em um fluxo de integração controlado pelo usuário.

## Fluxo

1. A primeira contribuição recebida para um projeto inicializa o workspace local.
2. Novas contribuições ZIP são preservadas em armazenamento privado do aplicativo.
3. O IaBrain compara o workspace atual com a contribuição mais recente por caminho e SHA-256.
4. Cada arquivo é classificado como `NOVO`, `MODIFICADO`, `REMOVIDO` ou `IGUAL`.
5. O usuário escolhe por arquivo entre `ACEITAR`, `MANTER` ou `REMOVER`.
6. As escolhas são aplicadas em um staging separado; o workspace atual só é substituído após o staging terminar.
7. Falhas removem o staging e tentam restaurar o backup do workspace.
8. A integração e a aplicação ficam registradas no histórico local.

## Segurança

- ZIPs continuam sujeitos à validação de caminho.
- Caminhos absolutos, Windows drive paths e `..` são rejeitados.
- Conteúdo recebido é armazenado no diretório privado do app, sem permissões extras.
- Nenhum código recebido é executado automaticamente.
- Não existe merge semântico automático nesta fase.
- Arquivos removidos permanecem no workspace por padrão; o usuário precisa escolher `REMOVER`.

## Limites desta fase

Ainda não fazem parte da Fase 2: GitHub, CI remoto, execução de builds externos, resolução semântica automática de conflitos, E2E e automação de envio para IAs.

O objetivo é estabelecer uma integração local, auditável e reversível antes da futura camada GitHub/CI. O uso de armazenamento interno segue o modelo de arquivos específicos do aplicativo recomendado pela documentação Android. 
