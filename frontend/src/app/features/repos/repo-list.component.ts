import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatDividerModule } from '@angular/material/divider';
import { RepoService } from './repo.service';
import { RepoSummary } from './repo.models';

@Component({
  selector: 'app-repo-list',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatChipsModule,
    MatProgressSpinnerModule,
    MatDividerModule,
  ],
  template: `
    <div class="wrap">
      <header>
        <div>
          <h1>Migrations</h1>
          <p class="tag">Every legacy repo you've ingested lives here.</p>
        </div>
        <a mat-flat-button color="primary" routerLink="/repos/new">
          <mat-icon>add</mat-icon>
          New migration
        </a>
      </header>

      @if (loading()) {
        <div class="center"><mat-spinner></mat-spinner></div>
      } @else if (repos().length === 0) {
        <mat-card class="empty">
          <mat-card-content>
            <mat-icon>folder_off</mat-icon>
            <h3>No repositories yet</h3>
            <p>Import a legacy Java repo from GitHub or upload a zip to begin.</p>
            <a mat-flat-button color="primary" routerLink="/repos/new">
              <mat-icon>add</mat-icon>
              Start your first migration
            </a>
          </mat-card-content>
        </mat-card>
      } @else {
        <div class="grid">
          @for (r of repos(); track r.id) {
            <mat-card class="row">
              <mat-card-header>
                <mat-card-title>
                  <a [routerLink]="['/repos', r.id]">{{ r.name }}</a>
                </mat-card-title>
                <mat-card-subtitle>
                  {{ r.sourceType === 'GITHUB' ? r.sourceUrl : 'Uploaded zip' }}
                </mat-card-subtitle>
              </mat-card-header>

              <mat-card-content>
                <mat-chip-set>
                  <mat-chip>{{ r.fileCount }} files</mat-chip>
                  <mat-chip>{{ formatBytes(r.totalSizeBytes) }}</mat-chip>
                  <mat-chip [class.err]="r.status==='FAILED'" [class.ok]="r.status==='READY'">
                    {{ r.status }}
                  </mat-chip>
                </mat-chip-set>
                @if (r.status === 'FAILED' && r.errorMessage) {
                  <p class="error">{{ r.errorMessage }}</p>
                }
              </mat-card-content>

              <mat-card-actions>
                <a mat-button [routerLink]="['/repos', r.id]">Open</a>
                <button mat-button color="warn" (click)="remove(r.id)">Delete</button>
              </mat-card-actions>
            </mat-card>
          }
        </div>
      }
    </div>
  `,
  styles: [`
    .wrap { max-width: 1200px; margin: 0 auto; }
    header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; }
    header h1 { margin: 0 0 4px; letter-spacing: -0.02em; }
    .tag { color: var(--lf-muted); margin: 0; }
    .center { display: grid; place-items: center; padding: 48px; }
    .empty {
      background: var(--lf-bg-elev);
      border: 1px dashed var(--lf-border);
      text-align: center;
      padding: 40px;
    }
    .empty mat-icon { font-size: 48px; width: 48px; height: 48px; color: var(--lf-muted); margin-bottom: 8px; }
    .empty h3 { margin: 0 0 8px; }
    .empty p { color: var(--lf-muted); margin: 0 0 20px; }
    .grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(320px, 1fr)); gap: 16px; }
    .row { background: var(--lf-bg-elev); border: 1px solid var(--lf-border); }
    .row mat-card-title a { color: var(--lf-text); text-decoration: none; }
    .row mat-card-title a:hover { color: var(--lf-accent); }
    .error { color: #ff8080; margin: 12px 0 0; font-size: 0.85rem; }
    .ok { background: rgba(76, 217, 123, 0.15); color: var(--lf-success); }
    .err { background: rgba(255, 95, 95, 0.15); color: #ff8080; }
  `],
})
export class RepoListComponent implements OnInit {
  private service = inject(RepoService);
  repos = signal<RepoSummary[]>([]);
  loading = signal(true);

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.loading.set(true);
    this.service.list().subscribe({
      next: (list) => {
        this.repos.set(list);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  remove(id: string): void {
    if (!confirm('Delete this repository and all its files?')) return;
    this.service.delete(id).subscribe(() => this.reload());
  }

  formatBytes(bytes: number): string {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    if (bytes < 1024 * 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
    return `${(bytes / 1024 / 1024 / 1024).toFixed(2)} GB`;
  }
}
