import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { IndexStatus, SearchHit, SearchRequest } from './search.models';

@Injectable({ providedIn: 'root' })
export class SearchService {
  private http = inject(HttpClient);
  private base = (repoId: string) => `${environment.apiBaseUrl}/api/repos/${repoId}`;

  status(repoId: string): Observable<IndexStatus> {
    return this.http.get<IndexStatus>(`${this.base(repoId)}/index`);
  }

  reindex(repoId: string): Observable<IndexStatus> {
    return this.http.post<IndexStatus>(`${this.base(repoId)}/index`, {});
  }

  search(repoId: string, req: SearchRequest): Observable<SearchHit[]> {
    return this.http.post<SearchHit[]>(`${this.base(repoId)}/search`, req);
  }
}
