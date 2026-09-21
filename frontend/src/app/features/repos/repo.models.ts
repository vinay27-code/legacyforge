export interface RepoSummary {
  id: string;
  name: string;
  sourceType: 'ZIP' | 'GITHUB';
  sourceUrl: string | null;
  fileCount: number;
  totalSizeBytes: number;
  status: 'PENDING' | 'READY' | 'FAILED';
  errorMessage: string | null;
  createdAt: string;
}

export interface FileTreeNode {
  name: string;
  path: string;
  type: 'dir' | 'file';
  sizeBytes: number | null;
  language: string | null;
  binary: boolean | null;
  children: FileTreeNode[] | null;
}

export interface FileContent {
  path: string;
  sizeBytes: number;
  language: string | null;
  binary: boolean;
  content: string | null;
}

export interface GithubIngestRequest {
  url: string;
}
