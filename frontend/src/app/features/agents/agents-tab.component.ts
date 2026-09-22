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
  imports: [CommonModule, MatDialogModule, MatIconModule, MatButtonModule, MatChipsModule, MatTooltipModule],
  template: `
    <div class="dlg">
      <div class="dlg-head">
        <div>
          <div class="dlg-title">
            <mat-icon>swap_horiz</mat-icon>
            <span class="path-from">{{ data.filePath }}</span>
            <mat-icon>arrow_forward</mat-icon>
            <span class="path-to">{{ data.targetPath || '(no target path)' }}</span>
            @if (data.parentArtifactId) {
              <span class="derived-tag" matTooltip="Split from a multi-target LLM response">DERIVED</span>
            }
          </div>
          <div class="dlg-meta">
            Phase {{ data.phaseNumber }} · {{ data.phaseTitle }}
            @if (data.declaredFqn) { · <span class="fqn">{{ data.declaredFqn }}</span> }
            @if (data.promptTokens) { · {{ data.promptTokens }} in / {{ data.outputTokens }} out tokens }
            @if (data.retryCount > 0) {
              · <span class="retry-note">{{ data.retryCount }} self-healing {{ data.retryCount === 1 ? 'retry' : 'retries' }}</span>
            }
            · <span class="val-badge" [class]="'v-' + data.validationStatus.toLowerCase()">
                {{ data.validationStatus }}
              </span>
          </div>
        </div>
        <button mat-icon-button (click)="close()">
          <mat-icon>close</mat-icon>
        </button>
      </div>

      @if (data.validationErrors) {
        <div class="val-errors">
          <div class="val-errors-head">
            <mat-icon>error_outline</mat-icon>
            Validation errors (still failing after {{ data.retryCount }} retries)
          </div>
          <pre>{{ data.validationErrors }}</pre>
        </div>
      }

      @if (data.outgoing.length > 0 || data.incoming.length > 0) {
        <div class="deps-section">
          @if (data.outgoing.length > 0) {
            <div class="deps-group">
              <div class="deps-head">
                <mat-icon>call_made</mat-icon>
                Uses ({{ data.outgoing.length }})
                @if (brokenCount(data.outgoing) > 0) {
                  <span class="broken-count">{{ brokenCount(data.outgoing) }} broken</span>
                }
              </div>
              <div class="deps-list">
                @for (e of data.outgoing; track e.toClassName + e.edgeType) {
                  <span class="dep-chip" [class.broken]="!e.resolved"
                        [matTooltip]="e.resolved
                          ? ('Resolved to ' + e.toTargetPath)
                          : 'Not found in generated code'">
                    <mat-icon>{{ e.resolved ? 'link' : 'link_off' }}</mat-icon>
                    <span class="dep-name">{{ shortName(e.toClassName) }}</span>
                    <span class="dep-type">{{ e.edgeType.toLowerCase() }}</span>
                  </span>
                }
              </div>
            </div>
          }
          @if (data.incoming.length > 0) {
            <div class="deps-group">
              <div class="deps-head">
                <mat-icon>call_received</mat-icon>
                Used by ({{ data.incoming.length }})
              </div>
              <div class="deps-list">
                @for (e of data.incoming; track e.toClassName + e.edgeType) {
                  <span class="dep-chip">
                    <mat-icon>arrow_back</mat-icon>
                    <span class="dep-name">{{ shortName(e.toClassName) }}</span>
                  </span>
                }
              </div>
            </div>
          }
        </div>
      }

      <div class="split">
        <div class="pane">
          <div class="pane-head"><mat-icon>history_edu</mat-icon> Legacy</div>
          <pre class="code"><code>{{ data.originalCode || '(source unavailable — derived artifact)' }}</code></pre>
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
    .derived-tag {
      font-size: 0.65rem; padding: 2px 6px; border-radius: 4px;
      background: rgba(108,176,255,0.18); color: #6cb0ff; font-weight: 600;
    }
    .dlg-meta { color: var(--lf-muted); font-size: 0.8rem; margin-top: 4px; }
    .fqn { font-family: 'SF Mono', monospace; color: var(--lf-accent); }
    .retry-note { color: #f1c40f; }

    .val-badge { padding: 2px 8px; border-radius: 999px; font-size: 0.7rem; font-weight: 600; }
    .v-valid   { background: rgba(46,204,113,0.18); color: #2ecc71; }
    .v-invalid { background: rgba(231,76,60,0.20); color: #ff6b5c; }
    .v-skipped { background: rgba(150,150,150,0.15); color: #b0b0b0; }

    .val-errors {
      margin: 8px 12px; padding: 12px 14px;
      background: rgba(231,76,60,0.08);
      border: 1px solid rgba(231,76,60,0.4);
      border-radius: 8px;
    }
    .val-errors-head {
      display: flex; align-items: center; gap: 6px;
      color: #ff8080; font-weight: 500; margin-bottom: 6px; font-size: 0.9rem;
    }
    .val-errors pre {
      margin: 0; padding: 0;
      color: #ffb0b0; font-family: 'SF Mono', monospace;
      font-size: 0.8rem; white-space: pre-wrap;
    }

    .deps-section {
      margin: 8px 12px; padding: 10px 14px;
      background: rgba(0,0,0,0.2);
      border: 1px solid var(--lf-border);
      border-radius: 8px;
      display: flex; gap: 20px; flex-wrap: wrap;
    }
    .deps-group { flex: 1; min-width: 300px; }
    .deps-head {
      display: flex; align-items: center; gap: 6px;
      color: var(--lf-muted); font-size: 0.85rem; margin-bottom: 6px;
    }
    .deps-head mat-icon { font-size: 16px; height: 16px; width: 16px; }
    .broken-count {
      margin-left: auto; padding: 1px 8px; border-radius: 999px;
      background: rgba(231,76,60,0.20); color: #ff6b5c;
      font-size: 0.7rem; font-weight: 600;
    }
    .deps-list { display: flex; flex-wrap: wrap; gap: 6px; }
    .dep-chip {
      display: inline-flex; align-items: center; gap: 4px;
      padding: 3px 8px; border-radius: 6px;
      background: rgba(46,204,113,0.12); color: #2ecc71;
      font-size: 0.75rem; font-family: 'SF Mono', monospace;
    }
    .dep-chip.broken { background: rgba(231,76,60,0.15); color: #ff6b5c; }
    .dep-chip mat-icon { font-size: 12px; height: 12px; width: 12px; }
    .dep-name { font-weight: 500; }
    .dep-type { opacity: 0.6; font-size: 0.65rem; margin-left: 2px; }

    .split { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; padding: 8px; flex: 1; min-height: 400px; }
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

  shortName(fqn: string): string {
    const idx = fqn.lastIndexOf('.');
    return idx > 0 ? fqn.substring(idx + 1) : fqn;
  }

  brokenCount(edges: ArtifactDetail['outgoing']): number {
    return edges.filter(e => !e.resolved).length;
  }
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
              Each agent generates code, self-validates via JavaParser (with up
              to 2 retries), and the whole run rebuilds the cross-file
              dependency graph so you can see structural coherence.
            </p>
            <button mat-flat-button color="primary" (click)="run()" [disabled]="running()">
              @if (running()) {
                <mat-spinner diameter="20"></mat-spinner>
                Running agents... (30-120s)
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
              <mat-chip class="s-success"><mat-icon>check_circle</mat-icon> {{ summary()!.success }} generated</mat-chip>
              @if (summary()!.failed > 0) {
                <mat-chip class="s-failed"><mat-icon>error</mat-icon> {{ summary()!.failed }} failed</mat-chip>
              }
              @if (running() || summary()!.running + summary()!.pending > 0) {
                <mat-chip class="s-running"><mat-icon>autorenew</mat-icon> {{ summary()!.running + summary()!.pending }} in flight</mat-chip>
              }
              <mat-chip class="v-valid"><mat-icon>verified</mat-icon> {{ summary()!.valid }} valid</mat-chip>
              @if (summary()!.invalid > 0) {
                <mat-chip class="v-invalid"><mat-icon>report</mat-icon> {{ summary()!.invalid }} invalid</mat-chip>
              }
              @if (summary()!.totalRetries > 0) {
                <mat-chip class="s-retry"
                          matTooltip="Files that failed initial validation and were regenerated with error context">
                  <mat-icon>healing</mat-icon> {{ summary()!.totalRetries }} self-healing retries
                </mat-chip>
              }
              @if (summary()!.derivedCount > 0) {
                <mat-chip class="s-derived"
                          matTooltip="Additional files split out from a multi-target LLM response">
                  <mat-icon>call_split</mat-icon> {{ summary()!.derivedCount }} derived
                </mat-chip>
              }
              @if (summary()!.totalEdges > 0) {
                <mat-chip class="s-edges"
                          matTooltip="Class references across the generated code">
                  <mat-icon>hub</mat-icon> {{ summary()!.totalEdges }} deps
                </mat-chip>
                @if (summary()!.brokenEdges > 0) {
                  <mat-chip class="s-broken"
                            matTooltip="References that don't resolve to any generated class">
                    <mat-icon>link_off</mat-icon> {{ summary()!.brokenEdges }} broken links
                  </mat-chip>
                }
              }
            </mat-chip-set>
          </div>
          <div class="controls">
            <label>Filter:
              <select [(ngModel)]="filter" (change)="applyFilter()">
                <option value="all">All</option>
                <option value="valid">Valid</option>
                <option value="invalid">Invalid</option>
                <option value="retried">Retried (self-healed)</option>
                <option value="derived">Derived (multi-target splits)</option>
                <option value="broken">Broken deps</option>
                <option value="failed">Failed</option>
                <option value="high">HIGH risk only</option>
              </select>
            </label>
            @if (summary()!.brokenEdges > 0) {
              <button mat-flat-button color="accent" (click)="patchPlan()" [disabled]="patching() || running()"
                      matTooltip="Ask the LLM to add plan entries covering the broken references, then rerun the agents">
                @if (patching()) {
                  <mat-spinner diameter="18"></mat-spinner>
                  Patching...
                } @else {
                  <mat-icon>healing</mat-icon> Patch plan ({{ summary()!.brokenEdges }})
                }
              </button>
            }
            <button mat-stroked-button (click)="run()" [disabled]="running() || patching()">
              @if (running()) {
                <mat-spinner diameter="18"></mat-spinner>
              } @else {
                <mat-icon>refresh</mat-icon> Rerun
              }
            </button>
          </div>
        </div>

        @if (patchMessage()) {
          <div class="patch-toast">
            <mat-icon>auto_fix_high</mat-icon>
            <span>{{ patchMessage() }}</span>
            <button mat-stroked-button (click)="run()" [disabled]="running()">
              <mat-icon>bolt</mat-icon> Rerun agents to fill new slots
            </button>
            <button mat-icon-button (click)="patchMessage.set(null)"><mat-icon>close</mat-icon></button>
          </div>
        }

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
                       [class.derived]="a.parentArtifactId"
                       [matTooltip]="a.status === 'FAILED' ? (a.errorMessage || 'Failed') : 'Click to view diff + deps'">
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
                        @if (a.parentArtifactId) { <mat-icon class="derived-icon">call_split</mat-icon> }
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
                    @if (a.depsOut > 0) {
                      <span class="deps-chip"
                            [class.has-broken]="a.depsBroken > 0"
                            [matTooltip]="a.depsOut + ' outgoing refs, ' + a.depsBroken + ' broken. In: ' + a.depsIn">
                        <mat-icon>hub</mat-icon>
                        {{ a.depsOut }}@if (a.depsBroken > 0) { <span class="broken-slash">/{{ a.depsBroken }}✗</span> }
                      </span>
                    }
                    @if (a.retryCount > 0) {
                      <span class="retry-chip"
                            [matTooltip]="a.retryCount + ' self-healing retries'">
                        <mat-icon>healing</mat-icon> {{ a.retryCount }}
                      </span>
                    }
                    @if (a.validationStatus !== 'SKIPPED') {
                      <span class="val-badge" [class]="'v-' + a.validationStatus.toLowerCase()">
                        {{ a.validationStatus }}
                      </span>
                    }
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

    .head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; flex-wrap: wrap; gap: 12px; }
    .controls { display: flex; align-items: center; gap: 12px; color: var(--lf-muted); font-size: 0.9rem; }
    .controls select {
      background: var(--lf-bg-elev); color: var(--lf-text);
      border: 1px solid var(--lf-border); border-radius: 6px; padding: 4px 8px;
    }
    .meta mat-chip mat-icon { font-size: 16px; height: 16px; width: 16px; margin-right: 4px; vertical-align: -3px; }
    .s-success mat-icon { color: #2ecc71; }
    .s-failed mat-icon { color: #ff6b5c; }
    .s-running mat-icon { color: #6cb0ff; animation: spin 1.5s linear infinite; }
    .s-retry mat-icon { color: #f1c40f; }
    .s-derived mat-icon { color: #6cb0ff; }
    .s-edges mat-icon { color: #b28eff; }
    .s-broken mat-icon { color: #ff6b5c; }
    .v-valid mat-icon { color: #2ecc71; }
    .v-invalid mat-icon { color: #ff6b5c; }

    .patch-toast {
      display: flex; align-items: center; gap: 12px;
      padding: 12px 16px; margin-bottom: 12px;
      background: rgba(178,142,255,0.10);
      border: 1px solid rgba(178,142,255,0.35);
      border-radius: 8px;
      color: var(--lf-text);
    }
    .patch-toast mat-icon { color: #b28eff; }
    .patch-toast span { flex: 1; }

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
    .file-row.derived { border-left: 3px solid #6cb0ff; padding-left: 9px; }
    .status-icon { flex: 0 0 auto; }
    .status-icon.s-success { color: #2ecc71; }
    .status-icon.s-failed { color: #ff6b5c; }
    .status-icon.s-running { color: #6cb0ff; animation: spin 1.5s linear infinite; }
    .status-icon.s-pending { color: var(--lf-muted); }
    .derived-icon { font-size: 14px; height: 14px; width: 14px; color: #6cb0ff; }

    .file-info { flex: 1; min-width: 0; }
    .paths {
      display: flex; align-items: center; gap: 6px;
      font-family: 'SF Mono', monospace; font-size: 0.85rem;
    }
    .paths .from { color: var(--lf-muted); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .paths .to { color: var(--lf-text); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .paths .arrow { font-size: 14px; height: 14px; width: 14px; color: var(--lf-muted); }
    .err { font-size: 0.78rem; color: #ff8080; margin-top: 4px; }

    .deps-chip, .retry-chip {
      display: inline-flex; align-items: center; gap: 2px;
      padding: 2px 8px; border-radius: 999px; font-size: 0.7rem; font-weight: 600;
    }
    .deps-chip { background: rgba(178,142,255,0.15); color: #b28eff; }
    .deps-chip.has-broken { background: rgba(231,76,60,0.15); color: #ff8080; }
    .deps-chip mat-icon { font-size: 12px; height: 12px; width: 12px; }
    .broken-slash { color: #ff6b5c; }
    .retry-chip { background: rgba(241,196,15,0.18); color: #f1c40f; }
    .retry-chip mat-icon { font-size: 12px; height: 12px; width: 12px; }

    .val-badge { padding: 2px 8px; border-radius: 999px; font-size: 0.7rem; font-weight: 600; }
    .v-valid   { background: rgba(46,204,113,0.18); color: #2ecc71; }
    .v-invalid { background: rgba(231,76,60,0.20); color: #ff6b5c; }
    .v-skipped { background: rgba(150,150,150,0.15); color: #b0b0b0; }

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
  patching = signal(false);
  patchMessage = signal<string | null>(null);
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
    this.patchMessage.set(null);
    this.service.run(this.repoId).subscribe({
      next: (s) => { this.summary.set(s); this.running.set(false); },
      error: (err) => {
        this.errorMessage.set(err?.error?.message ?? 'Agent run failed');
        this.running.set(false);
      },
    });
  }

  patchPlan(): void {
    this.patching.set(true);
    this.errorMessage.set(null);
    this.service.patchPlan(this.repoId).subscribe({
      next: (r) => {
        this.patching.set(false);
        this.patchMessage.set(r.summary);
      },
      error: (err) => {
        this.patching.set(false);
        this.errorMessage.set(err?.error?.message ?? 'Plan patch failed');
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
        case 'valid':   return a.validationStatus === 'VALID';
        case 'invalid': return a.validationStatus === 'INVALID';
        case 'retried': return a.retryCount > 0;
        case 'derived': return a.parentArtifactId !== null;
        case 'broken':  return a.depsBroken > 0;
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
