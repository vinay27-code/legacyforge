import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatMenuModule } from '@angular/material/menu';
import { AuthService } from '../auth/auth.service';

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    RouterLinkActive,
    RouterOutlet,
    MatIconModule,
    MatButtonModule,
    MatMenuModule,
  ],
  template: `
    <div class="app">
      <aside class="sidebar">
        <div class="brand">
          <div class="brand-mark">LF</div>
          <div class="brand-text">
            <span class="brand-legacy">Legacy</span><span class="brand-forge">Forge</span>
          </div>
        </div>

        <nav class="nav">
          <div class="nav-section">
            <div class="nav-label">Overview</div>
            <a routerLink="/dashboard" routerLinkActive="active" class="nav-item">
              <mat-icon>dashboard</mat-icon>
              <span>Dashboard</span>
            </a>
          </div>

          <div class="nav-section">
            <div class="nav-label">Work</div>
            <a routerLink="/repos" routerLinkActive="active"
               [routerLinkActiveOptions]="{exact:true}" class="nav-item">
              <mat-icon>account_tree</mat-icon>
              <span>Migrations</span>
            </a>
            <a routerLink="/repos/new" routerLinkActive="active"
               [routerLinkActiveOptions]="{exact:true}" class="nav-item">
              <mat-icon>add_circle_outline</mat-icon>
              <span>New migration</span>
            </a>
          </div>
        </nav>

        <div class="sidebar-footer">
          <button class="user-pill" [matMenuTriggerFor]="userMenu">
            <div class="avatar">{{ initials() }}</div>
            <div class="user-info">
              <div class="user-email">{{ auth.user()?.email }}</div>
              <div class="user-role">Admin</div>
            </div>
            <mat-icon class="chevron">unfold_more</mat-icon>
          </button>
          <mat-menu #userMenu="matMenu" xPosition="after" yPosition="above">
            <button mat-menu-item (click)="logout()">
              <mat-icon>logout</mat-icon>
              <span>Log out</span>
            </button>
          </mat-menu>
        </div>
      </aside>

      <main class="content">
        <router-outlet></router-outlet>
      </main>
    </div>
  `,
  styles: [`
    :host { display: block; height: 100vh; overflow: hidden; }

    .app {
      display: grid;
      grid-template-columns: 260px 1fr;
      height: 100vh;
    }

    /* --------- Sidebar --------- */

    .sidebar {
      display: flex; flex-direction: column;
      background: linear-gradient(180deg, var(--lf-bg-elev) 0%, var(--lf-bg) 100%);
      border-right: 1px solid var(--lf-border);
      padding: 20px 14px 14px;
      overflow-y: auto;
      position: relative;
    }
    .sidebar::after {
      /* subtle vertical accent line on the outer edge */
      content: '';
      position: absolute; right: -1px; top: 0; bottom: 0; width: 1px;
      background: linear-gradient(180deg, transparent 0%, var(--lf-border-strong) 30%, transparent 100%);
      opacity: 0.5;
    }

    /* --------- Brand lockup --------- */

    .brand {
      display: flex; align-items: center; gap: 10px;
      padding: 4px 8px 24px;
    }
    .brand-mark {
      width: 32px; height: 32px;
      border-radius: 9px;
      background: var(--lf-gradient-primary);
      display: grid; place-items: center;
      font-family: var(--lf-font-display);
      font-weight: 800;
      font-size: 0.82rem;
      color: white;
      letter-spacing: -0.03em;
      box-shadow: 0 4px 16px var(--lf-primary-glow),
                  inset 0 1px 0 rgba(255,255,255,0.25);
    }
    .brand-text {
      font-family: var(--lf-font-display);
      font-weight: 700;
      font-size: 1.15rem;
      letter-spacing: -0.02em;
      line-height: 1;
    }
    .brand-legacy { color: var(--lf-text); }
    .brand-forge {
      background: var(--lf-gradient-primary);
      -webkit-background-clip: text; background-clip: text;
      -webkit-text-fill-color: transparent; color: transparent;
    }

    /* --------- Nav --------- */

    .nav {
      flex: 1; min-height: 0;
      display: flex; flex-direction: column; gap: 20px;
      overflow-y: auto;
    }
    .nav-section { display: flex; flex-direction: column; gap: 2px; }
    .nav-label {
      padding: 0 12px 6px;
      font-size: 0.68rem; font-weight: 600;
      letter-spacing: 0.09em; text-transform: uppercase;
      color: var(--lf-text-dim);
    }

    .nav-item {
      position: relative;
      display: flex; align-items: center; gap: 12px;
      padding: 9px 12px;
      border-radius: 8px;
      font-size: 0.92rem;
      font-weight: 500;
      color: var(--lf-text-muted);
      text-decoration: none;
      transition: color var(--lf-t-fast), background var(--lf-t-fast);
    }
    .nav-item mat-icon {
      font-size: 20px; height: 20px; width: 20px;
      color: var(--lf-text-dim);
      transition: color var(--lf-t-fast);
    }
    .nav-item:hover {
      color: var(--lf-text);
      background: rgba(255,255,255,0.03);
    }
    .nav-item:hover mat-icon { color: var(--lf-text-muted); }

    .nav-item.active {
      color: var(--lf-text);
      background: linear-gradient(90deg, var(--lf-primary-glow) 0%, transparent 70%);
    }
    .nav-item.active mat-icon { color: var(--lf-primary); }
    .nav-item.active::before {
      content: '';
      position: absolute;
      left: 0; top: 6px; bottom: 6px; width: 3px;
      background: var(--lf-gradient-primary);
      border-radius: 0 3px 3px 0;
    }

    /* --------- Footer / user pill --------- */

    .sidebar-footer {
      padding-top: 14px;
      border-top: 1px solid var(--lf-border);
    }
    .user-pill {
      width: 100%;
      display: flex; align-items: center; gap: 10px;
      padding: 8px 10px;
      border-radius: 10px;
      border: 1px solid transparent;
      background: transparent;
      color: var(--lf-text);
      cursor: pointer;
      font: inherit; text-align: left;
      transition: all var(--lf-t-fast);
    }
    .user-pill:hover {
      background: rgba(255,255,255,0.04);
      border-color: var(--lf-border);
    }
    .avatar {
      width: 32px; height: 32px; border-radius: 50%;
      background: var(--lf-gradient-accent);
      display: grid; place-items: center;
      font-weight: 600; font-size: 0.78rem;
      color: white;
      flex: 0 0 auto;
      box-shadow: inset 0 1px 0 rgba(255,255,255,0.25);
    }
    .user-info { flex: 1; min-width: 0; }
    .user-email {
      font-size: 0.82rem; font-weight: 500;
      overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
      color: var(--lf-text);
    }
    .user-role {
      font-size: 0.7rem; color: var(--lf-text-dim);
      letter-spacing: 0.02em;
    }
    .chevron {
      font-size: 16px; height: 16px; width: 16px;
      color: var(--lf-text-dim);
      flex: 0 0 auto;
    }

    /* --------- Main content --------- */

    .content {
      overflow-y: auto;
      height: 100vh;
      background: var(--lf-bg);
    }
  `],
})
export class ShellComponent {
  auth = inject(AuthService);
  private router = inject(Router);

  logout(): void {
    this.auth.logout().subscribe(() => this.router.navigateByUrl('/login'));
  }

  initials(): string {
    const email = this.auth.user()?.email ?? '';
    if (!email) return '?';
    const local = email.split('@')[0];
    const parts = local.split(/[._-]/).filter(Boolean);
    if (parts.length >= 2) return (parts[0][0] + parts[1][0]).toUpperCase();
    return local.slice(0, 2).toUpperCase();
  }
}
