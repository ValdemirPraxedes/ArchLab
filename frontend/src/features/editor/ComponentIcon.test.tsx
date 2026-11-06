import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { ComponentIcon } from './ComponentIcon';

describe('Lucide nos componentes', () => {
  it.each(['CLIENT','API','DATABASE','SERVICE','CACHE'] as const)('renderiza %s com SVG decorativo e sem alterar o rótulo acessível', (type) => {
    const {container}=render(<div><ComponentIcon type={type}/><span>{type}</span></div>);
    const icon=container.querySelector('.component-icon svg');
    expect(icon).not.toBeNull();
    expect(icon?.tagName.toLowerCase()).toBe('svg');
    expect(screen.getByText(type)).toBeInTheDocument();
    expect(icon?.closest('[aria-hidden="true"]')).not.toBeNull();
  });
  it.each([
    ['WEB_BROWSER', 'globe'], ['MOBILE_APP', 'smartphone'], ['DESKTOP_APP', 'monitor'],
    ['CLI', 'terminal'], ['BOT', 'bot'], ['EMBEDDED_SYSTEM', 'cpu'],
    ['CLIENT', 'monitor'], ['API', 'server'], ['DATABASE', 'database'],
    ['SERVICE','server'], ['API_GATEWAY','route'], ['BFF','layers'], ['WORKER','cog'],
    ['SCHEDULER','calendar-clock'], ['AUTHENTICATION_SERVICE','shield-check'], ['WEBHOOK_RECEIVER','webhook'],
    ['ORCHESTRATOR_SERVICE','workflow'], ['INTEGRATION_BUS','cable'], ['CACHE','database-zap'],
    ['OBJECT_STORAGE','hard-drive'], ['NOTIFICATION_SERVICE','bell-ring'], ['SEARCH_ENGINE','search'],
    ['SERVICE_REGISTRY','waypoints'], ['RULES_ENGINE','list-checks'],
  ] as const)('associa %s ao ícone %s', (type, icon) => {
    const { container } = render(<ComponentIcon type={type} />);
    expect(container.querySelector(`svg.lucide-${icon}`)).not.toBeNull();
  });
});
