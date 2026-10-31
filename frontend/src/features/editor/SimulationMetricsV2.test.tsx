import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import type { SimulationResponseV2 } from '../../api/archlabV2Client';
import { SimulationMetricsV2 } from './SimulationMetricsV2';

describe('métricas v2', () => {
  it('apresenta valores do backend sem somar esperas simultâneas ou sobrepostas', () => {
    const result = {metrics:{
      totalDurationMs:260,
      processingByComponent:[{componentId:'api',durationMs:70}],
      communicationByConnection:[{connectionId:'edge',traversals:2,durationMs:40}],
      waitingByComponent:[{componentId:'api',exchangeId:'child',startMs:10,endMs:150,durationMs:140},{componentId:'client',exchangeId:'root',startMs:0,endMs:260,durationMs:260}],
      exchanges:[{exchangeId:'root',parentExchangeId:null,callerComponentId:'client',calleeComponentId:'api',connectionId:'edge',startMs:0,endMs:260,durationMs:260}],
    }} as SimulationResponseV2;
    render(<SimulationMetricsV2 result={result}/>);
    expect(screen.getByText('Tempo virtual total: 260 ms')).toBeInTheDocument();
    expect(screen.getByRole('region',{name:'Esperas por exchange'})).toHaveTextContent('140 ms');
    expect(screen.getByRole('region',{name:'Duração inclusiva das exchanges'})).toHaveTextContent('260 ms');
    expect(screen.getByText(/podem se sobrepor/)).toBeInTheDocument();
  });
});
