import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { DashboardService } from './dashboard.service';
import { PlatformStats } from './dashboard.models';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    MatCardModule,
    MatIconModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
  ],
  template: `
    <div class="page">
      <header>
        <div>
          <h1>Dashboard</h1>
          <p class="lede">Everything the LegacyForge platform has done, at a glance.</p>
        </div>
        <button mat-stroked-button (click)="load()" [disabled]="loading()">
          <mat-icon>refresh</mat-icon> Refresh
        </button>
      </header>

      @if (loading()) {
        <div class="center"><mat-spinner></mat-spinner></div>
      } @else if (stats()) {
        <section class="tiles">
          <div class="tile">
            <div class="tile-icon"><mat-icon>account_tree</mat-icon></div>
            <div class="tile-val">{{ stats()!.counts.repos | number }}</div>
            <div class="tile-label">Repositories</div>
          </div>
          <div class="tile">
            <div class="tile-icon"><mat-icon>description</mat-icon></div>
            <div class="tile-val">{{ stats()!.counts.files | number }}</div>
            <div class="tile-label">Source files</div>
          </div>
          <div class="tile">
            <div class="tile-icon"><mat-icon>storage</mat-icon></div>
            <div class="tile-val">{{ stats()!.counts.chunks | number }}</div>
            <div class="tile-label">Semantic chunks</div>
          </div>
          <div class="tile">
            <div class="tile-icon"><mat-icon>auto_awesome</mat-icon></div>
            <div class="tile-val">{{ stats()!.counts.artifacts | number }}</div>
            <div class="tile-label">Generated artifacts</div>
          </div>
          <div class="tile">
            <div class="tile-icon"><mat-icon>hub</mat-icon></div>
            <div class="tile-val">{{ stats()!.counts.dependencyEdges | number }}</div>
            <div class="tile-label">Dependency edges</div>
            @if (stats()!.counts.brokenEdges > 0) {
              <div class="tile-sub broken">{{ stats()!.counts.brokenEdges }} broken</div>
            }
          </div>
          <div class="tile">
            <div class="tile-icon"><mat-icon>healing</mat-icon></div>
            <div class="tile-val">{{ stats()!.counts.planPatches | number }}</div>
            <div class="tile-label">Plan patches applied</div>
          </div>
        </section>

        <div class="two-col">
          <mat-card class="panel">
            <mat-card-header>
              <mat-card-title>
                <mat-icon>bar_chart</mat-icon>
                Agent output quality
              </mat-card-title>
              <mat-card-subtitle>
                Across every migration run tracked
              </mat-card-subtitle>
            </mat-card-header>
            <mat-card-content>
              <div class="quality-bar">
                @for (b of qualityBars(); track b.label) {
                  <div class="qb-row">
                    <div class="qb-label">
                      <mat-icon [style.color]="b.color">{{ b.icon }}</mat-icon>
                      {{ b.label }}
                    </div>
                    <div class="qb-track">
                      <div class="qb-fill" [style.width.%]="b.pct" [style.background]="b.color"></div>
                    </div>
                    <div class="qb-val">{{ b.value | number }}</div>
                  </div>
                }
              </div>
              <div class="quality-notes">
                <span class="qn">
                  <mat-icon>healing</mat-icon> {{ stats()!.artifacts.retriesTotal }} self-healing retries fired
                </span>
                <span class="qn">
                  <mat-icon>call_split</mat-icon> {{ stats()!.artifacts.derived }} derived from multi-target splits
                </span>
              </div>
            </mat-card-content>
          </mat-card>

          <mat-card class="panel cost-panel">
            <mat-card-header>
              <mat-card-title>
                <mat-icon>payments</mat-icon>
                LLM usage
              </mat-card-title>
              <mat-card-subtitle>
                Tokens spent and rough OpenAI cost
              </mat-card-subtitle>
            </mat-card-header>
            <mat-card-content>
              <div class="big-cost">\${{ stats()!.tokens.estimatedCostUsd | number:'1.2-2' }}</div>
              <div class="big-cost-sub">estimated OpenAI spend so far</div>

              <div class="token-breakdown">
                <div class="tb-row">
                  <span class="tb-key">Embedding</span>
                  <span class="tb-val">{{ formatTokens(stats()!.tokens.embeddingTokens) }}</span>
                </div>
                <div class="tb-row">
                  <span class="tb-key">Planning (in / out)</span>
                  <span class="tb-val">
                    {{ formatTokens(stats()!.tokens.planningPromptTokens) }}
                    / {{ formatTokens(stats()!.tokens.planningOutputTokens) }}
                  </span>
                </div>
                <div class="tb-row">
                  <span class="tb-key">Agents (in / out)</span>
                  <span class="tb-val">
                    {{ formatTokens(stats()!.tokens.agentPromptTokens) }}
                    / {{ formatTokens(stats()!.tokens.agentOutputTokens) }}
                  </span>
                </div>
                <div class="tb-divider"></div>
                <div class="tb-row total">
                  <span class="tb-key">Total tokens</span>
                  <span class="tb-val">{{ formatTokens(stats()!.tokens.totalTokens) }}</span>
                </div>
              </div>
            </mat-card-content>
          </mat-card>
        </div>

        <mat-card class="panel">
          <mat-card-header>
            <mat-card-title>
              <mat-icon>history</mat-icon>
              Recent activity
            </mat-card-title>
          </mat-card-header>
          <mat-card-content>
            @if (stats()!.recent.length === 0) {
              <p class="empty">Nothing yet. Ingest a repository to get started.</p>
            } @else {
              <div class="feed">
                @for (a of stats()!.recent; track $index) {
                  <div class="feed-row">
                    <mat-icon class="feed-icon" [class]="'k-' + a.kind.toLowerCase()">
                      @switch (a.kind) {
                        @case ('PLAN') { auto_awesome }
                        @case ('AGENT_RUN') { psychology }
                        @default { cloud_upload }
                      }
                    </mat-icon>
                    <div class="feed-body">
                      <div class="feed-line"><strong>{{ a.repoName }}</strong> · {{ a.summary }}</div>
                      <div class="feed-when">{{ relativeTime(a.when) }}</div>
                    </div>
                    <span class="feed-kind" [class]="'k-' + a.kind.toLowerCase()">{{ prettyKind(a.kind) }}</span>
                  </div>
                }
              </div>
            }
          </mat-card-content>
        </mat-card>

        <div class="cta">
          <a mat-flat-button color="primary" routerLink="/repos">
            <mat-icon>account_tree</mat-icon> View all migrations
          </a>
        </div>
      }
    </div>
  `,
  styles: [`
    .page { max-width: 1200px; margin: 0 auto; padding: 8px 4px; color: var(--lf-text); }
    header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 24px; }
    h1 { margin: 0; letter-spacing: -0.02em; }
    .lede { color: var(--lf-muted); margin: 4px 0 0; }
    .center { display: grid; place-items: center; padding: 80px; }

    .tiles { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 12px; margin-bottom: 20px; }
    .tile {
      background: var(--lf-bg-elev);
      border: 1px solid var(--lf-border);
      border-radius: 12px;
      padding: 16px 18px;
      display: flex; flex-direction: column; gap: 4px;
    }
    .tile-icon { color: var(--lf-accent); }
    .tile-icon mat-icon { font-size: 22px; height: 22px; width: 22px; }
    .tile-val { font-size: 2rem; font-weight: 600; line-height: 1.1; letter-spacing: -0.02em; }
    .tile-label { color: var(--lf-muted); font-size: 0.85rem; }
    .tile-sub { font-size: 0.75rem; margin-top: 2px; }
    .tile-sub.broken { color: #ff8080; }

    .two-col { display: grid; grid-template-columns: 1.4fr 1fr; gap: 16px; margin-bottom: 20px; }
    @media (max-width: 900px) { .two-col { grid-template-columns: 1fr; } }

    .panel { background: var(--lf-bg-elev); border: 1px solid var(--lf-border); }
    .panel mat-card-title { display: flex; align-items: center; gap: 8px; }
    .panel mat-card-title mat-icon { color: var(--lf-accent); }

    .quality-bar { display: flex; flex-direction: column; gap: 10px; margin-top: 8px; }
    .qb-row { display: grid; grid-template-columns: 140px 1fr 80px; align-items: center; gap: 10px; }
    .qb-label { display: flex; align-items: center; gap: 6px; font-size: 0.85rem; color: var(--lf-muted); }
    .qb-label mat-icon { font-size: 14px; height: 14px; width: 14px; }
    .qb-track { height: 8px; background: rgba(255,255,255,0.05); border-radius: 4px; overflow: hidden; }
    .qb-fill { height: 100%; border-radius: 4px; transition: width 0.3s; }
    .qb-val { font-family: 'SF Mono', monospace; text-align: right; font-size: 0.85rem; }

    .quality-notes { display: flex; gap: 20px; margin-top: 20px; padding-top: 16px; border-top: 1px solid var(--lf-border); flex-wrap: wrap; }
    .qn { display: flex; align-items: center; gap: 6px; color: var(--lf-muted); font-size: 0.85rem; }
    .qn mat-icon { font-size: 14px; height: 14px; width: 14px; }

    .cost-panel .big-cost { font-size: 2.8rem; font-weight: 700; letter-spacing: -0.03em; color: #2ecc71; line-height: 1; }
    .cost-panel .big-cost-sub { color: var(--lf-muted); font-size: 0.85rem; margin-top: 4px; margin-bottom: 20px; }

    .token-breakdown { display: flex; flex-direction: column; gap: 8px; }
    .tb-row { display: flex; justify-content: space-between; font-size: 0.9rem; }
    .tb-key { color: var(--lf-muted); }
    .tb-val { font-family: 'SF Mono', monospace; color: var(--lf-text); }
    .tb-divider { border-top: 1px solid var(--lf-border); margin: 4px 0; }
    .tb-row.total .tb-val, .tb-row.total .tb-key { font-weight: 600; color: var(--lf-text); }

    .empty { color: var(--lf-muted); text-align: center; padding: 20px; }
    .feed { display: flex; flex-direction: column; gap: 8px; }
    .feed-row {
      display: flex; align-items: center; gap: 12px;
      padding: 10px 14px; border-radius: 8px;
      background: rgba(0,0,0,0.15);
    }
    .feed-icon { flex: 0 0 auto; }
    .feed-icon.k-plan { color: #b28eff; }
    .feed-icon.k-agent_run { color: #6cb0ff; }
    .feed-icon.k-index { color: #2ecc71; }
    .feed-body { flex: 1; min-width: 0; }
    .feed-line { font-size: 0.9rem; }
    .feed-when { color: var(--lf-muted); font-size: 0.75rem; margin-top: 2px; }
    .feed-kind {
      font-size: 0.7rem; font-weight: 600; padding: 2px 8px; border-radius: 999px;
      background: rgba(255,255,255,0.06); color: var(--lf-muted);
    }
    .feed-kind.k-plan { background: rgba(178,142,255,0.15); color: #b28eff; }
    .feed-kind.k-agent_run { background: rgba(108,176,255,0.15); color: #6cb0ff; }
    .feed-kind.k-index { background: rgba(46,204,113,0.15); color: #2ecc71; }

    .cta { display: flex; justify-content: center; margin-top: 24px; }
  `],
})
export class DashboardComponent implements OnInit {
  private service = inject(DashboardService);

  loading = signal(true);
  stats = signal<PlatformStats | null>(null);

  ngOnInit(): void { this.load(); }

  load(): void {
    this.loading.set(true);
    this.service.platform().subscribe({
      next: (s) => { this.stats.set(s); this.loading.set(false); },
      error: () => { this.loading.set(false); },
    });
  }

  qualityBars = computed(() => {
    const s = this.stats();
    if (!s) return [];
    const a = s.artifacts;
    const total = a.success + a.failed || 1;
    return [
      { label: 'Success',  value: a.success,  pct: (a.success  / total) * 100, color: '#2ecc71', icon: 'check_circle' },
      { label: 'Failed',   value: a.failed,   pct: (a.failed   / total) * 100, color: '#ff6b5c', icon: 'error' },
      { label: 'Valid',    value: a.valid,    pct: (a.valid    / Math.max(a.success, 1)) * 100, color: '#2ecc71', icon: 'verified' },
      { label: 'Invalid',  value: a.invalid,  pct: (a.invalid  / Math.max(a.success, 1)) * 100, color: '#ff6b5c', icon: 'report' },
      { label: 'Skipped',  value: a.skipped,  pct: (a.skipped  / Math.max(a.success, 1)) * 100, color: '#b0b0b0', icon: 'block' },
    ];
  });

  formatTokens(n: number): string {
    if (n >= 1_000_000) return (n / 1_000_000).toFixed(2) + 'M';
    if (n >= 1_000) return (n / 1_000).toFixed(1) + 'K';
    return String(n);
  }

  prettyKind(k: string): string {
    switch (k) {
      case 'AGENT_RUN': return 'Agent run';
      case 'PLAN':      return 'Plan';
      case 'INDEX':     return 'Ingest';
      default: return k;
    }
  }

  relativeTime(iso: string): string {
    const then = new Date(iso).getTime();
    const now = Date.now();
    const diffSec = Math.floor((now - then) / 1000);
    if (diffSec < 60) return `${diffSec}s ago`;
    if (diffSec < 3600) return `${Math.floor(diffSec / 60)}m ago`;
    if (diffSec < 86400) return `${Math.floor(diffSec / 3600)}h ago`;
    return `${Math.floor(diffSec / 86400)}d ago`;
  }
}
