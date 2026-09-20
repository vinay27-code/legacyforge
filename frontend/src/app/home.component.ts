import { Component, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { CommonModule } from '@angular/common';
import { environment } from '../environments/environment';

interface HelloResponse {
  message: string;
  service: string;
  version: string;
  timestamp: string;
}

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [CommonModule],
  template: `
    <main class="wrap">
      <header>
        <h1>LegacyForge</h1>
        <p class="tag">
          Agentic AI that migrates legacy Java monoliths to Spring Boot 3 + Angular,
          with human in the loop review.
        </p>
      </header>

      <section class="card">
        <h2>Backend health check</h2>
        <p class="muted">Calls <code>GET /api/hello</code> on the Spring Boot service.</p>
        <button (click)="ping()" [disabled]="loading()">
          {{ loading() ? 'Pinging…' : 'Ping backend' }}
        </button>

        @if (response()) {
          <pre class="ok">{{ response() | json }}</pre>
        }
        @if (error()) {
          <pre class="err">{{ error() }}</pre>
        }
      </section>

      <footer>
        <span>Week 1 scaffolding</span>
        <a href="https://github.com/vinay27-code/legacyforge" target="_blank" rel="noopener">
          github.com/vinay27-code/legacyforge
        </a>
      </footer>
    </main>
  `,
  styles: [`
    .wrap { max-width: 720px; margin: 0 auto; padding: 64px 24px; }
    header h1 { font-size: 3rem; margin: 0 0 8px; letter-spacing: -0.02em; }
    .tag { color: var(--lf-muted); font-size: 1.15rem; margin: 0 0 48px; }
    .card {
      background: var(--lf-bg-elev);
      border: 1px solid var(--lf-border);
      border-radius: 12px;
      padding: 24px;
    }
    .card h2 { margin: 0 0 8px; font-size: 1.25rem; }
    .muted { color: var(--lf-muted); margin: 0 0 16px; }
    button {
      background: var(--lf-accent);
      color: #1a1006;
      border: 0;
      padding: 10px 20px;
      border-radius: 8px;
      font-weight: 600;
      cursor: pointer;
      font-size: 1rem;
    }
    button:disabled { opacity: 0.6; cursor: wait; }
    pre {
      margin-top: 16px;
      padding: 16px;
      border-radius: 8px;
      overflow-x: auto;
      font-size: 0.9rem;
    }
    pre.ok { background: rgba(76, 217, 123, 0.12); color: var(--lf-success); border: 1px solid rgba(76, 217, 123, 0.3); }
    pre.err { background: rgba(255, 95, 95, 0.12); color: #ff8080; border: 1px solid rgba(255, 95, 95, 0.3); }
    footer {
      margin-top: 48px;
      display: flex;
      justify-content: space-between;
      color: var(--lf-muted);
      font-size: 0.9rem;
    }
  `]
})
export class HomeComponent {
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
        this.error.set(err.message ?? 'Request failed');
        this.loading.set(false);
      }
    });
  }
}
