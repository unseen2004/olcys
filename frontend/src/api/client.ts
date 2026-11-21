import type {
  AnalysisRunDto,
  CreateSubjectRequest,
  HealthStatusDto,
  RunDiffDto,
  ScenarioPresetDto,
  SubjectSummaryDto,
  TriggerRunRequest,
} from '../types/api';

export class ApiError extends Error {
  public readonly status: number;
  public readonly detail?: string;

  constructor(message: string, status = 0, detail?: string) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.detail = detail;
  }
}

async function requestJson<T>(url: string, init?: RequestInit): Promise<T> {
  let response: Response;
  try {
    response = await fetch(url, {
      ...init,
      headers: {
        Accept: 'application/json',
        ...(init?.body ? { 'Content-Type': 'application/json' } : {}),
        ...(init?.headers ?? {}),
      },
    });
  } catch (err) {
    const rawMsg = err instanceof Error ? err.message : 'Nieznany błąd sieci';
    throw new ApiError(
      'Brak połączenia z serwerem analitycznym OŁCyŚ. Upewnij się, że backend Spring Boot działa na porcie 8080.',
      0,
      rawMsg,
    );
  }

  if (!response.ok) {
    let errorMessage = `Błąd serwera HTTP ${response.status}`;
    let detail: string | undefined;
    try {
      const contentType = response.headers.get('content-type') ?? '';
      if (contentType.includes('application/json')) {
        const body = (await response.json()) as Record<string, unknown>;
        const candidate =
          (typeof body.message === 'string' && body.message) ||
          (typeof body.detail === 'string' && body.detail) ||
          (typeof body.error === 'string' && body.error);
        if (candidate) {
          errorMessage = candidate;
        }
        if (typeof body.path === 'string') {
          detail = `Ścieżka: ${body.path}`;
        }
      } else {
        const text = await response.text();
        if (text.trim().length > 0) {
          detail = text.slice(0, 300);
        }
      }
    } catch {
      // Fallback to HTTP status text
    }
    throw new ApiError(errorMessage, response.status, detail);
  }

  return (await response.json()) as T;
}

export function createSubjectAnalysis(payload: CreateSubjectRequest): Promise<AnalysisRunDto> {
  return requestJson<AnalysisRunDto>('/api/subjects', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

export function triggerSubjectRun(
  subjectId: string,
  payload: TriggerRunRequest,
): Promise<AnalysisRunDto> {
  return requestJson<AnalysisRunDto>(
    `/api/subjects/${encodeURIComponent(subjectId)}/runs`,
    {
      method: 'POST',
      body: JSON.stringify(payload),
    },
  );
}

export function listSubjects(): Promise<SubjectSummaryDto[]> {
  return requestJson<SubjectSummaryDto[]>('/api/subjects');
}

export function getAnalysisRun(runId: string): Promise<AnalysisRunDto> {
  return requestJson<AnalysisRunDto>(`/api/runs/${encodeURIComponent(runId)}`);
}

export function getRunDiff(
  subjectId: string,
  fromRunId: string,
  toRunId: string,
): Promise<RunDiffDto> {
  const params = new URLSearchParams({ from: fromRunId, to: toRunId });
  return requestJson<RunDiffDto>(
    `/api/subjects/${encodeURIComponent(subjectId)}/diff?${params.toString()}`,
  );
}

export function listScenarios(): Promise<ScenarioPresetDto[]> {
  return requestJson<ScenarioPresetDto[]>('/api/scenarios');
}

export function getHealth(): Promise<HealthStatusDto> {
  return requestJson<HealthStatusDto>('/api/health');
}

export function getReportPdfUrl(runId: string): string {
  return `/api/runs/${encodeURIComponent(runId)}/report.pdf`;
}

export function getCaspRequestPdfUrl(runId: string): string {
  return `/api/runs/${encodeURIComponent(runId)}/casp-request.pdf`;
}

export function getReportJsonUrl(runId: string): string {
  return `/api/runs/${encodeURIComponent(runId)}/report.json`;
}

export interface MonitoringStatusDto {
  subjectId: string;
  subjectDisplayName: string;
  enabled: boolean;
  intervalHours: number;
  updatedAt: string;
  lastHeartbeatAt: string | null;
}

export function listMonitoringSchedules(): Promise<MonitoringStatusDto[]> {
  return requestJson<MonitoringStatusDto[]>('/api/monitoring');
}

export function toggleSubjectMonitoring(
  subjectId: string,
  enabled: boolean,
  intervalHours = 24,
): Promise<MonitoringStatusDto> {
  return requestJson<MonitoringStatusDto>(
    `/api/subjects/${encodeURIComponent(subjectId)}/monitoring`,
    {
      method: 'POST',
      body: JSON.stringify({ enabled, intervalHours }),
    },
  );
}
