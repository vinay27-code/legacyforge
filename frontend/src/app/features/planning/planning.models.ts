export type Risk = 'LOW' | 'MEDIUM' | 'HIGH';

export interface FileRisk {
  path: string;
  risk: Risk;
  reason: string;
  notes: string;
}

export interface Phase {
  phaseNumber: number;
  title: string;
  description: string;
  estimatedDays: number;
  files: FileRisk[];
}

export interface PlanContent {
  summary: string;
  overallRisk: Risk;
  totalEstimatedDays: number;
  phases: Phase[];
}

export interface PlanResponse {
  id: string;
  repoId: string;
  provider: string;
  generatedAt: string;
  promptTokens: number | null;
  outputTokens: number | null;
  plan: PlanContent;
}
