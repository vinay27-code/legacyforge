export interface DetectedFramework {
  key: string;
  name: string;
  version: string | null;
  confidence: number;
  evidence: string[];
}

export interface LangBreakdown {
  language: string;
  files: number;
  bytes: number;
}

export interface RepoFinding {
  code: string;
  severity: 'low' | 'medium' | 'high';
  message: string;
  occurrences: number;
}

export interface AnalysisReport {
  id: string;
  repoId: string;
  status: 'PENDING' | 'READY' | 'FAILED';
  frameworks: DetectedFramework[];
  summary: {
    languageBreakdown: LangBreakdown[];
    javaFileCount: number;
    javaLoc: number;
    methodCount: number;
  };
  findings: RepoFinding[];
  totalJavaFiles: number;
  totalJavaLoc: number;
  maxComplexity: number;
  avgComplexity: number;
  errorMessage: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface FileAnalysis {
  id: string;
  fileId: string;
  filePath: string;
  className: string | null;
  packageName: string | null;
  loc: number;
  methodCount: number;
  complexity: number;
  frameworks: string[];
  findings: Array<{ code: string; severity: string; message: string }>;
}
