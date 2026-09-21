import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { AuthService } from '../../core/auth/auth.service';
import { environment } from '../../../environments/environment';

interface HelloResponse {
  message: string;
  service: string;
  version: string;
  timestamp: string;
}

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, MatCardModule, MatButtonModule, MatIconModule, MatChipsModule],
  template: `
    <div class="wrap">
      <header>
        <h1>Welcome{{ auth.user() ? ', ' + auth.user()!.email : '' }}</h1>
        <mat-chip-set>
          <mat-chip>{{ auth.user()?.role }}</mat-chip>
        </mat-chip-set>
      </header>

      <p class="tag">
        This is your LegacyForge control center. The wizard, jobs list,
        and review UI arrive in later weeks. Right now: a smoke test.
      </p>

      <mat-card class="card">
        <mat-card-header>
          <mat-card-title>Backend health check</mat-card-title>
          <mat-card-subtitle>Calls <code>GET /api/hello</code> on the Spring Boot service.</mat-card-subtitle>
        </mat-card-header>
        <mat-card-content>
          <button mat-flat-button color="primary" (click)="ping()" [disabled]="loading()">
            <mat-icon>network_check</mat-icon>
            {{ loading() ? 'Pinging…' : 'Ping backend' }}
          </button>

          @if (response()) {
            <pre class="ok">{{ response() | json }}</pre>
          }
          @if (error()) {
            <pre class="err">{{ error() }}</pre>
          }
        </mat-card-content>
      </mat-card>
    </div>
  `,
  styles: [`
    .wrap { max-width: 960px; margin: 0 auto; }
    header { display: flex; align-items: center; gap: 16px; margin-bottom: 8px; }
    h1 { margin: 0; letter-spacing: -0.02em; }
    .tag { color: var(--lf-muted); margin: 0 0 32px; }
    .card { background: var(--lf-bg-elev); border: 1px solid var(--lf-border); }
    pre {
      margin-top: 16px;
      padding: 16px;
      border-radius: 8px;
      overflow-x: auto;
      font-size: 0.9rem;
    }
    pre.ok { background: rgba(76, 217, 123, 0.12); color: var(--lf-success); border: 1px solid rgba(76, 217, 123, 0.3); }
    pre.err { background: rgba(255, 95, 95, 0.12); color: #ff8080; border: 1px solid rgba(255, 95, 95, 0.3); }
  `],
})
export class DashboardComponent {
  auth = inject(AuthService);
  private http = inject(HttpClient);

  loading = signal(false);
  response = signal<HelloResponse | null>(null);
  error = signal<string | null>(null);

  ping(): void {
    this.loading.set(true);
    this.response.set(null);
    this.error.set(null);
    this.http.get<HelloResponse>(`${environment.apiBaseUrl}/api/hello`).subscribe({
      next: (res) => {
        this.response.set(res);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(err?.error?.message ?? err?.message ?? 'Request failed');
        this.loading.set(false);
      },
    });
  }
}
