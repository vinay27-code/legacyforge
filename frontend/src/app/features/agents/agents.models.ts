export type Risk = 'LOW' | 'MEDIUM' | 'HIGH';
export type AgentStatus = 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED';

export interface ArtifactSummary {
  id: string;
  filePath: string;
  targetPath: string | null;
  phaseNumber: number;
  phaseTitle: string;
  risk: Risk;
  status: AgentStatus;
  errorMessage: string | null;
  promptTokens: number | null;
  outputTokens: number | null;
  startedAt: string | null;
  completedAt: string | null;
}

export interface ArtifactDetail extends ArtifactSummary {
  originalCode: string | null;
  generatedCode: string | null;
}

export interface RunSummary {
  repoId: string;
  total: number;
  success: number;
  failed: number;
  pending: number;
  running: number;
  artifacts: ArtifactSummary[];
}
