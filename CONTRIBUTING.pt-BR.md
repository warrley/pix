# Regras de Contribuição

Este é apenas o guia de convenções da nossa equipe para que não quebremos o código e os padrões estabelecidos.

## Branches
- Nunca faça push direto na branch `main`.
- Crie uma nova branch para a sua tarefa. 
- Nomeie a branch como `feat/sua-funcionalidade` ou `fix/o-que-voce-corrigiu`.

## Commits
Estamos utilizando o padrão básico de [conventional commits](https://www.conventionalcommits.org/en/v1.0.0/). Mantenha simples:
`tipo(escopo): mensagem`

O `(escopo)` ajuda a mostrar qual parte do projeto você alterou, como `(frontend)` ou `(backend)`.

Principais tipos que você pode usar:
- `feat`: para novas funcionalidades
- `fix`: para correção de bugs
- `docs`: para alterações na documentação/texto
- `chore`: para configurações ou tarefas de manutenção

Exemplo:
`feat(frontend): criar pagina de login`

## Pull Requests
1. Faça push da sua branch.
2. Abra um PR para a branch `main`.
3. Peça a alguém do grupo para revisar e aprovar.
4. Faça o merge!
