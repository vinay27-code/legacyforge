export interface SearchHit {
  id: string;
  fileId: string;
  filePath: string;
  chunkType: 'METHOD' | 'CLASS' | 'WHOLE_FILE' | 'BLOCK';
  className: string | null;
  methodName: string | null;
  startLine: number | null;
  endLine: number | null;
  content: string;
  similarity: number;
}

export interface IndexStatus {
  repoId: string;
  chunkCount: number;
  provider: string;
}

export interface SearchRequest {
  query: string;
  k?: number;
}
