export type Risk = 'LOW' | 'MEDIUM' | 'HIGH';
export type AgentStatus = 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED';
export type ValidationStatus = 'VALID' | 'INVALID' | 'SKIPPED';
export type EdgeType = 'IMPORT' | 'FIELD' | 'METHOD_PARAM' | 'INSTANTIATION';

export interface DependencyEdge {
  toClassName: string;
  toArtifactId: string | null;
  toTargetPath: string | null;
  edgeType: EdgeType;
  resolved: boolean;
}

export interface ArtifactSummary {
  id: string;
  parentArtifactId: string | null;
  filePath: string;
  targetPath: string | null;
  declaredFqn: string | null;
  phaseNumber: number;
  phaseTitle: string;
  risk: Risk;
  status: AgentStatus;
  validationStatus: ValidationStatus;
  retryCount: number;
  depsOut: number;
  depsBroken: number;
  depsIn: number;
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
  outgoing: DependencyEdge[];
  incoming: DependencyEdge[];
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
  derivedCount: number;
  totalEdges: number;
  brokenEdges: number;
  artifacts: ArtifactSummary[];
}
