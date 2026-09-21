import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import hljs from 'highlight.js/lib/core';
import javaLang from 'highlight.js/lib/languages/java';
import xmlLang from 'highlight.js/lib/languages/xml';
import jsLang from 'highlight.js/lib/languages/javascript';
import tsLang from 'highlight.js/lib/languages/typescript';
import cssLang from 'highlight.js/lib/languages/css';
import jsonLang from 'highlight.js/lib/languages/json';
import yamlLang from 'highlight.js/lib/languages/yaml';
import sqlLang from 'highlight.js/lib/languages/sql';
import propertiesLang from 'highlight.js/lib/languages/properties';
import bashLang from 'highlight.js/lib/languages/bash';
import mdLang from 'highlight.js/lib/languages/markdown';
import pyLang from 'highlight.js/lib/languages/python';
import { RepoService } from './repo.service';
import { FileContent, FileTreeNode, RepoSummary } from './repo.models';
import { FileTreeComponent } from './file-tree.component';

hljs.registerLanguage('java', javaLang);
hljs.registerLanguage('xml', xmlLang);
hljs.registerLanguage('jsp', xmlLang);
hljs.registerLanguage('html', xmlLang);
hljs.registerLanguage('javascript', jsLang);
hljs.registerLanguage('typescript', tsLang);
hljs.registerLanguage('css', cssLang);
hljs.registerLanguage('json', jsonLang);
hljs.registerLanguage('yaml', yamlLang);
hljs.registerLanguage('sql', sqlLang);
hljs.registerLanguage('properties', propertiesLang);
hljs.registerLanguage('shell', bashLang);
hljs.registerLanguage('markdown', mdLang);
hljs.registerLanguage('python', pyLang);

@Component({
  selector: 'app-repo-detail',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    MatCardModule,
    MatIconModule,
    MatButtonModule,
    MatChipsModule,
    MatProgressSpinnerModule,
    FileTreeComponent,
  ],
  template: `
    @if (loading()) {
      <div class="center"><mat-spinner></mat-spinner></div>
    } @else if (repo()) {
      <div class="page">
        <header>
          <a mat-button routerLink="/repos"><mat-icon>arrow_back</mat-icon> All migrations</a>
          <div class="title">
            <h1>{{ repo()!.name }}</h1>
            <mat-chip-set>
              <mat-chip>{{ repo()!.fileCount }} files</mat-chip>
              <mat-chip>{{ formatBytes(repo()!.totalSizeBytes) }}</mat-chip>
              <mat-chip>{{ repo()!.sourceType }}</mat-chip>
            </mat-chip-set>
          </div>
          @if (repo()!.sourceUrl) {
            <a class="source-link" [href]="repo()!.sourceUrl" target="_blank">
              <mat-icon>open_in_new</mat-icon> {{ repo()!.sourceUrl }}
            </a>
          }
        </header>

        <div class="split">
          <aside class="tree">
            <div class="tree-head">
              <mat-icon>folder_open</mat-icon>
              <span>Files</span>
            </div>
            @if (tree()) {
              <app-file-tree
                [node]="tree()!"
                [selectedPath]="selectedPath()"
                (fileSelected)="openFile($event)"
              ></app-file-tree>
            }
          </aside>

          <section class="viewer">
            @if (currentFile()) {
              <div class="file-head">
                <mat-icon>description</mat-icon>
                <span class="path">{{ currentFile()!.path }}</span>
                <span class="meta">
                  {{ currentFile()!.language ?? 'plaintext' }}
                  · {{ formatBytes(currentFile()!.sizeBytes) }}
                </span>
              </div>
              @if (currentFile()!.binary || currentFile()!.content === null) {
                <div class="empty">Binary or oversized file. Preview unavailable.</div>
              } @else {
                <pre class="code"><code [innerHTML]="highlighted()"></code></pre>
              }
            } @else {
              <div class="empty">Pick a file from the tree on the left.</div>
            }
          </section>
        </div>
      </div>
    } @else {
      <p>Repo not found.</p>
    }
  `,
  styles: [`
    .center { display: grid; place-items: center; padding: 80px; }
    .page { display: flex; flex-direction: column; height: calc(100vh - 128px); color: var(--lf-text); }
    header { margin-bottom: 16px; }
    .title { display: flex; align-items: center; gap: 16px; margin: 8px 0; }
    .title h1 { margin: 0; letter-spacing: -0.02em; }
    .source-link {
      color: var(--lf-muted); font-size: 0.9rem;
      display: inline-flex; align-items: center; gap: 4px;
    }
    .split {
      flex: 1; display: grid; grid-template-columns: 320px 1fr;
      gap: 16px; min-height: 0;
    }
    .tree {
      background: var(--lf-bg-elev);
      border: 1px solid var(--lf-border);
      border-radius: 12px;
      overflow-y: auto;
      padding: 8px;
      color: var(--lf-text);
    }
    .tree-head {
      display: flex; align-items: center; gap: 8px;
      padding: 8px 12px; font-weight: 500; color: var(--lf-muted);
      border-bottom: 1px solid var(--lf-border); margin-bottom: 8px;
    }
    .viewer {
      background: #0d1220;
      border: 1px solid var(--lf-border);
      border-radius: 12px;
      overflow: hidden;
      display: flex; flex-direction: column;
    }
    .file-head {
      display: flex; align-items: center; gap: 8px;
      padding: 12px 16px; border-bottom: 1px solid var(--lf-border);
      background: rgba(0,0,0,0.35);
      color: var(--lf-text);
    }
    .file-head .path { font-family: 'SF Mono', monospace; font-size: 0.9rem; }
    .file-head .meta { margin-left: auto; color: var(--lf-muted); font-size: 0.8rem; }
    .code {
      flex: 1;
      margin: 0;
      padding: 16px 20px;
      overflow: auto;
      font-family: 'SF Mono', Menlo, Monaco, "Courier New", monospace;
      font-size: 0.9rem;
      line-height: 1.55;
      white-space: pre;
      background: #0d1220;
      color: #e6ebff;
      tab-size: 4;
    }
    .empty { padding: 60px; text-align: center; color: var(--lf-muted); }
  `],
})
export class RepoDetailComponent implements OnInit {
  private service = inject(RepoService);
  private route = inject(ActivatedRoute);

  repo = signal<RepoSummary | null>(null);
  tree = signal<FileTreeNode | null>(null);
  currentFile = signal<FileContent | null>(null);
  selectedPath = signal<string | null>(null);
  loading = signal(true);
  highlighted = signal<string>('');

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id')!;
    this.service.get(id).subscribe((repo) => this.repo.set(repo));
    this.service.tree(id).subscribe((tree) => {
      this.tree.set(tree);
      this.loading.set(false);
    });
  }

  openFile(path: string): void {
    this.selectedPath.set(path);
    const id = this.route.snapshot.paramMap.get('id')!;
    this.service.file(id, path).subscribe((content) => {
      this.currentFile.set(content);
      this.highlighted.set(this.render(content));
    });
  }

  private render(file: FileContent): string {
    if (!file.content) return '';
    const lang = file.language ?? '';
    const known = hljs.getLanguage(lang);
    try {
      return known
        ? hljs.highlight(file.content, { language: lang, ignoreIllegals: true }).value
        : this.escape(file.content);
    } catch {
      return this.escape(file.content);
    }
  }

  private escape(s: string): string {
    return s.replace(/[&<>"']/g, (c) => ({
      '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
    }[c]!));
  }

  formatBytes(bytes: number): string {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    if (bytes < 1024 * 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
    return `${(bytes / 1024 / 1024 / 1024).toFixed(2)} GB`;
  }
}
