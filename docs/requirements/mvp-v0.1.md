# ArchLab — Requisitos funcionais do MVP v0.1

> Status: **aprovado para planejamento**, com detalhes de UX e restrições numéricas ainda refináveis. Atualizado em 2026-10-20.

## Objetivo

Simulador visual e didático de arquitetura backend: montar um fluxo, configurar tempos e observar requisição/resposta, estados e resultados. Simulações são modelos hipotéticos, não predições de produção.

## Escopo

- Componentes: **Client**, **API**, **Database**.
- Fluxo obrigatório para simular: **Client → API → Database → API → Client**.
- Editor admite várias instâncias, mas a simulação exige **exatamente uma de cada tipo** no canvas inteiro.
- Apenas uma requisição por execução, tempos fixos e determinísticos, pré/pós-processamento da API e espera síncrona pelo Database.
- Conexões lógicas: Client→API (REST ou SOAP), API→Database (TCP simplificado); retornos compartilham enlace no sentido inverso.
- Protocolos são rótulos simplificados: não há chamadas HTTP ou TCP reais.
- Controles de reprodução, painel inferior com histórico, métricas e estados.

## Fora do escopo v0.1

Autenticação, persistência, colaboração, simulação de tráfego real, cargas concorrentes, filas de execução, saturação, timeout, retries, falhas simuladas, mensageria, cache e novos tipos de componentes. Dados do editor permanecem apenas na sessão.

## Catálogo de requisitos

### RF-001 — Adicionar componentes

Arrastar Client, API ou Database da paleta para o canvas cria uma nova instância com ID único na posição de soltura; fora do canvas não cria.

**Critérios de aceite:** Paleta permanece disponível; múltiplas instâncias são possíveis; cada ID é único.

### RF-002 — Selecionar componentes

Selecionar uma instância abre informações e ações Conectar e Remover; clicar no vazio desmarca.

**Critérios de aceite:** Somente um componente ou conexão fica selecionado por vez.

### RF-003 — Criar conexões

Selecionar origem, ação Conectar, instância destino e propriedades (protocolo e latência) e confirmar.

**Critérios de aceite:** Client→API aceita REST ou SOAP; API→Database aceita TCP simplificado; origem=destino ou par incompatível é rejeitado.

### RF-004 — Remover componentes

Excluir instância selecionada e todas as conexões incidentes.

**Critérios de aceite:** Outras instâncias e suas conexões não incidentes permanecem intactas.

### RF-005 — Executar simulação visual

Animar uma requisição única no fluxo Client→API→Database→API→Client, usando cores distintas na ida e resposta.

**Critérios de aceite:** Representar espera/processamento; concluir no Client; nenhum tráfego de rede real.

### RF-006 — Configurar latências

Cada conexão tem latência fixa em milissegundos, padrão zero, aplicada a cada travessia de ida e volta.

**Critérios de aceite:** Rejeitar valores negativos ou inválidos; alterar latência altera o tempo virtual.

### RF-007 — Processamento da API

Configurar pré-processamento e pós-processamento em tempos não negativos.

**Critérios de aceite:** Pós-processamento só começa após retorno do banco; tempos são contabilizados separadamente.

### RF-008 — Espera síncrona

A API aguarda virtualmente a consulta ao Database, incluindo latência ida + processamento DB + latência volta.

**Critérios de aceite:** API fica WAITING até chegada da resposta; sem threads de bloqueio real ou concorrência simulada.

### RF-009 — Mover componentes

Mover instâncias no canvas mantendo conexões acompanhando visualmente.

**Critérios de aceite:** ID, configurações e ligações permanecem; posição respeita limites do editor.

### RF-010 — Editar conexão

Selecionar conexão para alterar protocolo compatível e latência; origem e destino não mudam.

**Critérios de aceite:** Confirmar aplica; cancelar preserva dados; troca de destino exige excluir e recriar.

### RF-011 — Remover conexão

Excluir individualmente uma conexão selecionada.

**Critérios de aceite:** Extremidades permanecem; caminho removido não participa de futuras simulações.

### RF-012 — Validar antes de simular

No clique Simular, verificar topologia completa, direções, protocolos e tempos.

**Critérios de aceite:** Problemas são apresentados sem modificar diagrama; execução inválida não inicia.

### RF-013 — Limitar multiplicidade na simulação

Editor permite várias instâncias, mas simulação exige exatamente um Client, uma API e um Database no diagrama inteiro.

**Critérios de aceite:** Mesmo instâncias excedentes desconectadas impedem simulação; identificar ausentes ou duplicadas.

### RF-014 — Unicidade de conexões

Permitir no máximo uma conexão por par não ordenado de instâncias, independentemente de protocolo.

**Critérios de aceite:** Duplicação recusada sem alterar a conexão existente; retorno usa mesma conexão invertida.

### RF-015 — Pausar e continuar

Congelar e retomar reprodução, relógio virtual exibido e posição da animação.

**Critérios de aceite:** Pausas não afetam tempo total calculado.

### RF-016 — Reiniciar

Cancelar o progresso atual e voltar ao estado Pronta, tempo zero, liberando edição.

**Critérios de aceite:** Não reinicia automaticamente; preserva diagrama e configurações; limpa resultados da execução anterior.

### RF-017 — Velocidade de reprodução

Oferecer 0,5×, 1×, 2× e 4×, com padrão 1×.

**Critérios de aceite:** Velocidade pode mudar durante execução; altera somente a duração visual, nunca métricas.

### RF-018 — Passo a passo

Avançar manualmente pelas sete atividades da requisição e permitir voltar reconstruindo o estado.

**Critérios de aceite:** Não avançar automaticamente entre etapas; mesmos estados e total que a reprodução automática.

### RF-019 — Bloquear edição

Quando a simulação começa, bloquear adição, movimento, conexão, exclusão e mudanças de propriedades inclusive em pausa e conclusão.

**Critérios de aceite:** Seleção e leitura continuam possíveis; atalhos também respeitam bloqueio; Reiniciar desbloqueia.

### RF-020 — Tempo total

Exibir a duração virtual do envio pelo Client até recebimento da resposta.

**Critérios de aceite:** Cenário de referência soma 260 ms; velocidade/pausa não mudam resultado.

### RF-021 — Detalhamento dos tempos

Mostrar API pré/pós, Database, comunicação e espera bloqueante da API.

**Critérios de aceite:** Categorias não sobrepostas somam o total; espera bloqueante aparece separada e não é somada novamente.

### RF-022 — Linha do tempo

Exibir sete atividades, com início, fim, duração e elemento associado.

**Critérios de aceite:** Ordem cronológica; seleção destaca elemento sem avançar/retroceder simulação.

### RF-023 — Estados dos componentes

Indicar Client aguardando/concluído, API processando/aguardando/concluída e DB ocioso/processando/concluído.

**Critérios de aceite:** Estados coerentes com trace; pausa preserva; reinício redefine.

### RF-024 — Painel inferior expansível

Exibir resultados em painel inferior com resumo e abas Tempos, Eventos e Estados; permitir recolher, expandir e redimensionar.

**Critérios de aceite:** Ao iniciar abre painel; layout não afeta motor; editor permanece visível; reiniciar limpa dados.

### RF-025 — Acompanhamento automático do histórico

Acompanhar e rolar até evento atual; seleção manual de outro evento suspende acompanhamento até clique em Acompanhar execução.

**Critérios de aceite:** Consulta não interfere na execução; pausas preservam modo; reinício reativa acompanhamento.

## Cenário de referência e critérios temporais

| Intervalo virtual | Atividade | Duração |
|---|---|---:|
| 0–20 ms | Client → API | 20 ms |
| 20–50 ms | Pré-processamento da API | 30 ms |
| 50–60 ms | API → Database | 10 ms |
| 60–210 ms | Processamento do Database | 150 ms |
| 210–220 ms | Database → API | 10 ms |
| 220–240 ms | Pós-processamento da API | 20 ms |
| 240–260 ms | API → Client | 20 ms |

**Total = 260 ms.** Espera bloqueante da API = 50–220 ms = **170 ms**, já contabilizados dentro de comunicação e banco.

`T_total = 2 × L(client,api) + T_pre + 2 × L(api,db) + T_db + T_post`.

## Regras transversais

- O motor usa a configuração validada e imutável da execução; canvas e editor são bloqueados durante execução, pausa e conclusão.
- Tempo virtual e velocidade de animação são independentes.
- Execuções automáticas e passo a passo produzem o mesmo resultado.
- Eventos simultâneos precisam de ordem determinística.
- O usuário pode consultar eventos históricos sem alterar o instante atual da reprodução.
- Ao reiniciar, libera edição e descarta resultados temporários, sem apagar a arquitetura.

## Pontos de revisão futura (não bloqueiam o registro da especificação)

1. Limites máximos de valores em ms e dimensões responsivas do painel.
2. Detalhes de navegação ao usar Anterior em processamento parcial.
3. Reação da timeline quando o usuário apenas rolar manualmente, sem selecionar um evento.
4. Forma final do editor para escolher instâncias, exibir mensagens de validação e propriedades.

## Documentação relacionada

Ver [Decisões de arquitetura](../architecture/ADR.md).
