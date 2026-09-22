import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { PlatformStats } from './dashboard.models';

@Injectable({ providedIn: 'root' })
export class DashboardService {
  private http = inject(HttpClient);

  platform(): Observable<PlatformStats> {
    return this.http.get<PlatformStats>(`${environment.apiBaseUrl}/api/stats`);
  }
}
