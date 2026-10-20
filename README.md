# ArchLab

**Desenhe. Simule. Compare. Decida.**

> **Status:** Planejamento técnico — implementação ainda não iniciada.

O **ArchLab** é uma plataforma web profissional de **modelagem e simulação de arquiteturas de software**. O objetivo é ajudar arquitetos de software, tech leads e desenvolvedores a construir modelos de sistemas distribuídos, simular cargas de trabalho e comparar alternativas antes de modificar sistemas reais.

Diferentemente de um editor de diagramas tradicional, os componentes do ArchLab terão propriedades e comportamentos simuláveis. Assim, será possível investigar **capacidade, latência, concorrência, filas, throughput e gargalos** com base em premissas configuráveis.

> **Limitação importante:** os resultados serão estimativas do modelo, não garantias de desempenho em produção. O ArchLab não substituirá testes de carga ou ferramentas de observabilidade.

## Objetivos

- Modelar componentes e suas dependências visualmente.
- Configurar capacidade de processamento, concorrência, filas e tempos de atendimento.
- Simular diferentes perfis e volumes de tráfego.
- Identificar saturação, rejeições e gargalos.
- Comparar cenários arquiteturais de forma reproduzível.
- Apoiar decisões técnicas com métricas e premissas explícitas.

## Funcionalidades planejadas

### MVP

- Editor visual de arquiteturas.
- Componentes iniciais: **Service/API**, **Database** e **Cache**.
- Configuração de capacidade, concorrência, filas e tempos de processamento.
- Cenários com diferentes volumes de tráfego.
- Motor de **simulação de eventos discretos** (*Discrete Event Simulation*, DES).
- Métricas de throughput, latência, utilização e rejeições.
- Identificação de gargalos e comparação de cenários.
- Persistência de projetos e resultados.

### Evoluções futuras

Load balancers, filas de mensagens, workers, circuit breakers, retries, timeouts avançados, cenários de indisponibilidade, estimativas de custo, relatórios, calibração com métricas reais, colaboração entre equipes e assistência por IA.

**As funcionalidades acima são planejadas, não implementadas.**

## Arquitetura planejada

O ArchLab começará como um **monólito modular**, com um motor de simulação independente do Spring Boot.

```text
archlab/
├── archlab-engine/   # Motor de simulação em Java puro
├── archlab-api/      # API Spring Boot e persistência
└── archlab-web/      # Interface React e TypeScript
```

> Esta é a estrutura pretendida do repositório; os módulos serão criados progressivamente.

### Stack tecnológica

| Camada | Tecnologias planejadas |
| --- | --- |
| Motor | Java 21, Maven, JUnit 5 |
| Backend | Spring Boot, PostgreSQL |
| Frontend | React, TypeScript, React Flow |
| Simulação | Eventos discretos, execução sequencial e determinística |

## Motor de simulação

O `archlab-engine` será uma biblioteca **Java 21 independente do Spring Boot**. Não executará requisições HTTP reais e não criará uma thread por requisição simulada.

Principais decisões técnicas:

- Relógio virtual em microssegundos, representado por `long`.
- `PriorityQueue` para ordenação por timestamp e sequência de criação.
- Execução sequencial e determinística.
- `SplittableRandom` para aleatoriedade reproduzível.
- Separação entre definições imutáveis e estado mutável de execução.
- Limites de tempo virtual e quantidade de eventos.

**Eventos iniciais previstos:**

| Evento | Papel |
| --- | --- |
| `RequestArrival` | Chegada a um componente |
| `ProcessingComplete` | Conclusão de uma etapa de processamento |
| `RequestTimeout` | Expiração do prazo de uma requisição |

Inicialmente, os fluxos distribuídos serão modelados com **chamadas síncronas bloqueantes**. Uma requisição lógica poderá visitar diferentes componentes, sem ser contabilizada várias vezes como requisição concluída.

## Roadmap

| Marco | Entrega |
| --- | --- |
| **1 — Núcleo DES** | Relógio virtual, eventos, fila de prioridade, dispatcher, limites e testes |
| **2 — Recursos e filas** | Concorrência, processamento FIFO, fila limitada e rejeições |
| **3 — Tráfego e métricas** | Geradores de chegada, throughput, latência e utilização |
| **4 — Fluxos distribuídos** | Service, Database, Cache, dependências, timeouts e falhas |
| **5 — Validação** | Testes estatísticos, referências analíticas, benchmarks e documentação |
| **6 — API e persistência** | Gerenciamento de projetos, cenários e resultados |
| **7 — Editor visual** | Modelagem web, execução e comparação de cenários |

Os marcos 6 e 7 são etapas propostas para completar a plataforma. **O desenvolvimento começará pelo Marco 1.**

### Primeira versão executável

O primeiro experimento do motor terá:

- Um componente genérico.
- Duas unidades de processamento simultâneo.
- Uma fila limitada.
- Um gerador de tráfego.
- **60 segundos de tempo virtual**.
- Métricas de requisições concluídas, rejeitadas e pendentes, throughput, latência e utilização.

Essa primeira entrega **não** terá frontend, banco de dados, Spring Boot ou comunicação HTTP.

## Testes e confiabilidade

As validações prioritárias incluem ordenação cronológica, desempate estável entre eventos simultâneos, limites de concorrência, FIFO, rejeição por fila cheia, tratamento correto de timeouts, ausência de contagens duplicadas, reprodutibilidade por semente e comparação com cenários analíticos conhecidos.

## Como executar

O projeto ainda não possui uma versão executável. As instruções de compilação e testes serão adicionadas após a implementação do primeiro módulo Maven.

## Acompanhe o desenvolvimento

- [Repositório GitHub](https://github.com/ValdemirPraxedes/ArchLab)
- [Quadro Trello — ArchLab | Desenvolvimento](https://trello.com/b/nGBgLrB9/archlab-desenvolvimento)

---

**ArchLab** — *Desenhe. Simule. Compare. Decida.*
