# IaBrain CI Standard

## Objetivo

O **IaBrain CI Standard** define uma política de validação adaptável por stack. Ele separa regras comuns do projeto de comandos específicos do ambiente. O padrão não é um pipeline Android fixo aplicado a todos os projetos.

```text
IaBrain CI Standard
        ↓
Project CI Profile
        ↓
Stack Adapter
        ↓
Runner local ou CI remoto
```

Um resultado só pode ser marcado como aprovado quando a verificação correspondente foi realmente executada.

## Níveis de execução

| Nível | Responsabilidade | Resultado padrão sem execução |
|---|---|---|
| Local | Análise determinística sem depender de servidor | `NÃO EXECUTADO` |
| Ambiente | Ferramentas como Gradle, Node, Python, banco ou emulator | `NÃO EXECUTADO` |
| Remoto | GitHub Actions, OAuth, APIs, cloud e serviços hospedados | `NÃO VALIDADO LOCALMENTE` |

A classificação do nível é parte do resultado. Ela não deve ser inferida somente pela existência de um comando configurado.

## Validações comuns

Todo perfil deve considerar estrutura de arquivos, duplicidade de caminhos, documentação, testes disponíveis, referências, configuração, dependências, segurança básica e `diff check`. O adaptador deve indicar quais itens foram executados, quais falharam e quais não se aplicam.

| Grupo | Critério de aprovação |
|---|---|
| Estrutura | Não há caminhos duplicados ou pacote inválido |
| Documentação | A documentação obrigatória do perfil está presente |
| Testes | Os testes recomendados executados passaram |
| Configuração | Manifests e arquivos de configuração são consistentes |
| Dependências | Não há conflito ou vulnerabilidade bloqueante identificada |
| Segurança | Scripts, workflows e arquivos sensíveis foram revisados |
| Diff | A diferença é limpa e compatível com as regras do projeto |
| Ambiente | Build, lint e análise estática executados no runner adequado |
| Remoto | CI externo concluído e resultado consultado |

## Perfis atuais

O código mantém perfis iniciais para Android/Kotlin, React/TypeScript e Python. Outros stacks podem ser adicionados sem alterar o padrão comum.

| Perfil | Build | Lint | Análise estática | Testes recomendados |
|---|---|---|---|---|
| Android/Kotlin | `./gradlew assembleDebug` | `./gradlew lint` | `./gradlew detekt` | Unit tests, compile debug, instrumentação |
| React/TypeScript | `npm run build` | `npm run lint` | `npm run typecheck` | Unit tests e typecheck |
| Python | `python -m build` | `ruff check` | `mypy` | `pytest` e type checking |

Esses comandos são referências de perfil. O IaBrain não declara que foram executados apenas porque estão cadastrados.

## Estado e relatório

Cada item de validação possui nome, nível, status e detalhes. Os estados válidos são `OK`, `PENDENTE`, `FALHA`, `NÃO EXECUTADO`, `NÃO VERIFICADO` e `DEPENDE DE AMBIENTE EXTERNO`.

Um projeto é aprovado localmente somente quando todos os itens não remotos estão em `OK`. Um resultado remoto pendente impede a afirmação de aprovação de CI.

Exemplo de relatório:

```text
VALIDAÇÃO DO PROJETO
✓ Estrutura de arquivos ........ OK
⚠ Documentação ................. PENDENTE
⚠ Testes disponíveis ........... PENDENTE
○ Diff check .................... NÃO EXECUTADO
○ Build de ambiente ............. NÃO EXECUTADO
○ CI remoto ..................... DEPENDE DE AMBIENTE EXTERNO

RESULTADO
Projeto parcialmente validado.
CI remoto não aprovado.
```

## Segurança do runner

Nenhum runner deve executar código recebido de uma IA sem revisão. Antes da execução, o adaptador deve sinalizar scripts, comandos shell, Dockerfiles, workflows, mudanças em dependências, permissões, acesso a rede e arquivos sensíveis.

A validação deve ocorrer em workspace isolado quando o runner permitir. O usuário deve conseguir revisar a contribuição e os conflitos antes de qualquer execução com efeitos externos.

## Android e stacks arbitrárias

O APK pode fazer análises internas e persistir o relatório. Ele não deve prometer que consegue compilar qualquer stack. Para Android/Kotlin, o ambiente local pode executar Gradle quando o runner estiver disponível. Para stacks que não possuem runner local, o resultado deve permanecer como não executado até uma validação externa.

## GitHub e CI remoto

GitHub é opcional. A ausência de credencial ou conexão não reduz o projeto a um estado inválido. O fluxo remoto recomendado é validar localmente, criar branch, registrar commit, enviar para uma branch de trabalho, aguardar CI, consultar o resultado, preparar PR e exigir aprovação humana antes do merge.

O IaBrain não envia diretamente para `main` e não trata um commit local como CI aprovado. Uma futura integração deve usar autenticação segura e permissões mínimas.

## Critérios de conclusão

Um projeto pode ser considerado **localmente aprovado** quando as validações locais e de ambiente aplicáveis foram executadas e passaram, não há conflitos pendentes e a documentação obrigatória está presente.

Um projeto pode ser considerado **remotamente aprovado** somente quando o CI externo correspondente foi executado, o resultado foi consultado e não há pendências de auditoria. Sem essa evidência, o resultado correto é **NÃO VALIDADO LOCALMENTE**.

## Referências

[1]: projects.md "Projetos — workspace local do IaBrain"
[2]: design-system.md "Design System IaBrain"
[3]: ROADMAP.md "Roadmap do IaBrain"
