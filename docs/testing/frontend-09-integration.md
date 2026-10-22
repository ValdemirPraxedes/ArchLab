# FRONT 09 — integração real e checklist funcional

## Pré-requisitos

1. Backend Spring Boot iniciado na porta 8080 (`cd backend; .\\mvnw spring-boot:run -pl archlab-api -am` — ajustar conforme comandos de execução do repositório).
2. Frontend em outra janela (`cd frontend; npm install; npm run dev`).
3. Node compatível com o `package.json` e API `fetch` integrada (Node 22 recomendado).

## Verificação automatizada sem MSW

```powershell
cd frontend
npm run lint
npm test
npm run build
npm run test:integration
```

`npm run test:integration` usa `http://localhost:8080`; para alterar: `$env:ARCHLAB_API_URL='http://localhost:8080'; npm run test:integration`.

O comando envia requisições HTTP reais para os dois endpoints. Confere 260 ms no cenário de referência, 170 ms de espera da API, 7 atividades, trace cronológico com sequências consecutivas, transições da API em 50/220 ms, rejeição de arquitetura incompleta, conexão duplicada e cenário de latência/processamento zero. Nenhum desses testes usa MSW.

## Checklist manual — navegador conectado ao backend real

- [ ] Inserir Client, API e Database; conectar Client→API (REST 20 ms) e API→Database (TCP 10 ms)
- [ ] Configurar API 30 ms antes do banco e 20 ms depois; Database 150 ms
- [ ] Simular, confirmar 260 ms, 7 atividades, espera bloqueante da API 170 ms (sem dupla contagem)
- [ ] Durante playback observar IDLE/PROCESSING/WAITING/COMPLETED e passagens API WAITING de 50 até antes de 220 ms
- [ ] Validar pause/continue, velocidades 0,5× 1× 2× 4× e avanço/retorno por trace com timestamp/sequence
- [ ] Selecionar evento passado, confirmar suspensão do auto-follow sem mudar relógio; reativar via Acompanhar execução
- [ ] Conferir bloqueio de adicionar, remover, arrastar e alterar tempos enquanto execução completa; Reiniciar destrava e mantém diagrama
- [ ] Testar ausência de componente obrigatório, componente duplicado, conexão duplicada, protocolo inválido e duração negativa
- [ ] Conferir abas Tempos, Eventos e Estados, inspeção, resize/recolhimento e navegação por teclado
- [ ] Repetir com latências e processamento zero; conferir os estados finais

## Limitações e observações

O script Node cobre integração HTTP com backend real, **não automatiza eventos no navegador**. A revisão de drag/drop, acessibilidade e responsividade exige inspeção manual ou suíte browser E2E dedicada. Problemas encontrados devem ser registrados antes do aceite e merge.
