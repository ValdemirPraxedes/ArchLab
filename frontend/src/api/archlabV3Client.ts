import type { paths, components } from './generated/schema-v3';

type Schemas = components['schemas'];
export type ArchitectureV3 = Schemas['ArchitectureDefinitionV3'];
export type ExecutionPlanValidationRequestV3 = Schemas['ExecutionPlanValidationRequestV3'];
export type SimulationRequestV3 = Schemas['SimulationRequestV3'];
export type SimulationResponseV3 = paths['/api/v3/simulations']['post']['responses'][200]['content']['application/json'];
export type ValidationResponseV3 = Schemas['ValidationResponseV3'];
export type ProblemV3 = Schemas['ProblemV3'];
export type ViolationV3 = Schemas['ViolationV3'];

export class ArchlabV3NetworkError extends Error {
  constructor(public readonly cause: unknown) { super('Não foi possível conectar à API v3. Confira a conexão e tente novamente.'); this.name = 'ArchlabV3NetworkError'; }
}
export class ArchlabV3HttpError extends Error {
  constructor(public readonly status: number, public readonly problem: ProblemV3 | null) {
    super(problem?.detail ?? problem?.title ?? `Falha HTTP ${status} na API ArchLab v3`);
    this.name = 'ArchlabV3HttpError';
  }
  get violations(): ViolationV3[] { return this.problem?.violations ?? []; }
}
function readProblem(value: unknown): ProblemV3 | null {
  if (typeof value !== 'object' || value === null) return null;
  const candidate = value as Partial<ProblemV3>;
  if (typeof candidate.type !== 'string' || typeof candidate.title !== 'string' ||
      typeof candidate.status !== 'number' || typeof candidate.code !== 'string') return null;
  return candidate as ProblemV3;
}
/** Only HTTP failures reject; valid:false and simulated domain failures remain typed results. */
export class ArchlabV3Client {
  constructor(private readonly baseUrl = import.meta.env.VITE_API_BASE_URL ?? '') {}
  private async post<T>(path: string, payload: ArchitectureV3 | ExecutionPlanValidationRequestV3 | SimulationRequestV3, signal?: AbortSignal): Promise<T> {
    let response: Response;
    try { response = await fetch(this.baseUrl + path, {
      method: 'POST', headers: { 'Content-Type': 'application/json', Accept: 'application/json, application/problem+json' },
      body: JSON.stringify(payload), signal,
    }); } catch (error) { if (signal?.aborted) throw error; throw new ArchlabV3NetworkError(error); }
    if (!response.ok) {
      let value: unknown = null;
      try { value = await response.json(); } catch { /* Non-JSON HTTP errors have no Problem. */ }
      throw new ArchlabV3HttpError(response.status, readProblem(value));
    }
    return await response.json() as T;
  }
  validateArchitecture(input: ArchitectureV3, signal?: AbortSignal): Promise<ValidationResponseV3> { return this.post('/api/v3/architectures/validate', input, signal); }
  validateExecutionPlan(input: ExecutionPlanValidationRequestV3, signal?: AbortSignal): Promise<ValidationResponseV3> { return this.post('/api/v3/execution-plans/validate', input, signal); }
  validateScenario(input: SimulationRequestV3, signal?: AbortSignal): Promise<ValidationResponseV3> { return this.post('/api/v3/scenarios/validate', input, signal); }
  simulate(input: SimulationRequestV3, signal?: AbortSignal): Promise<SimulationResponseV3> { return this.post('/api/v3/simulations', input, signal); }
}
export const archlabV3Client = new ArchlabV3Client();
