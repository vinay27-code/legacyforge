import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { PlanResponse } from './planning.models';

@Injectable({ providedIn: 'root' })
export class PlanningService {
  private http = inject(HttpClient);
  private base = (repoId: string) => `${environment.apiBaseUrl}/api/repos/${repoId}/plan`;

  get(repoId: string): Observable<PlanResponse> {
    return this.http.get<PlanResponse>(this.base(repoId));
  }

  generate(repoId: string): Observable<PlanResponse> {
    return this.http.post<PlanResponse>(this.base(repoId), {});
  }
}
