import type { paths, components } from './generated/schema-v2';

type ArchitectureValidation = paths['/api/v2/architectures/validate']['post'];
type Simulation = paths['/api/v2/simulations']['post'];

export type ArchitectureV2 = NonNullable<ArchitectureValidation['requestBody']>['content']['application/json'];
export type SimulationRequestV2 = NonNullable<Simulation['requestBody']>['content']['application/json'];
export type ScenarioV2 = components['schemas']['ScenarioV2'];
export type ValidationResponseV2 = ArchitectureValidation['responses'][200]['content']['application/json'];
export type SimulationResponseV2 = Simulation['responses'][200]['content']['application/json'];
export type ViolationV2 = components['schemas']['ViolationV2'];
export type ProblemV2 = components['schemas']['ProblemV2'];
export type InvalidSimulationProblemV2 = components['schemas']['InvalidSimulationProblemV2'];
export type IntegrationV2 = components['schemas']['IntegrationV2'];
export type ComponentV2 = components['schemas']['ComponentV2'];

export type V2ErrorStatus = 400 | 413 | 415 | 422 | 500;
export class ArchlabV2HttpError extends Error {
  constructor(
    public readonly status: number,
    public readonly problem: ProblemV2 | InvalidSimulationProblemV2 | null,
  ) {
    super(problem?.detail ?? problem?.title ?? `Falha HTTP ${status} na API ArchLab v2`);
    this.name = 'ArchlabV2HttpError';
  }
  get violations(): ViolationV2[] {
    if (!this.problem || !('violations' in this.problem) || !Array.isArray(this.problem.violations)) return [];
    return this.problem.violations;
  }
}
function readProblem(value: unknown): ProblemV2 | InvalidSimulationProblemV2 | null {
  if (typeof value !== 'object' || value === null) return null;
  const p = value as Record<string, unknown>;
  if (typeof p.type !== 'string' || typeof p.title !== 'string' ||
      typeof p.status !== 'number' || typeof p.code !== 'string') return null;
  const result: ProblemV2 = { type: p.type, title: p.title, status: p.status, code: p.code };
  if (typeof p.detail === 'string') result.detail = p.detail;
  if (typeof p.instance === 'string') result.instance = p.instance;
  if (Array.isArray(p.violations)) {
    const violations = p.violations.filter((v): v is ViolationV2 => {
      if (typeof v !== 'object' || v === null) return false;
      const item = v as Record<string, unknown>;
      return typeof item.code === 'string' && typeof item.path === 'string' && typeof item.message === 'string';
    });
    return { ...result, violations };
  }
  return result;
}
export class ArchlabV2Client {
  constructor(private readonly baseUrl = import.meta.env.VITE_API_BASE_URL ?? '') {}
  private async post<T>(path: string, payload: ArchitectureV2 | SimulationRequestV2): Promise<T> {
    const response = await fetch(`${this.baseUrl}${path}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json, application/problem+json' },
      body: JSON.stringify(payload),
    });
    if (!response.ok) {
      let body: unknown = null;
      try { body = await response.json(); } catch { /* non-JSON error body */ }
      throw new ArchlabV2HttpError(response.status, readProblem(body));
    }
    return (await response.json()) as T;
  }
  validateArchitecture(architecture: ArchitectureV2): Promise<ValidationResponseV2> {
    return this.post('/api/v2/architectures/validate', architecture);
  }
  validateScenario(request: SimulationRequestV2): Promise<ValidationResponseV2> {
    return this.post('/api/v2/scenarios/validate', request);
  }
  simulate(request: SimulationRequestV2): Promise<SimulationResponseV2> {
    return this.post('/api/v2/simulations', request);
  }
}
export const archlabV2Client = new ArchlabV2Client();
