import type { SimulationResponseV2 } from '../../api/archlabV2Client';

type Props = { result: SimulationResponseV2 };
export function SimulationMetricsV2({ result }: Props) {
  const { metrics } = result;
  return <section aria-label="Análise de métricas v2" className="metrics-v2">
    <h3>Métricas da execução</h3>
    <p><strong>Tempo virtual total: {metrics.totalDurationMs} ms</strong></p>
    <p className="metrics-v2-note">Processamento, comunicação, espera e duração inclusiva são métricas independentes. Esperas aninhadas podem se sobrepor e não devem ser somadas ao tempo total.</p>
    <div className="metrics-v2-grid">
      <section aria-label="Processamento por componente"><h4>Processamento próprio por componente</h4>
        <table><thead><tr><th>Componente</th><th>Duração</th></tr></thead><tbody>{metrics.processingByComponent.map(item => <tr key={item.componentId}><td>{item.componentId}</td><td>{item.durationMs} ms</td></tr>)}</tbody></table>
      </section>
      <section aria-label="Comunicação por conexão"><h4>Comunicação por conexão</h4>
        <table><thead><tr><th>Conexão</th><th>Travessias</th><th>Duração</th></tr></thead><tbody>{metrics.communicationByConnection.map(item => <tr key={item.connectionId}><td>{item.connectionId}</td><td>{item.traversals}</td><td>{item.durationMs} ms</td></tr>)}</tbody></table>
      </section>
      <section aria-label="Esperas por exchange"><h4>Esperas por API e exchange</h4>
        <table><thead><tr><th>Componente</th><th>Exchange</th><th>Intervalo</th><th>Duração</th></tr></thead><tbody>{metrics.waitingByComponent.map((item,index) => <tr key={item.exchangeId+'-'+index}><td>{item.componentId}</td><td>{item.exchangeId}</td><td>{item.startMs}–{item.endMs} ms</td><td>{item.durationMs} ms</td></tr>)}</tbody></table>
      </section>
      <section aria-label="Duração inclusiva das exchanges"><h4>Duração inclusiva das exchanges</h4>
        <table><thead><tr><th>Exchange</th><th>Caller → Callee</th><th>Intervalo</th><th>Duração</th></tr></thead><tbody>{metrics.exchanges.map(item => <tr key={item.exchangeId}><td>{item.exchangeId}</td><td>{item.callerComponentId} → {item.calleeComponentId}</td><td>{item.startMs}–{item.endMs} ms</td><td>{item.durationMs} ms</td></tr>)}</tbody></table>
      </section>
    </div>
  </section>;
}
