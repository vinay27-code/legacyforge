import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AnalysisReport, FileAnalysis } from './analysis.models';

@Injectable({ providedIn: 'root' })
export class AnalysisService {
  private http = inject(HttpClient);
  private base = (repoId: string) => `${environment.apiBaseUrl}/api/repos/${repoId}/analysis`;

  get(repoId: string): Observable<AnalysisReport | null> {
    return this.http.get<AnalysisReport | null>(this.base(repoId));
  }

  run(repoId: string): Observable<AnalysisReport> {
    return this.http.post<AnalysisReport>(this.base(repoId), {});
  }

  topComplex(repoId: string, limit = 20): Observable<FileAnalysis[]> {
    return this.http.get<FileAnalysis[]>(`${this.base(repoId)}/top-complex?limit=${limit}`);
  }
}
