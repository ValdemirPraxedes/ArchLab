# ArchLab — Escopo da v0.3 (novo planejamento)

> **Status: proposto para revisão e aprovação.** Este documento define apenas o **limite de escopo** da v0.3 reiniciada. O catálogo de novos componentes, suas ligações, propriedades e comportamentos ainda serão definidos e aprovados separadamente. Não autoriza implementação.

## 1. Objetivo

Evoluir o ArchLab **a partir da experiência funcional e visual da v0.2**, ampliando a quantidade de **tipos de componentes** disponíveis e as **conexões entre eles**, inclusive com os três tipos já existentes: **Client, API e Database**.

A v0.3 deve acrescentar **somente** os comportamentos e requisitos necessários para que os componentes novos e suas conexões sejam configurados e simulados corretamente.

**Regra central de escopo:** toda alteração funcional da v0.3 deve estar diretamente ligada a um componente novo, à conexão dele com outros componentes ou à integração desses elementos com Client, API e Database. Sem essa justificativa, a alteração não pertence à v0.3.

## 2. Base obrigatória: preservar a v0.2

A v0.2 é a referência para os comportamentos existentes, incluindo:

- arrastar componentes da paleta para o canvas;
- selecionar e mover componentes com fluidez;
- criar, visualizar e remover conexões;
- manter as conexões acompanhando o movimento dos componentes;
- editar propriedades existentes;
- excluir componentes e conexões;
- configurar, executar e consultar simulações com a experiência já aprovada;
- preservar a organização visual, os temas e a navegação já existentes.

A inclusão dos novos elementos **não autoriza redesenhar o editor**, alterar gestos existentes ou introduzir etapas adicionais no fluxo básico. Ajustes pontuais para tornar visível ou configurável um componente novo são permitidos, mas devem **preservar os comportamentos existentes** e ser justificados pelos requisitos aprovados.

A compatibilidade funcional dos cenários e das APIs v1/v2 deve ser mantida.

## 3. Dentro do escopo

### 3.1 Novos tipos de componentes

- Definir e implementar apenas os **tipos novos explicitamente aprovados**.
- Fornecer para cada um representação no catálogo/canvas, identificação, propriedades mínimas e comportamento de simulação necessário.
- Não considerar um componente entregue apenas porque aparece como ícone: ele precisa ter utilidade e comportamento verificáveis.

**Nota:** nenhum nome de novo componente é aprovado por este documento; a seleção ocorrerá na etapa de catálogo.

### 3.2 Conexões dos novos componentes

- Definir que tipos de componentes podem se conectar e em quais direções.
- Permitir conexões válidas **entre os novos componentes** e **entre novos componentes e Client/API/Database**, quando fizerem sentido para os comportamentos aprovados.
- Definir a semântica mínima de cada nova conexão e os efeitos de simulação necessários.
- Manter as interações visuais de criação, edição e remoção das conexões tão simples e consistentes quanto na v0.2.

### 3.3 Requisitos estritamente necessários ao funcionamento

Entram apenas propriedades, regras de validação, adaptações do modelo e do motor, eventos, resultados ou ajustes de interface **indispensáveis** para configurar, ligar, executar e observar os novos componentes.

Essas alterações devem ser **incrementais e localizadas**, reutilizando o comportamento existente sempre que possível. Complexidade interna não deve ser exposta ao usuário sem necessidade comprovada.

### 3.4 Integração com os três tipos atuais

Client, API e Database continuam disponíveis e operacionais. Poderão receber os **ajustes mínimos necessários** para conectar ou interagir com tipos novos, sem regressão no comportamento já aprovado.

## 4. Fora do escopo

Não implementar nesta v0.3:

- Undo/Redo (desfazer/refazer) ou novos comandos genéricos de edição não solicitados;
- reformulação visual completa, novo modo de editor ou mudança dos gestos já aprovados;
- editor genérico de workflows/ExecutionPlan e novas abstrações expostas ao usuário sem necessidade de um componente aprovado;
- funcionalidades de gerenciamento de projetos, autenticação, persistência, colaboração ou outras expansões independentes do catálogo;
- recursos de simulação, métricas ou controles não exigidos pelo funcionamento e observação dos componentes novos;
- componentes extras incluídos por conveniência técnica, sem aprovação;
- refatorações amplas, migrações de stack ou reescritas sem necessidade demonstrável para os objetivos desta versão.

**Exceção técnica limitada:** correções internas essenciais para suportar os novos componentes são permitidas se não alterarem a experiência existente, não adicionarem funcionalidades independentes e tiverem justificativa e testes de regressão.

## 5. Critério obrigatório de admissão de requisitos

Cada requisito novo deve responder explicitamente:

1. **Qual componente novo** (ou qual interação com Client/API/Database) ele habilita?
2. **Qual conexão ou comportamento concreto** precisa dele?
3. **Por que** a funcionalidade atual da v0.2 não é suficiente?
4. **Qual é a menor mudança** que atende à necessidade sem piorar a fluidez do editor?
5. **Como testar** o novo comportamento e a ausência de regressão?

Se não houver resposta concreta aos itens 1 a 3, a proposta fica **fora da v0.3**. Propostas úteis mas não relacionadas vão ao backlog futuro, não à implementação desta versão.

## 6. Governança e proteção contra aumento indevido de escopo

- Requisitos são definidos e aprovados **antes** das tarefas e da implementação.
- Planejamento, Codex ou qualquer outro agente/desenvolvedor **não pode acrescentar funcionalidades por iniciativa própria** apenas porque parecem melhorias.
- As tarefas no Trello devem referenciar requisitos aprovados; não criam requisitos por conta própria.
- Toda mudança de escopo exige revisão explícita deste documento e aprovação do responsável pelo produto.
- Decisões técnicas e ADRs devem implementar requisitos aprovados, não ampliar seu alcance.
- Modificações visuais ou na interação do editor exigem validação específica antes de serem consideradas aprovadas.
- Nenhuma integração na `main` ou release decorre automaticamente da conclusão de tarefas.

## 7. Pendências do planejamento — ainda não aprovadas

1. Catálogo exato dos novos tipos de componentes.
2. Matriz de conexões permitidas, incluindo os três tipos existentes.
3. Propriedades essenciais e comportamento simulado de cada novo componente.
4. Cenários de referência e critérios de aceite por componente e conexão.
5. Identificação dos ajustes mínimos no backend, frontend e contrato de API.
6. Somente depois: ADRs pertinentes, contrato API, tarefas de implementação e plano de testes.

**Não transportar automaticamente requisitos, catálogo, ExecutionPlan ou arquitetura do planejamento anterior da v0.3.** Documentos históricos da v0.3 podem permanecer no repositório como referência, mas **não são autorização para implementar funcionalidades** nesta versão reiniciada. Qualquer conteúdo a reutilizar deve ser reavaliado e aprovado à luz deste escopo.

## 8. Critérios de aceite da v0.3 (nível de escopo)

A versão poderá ser homologada quando:

- os componentes novos explicitamente aprovados funcionarem como componentes de simulação, não apenas como elementos visuais;
- todas as novas conexões aprovadas forem criáveis e simuláveis com regras verificáveis;
- Client, API e Database continuarem funcionando como na v0.2, inclusive nos cenários anteriores;
- arrastar, mover, selecionar, conectar e remover mantiverem a fluidez e o comportamento visual da v0.2;
- não houver funcionalidades independentes do escopo incluídas sem aprovação;
- testes de regressão e cenários novos tiverem evidências verificáveis;
- a interface for validada pelo responsável pelo produto antes do encerramento.

## 9. Referências

- [Requisitos v0.2 — baseline funcional](mvp-v0.2.md)
- [ADRs históricos](../architecture/ADR.md)

**Este documento define o escopo; ele não escolhe os novos componentes nem estabelece detalhes de implementação.**
