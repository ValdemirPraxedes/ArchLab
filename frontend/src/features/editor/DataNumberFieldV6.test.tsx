import {describe,expect,it,vi} from 'vitest';
import {fireEvent,render,screen} from '@testing-library/react';
import {DataNumberFieldV6} from './DataNumberFieldV6';
import {DATA_RESOURCE_LIMITS_V6,ETL_PIPELINE_LIMITS_V6} from './dataLimitsV6';

describe('campo numérico de dados v0.6',()=>{
  it('aplica limites do contrato e não mostra erro para valor válido',()=>{
    render(<DataNumberFieldV6 field="queueCapacity" limit={DATA_RESOURCE_LIMITS_V6.queueCapacity} value={10} disabled={false} onChange={()=>{}}/>);
    const input=screen.getByRole('spinbutton',{name:'queueCapacity'});
    expect(input).toHaveAttribute('min','0');
    expect(input).toHaveAttribute('max','10000');
    expect(input).not.toHaveAttribute('aria-invalid');
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });
  it('sinaliza valor fora do limite com mensagem acessível',()=>{
    render(<DataNumberFieldV6 field="connectionPoolSize" limit={DATA_RESOURCE_LIMITS_V6.connectionPoolSize} value={0} disabled={false} onChange={()=>{}}/>);
    const input=screen.getByRole('spinbutton',{name:'connectionPoolSize'});
    expect(input).toHaveAttribute('aria-invalid','true');
    expect(screen.getByRole('alert')).toHaveTextContent('connectionPoolSize deve estar entre 1 e 1000.');
    expect(input).toHaveAttribute('aria-describedby',screen.getByRole('alert').id);
  });
  it('propaga o valor digitado',()=>{
    const onChange=vi.fn();
    render(<DataNumberFieldV6 field="maxBatches" limit={ETL_PIPELINE_LIMITS_V6.maxBatches} value={1} disabled={false} onChange={onChange}/>);
    fireEvent.change(screen.getByRole('spinbutton',{name:'maxBatches'}),{target:{value:'1001'}});
    expect(onChange).toHaveBeenCalledWith(1001);
  });
});
