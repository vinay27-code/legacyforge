import { Component, Input, output, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { FileTreeNode } from './repo.models';

/**
 * Recursive file tree with folders you can expand/collapse and files you can click.
 */
@Component({
  selector: 'app-file-tree',
  standalone: true,
  imports: [CommonModule, MatIconModule],
  template: `
    @if (node) {
      @if (node.type === 'dir') {
        @if (node.name) {
          <div class="row dir" (click)="toggle()">
            <mat-icon class="chevron" [class.open]="expanded()">chevron_right</mat-icon>
            <mat-icon class="icon">folder</mat-icon>
            <span class="name">{{ node.name }}</span>
          </div>
        }
        @if (expanded() || !node.name) {
          <div class="children" [class.root]="!node.name">
            @for (child of node.children ?? []; track child.path) {
              <app-file-tree
                [node]="child"
                [selectedPath]="selectedPath"
                (fileSelected)="fileSelected.emit($event)"
              ></app-file-tree>
            }
          </div>
        }
      } @else {
        <div
          class="row file"
          [class.selected]="node.path === selectedPath"
          (click)="fileSelected.emit(node.path)"
        >
          <span class="spacer"></span>
          <mat-icon class="icon">{{ node.binary ? 'insert_drive_file' : 'description' }}</mat-icon>
          <span class="name">{{ node.name }}</span>
          @if (node.language) {
            <span class="lang">{{ node.language }}</span>
          }
        </div>
      }
    }
  `,
  styles: [`
    :host { display: block; }
    .row {
      display: flex; align-items: center; gap: 4px;
      padding: 4px 8px; cursor: pointer;
      user-select: none;
      border-radius: 4px;
      font-size: 0.9rem;
    }
    .row:hover { background: rgba(255, 255, 255, 0.04); }
    .row.selected { background: rgba(255, 140, 66, 0.15); color: var(--lf-accent); }
    .chevron { transition: transform 0.15s; opacity: 0.6; }
    .chevron.open { transform: rotate(90deg); }
    .icon { color: var(--lf-muted); }
    .file .icon { color: var(--lf-accent-2); opacity: 0.7; }
    .name { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .lang { margin-left: auto; color: var(--lf-muted); font-size: 0.75rem; }
    .spacer { display: inline-block; width: 24px; }
    .children { padding-left: 20px; }
    .children.root { padding-left: 0; }
  `],
})
export class FileTreeComponent {
  @Input({ required: true }) node!: FileTreeNode;
  @Input() selectedPath: string | null = null;
  fileSelected = output<string>();

  expanded = signal(false);

  toggle(): void {
    this.expanded.update((v) => !v);
  }
}
