export type Risk = 'LOW' | 'MEDIUM' | 'HIGH';
export type AgentStatus = 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED';
export type ValidationStatus = 'VALID' | 'INVALID' | 'SKIPPED';

export interface ArtifactSummary {
  id: string;
  filePath: string;
  targetPath: string | null;
  phaseNumber: number;
  phaseTitle: string;
  risk: Risk;
  status: AgentStatus;
  validationStatus: ValidationStatus;
  retryCount: number;
  errorMessage: string | null;
  promptTokens: number | null;
  outputTokens: number | null;
  startedAt: string | null;
  completedAt: string | null;
}

export interface ArtifactDetail extends ArtifactSummary {
  originalCode: string | null;
  generatedCode: string | null;
  validationErrors: string | null;
}

export interface RunSummary {
  repoId: string;
  total: number;
  success: number;
  failed: number;
  pending: number;
  running: number;
  valid: number;
  invalid: number;
  skipped: number;
  totalRetries: number;
  artifacts: ArtifactSummary[];
}
