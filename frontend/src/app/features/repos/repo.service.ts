import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpEvent, HttpEventType, HttpRequest } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  FileContent,
  FileTreeNode,
  GithubIngestRequest,
  RepoSummary,
} from './repo.models';

export interface UploadProgress {
  kind: 'progress' | 'done';
  progressPct?: number;
  repo?: RepoSummary;
}

@Injectable({ providedIn: 'root' })
export class RepoService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/api/repos`;

  list(): Observable<RepoSummary[]> {
    return this.http.get<RepoSummary[]>(this.baseUrl);
  }

  get(id: string): Observable<RepoSummary> {
    return this.http.get<RepoSummary>(`${this.baseUrl}/${id}`);
  }

  tree(id: string): Observable<FileTreeNode> {
    return this.http.get<FileTreeNode>(`${this.baseUrl}/${id}/tree`);
  }

  file(id: string, path: string): Observable<FileContent> {
    const params = new URLSearchParams({ path }).toString();
    return this.http.get<FileContent>(`${this.baseUrl}/${id}/file?${params}`);
  }

  fromGithub(req: GithubIngestRequest): Observable<RepoSummary> {
    return this.http.post<RepoSummary>(`${this.baseUrl}/github`, req);
  }

  uploadZip(file: File): Observable<UploadProgress> {
    const form = new FormData();
    form.append('file', file);
    const req = new HttpRequest('POST', `${this.baseUrl}/upload`, form, {
      reportProgress: true,
      withCredentials: true,
    });
    return this.http.request<RepoSummary>(req).pipe(
      map((event: HttpEvent<RepoSummary>): UploadProgress => {
        if (event.type === HttpEventType.UploadProgress && event.total) {
          return { kind: 'progress', progressPct: Math.round((100 * event.loaded) / event.total) };
        }
        if (event.type === HttpEventType.Response) {
          return { kind: 'done', repo: event.body as RepoSummary };
        }
        return { kind: 'progress' };
      }),
    );
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }
}
