import { Component, Input, OnChanges, SimpleChanges, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatChipsModule } from '@angular/material/chips';
import { MatTooltipModule } from '@angular/material/tooltip';
import { SearchService } from './search.service';
import { IndexStatus, SearchHit } from './search.models';

@Component({
  selector: 'app-search-tab',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatChipsModule,
    MatTooltipModule,
  ],
  template: `
    <div class="wrap">
      @if (statusLoading()) {
        <div class="center"><mat-spinner></mat-spinner></div>
      } @else if (!status() || status()!.chunkCount === 0) {
        <mat-card class="empty">
          <mat-card-content>
            <mat-icon>search</mat-icon>
            <h3>Not indexed yet</h3>
            <p>
              Chunk every file at method boundaries, embed each chunk with
              <code>{{ status()?.provider ?? 'the configured model' }}</code>,
              and store the vectors in pgvector so we can find similar code by meaning.
            </p>
            <button mat-flat-button color="primary" (click)="reindex()" [disabled]="indexing()">
              @if (indexing()) {
                <mat-spinner diameter="20"></mat-spinner>
              } @else {
                <mat-icon>bolt</mat-icon>
                Build search index
              }
            </button>
            @if (errorMessage()) {
              <p class="error">{{ errorMessage() }}</p>
            }
          </mat-card-content>
        </mat-card>
      } @else {
        <div class="head">
          <div class="meta">
            <mat-chip-set>
              <mat-chip><mat-icon>storage</mat-icon> {{ status()!.chunkCount }} chunks</mat-chip>
              <mat-chip><mat-icon>psychology</mat-icon> {{ status()!.provider }}</mat-chip>
            </mat-chip-set>
          </div>
          <button mat-stroked-button (click)="reindex()" [disabled]="indexing()">
            @if (indexing()) {
              <mat-spinner diameter="18"></mat-spinner>
            } @else {
              <mat-icon>refresh</mat-icon> Rebuild
            }
          </button>
        </div>

        <mat-card class="search-card">
          <mat-card-content>
            <mat-form-field appearance="outline" class="full">
              <mat-label>Ask a question about the code</mat-label>
              <input
                matInput
                [(ngModel)]="query"
                (keydown.enter)="run()"
                placeholder="e.g. where is the user login logic?"
              />
              <button matSuffix mat-icon-button (click)="run()" [disabled]="running() || !query.trim()">
                <mat-icon>search</mat-icon>
              </button>
            </mat-form-field>
            @if (errorMessage()) {
              <p class="error">{{ errorMessage() }}</p>
            }
          </mat-card-content>
        </mat-card>

        @if (running()) {
          <div class="center"><mat-spinner diameter="40"></mat-spinner></div>
        } @else if (hits().length > 0) {
          <div class="hits">
            @for (hit of hits(); track hit.id) {
              <mat-card class="hit">
                <mat-card-header>
                  <mat-card-title>
                    <mat-icon>description</mat-icon>
                    <span class="path">{{ hit.filePath }}</span>
                    @if (hit.methodName) {
                      <span class="loc">::{{ hit.methodName }}()</span>
                    } @else if (hit.className) {
                      <span class="loc">::{{ hit.className }}</span>
                    }
                  </mat-card-title>
                  <mat-card-subtitle>
                    <span [matTooltip]="'Cosine similarity: ' + hit.similarity.toFixed(4)">
                      {{ (hit.similarity * 100).toFixed(1) }}% match
                    </span>
                    @if (hit.startLine) {
                      · lines {{ hit.startLine }}–{{ hit.endLine }}
                    }
                    · {{ hit.chunkType }}
                  </mat-card-subtitle>
                </mat-card-header>
                <mat-card-content>
                  <pre class="snippet"><code>{{ hit.content }}</code></pre>
                </mat-card-content>
              </mat-card>
            }
          </div>
        } @else if (hasSearched()) {
          <div class="empty-hits">No matching chunks.</div>
        }
      }
    </div>
  `,
  styles: [`
    .wrap { max-width: 1100px; margin: 0 auto; }
    .center { display: grid; place-items: center; padding: 60px; }
    .empty { max-width: 560px; margin: 40px auto; text-align: center; padding: 24px; }
    .empty mat-icon { font-size: 48px; width: 48px; height: 48px; color: var(--lf-accent); margin-bottom: 8px; }
    .empty h3 { margin: 4px 0 8px; }
    .empty p { color: var(--lf-muted); margin: 0 0 20px; }
    .error { color: #ff8080; margin: 12px 0 0; }

    .head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
    .meta mat-chip mat-icon { font-size: 16px; height: 16px; width: 16px; margin-right: 4px; vertical-align: -3px; }

    .search-card { background: var(--lf-bg-elev); border: 1px solid var(--lf-border); margin-bottom: 16px; }
    .full { width: 100%; }

    .hits { display: flex; flex-direction: column; gap: 12px; }
    .hit { background: var(--lf-bg-elev); border: 1px solid var(--lf-border); }
    .hit mat-card-title {
      display: flex; align-items: center; gap: 8px;
      font-family: 'SF Mono', monospace; font-size: 0.95rem;
    }
    .hit .path { color: var(--lf-text); }
    .hit .loc { color: var(--lf-accent); }
    .snippet {
      margin: 0;
      padding: 12px;
      background: #0d1220;
      border-radius: 8px;
      max-height: 300px;
      overflow: auto;
      font-family: 'SF Mono', monospace;
      font-size: 0.82rem;
      line-height: 1.5;
      color: #e6ebff;
      white-space: pre;
    }
    .empty-hits { padding: 40px; text-align: center; color: var(--lf-muted); }
  `],
})
export class SearchTabComponent implements OnChanges {
  @Input({ required: true }) repoId!: string;

  private service = inject(SearchService);

  status = signal<IndexStatus | null>(null);
  statusLoading = signal(true);
  indexing = signal(false);
  running = signal(false);
  hits = signal<SearchHit[]>([]);
  errorMessage = signal<string | null>(null);
  hasSearched = signal(false);
  query = '';

  ngOnChanges(_: SimpleChanges): void {
    this.loadStatus();
  }

  loadStatus(): void {
    this.statusLoading.set(true);
    this.service.status(this.repoId).subscribe({
      next: (s) => { this.status.set(s); this.statusLoading.set(false); },
      error: () => this.statusLoading.set(false),
    });
  }

  reindex(): void {
    this.indexing.set(true);
    this.errorMessage.set(null);
    this.service.reindex(this.repoId).subscribe({
      next: (s) => { this.status.set(s); this.indexing.set(false); },
      error: (err) => {
        this.errorMessage.set(err?.error?.message ?? 'Index build failed');
        this.indexing.set(false);
      },
    });
  }

  run(): void {
    const q = this.query.trim();
    if (!q) return;
    this.running.set(true);
    this.errorMessage.set(null);
    this.hasSearched.set(true);
    this.service.search(this.repoId, { query: q, k: 10 }).subscribe({
      next: (hits) => { this.hits.set(hits); this.running.set(false); },
      error: (err) => {
        this.errorMessage.set(err?.error?.message ?? 'Search failed');
        this.running.set(false);
      },
    });
  }
}
