/** Historical v0.1 payload retained for regression views. The editor uses ArchlabV1Client. */
export type ArchitectureRequest = {
  components: Array<{id: string; type: 'CLIENT' | 'API' | 'DATABASE'; preProcessingMs?: number;
    postProcessingMs?: number; processingMs?: number}>;
  connections: Array<{id: string; sourceId: string; targetId: string; protocol: 'REST' | 'SOAP' | 'TCP'; latencyMs: number}>;
};
export type ValidationResponse = {valid: boolean; violations: Array<{code: string; path: string; message: string}>};
export type SimulationResponse = {
  execution: {id: string; status: string; totalDurationMs: number};
  initialState: {components: Record<string,string>; requests: Record<string,string>; exchanges: Record<string,string>};
  trace: Array<{sequence: number; timestampMs: number; type: string; entityKind?: 'COMPONENT' | 'REQUEST' | 'EXCHANGE';
    entityId?: string; newState?: string; [key: string]: unknown}>;
  timeline: Array<{id: string; kind: string; startMs: number; endMs: number; durationMs: number; [key: string]: unknown}>;
  metrics: {apiPreProcessingMs: number; apiPostProcessingMs: number; databaseProcessingMs: number;
    communicationMs: number; apiWaitingMs: number};
};

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
