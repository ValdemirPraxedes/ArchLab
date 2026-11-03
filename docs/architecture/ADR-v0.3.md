# ArchLab — ADR v0.3: Clientes de modelagem visual sem execução

> **ERRATA POSTERIOR (2026-11-03):** as decisões de não permitir conexões/simulações e de não converter os seis tipos para `CLIENT` foram substituídas. Consulte [Errata 01](../requirements/v0.3-errata-client-behavior.md): seis tipos visuais reutilizam o comportamento `CLIENT` por adaptação na fronteira da API v2; `Client` genérico sai da paleta. As seções abaixo permanecem como registro histórico.


- **Status:** Aprovado para o planejamento da v0.3
- **Data:** 2026-11-03
- **Escopo:** v0.3 — seis clientes e organização da paleta
- **Requisitos de referência:** [v0.3-client-requirements.md](../requirements/v0.3-client-requirements.md) (RF-01 a RF-08 e RQ-01 a RQ-05)

## Contexto

O editor atual do ArchLab permite representar `CLIENT`, `API` e `DATABASE` e envia cenários compatíveis ao backend de simulação. A v0.3 foi deliberadamente reduzida a uma entrega pequena: adicionar seis clientes ao **editor visual**, sem implementar suas conexões nem a sua execução.

Os novos tipos são: **Web Browser, Mobile App, Desktop App, CLI, Bot e Embedded System**. A paleta deverá ser agrupada em **Clientes**, **Serviços** e **Dados**. Os perfis de comunicação e os demais componentes debatidos permanecem no backlog futuro.

Essa decisão introduz uma diferença temporária entre os **tipos representáveis no editor** e os **tipos executáveis pelo motor atual**.

## Decisão

1. **Tipos visuais explícitos no frontend.** Introduzir seis identificadores distintos no modelo do editor, mantendo nome, posição, identificação e operações usuais de edição. Usar a estrutura existente do React Flow, sem criar um novo editor.
2. **Paleta agrupada por categoria.** Exibir os seis tipos em **Clientes**, o componente existente **API** em **Serviços**, e o componente existente **Database** em **Dados**. Manter arrastar, clicar para adicionar e as interações existentes. Expandir/recolher categorias não é obrigatório.
3. **Ícones e nomes próprios.** Reutilizar a biblioteca de ícones e os padrões visuais já existentes.
4. **Sem ampliação de conexões ou execução.** Os seis novos tipos são **somente elementos de modelagem visual na v0.3**. Não podem criar conexões nem ser executados/simulados nesta entrega.
5. **Separar capacidade visual de suporte do backend.** O frontend precisa reconhecer os seis tipos em seu modelo local, mas **não deve serializá-los como componentes válidos do contrato v2**, que reconhece apenas os tipos atuais. Antes de chamar validação ou simulação no backend, detectar a presença dos novos tipos e interromper a operação com uma mensagem clara. Não converter silenciosamente os novos clientes em `CLIENT` nem gerar resultados fictícios.
6. **Backend sem alteração nesta entrega.** Não adicionar enums, DTOs, regras de validação ou comportamentos ao motor apenas para suportar os novos clientes visuais. A edição de cenários que contenham somente tipos existentes deve continuar utilizável.
7. **Versão experimental.** A série `0.x` admite mudanças incompatíveis, mas isso não justifica regressões desnecessárias nas funcionalidades atuais de edição.

## Alternativas consideradas

| Alternativa | Avaliação |
| --- | --- |
| Implementar imediatamente conexões, dez perfis e simulação para os seis clientes | Rejeitada para a v0.3 por ampliar excessivamente escopo e dependências. |
| Estender o contrato e o motor de backend agora, sem executar os clientes | Rejeitada por introduzir complexidade sem entregar comportamento necessário. |
| Reutilizar `CLIENT` como tipo interno para todos os seis clientes | Rejeitada: perderia a distinção real no modelo da arquitetura e criaria ambiguidade futura. |
| Tipos visuais no frontend com bloqueio explícito das operações de backend incompatíveis | **Escolhida:** permite a entrega incremental sem falsear suporte de simulação. |

## Consequências

**Positivas**
- Entrega pequena, com mudanças concentradas no frontend.
- Seis tipos são distinguíveis na paleta, no canvas e no estado da arquitetura.
- Evita modificar prematuramente contratos e motor de simulação.
- Organiza a paleta para novas categorias futuras.

**Limitações e riscos**
- Cenários contendo os novos clientes ainda não poderão ser validados ou simulados no backend. O editor deve explicar essa restrição.
- Os tipos visuais e os tipos simuláveis serão conjuntos diferentes até uma versão posterior; isso precisa ser tratado explicitamente, sem coerções automáticas.
- Perfis de comunicação, regras de estímulo do Bot/Embedded System e comportamento de rede ficam adiados.

## Verificação da decisão

- **RF-01–RF-08:** os seis clientes aparecem em Clientes, podem ser adicionados, selecionados, movimentados, renomeados e removidos, preservando os respectivos ícones/tipos; API e Database aparecem nas categorias corretas.
- **RQ-02:** cenários com tipos existentes mantêm as operações de edição e execução que já funcionam.
- **RQ-05:** tentar validar ou simular cenário com um dos seis novos tipos apresenta mensagem clara e **não realiza chamada incompatível ao backend**.
- Testes automatizados do modelo, paleta, ícones e bloqueio de validação/simulação; validação manual dos seis tipos pelo editor.

## Relação com os ADRs anteriores

Este ADR trata **somente** da modelagem visual e da organização da paleta da v0.3. Não substitui o motor de eventos, o transporte simulado nem os contratos existentes descritos em [ADR.md](ADR.md) e [ADR-v0.2.md](ADR-v0.2.md). Os documentos de planejamento mais amplos sobre clientes, serviços e perfis não ampliam o escopo desta entrega.

**Implementação e tarefas específicas serão refinadas separadamente.**
