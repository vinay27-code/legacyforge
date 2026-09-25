import { Component, Input, OnChanges, SimpleChanges, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatChipsModule } from '@angular/material/chips';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatTooltipModule } from '@angular/material/tooltip';
import { PlanningService } from './planning.service';
import { PlanResponse, Risk } from './planning.models';

@Component({
  selector: 'app-planning-tab',
  standalone: true,
  imports: [
    CommonModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatChipsModule,
    MatExpansionModule,
    MatTooltipModule,
  ],
  template: `
    <div class="wrap">
      @if (loading()) {
        <div class="center"><mat-spinner></mat-spinner></div>
      } @else if (!plan()) {
        <mat-card class="empty">
          <mat-card-content>
            <mat-icon>auto_awesome</mat-icon>
            <h3>No migration plan yet</h3>
            <p>
              Hand the entire codebase to Gemini 2.5 Flash and get back a phased
              migration plan with per-file risk scoring and concrete target
              patterns for each file.
            </p>
            <button mat-flat-button color="primary" (click)="generate()" [disabled]="generating()">
              @if (generating()) {
                <mat-spinner diameter="20"></mat-spinner>
                Planning... (30-60s)
              } @else {
                <mat-icon>bolt</mat-icon>
                Generate migration plan
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
              <mat-chip><mat-icon>timeline</mat-icon> {{ plan()!.plan.phases.length }} phases</mat-chip>
              <mat-chip><mat-icon>schedule</mat-icon> {{ plan()!.plan.totalEstimatedDays }} days</mat-chip>
              <mat-chip [class]="'risk-' + plan()!.plan.overallRisk.toLowerCase()">
                <mat-icon>gpp_maybe</mat-icon> {{ plan()!.plan.overallRisk }} risk
              </mat-chip>
              <mat-chip><mat-icon>psychology</mat-icon> {{ plan()!.provider }}</mat-chip>
            </mat-chip-set>
          </div>
          <button mat-stroked-button (click)="generate()" [disabled]="generating()">
            @if (generating()) {
              <mat-spinner diameter="18"></mat-spinner>
            } @else {
              <mat-icon>refresh</mat-icon> Regenerate
            }
          </button>
        </div>

        <mat-card class="summary-card">
          <mat-card-content>
            <h3>Executive summary</h3>
            <p>{{ plan()!.plan.summary }}</p>
            <div class="footer">
              Generated {{ formatDate(plan()!.generatedAt) }}
              @if (plan()!.promptTokens) {
                · {{ plan()!.promptTokens }} in / {{ plan()!.outputTokens }} out tokens
              }
            </div>
          </mat-card-content>
        </mat-card>

        <mat-accordion class="phases" multi>
          @for (phase of plan()!.plan.phases; track phase.phaseNumber) {
            <mat-expansion-panel expanded>
              <mat-expansion-panel-header>
                <mat-panel-title>
                  <span class="phase-num">{{ phase.phaseNumber }}</span>
                  <span>{{ phase.title }}</span>
                </mat-panel-title>
                <mat-panel-description>
                  <span class="phase-meta">
                    {{ phase.files.length }} files · {{ phase.estimatedDays }} days
                  </span>
                </mat-panel-description>
              </mat-expansion-panel-header>

              <p class="phase-desc">{{ phase.description }}</p>

              <div class="files">
                @for (file of phase.files; track file.path) {
                  <div class="file-row">
                    <div class="file-head">
                      <mat-icon>description</mat-icon>
                      <span class="path" [matTooltip]="file.path">{{ file.path }}</span>
                      <span class="risk-badge" [class]="'risk-' + file.risk.toLowerCase()">
                        {{ file.risk }}
                      </span>
                    </div>
                    <div class="file-body">
                      <div class="row"><strong>Why:</strong> {{ file.reason }}</div>
                      <div class="row"><strong>Approach:</strong> {{ file.notes }}</div>
                    </div>
                  </div>
                }
              </div>
            </mat-expansion-panel>
          }
        </mat-accordion>
      }
    </div>
  `,
  styles: [`
    .wrap { max-width: 1200px; margin: 0 auto; }
    .center { display: grid; place-items: center; padding: 60px; }
    .empty { max-width: 560px; margin: 40px auto; text-align: center; padding: 24px; }
    .empty > mat-card-content > mat-icon { font-size: 48px; width: 48px; height: 48px; color: var(--lf-accent); margin-bottom: 8px; }
    .empty h3 { margin: 4px 0 8px; }
    .empty p { color: var(--lf-muted); margin: 0 0 20px; }
    .error { color: #ff8080; margin: 12px 0 0; }

    .head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
    .meta mat-chip mat-icon { font-size: 16px; height: 16px; width: 16px; margin-right: 4px; vertical-align: -3px; }

    .summary-card {
      background: var(--lf-bg-elev);
      border: 1px solid var(--lf-border);
      margin-bottom: 20px;
    }
    .summary-card h3 { margin: 0 0 8px; color: var(--lf-accent); }
    .summary-card p { margin: 0 0 12px; line-height: 1.6; }
    .footer { font-size: 0.8rem; color: var(--lf-muted); }

    .phases { display: block; }
    .phases mat-expansion-panel {
      background: var(--lf-bg-elev) !important;
      border: 1px solid var(--lf-border);
      margin-bottom: 8px;
      color: var(--lf-text) !important;
    }
    .phase-num {
      background: var(--lf-accent); color: white;
      border-radius: 50%; width: 24px; height: 24px;
      display: inline-flex; align-items: center; justify-content: center;
      font-size: 0.8rem; margin-right: 10px; font-weight: 600;
    }
    .phase-meta { color: var(--lf-muted); font-size: 0.85rem; }
    .phase-desc { color: var(--lf-muted); margin: 0 0 16px; line-height: 1.5; }

    .files { display: flex; flex-direction: column; gap: 10px; }
    .file-row {
      background: rgba(0,0,0,0.2);
      border: 1px solid var(--lf-border);
      border-radius: 8px;
      padding: 10px 14px;
    }
    .file-head {
      display: flex; align-items: center; gap: 8px;
      font-family: 'SF Mono', monospace; font-size: 0.88rem;
      margin-bottom: 6px;
    }
    .file-head .path { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .file-body { font-size: 0.85rem; color: var(--lf-muted); line-height: 1.5; }
    .file-body .row { margin-top: 2px; }
    .file-body strong { color: var(--lf-text); }

    .risk-badge, .risk-low, .risk-medium, .risk-high {
      padding: 2px 8px; border-radius: 999px; font-size: 0.72rem;
      font-weight: 600; letter-spacing: 0.03em;
    }
    .risk-low    { background: rgba(46,204,113,0.18); color: #2ecc71; }
    .risk-medium { background: rgba(241,196,15,0.18); color: #f1c40f; }
    .risk-high   { background: rgba(231,76,60,0.20);  color: #ff6b5c; }
  `],
})
export class PlanningTabComponent implements OnChanges {
  @Input({ required: true }) repoId!: string;

  private service = inject(PlanningService);

  loading = signal(true);
  generating = signal(false);
  plan = signal<PlanResponse | null>(null);
  errorMessage = signal<string | null>(null);

  ngOnChanges(_: SimpleChanges): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.service.get(this.repoId).subscribe({
      next: (p) => { this.plan.set(p); this.loading.set(false); },
      error: () => { this.plan.set(null); this.loading.set(false); },
    });
  }

  generate(): void {
    this.generating.set(true);
    this.errorMessage.set(null);
    this.service.generate(this.repoId).subscribe({
      next: (p) => { this.plan.set(p); this.generating.set(false); },
      error: (err) => {
        this.errorMessage.set(err?.error?.message ?? 'Plan generation failed');
        this.generating.set(false);
      },
    });
  }

  formatDate(iso: string): string {
    return new Date(iso).toLocaleString();
  }
}
