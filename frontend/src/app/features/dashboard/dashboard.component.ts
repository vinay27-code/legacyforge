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
      <header class="hero">
        <div class="hero-content">
          <div class="hero-eyebrow">
            <span class="pulse-dot"></span>
            Live platform
          </div>
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
          <div class="tile" [style.--tile-color]="'var(--lf-primary)'">
            <div class="tile-icon"><mat-icon>account_tree</mat-icon></div>
            <div class="tile-val">{{ stats()!.counts.repos | number }}</div>
            <div class="tile-label">Repositories</div>
          </div>
          <div class="tile" [style.--tile-color]="'var(--lf-info)'">
            <div class="tile-icon"><mat-icon>description</mat-icon></div>
            <div class="tile-val">{{ stats()!.counts.files | number }}</div>
            <div class="tile-label">Source files</div>
          </div>
          <div class="tile" [style.--tile-color]="'var(--lf-accent)'">
            <div class="tile-icon"><mat-icon>storage</mat-icon></div>
            <div class="tile-val">{{ stats()!.counts.chunks | number }}</div>
            <div class="tile-label">Semantic chunks</div>
          </div>
          <div class="tile" [style.--tile-color]="'var(--lf-primary)'">
            <div class="tile-icon"><mat-icon>auto_awesome</mat-icon></div>
            <div class="tile-val">{{ stats()!.counts.artifacts | number }}</div>
            <div class="tile-label">Generated artifacts</div>
          </div>
          <div class="tile" [style.--tile-color]="'var(--lf-accent)'">
            <div class="tile-icon"><mat-icon>hub</mat-icon></div>
            <div class="tile-val">{{ stats()!.counts.dependencyEdges | number }}</div>
            <div class="tile-label">Dependency edges</div>
            @if (stats()!.counts.brokenEdges > 0) {
              <div class="tile-sub broken">{{ stats()!.counts.brokenEdges }} broken</div>
            }
          </div>
          <div class="tile" [style.--tile-color]="'var(--lf-warning)'">
            <div class="tile-icon"><mat-icon>healing</mat-icon></div>
            <div class="tile-val">{{ stats()!.counts.planPatches | number }}</div>
            <div class="tile-label">Plan patches applied</div>
          </div>
        </section>

        <div class="two-col">
          <div class="panel">
            <div class="panel-head">
              <div class="panel-title">
                <div class="panel-icon accent"><mat-icon>bar_chart</mat-icon></div>
                <div>
                  <h3>Agent output quality</h3>
                  <p>Across every migration run tracked</p>
                </div>
              </div>
            </div>
            <div class="panel-body">
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
            </div>
          </div>

          <div class="panel cost-panel">
            <div class="panel-head">
              <div class="panel-title">
                <div class="panel-icon success"><mat-icon>payments</mat-icon></div>
                <div>
                  <h3>LLM usage</h3>
                  <p>Tokens spent and rough OpenAI cost</p>
                </div>
              </div>
            </div>
            <div class="panel-body">
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
                    <span class="tb-slash">/</span>
                    {{ formatTokens(stats()!.tokens.planningOutputTokens) }}
                  </span>
                </div>
                <div class="tb-row">
                  <span class="tb-key">Agents (in / out)</span>
                  <span class="tb-val">
                    {{ formatTokens(stats()!.tokens.agentPromptTokens) }}
                    <span class="tb-slash">/</span>
                    {{ formatTokens(stats()!.tokens.agentOutputTokens) }}
                  </span>
                </div>
                <div class="tb-divider"></div>
                <div class="tb-row total">
                  <span class="tb-key">Total tokens</span>
                  <span class="tb-val">{{ formatTokens(stats()!.tokens.totalTokens) }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div class="panel">
          <div class="panel-head">
            <div class="panel-title">
              <div class="panel-icon primary"><mat-icon>history</mat-icon></div>
              <div>
                <h3>Recent activity</h3>
                <p>Latest platform events across every repo</p>
              </div>
            </div>
          </div>
          <div class="panel-body">
            @if (stats()!.recent.length === 0) {
              <p class="empty">Nothing yet. Ingest a repository to get started.</p>
            } @else {
              <div class="feed">
                @for (a of stats()!.recent; track $index) {
                  <div class="feed-row">
                    <div class="feed-icon-wrap" [class]="'k-' + a.kind.toLowerCase()">
                      <mat-icon>
                        @switch (a.kind) {
                          @case ('PLAN') { auto_awesome }
                          @case ('AGENT_RUN') { psychology }
                          @default { cloud_upload }
                        }
                      </mat-icon>
                    </div>
                    <div class="feed-body">
                      <div class="feed-line"><strong>{{ a.repoName }}</strong> · {{ a.summary }}</div>
                      <div class="feed-when">{{ relativeTime(a.when) }}</div>
                    </div>
                    <span class="feed-kind" [class]="'k-' + a.kind.toLowerCase()">{{ prettyKind(a.kind) }}</span>
                  </div>
                }
              </div>
            }
          </div>
        </div>

        <div class="cta">
          <a mat-flat-button color="primary" routerLink="/repos">
            <mat-icon>account_tree</mat-icon> View all migrations
          </a>
        </div>
      }
    </div>
  `,
  styles: [`
    :host { display: block; position: relative; z-index: 1; }
    .page { max-width: 1280px; margin: 0 auto; padding: 24px 20px 60px; color: var(--lf-text); }

    /* Hero */
    .hero { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 40px; padding-top: 16px; }
    .hero-content { display: flex; flex-direction: column; gap: 12px; }
    .hero-eyebrow {
      display: inline-flex; align-items: center; gap: 8px; align-self: flex-start;
      padding: 5px 12px; border-radius: 999px;
      background: var(--lf-success-glow);
      border: 1px solid rgba(16, 185, 129, 0.3);
      color: var(--lf-success);
      font-size: 0.75rem; font-weight: 600; letter-spacing: 0.04em; text-transform: uppercase;
    }
    .pulse-dot {
      width: 6px; height: 6px; border-radius: 50%;
      background: var(--lf-success);
      box-shadow: 0 0 8px var(--lf-success);
      animation: pulse 2s ease-in-out infinite;
    }
    @keyframes pulse { 0%, 100% { opacity: 1; } 50% { opacity: 0.4; } }
    .hero h1 { font-size: 3rem; line-height: 1; }
    .lede { color: var(--lf-text-muted); margin: 0; font-size: 1.05rem; }

    .center { display: grid; place-items: center; padding: 120px; }

    /* Tiles */
    .tiles { display: grid; grid-template-columns: repeat(auto-fit, minmax(190px, 1fr)); gap: 14px; margin-bottom: 28px; }
    .tile {
      position: relative; overflow: hidden;
      background: var(--lf-bg-elev);
      border: 1px solid var(--lf-border);
      border-radius: var(--lf-radius-lg);
      padding: 18px 20px;
      display: flex; flex-direction: column; gap: 6px;
      transition: border-color var(--lf-t-fast), transform var(--lf-t-fast);
    }
    .tile::before {
      content: '';
      position: absolute; top: 0; left: 0; right: 0; height: 2px;
      background: var(--tile-color);
      opacity: 0.5;
      transition: opacity var(--lf-t-med);
    }
    .tile:hover { border-color: var(--lf-border-strong); transform: translateY(-1px); }
    .tile:hover::before { opacity: 1; }
    .tile-icon {
      color: var(--tile-color);
      background: color-mix(in srgb, var(--tile-color) 12%, transparent);
      width: 36px; height: 36px; border-radius: 10px;
      display: grid; place-items: center;
      margin-bottom: 8px;
    }
    .tile-icon mat-icon { font-size: 20px; height: 20px; width: 20px; }
    .tile-val { font-family: var(--lf-font-display); font-size: 2.1rem; font-weight: 700; line-height: 1; letter-spacing: -0.025em; }
    .tile-label { color: var(--lf-text-muted); font-size: 0.85rem; font-weight: 500; }
    .tile-sub { font-size: 0.72rem; margin-top: 2px; font-weight: 500; }
    .tile-sub.broken { color: var(--lf-danger); }

    /* Panels */
    .two-col { display: grid; grid-template-columns: 1.4fr 1fr; gap: 16px; margin-bottom: 20px; }
    @media (max-width: 900px) { .two-col { grid-template-columns: 1fr; } }

    .panel {
      background: var(--lf-bg-elev);
      border: 1px solid var(--lf-border);
      border-radius: var(--lf-radius-lg);
      margin-bottom: 20px;
      overflow: hidden;
    }
    .panel-head { padding: 20px 24px 16px; border-bottom: 1px solid var(--lf-border); }
    .panel-title { display: flex; align-items: center; gap: 14px; }
    .panel-icon {
      width: 38px; height: 38px; border-radius: 10px;
      display: grid; place-items: center;
    }
    .panel-icon.primary  { background: var(--lf-primary-glow); color: var(--lf-primary); }
    .panel-icon.accent   { background: var(--lf-accent-glow);  color: var(--lf-accent); }
    .panel-icon.success  { background: var(--lf-success-glow); color: var(--lf-success); }
    .panel-icon mat-icon { font-size: 20px; height: 20px; width: 20px; }
    .panel-title h3 { font-size: 1.15rem; margin: 0; }
    .panel-title p { margin: 2px 0 0; color: var(--lf-text-muted); font-size: 0.85rem; font-family: var(--lf-font-sans); font-weight: 400; }
    .panel-body { padding: 20px 24px 24px; }

    /* Quality bars */
    .quality-bar { display: flex; flex-direction: column; gap: 12px; }
    .qb-row { display: grid; grid-template-columns: 140px 1fr 70px; align-items: center; gap: 12px; }
    .qb-label { display: flex; align-items: center; gap: 8px; font-size: 0.87rem; color: var(--lf-text-muted); }
    .qb-label mat-icon { font-size: 14px; height: 14px; width: 14px; }
    .qb-track { height: 6px; background: rgba(255,255,255,0.04); border-radius: 3px; overflow: hidden; }
    .qb-fill { height: 100%; border-radius: 3px; transition: width 0.4s var(--lf-ease-out); }
    .qb-val { font-family: var(--lf-font-mono); text-align: right; font-size: 0.85rem; font-weight: 500; }

    .quality-notes { display: flex; gap: 24px; margin-top: 22px; padding-top: 18px; border-top: 1px solid var(--lf-border); flex-wrap: wrap; }
    .qn { display: flex; align-items: center; gap: 6px; color: var(--lf-text-muted); font-size: 0.85rem; }
    .qn mat-icon { font-size: 14px; height: 14px; width: 14px; }

    /* Cost panel */
    .big-cost {
      background: var(--lf-gradient-cost);
      -webkit-background-clip: text; background-clip: text;
      -webkit-text-fill-color: transparent; color: transparent;
      font-family: var(--lf-font-display);
      font-size: 3.2rem; font-weight: 800; letter-spacing: -0.04em; line-height: 1;
    }
    .big-cost-sub { color: var(--lf-text-muted); font-size: 0.85rem; margin-top: 6px; margin-bottom: 22px; }

    .token-breakdown { display: flex; flex-direction: column; gap: 10px; }
    .tb-row { display: flex; justify-content: space-between; font-size: 0.88rem; }
    .tb-key { color: var(--lf-text-muted); }
    .tb-val { font-family: var(--lf-font-mono); color: var(--lf-text); font-weight: 500; }
    .tb-slash { color: var(--lf-text-dim); margin: 0 2px; }
    .tb-divider { border-top: 1px solid var(--lf-border); margin: 6px 0; }
    .tb-row.total .tb-val, .tb-row.total .tb-key { font-weight: 600; color: var(--lf-text); font-size: 0.95rem; }

    /* Feed */
    .empty { color: var(--lf-text-muted); text-align: center; padding: 24px; }
    .feed { display: flex; flex-direction: column; gap: 6px; }
    .feed-row {
      display: flex; align-items: center; gap: 14px;
      padding: 12px 16px; border-radius: var(--lf-radius-md);
      transition: background var(--lf-t-fast);
    }
    .feed-row:hover { background: rgba(255,255,255,0.02); }
    .feed-icon-wrap {
      width: 34px; height: 34px; border-radius: 10px;
      display: grid; place-items: center; flex: 0 0 auto;
    }
    .feed-icon-wrap.k-plan      { background: var(--lf-accent-glow); color: var(--lf-accent); }
    .feed-icon-wrap.k-agent_run { background: var(--lf-info-glow);   color: var(--lf-info); }
    .feed-icon-wrap.k-index     { background: var(--lf-success-glow); color: var(--lf-success); }
    .feed-icon-wrap mat-icon { font-size: 18px; height: 18px; width: 18px; }

    .feed-body { flex: 1; min-width: 0; }
    .feed-line { font-size: 0.92rem; }
    .feed-line strong { font-weight: 600; }
    .feed-when { color: var(--lf-text-dim); font-size: 0.78rem; margin-top: 2px; }
    .feed-kind {
      font-size: 0.7rem; font-weight: 600; padding: 3px 10px; border-radius: 999px;
      letter-spacing: 0.03em; text-transform: uppercase;
    }
    .feed-kind.k-plan      { background: var(--lf-accent-glow);  color: var(--lf-accent); }
    .feed-kind.k-agent_run { background: var(--lf-info-glow);    color: var(--lf-info); }
    .feed-kind.k-index     { background: var(--lf-success-glow); color: var(--lf-success); }

    .cta { display: flex; justify-content: center; margin-top: 32px; }
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
      { label: 'Success',  value: a.success,  pct: (a.success  / total) * 100,             color: 'var(--lf-success)', icon: 'check_circle' },
      { label: 'Failed',   value: a.failed,   pct: (a.failed   / total) * 100,             color: 'var(--lf-danger)',  icon: 'error' },
      { label: 'Valid',    value: a.valid,    pct: (a.valid    / Math.max(a.success, 1)) * 100, color: 'var(--lf-success)', icon: 'verified' },
      { label: 'Invalid',  value: a.invalid,  pct: (a.invalid  / Math.max(a.success, 1)) * 100, color: 'var(--lf-danger)',  icon: 'report' },
      { label: 'Skipped',  value: a.skipped,  pct: (a.skipped  / Math.max(a.success, 1)) * 100, color: 'var(--lf-text-dim)', icon: 'block' },
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
