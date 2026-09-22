import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ArtifactDetail, RunSummary } from './agents.models';

@Injectable({ providedIn: 'root' })
export class AgentsService {
  private http = inject(HttpClient);
  private base = (repoId: string) => `${environment.apiBaseUrl}/api/repos/${repoId}/agents`;

  list(repoId: string): Observable<RunSummary> {
    return this.http.get<RunSummary>(this.base(repoId));
  }

  run(repoId: string): Observable<RunSummary> {
    return this.http.post<RunSummary>(`${this.base(repoId)}/run`, {});
  }

  detail(repoId: string, artifactId: string): Observable<ArtifactDetail> {
    return this.http.get<ArtifactDetail>(`${this.base(repoId)}/${artifactId}`);
  }
}
