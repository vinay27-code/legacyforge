import { Component, Input, OnChanges, SimpleChanges, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { AnalysisService } from './analysis.service';
import { AnalysisReport, DetectedFramework, FileAnalysis } from './analysis.models';

@Component({
  selector: 'app-analysis-tab',
  standalone: true,
  imports: [
    CommonModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatChipsModule,
    MatProgressSpinnerModule,
    MatProgressBarModule,
    MatTooltipModule,
  ],
  template: `
    @if (loading()) {
      <div class="center"><mat-spinner></mat-spinner></div>
    } @else if (!report()) {
      <mat-card class="empty">
        <mat-card-content>
          <mat-icon>psychology</mat-icon>
          <h3>Not analyzed yet</h3>
          <p>Parse every Java file, detect frameworks, and score complexity.</p>
          <button mat-flat-button color="primary" (click)="run()" [disabled]="running()">
            @if (running()) {
              <mat-spinner diameter="20"></mat-spinner>
            } @else {
              <mat-icon>play_arrow</mat-icon>
              Run analysis
            }
          </button>
          @if (errorMessage()) {
            <p class="error">{{ errorMessage() }}</p>
          }
        </mat-card-content>
      </mat-card>
    } @else {
      <div class="grid">
        <!-- KPI row -->
        <mat-card class="kpi">
          <div class="v">{{ report()!.totalJavaFiles }}</div>
          <div class="l">Java files</div>
        </mat-card>
        <mat-card class="kpi">
          <div class="v">{{ report()!.totalJavaLoc | number }}</div>
          <div class="l">Lines of Java</div>
        </mat-card>
        <mat-card class="kpi">
          <div class="v">{{ report()!.avgComplexity }}</div>
          <div class="l">Avg complexity</div>
        </mat-card>
        <mat-card class="kpi">
          <div class="v">{{ report()!.maxComplexity }}</div>
          <div class="l">Max complexity</div>
        </mat-card>

        <!-- Frameworks -->
        <mat-card class="span2">
          <mat-card-header>
            <mat-card-title>Detected frameworks</mat-card-title>
            <mat-card-subtitle>{{ report()!.frameworks.length }} frameworks identified with weighted evidence</mat-card-subtitle>
          </mat-card-header>
          <mat-card-content>
            @if (report()!.frameworks.length === 0) {
              <p class="muted">No frameworks detected. This may be plain Java or an unusual stack.</p>
            }
            <div class="fw-list">
              @for (fw of report()!.frameworks; track fw.key) {
                <div class="fw" [matTooltip]="fw.evidence.join(' • ')" matTooltipPosition="above">
                  <div class="fw-head">
                    <span class="name">{{ fw.name }}</span>
                    @if (fw.version) {
                      <span class="ver">{{ fw.version }}</span>
                    }
                    <span class="conf">{{ fw.confidence }}%</span>
                  </div>
                  <mat-progress-bar
                    mode="determinate"
                    [value]="fw.confidence"
                    [color]="fw.confidence >= 70 ? 'primary' : 'accent'"
                  ></mat-progress-bar>
                </div>
              }
            </div>
          </mat-card-content>
        </mat-card>

        <!-- Language breakdown -->
        <mat-card>
          <mat-card-header>
            <mat-card-title>Languages</mat-card-title>
          </mat-card-header>
          <mat-card-content>
            @for (lang of report()!.summary.languageBreakdown; track lang.language) {
              <div class="lang-row">
                <span class="lang-name">{{ lang.language }}</span>
                <span class="lang-count">{{ lang.files }} files · {{ formatBytes(lang.bytes) }}</span>
              </div>
            }
          </mat-card-content>
        </mat-card>

        <!-- Findings -->
        <mat-card class="span3">
          <mat-card-header>
            <mat-card-title>Findings</mat-card-title>
            <mat-card-subtitle>Migration hot-spots detected across the codebase</mat-card-subtitle>
          </mat-card-header>
          <mat-card-content>
            @if (report()!.findings.length === 0) {
              <p class="muted">No noteworthy findings.</p>
            }
            @for (f of report()!.findings; track f.code) {
              <div class="finding">
                <span class="sev" [class]="'sev-' + f.severity">{{ f.severity }}</span>
                <span class="msg">{{ f.message }}</span>
                <span class="count">{{ f.occurrences }}×</span>
              </div>
            }
          </mat-card-content>
        </mat-card>

        <!-- Top complex files -->
        <mat-card class="span3">
          <mat-card-header>
            <mat-card-title>Most complex files</mat-card-title>
            <mat-card-subtitle>Highest cyclomatic complexity — good migration candidates to review first</mat-card-subtitle>
          </mat-card-header>
          <mat-card-content>
            @if (topFiles().length === 0) {
              <p class="muted">Loading…</p>
            }
            <table class="complex-table">
              <thead>
                <tr>
                  <th>File</th><th>Class</th><th>Methods</th><th>LOC</th><th>Complexity</th>
                </tr>
              </thead>
              <tbody>
                @for (f of topFiles(); track f.id) {
                  <tr>
                    <td class="path">{{ f.filePath }}</td>
                    <td>{{ f.className ?? '—' }}</td>
                    <td>{{ f.methodCount }}</td>
                    <td>{{ f.loc }}</td>
                    <td>
                      <span class="cx" [class.hot]="f.complexity > 20" [class.warm]="f.complexity > 10 && f.complexity <= 20">
                        {{ f.complexity }}
                      </span>
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </mat-card-content>
        </mat-card>
      </div>

      <div class="rerun-row">
        <button mat-stroked-button (click)="run()" [disabled]="running()">
          @if (running()) {
            <mat-spinner diameter="18"></mat-spinner>
          } @else {
            <mat-icon>refresh</mat-icon>
            Re-run analysis
          }
        </button>
        <span class="ts">Last run: {{ report()!.updatedAt | date:'medium' }}</span>
      </div>
    }
  `,
  styles: [`
    .center { display: grid; place-items: center; padding: 80px; }
    .empty { max-width: 480px; margin: 40px auto; text-align: center; padding: 24px; }
    .empty mat-icon { font-size: 48px; width: 48px; height: 48px; color: var(--lf-accent); margin-bottom: 8px; }
    .empty h3 { margin: 4px 0 8px; }
    .empty p { color: var(--lf-muted); margin: 0 0 20px; }
    .error { color: #ff8080; margin-top: 12px; }

    .grid {
      display: grid;
      grid-template-columns: repeat(4, 1fr);
      gap: 16px;
    }
    .kpi { text-align: center; padding: 20px 12px; }
    .kpi .v { font-size: 2rem; font-weight: 600; color: var(--lf-accent); }
    .kpi .l { color: var(--lf-muted); font-size: 0.85rem; margin-top: 4px; }
    .span2 { grid-column: span 2; }
    .span3 { grid-column: span 4; }

    .muted { color: var(--lf-muted); margin: 4px 0; }

    .fw-list { display: flex; flex-direction: column; gap: 14px; margin-top: 8px; }
    .fw-head { display: flex; align-items: center; gap: 8px; margin-bottom: 4px; }
    .fw .name { font-weight: 500; }
    .fw .ver { font-size: 0.8rem; padding: 2px 6px; border-radius: 4px;
               background: rgba(255,140,66,0.15); color: var(--lf-accent); }
    .fw .conf { margin-left: auto; color: var(--lf-muted); font-size: 0.85rem; }

    .lang-row { display: flex; justify-content: space-between; padding: 6px 0; border-bottom: 1px solid var(--lf-border); }
    .lang-row:last-child { border: 0; }
    .lang-name { font-weight: 500; }
    .lang-count { color: var(--lf-muted); font-size: 0.85rem; }

    .finding { display: flex; align-items: center; gap: 12px; padding: 8px 0; border-bottom: 1px solid var(--lf-border); }
    .finding:last-child { border: 0; }
    .sev { text-transform: uppercase; font-size: 0.7rem; font-weight: 700; padding: 2px 8px; border-radius: 4px; }
    .sev-low { background: rgba(99,179,237,0.15); color: var(--lf-accent-2); }
    .sev-medium { background: rgba(255,189,46,0.15); color: #ffbd2e; }
    .sev-high { background: rgba(255,95,95,0.15); color: #ff8080; }
    .finding .msg { flex: 1; }
    .finding .count { color: var(--lf-muted); font-size: 0.85rem; }

    .complex-table { width: 100%; border-collapse: collapse; margin-top: 8px; }
    .complex-table th { text-align: left; padding: 8px 6px; color: var(--lf-muted); font-weight: 500; font-size: 0.85rem; border-bottom: 1px solid var(--lf-border); }
    .complex-table td { padding: 8px 6px; border-bottom: 1px solid rgba(255,255,255,0.04); font-size: 0.9rem; }
    .complex-table td.path { font-family: 'SF Mono', monospace; font-size: 0.82rem; color: var(--lf-muted); }
    .cx { padding: 2px 8px; border-radius: 4px; background: rgba(255,255,255,0.06); font-weight: 500; }
    .cx.warm { background: rgba(255,189,46,0.18); color: #ffbd2e; }
    .cx.hot { background: rgba(255,95,95,0.2); color: #ff8080; }

    .rerun-row { margin-top: 16px; display: flex; align-items: center; gap: 12px; }
    .rerun-row .ts { color: var(--lf-muted); font-size: 0.85rem; }
  `],
})
export class AnalysisTabComponent implements OnChanges {
  @Input({ required: true }) repoId!: string;

  private service = inject(AnalysisService);

  report = signal<AnalysisReport | null>(null);
  topFiles = signal<FileAnalysis[]>([]);
  loading = signal(true);
  running = signal(false);
  errorMessage = signal<string | null>(null);

  ngOnChanges(_: SimpleChanges): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.service.get(this.repoId).subscribe({
      next: (r) => {
        this.report.set(r);
        this.loading.set(false);
        if (r) this.loadTopComplex();
      },
      error: () => this.loading.set(false),
    });
  }

  run(): void {
    this.running.set(true);
    this.errorMessage.set(null);
    this.service.run(this.repoId).subscribe({
      next: (r) => {
        this.report.set(r);
        this.running.set(false);
        this.loadTopComplex();
      },
      error: (err: HttpErrorResponse) => {
        this.errorMessage.set(err?.error?.message ?? 'Analysis failed');
        this.running.set(false);
      },
    });
  }

  private loadTopComplex(): void {
    this.service.topComplex(this.repoId, 20).subscribe((files) => this.topFiles.set(files));
  }

  formatBytes(bytes: number): string {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
  }
}
