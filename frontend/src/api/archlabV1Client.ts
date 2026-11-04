import type { paths, components } from './generated/schema';

type Validation = paths['/api/v1/architectures/validate']['post'];
type Simulation = paths['/api/v1/simulations']['post'];
export type ArchitectureV1 = NonNullable<Validation['requestBody']>['content']['application/json'];
export type SimulationRequestV1 = NonNullable<Simulation['requestBody']>['content']['application/json'];
export type ScenarioV1 = components['schemas']['Scenario'];
export type IntegrationV1 = components['schemas']['Integration'];
export type BrokerDestinationV1 = components['schemas']['BrokerDestination'];
export type BrokerSubscriptionV1 = components['schemas']['BrokerSubscription'];
export type SimulationResponseV1 = Simulation['responses'][200]['content']['application/json'];
export type ValidationResponseV1 = Validation['responses'][200]['content']['application/json'];
export type ViolationV1 = components['schemas']['Violation'];
export type ProblemV1 = components['schemas']['Problem'];

export class ArchlabV1HttpError extends Error {
  constructor(public readonly status: number, public readonly problem: ProblemV1 | null) {
    super(problem?.detail ?? problem?.title ?? `Falha HTTP ${status} na API ArchLab v1`);
    this.name = 'ArchlabV1HttpError';
  }
  get violations(): ViolationV1[] {
    return this.problem && 'violations' in this.problem && Array.isArray(this.problem.violations)
      ? this.problem.violations : [];
  }
}

function readProblem(value: unknown): ProblemV1 | null {
  if (typeof value !== 'object' || value === null) return null;
  const p = value as Record<string, unknown>;
  if (typeof p.type !== 'string' || typeof p.title !== 'string' ||
      typeof p.status !== 'number' || typeof p.code !== 'string') return null;
  const result: Record<string, unknown> = {type: p.type, title: p.title, status: p.status, code: p.code};
  if (typeof p.detail === 'string') result.detail = p.detail;
  if (typeof p.instance === 'string') result.instance = p.instance;
  if (Array.isArray(p.violations)) result.violations = p.violations.filter(v =>
    typeof v === 'object' && v !== null && typeof v.code === 'string' &&
    typeof v.path === 'string' && typeof v.message === 'string');
  return result as ProblemV1;
}

export class ArchlabV1Client {
  constructor(private readonly baseUrl = import.meta.env.VITE_API_BASE_URL ?? '') {}
  private async post<T>(path: string, payload: ArchitectureV1 | SimulationRequestV1): Promise<T> {
    const response = await fetch(`${this.baseUrl}${path}`, {
      method: 'POST',
      headers: {'Content-Type': 'application/json', Accept: 'application/json, application/problem+json'},
      body: JSON.stringify(payload),
    });
    if (!response.ok) {
      let body: unknown = null;
      try { body = await response.json(); } catch { /* non-JSON error body */ }
      throw new ArchlabV1HttpError(response.status, readProblem(body));
    }
    return (await response.json()) as T;
  }
  validateArchitecture(architecture: ArchitectureV1): Promise<ValidationResponseV1> {
    return this.post('/api/v1/architectures/validate', architecture);
  }
  validateScenario(request: SimulationRequestV1): Promise<ValidationResponseV1> {
    return this.post('/api/v1/scenarios/validate', request);
  }
  simulate(request: SimulationRequestV1): Promise<SimulationResponseV1> {
    return this.post('/api/v1/simulations', request);
  }
}
export const archlabV1Client = new ArchlabV1Client();
