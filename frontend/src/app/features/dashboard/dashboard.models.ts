export interface Counts {
  repos: number;
  files: number;
  chunks: number;
  plans: number;
  artifacts: number;
  dependencyEdges: number;
  brokenEdges: number;
  planPatches: number;
}

export interface ArtifactBreakdown {
  success: number;
  failed: number;
  valid: number;
  invalid: number;
  skipped: number;
  derived: number;
  retriesTotal: number;
}

export interface TokenUsage {
  embeddingTokens: number;
  planningPromptTokens: number;
  planningOutputTokens: number;
  agentPromptTokens: number;
  agentOutputTokens: number;
  totalTokens: number;
  estimatedCostUsd: number;
}

export interface RecentActivity {
  kind: 'PLAN' | 'AGENT_RUN' | 'INDEX';
  repoName: string;
  when: string;
  summary: string;
}

export interface PlatformStats {
  counts: Counts;
  artifacts: ArtifactBreakdown;
  tokens: TokenUsage;
  recent: RecentActivity[];
}
