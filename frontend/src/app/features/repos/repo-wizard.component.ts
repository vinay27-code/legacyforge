import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatTabsModule } from '@angular/material/tabs';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RepoService } from './repo.service';

@Component({
  selector: 'app-repo-wizard',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatTabsModule,
    MatProgressBarModule,
    MatProgressSpinnerModule,
  ],
  template: `
    <div class="wrap">
      <header>
        <h1>New Migration</h1>
        <p class="tag">Point LegacyForge at a legacy Java codebase to analyze.</p>
      </header>

      <mat-card class="card">
        <mat-tab-group>
          <mat-tab label="GitHub URL">
            <div class="pad">
              <p>Paste any public GitHub repo. We'll shallow clone it and index every file.</p>
              <form [formGroup]="urlForm" (ngSubmit)="submitUrl()">
                <mat-form-field appearance="outline" class="full">
                  <mat-label>GitHub URL</mat-label>
                  <input
                    matInput
                    formControlName="url"
                    placeholder="https://github.com/vinay27-code/jpetstore-legacy"
                  />
                </mat-form-field>

                @if (errorMessage()) {
                  <p class="error">{{ errorMessage() }}</p>
                }

                <button
                  mat-flat-button
                  color="primary"
                  type="submit"
                  [disabled]="urlForm.invalid || busy()"
                >
                  @if (busy()) {
                    <mat-spinner diameter="20"></mat-spinner>
                  } @else {
                    Ingest repository
                  }
                </button>
              </form>
            </div>
          </mat-tab>

          <mat-tab label="Upload zip">
            <div class="pad">
              <p>Drag a zip of a legacy repo here, or click to browse. Max 100 MB.</p>
              <div
                class="drop"
                [class.hover]="dragging()"
                (dragover)="onDragOver($event)"
                (dragleave)="dragging.set(false)"
                (drop)="onDrop($event)"
                (click)="fileInput.click()"
              >
                <mat-icon>upload_file</mat-icon>
                <span>{{ selectedFile() ? selectedFile()!.name : 'Drop zip file here' }}</span>
                <input
                  #fileInput
                  type="file"
                  accept=".zip"
                  hidden
                  (change)="onFileSelected($event)"
                />
              </div>

              @if (uploadProgress() !== null) {
                <mat-progress-bar mode="determinate" [value]="uploadProgress()!"></mat-progress-bar>
                <span class="progress-label">{{ uploadProgress() }}%</span>
              }

              @if (errorMessage()) {
                <p class="error">{{ errorMessage() }}</p>
              }

              <button
                mat-flat-button
                color="primary"
                [disabled]="!selectedFile() || busy()"
                (click)="submitZip()"
              >
                @if (busy()) {
                  <mat-spinner diameter="20"></mat-spinner>
                } @else {
                  Upload and ingest
                }
              </button>
            </div>
          </mat-tab>
        </mat-tab-group>
      </mat-card>
    </div>
  `,
  styles: [`
    .wrap { max-width: 720px; margin: 0 auto; }
    header h1 { margin: 0 0 4px; letter-spacing: -0.02em; }
    .tag { color: var(--lf-muted); margin: 0 0 24px; }
    .card { background: var(--lf-bg-elev); border: 1px solid var(--lf-border); }
    .pad { padding: 24px 8px 8px; }
    .full { width: 100%; }
    .drop {
      border: 2px dashed var(--lf-border);
      border-radius: 12px;
      padding: 40px 20px;
      display: flex; flex-direction: column; align-items: center; gap: 12px;
      cursor: pointer;
      transition: border-color 0.15s, background 0.15s;
      margin-bottom: 16px;
    }
    .drop.hover { border-color: var(--lf-accent); background: rgba(255,140,66,0.05); }
    .drop mat-icon { font-size: 40px; width: 40px; height: 40px; color: var(--lf-muted); }
    .error { color: #ff8080; margin: 8px 0; font-size: 0.9rem; }
    .progress-label { display: block; text-align: right; color: var(--lf-muted); font-size: 0.85rem; margin-bottom: 12px; }
    p { color: var(--lf-muted); }
    button[disabled] mat-spinner { margin: 0 auto; }
    form { display: flex; flex-direction: column; gap: 4px; }
  `],
})
export class RepoWizardComponent {
  private fb = inject(FormBuilder);
  private service = inject(RepoService);
  private router = inject(Router);

  readonly urlForm = this.fb.nonNullable.group({
    url: [
      '',
      [
        Validators.required,
        Validators.pattern(
          /^https?:\/\/github\.com\/[a-zA-Z0-9._-]+\/[a-zA-Z0-9._-]+(\.git)?\/?$/,
        ),
      ],
    ],
  });

  selectedFile = signal<File | null>(null);
  dragging = signal(false);
  busy = signal(false);
  uploadProgress = signal<number | null>(null);
  errorMessage = signal<string | null>(null);

  submitUrl(): void {
    if (this.urlForm.invalid) return;
    this.busy.set(true);
    this.errorMessage.set(null);
    this.service.fromGithub(this.urlForm.getRawValue()).subscribe({
      next: (repo) => {
        this.busy.set(false);
        this.router.navigate(['/repos', repo.id]);
      },
      error: (err) => {
        this.busy.set(false);
        this.errorMessage.set(err?.error?.message ?? 'Ingestion failed');
      },
    });
  }

  submitZip(): void {
    const file = this.selectedFile();
    if (!file) return;
    this.busy.set(true);
    this.errorMessage.set(null);
    this.uploadProgress.set(0);
    this.service.uploadZip(file).subscribe({
      next: (event) => {
        if (event.kind === 'progress' && event.progressPct !== undefined) {
          this.uploadProgress.set(event.progressPct);
        } else if (event.kind === 'done' && event.repo) {
          this.busy.set(false);
          this.router.navigate(['/repos', event.repo.id]);
        }
      },
      error: (err) => {
        this.busy.set(false);
        this.uploadProgress.set(null);
        this.errorMessage.set(err?.error?.message ?? 'Upload failed');
      },
    });
  }

  onFileSelected(evt: Event): void {
    const input = evt.target as HTMLInputElement;
    if (input.files && input.files[0]) {
      this.selectedFile.set(input.files[0]);
      this.errorMessage.set(null);
    }
  }

  onDragOver(evt: DragEvent): void {
    evt.preventDefault();
    this.dragging.set(true);
  }

  onDrop(evt: DragEvent): void {
    evt.preventDefault();
    this.dragging.set(false);
    const file = evt.dataTransfer?.files?.[0];
    if (file && file.name.toLowerCase().endsWith('.zip')) {
      this.selectedFile.set(file);
      this.errorMessage.set(null);
    } else {
      this.errorMessage.set('Only .zip files are accepted');
    }
  }
}
