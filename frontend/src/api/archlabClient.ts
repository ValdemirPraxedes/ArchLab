import type { paths } from './generated/schema';

type ValidationPath = paths['/api/v1/architectures/validate']['post'];
type SimulationPath = paths['/api/v1/simulations']['post'];
export type ArchitectureRequest = NonNullable<ValidationPath['requestBody']>['content']['application/json'];
export type ValidationResponse = ValidationPath['responses'][200]['content']['application/json'];
export type SimulationResponse = SimulationPath['responses'][200]['content']['application/json'];

export class ArchlabHttpError extends Error {
  constructor(public readonly status: number, public readonly problem: unknown) {
    super(`Falha na API ArchLab (HTTP ${status})`);
    this.name = 'ArchlabHttpError';
  }
}

export class ArchlabClient {
  constructor(private readonly baseUrl = import.meta.env.VITE_API_BASE_URL ?? '') {}

  private async post<T>(path: string, architecture: ArchitectureRequest): Promise<T> {
    const response = await fetch(`${this.baseUrl}${path}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      body: JSON.stringify(architecture),
    });
    if (!response.ok) {
      let problem: unknown;
      try { problem = await response.json(); } catch { problem = null; }
      throw new ArchlabHttpError(response.status, problem);
    }
    return (await response.json()) as T;
  }

  validate(architecture: ArchitectureRequest): Promise<ValidationResponse> {
    return this.post('/api/v1/architectures/validate', architecture);
  }

  simulate(architecture: ArchitectureRequest): Promise<SimulationResponse> {
    return this.post('/api/v1/simulations', architecture);
  }
}

export const archlabClient = new ArchlabClient();
