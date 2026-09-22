import { Component, Inject, Input, OnChanges, SimpleChanges, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatChipsModule } from '@angular/material/chips';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatDialog, MatDialogModule, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatSelectModule } from '@angular/material/select';
import { AgentsService } from './agents.service';
import { ArtifactDetail, ArtifactSummary, RunSummary } from './agents.models';

@Component({
  selector: 'app-artifact-diff-dialog',
  standalone: true,
  imports: [CommonModule, MatDialogModule, MatIconModule, MatButtonModule, MatChipsModule],
  template: `
    <div class="dlg">
      <div class="dlg-head">
        <div>
          <div class="dlg-title">
            <mat-icon>swap_horiz</mat-icon>
            <span class="path-from">{{ data.filePath }}</span>
            <mat-icon>arrow_forward</mat-icon>
            <span class="path-to">{{ data.targetPath || '(no target path)' }}</span>
          </div>
          <div class="dlg-meta">
            Phase {{ data.phaseNumber }} · {{ data.phaseTitle }}
            @if (data.promptTokens) { · {{ data.promptTokens }} in / {{ data.outputTokens }} out tokens }
          </div>
        </div>
        <button mat-icon-button (click)="close()">
          <mat-icon>close</mat-icon>
        </button>
      </div>

      <div class="split">
        <div class="pane">
          <div class="pane-head"><mat-icon>history_edu</mat-icon> Legacy</div>
          <pre class="code"><code>{{ data.originalCode || '(source unavailable)' }}</code></pre>
        </div>
        <div class="pane">
          <div class="pane-head"><mat-icon>auto_awesome</mat-icon> Generated</div>
          <pre class="code"><code>{{ data.generatedCode || '(no output)' }}</code></pre>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .dlg { display: flex; flex-direction: column; height: 100%; min-width: 900px; max-width: 90vw; }
    .dlg-head {
      display: flex; justify-content: space-between; align-items: flex-start;
      padding: 12px 16px 8px; border-bottom: 1px solid var(--lf-border);
    }
    .dlg-title {
      display: flex; align-items: center; gap: 8px;
      font-family: 'SF Mono', monospace; font-size: 0.95rem;
    }
    .dlg-title mat-icon { color: var(--lf-accent); }
    .path-from { color: var(--lf-muted); }
    .path-to { color: var(--lf-text); font-weight: 500; }
    .dlg-meta { color: var(--lf-muted); font-size: 0.8rem; margin-top: 4px; }
    .split { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; padding: 8px; height: 70vh; }
    .pane { display: flex; flex-direction: column; overflow: hidden; border: 1px solid var(--lf-border); border-radius: 8px; }
    .pane-head {
      display: flex; align-items: center; gap: 6px;
      padding: 8px 12px; background: rgba(0,0,0,0.3);
      color: var(--lf-muted); font-size: 0.85rem;
      border-bottom: 1px solid var(--lf-border);
    }
    .pane-head mat-icon { font-size: 16px; height: 16px; width: 16px; }
    .code {
      flex: 1; margin: 0; padding: 12px 16px; overflow: auto;
      background: #0d1220; color: #e6ebff;
      font-family: 'SF Mono', Menlo, Monaco, monospace;
      font-size: 0.82rem; line-height: 1.5; white-space: pre;
    }
  `],
})
export class ArtifactDiffDialogComponent {
  constructor(
    public dialogRef: MatDialogRef<ArtifactDiffDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: ArtifactDetail,
  ) {}
  close() { this.dialogRef.close(); }
}

@Component({
  selector: 'app-agents-tab',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatChipsModule,
    MatTooltipModule,
    MatSelectModule,
  ],
  template: `
    <div class="wrap">
      @if (loading()) {
        <div class="center"><mat-spinner></mat-spinner></div>
      } @else if (!summary() || summary()!.total === 0) {
        <mat-card class="empty">
          <mat-card-content>
            <mat-icon>psychology</mat-icon>
            <h3>No generated code yet</h3>
            <p>
              Spawn one LLM agent per file in the migration plan, in parallel.
              Each agent takes a legacy file and its plan entry and produces the
              modernized Spring Boot 3 / Angular 18 equivalent.
            </p>
            <button mat-flat-button color="primary" (click)="run()" [disabled]="running()">
              @if (running()) {
                <mat-spinner diameter="20"></mat-spinner>
                Running agents... (30-90s)
              } @else {
                <mat-icon>bolt</mat-icon>
                Run migration agents
              }
            </button>
            @if (errorMessage()) { <p class="error">{{ errorMessage() }}</p> }
          </mat-card-content>
        </mat-card>
      } @else {
        <div class="head">
          <div class="meta">
            <mat-chip-set>
              <mat-chip><mat-icon>description</mat-icon> {{ summary()!.total }} files</mat-chip>
              <mat-chip class="s-success"><mat-icon>check_circle</mat-icon> {{ summary()!.success }} success</mat-chip>
              @if (summary()!.failed > 0) {
                <mat-chip class="s-failed"><mat-icon>error</mat-icon> {{ summary()!.failed }} failed</mat-chip>
              }
              @if (running() || summary()!.running + summary()!.pending > 0) {
                <mat-chip class="s-running"><mat-icon>autorenew</mat-icon> {{ summary()!.running + summary()!.pending }} in flight</mat-chip>
              }
            </mat-chip-set>
          </div>
          <div class="controls">
            <label>Filter:
              <select [(ngModel)]="filter" (change)="applyFilter()">
                <option value="all">All</option>
                <option value="success">Success</option>
                <option value="failed">Failed</option>
                <option value="high">HIGH risk only</option>
              </select>
            </label>
            <button mat-stroked-button (click)="run()" [disabled]="running()">
              @if (running()) {
                <mat-spinner diameter="18"></mat-spinner>
              } @else {
                <mat-icon>refresh</mat-icon> Rerun
              }
            </button>
          </div>
        </div>

        @for (phase of phases(); track phase.number) {
          <mat-card class="phase-card">
            <mat-card-header>
              <mat-card-title>
                <span class="phase-num">{{ phase.number }}</span> {{ phase.title }}
              </mat-card-title>
              <mat-card-subtitle>
                {{ phase.files.length }} files
              </mat-card-subtitle>
            </mat-card-header>
            <mat-card-content>
              <div class="files">
                @for (a of phase.files; track a.id) {
                  <div class="file-row" (click)="openDetail(a)"
                       [matTooltip]="a.status === 'FAILED' ? (a.errorMessage || 'Failed') : 'Click to view diff'">
                    <mat-icon class="status-icon" [class]="'s-' + a.status.toLowerCase()">
                      @switch (a.status) {
                        @case ('SUCCESS') { check_circle }
                        @case ('FAILED') { error }
                        @case ('RUNNING') { autorenew }
                        @default { schedule }
                      }
                    </mat-icon>
                    <div class="file-info">
                      <div class="paths">
                        <span class="from">{{ a.filePath }}</span>
                        @if (a.targetPath) {
                          <mat-icon class="arrow">arrow_forward</mat-icon>
                          <span class="to">{{ a.targetPath }}</span>
                        }
                      </div>
                      @if (a.errorMessage) {
                        <div class="err">{{ a.errorMessage }}</div>
                      }
                    </div>
                    <span class="risk-badge" [class]="'risk-' + a.risk.toLowerCase()">{{ a.risk }}</span>
                  </div>
                }
              </div>
            </mat-card-content>
          </mat-card>
        }
      }
    </div>
  `,
  styles: [`
    .wrap { max-width: 1200px; margin: 0 auto; }
    .center { display: grid; place-items: center; padding: 60px; }
    .empty { max-width: 560px; margin: 40px auto; text-align: center; padding: 24px; }
    .empty mat-icon { font-size: 48px; width: 48px; height: 48px; color: var(--lf-accent); margin-bottom: 8px; }
    .empty h3 { margin: 4px 0 8px; }
    .empty p { color: var(--lf-muted); margin: 0 0 20px; }
    .error { color: #ff8080; margin: 12px 0 0; }

    .head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
    .controls { display: flex; align-items: center; gap: 12px; color: var(--lf-muted); font-size: 0.9rem; }
    .controls select {
      background: var(--lf-bg-elev); color: var(--lf-text);
      border: 1px solid var(--lf-border); border-radius: 6px; padding: 4px 8px;
    }
    .meta mat-chip mat-icon { font-size: 16px; height: 16px; width: 16px; margin-right: 4px; vertical-align: -3px; }
    .s-success mat-icon { color: #2ecc71; }
    .s-failed mat-icon { color: #ff6b5c; }
    .s-running mat-icon { color: #6cb0ff; animation: spin 1.5s linear infinite; }

    .phase-card { background: var(--lf-bg-elev); border: 1px solid var(--lf-border); margin-bottom: 12px; }
    .phase-card mat-card-title {
      display: flex; align-items: center;
      font-family: 'SF Mono', monospace; font-size: 1rem;
    }
    .phase-num {
      background: var(--lf-accent); color: white;
      border-radius: 50%; width: 22px; height: 22px;
      display: inline-flex; align-items: center; justify-content: center;
      font-size: 0.75rem; margin-right: 10px; font-weight: 600;
    }
    .files { display: flex; flex-direction: column; gap: 6px; }
    .file-row {
      display: flex; align-items: center; gap: 10px;
      padding: 8px 12px; border-radius: 6px;
      background: rgba(0,0,0,0.2); cursor: pointer;
      transition: background 0.1s;
    }
    .file-row:hover { background: rgba(0,0,0,0.35); }
    .status-icon { flex: 0 0 auto; }
    .status-icon.s-success { color: #2ecc71; }
    .status-icon.s-failed { color: #ff6b5c; }
    .status-icon.s-running { color: #6cb0ff; animation: spin 1.5s linear infinite; }
    .status-icon.s-pending { color: var(--lf-muted); }

    .file-info { flex: 1; min-width: 0; }
    .paths {
      display: flex; align-items: center; gap: 6px;
      font-family: 'SF Mono', monospace; font-size: 0.85rem;
    }
    .paths .from { color: var(--lf-muted); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .paths .to { color: var(--lf-text); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .paths .arrow { font-size: 14px; height: 14px; width: 14px; color: var(--lf-muted); }
    .err { font-size: 0.78rem; color: #ff8080; margin-top: 4px; }

    .risk-badge { padding: 2px 8px; border-radius: 999px; font-size: 0.7rem; font-weight: 600; }
    .risk-low    { background: rgba(46,204,113,0.18); color: #2ecc71; }
    .risk-medium { background: rgba(241,196,15,0.18); color: #f1c40f; }
    .risk-high   { background: rgba(231,76,60,0.20);  color: #ff6b5c; }

    @keyframes spin { from { transform: rotate(0); } to { transform: rotate(360deg); } }
  `],
})
export class AgentsTabComponent implements OnChanges {
  @Input({ required: true }) repoId!: string;

  private service = inject(AgentsService);
  private dialog = inject(MatDialog);

  loading = signal(true);
  running = signal(false);
  summary = signal<RunSummary | null>(null);
  errorMessage = signal<string | null>(null);
  filter = 'all';

  ngOnChanges(_: SimpleChanges): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.service.list(this.repoId).subscribe({
      next: (s) => { this.summary.set(s); this.loading.set(false); },
      error: () => { this.summary.set(null); this.loading.set(false); },
    });
  }

  run(): void {
    this.running.set(true);
    this.errorMessage.set(null);
    this.service.run(this.repoId).subscribe({
      next: (s) => { this.summary.set(s); this.running.set(false); },
      error: (err) => {
        this.errorMessage.set(err?.error?.message ?? 'Agent run failed');
        this.running.set(false);
      },
    });
  }

  applyFilter(): void {
    const s = this.summary();
    if (s) this.summary.set({ ...s });
  }

  phases(): Array<{ number: number; title: string; files: ArtifactSummary[] }> {
    const s = this.summary();
    if (!s) return [];
    const filtered = s.artifacts.filter(a => {
      switch (this.filter) {
        case 'success': return a.status === 'SUCCESS';
        case 'failed':  return a.status === 'FAILED';
        case 'high':    return a.risk === 'HIGH';
        default:        return true;
      }
    });
    const map = new Map<number, { number: number; title: string; files: ArtifactSummary[] }>();
    for (const a of filtered) {
      if (!map.has(a.phaseNumber)) {
        map.set(a.phaseNumber, { number: a.phaseNumber, title: a.phaseTitle, files: [] });
      }
      map.get(a.phaseNumber)!.files.push(a);
    }
    return [...map.values()].sort((a, b) => a.number - b.number);
  }

  openDetail(a: ArtifactSummary): void {
    if (a.status !== 'SUCCESS' && a.status !== 'FAILED') return;
    this.service.detail(this.repoId, a.id).subscribe(detail => {
      this.dialog.open(ArtifactDiffDialogComponent, {
        data: detail,
        panelClass: 'diff-dialog-panel',
        maxWidth: '95vw',
        maxHeight: '95vh',
      });
    });
  }
}
