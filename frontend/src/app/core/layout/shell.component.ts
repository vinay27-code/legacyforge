import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatListModule } from '@angular/material/list';
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
    MatToolbarModule,
    MatSidenavModule,
    MatListModule,
    MatIconModule,
    MatButtonModule,
    MatMenuModule,
  ],
  template: `
    <mat-sidenav-container class="container">
      <mat-sidenav mode="side" opened class="sidenav">
        <div class="brand">
          <mat-icon>bolt</mat-icon>
          <span>LegacyForge</span>
        </div>
        <mat-nav-list>
          <a mat-list-item routerLink="/dashboard" routerLinkActive="active">
            <mat-icon matListItemIcon>dashboard</mat-icon>
            <span matListItemTitle>Dashboard</span>
          </a>
          <a mat-list-item routerLink="/repos" routerLinkActive="active">
            <mat-icon matListItemIcon>source</mat-icon>
            <span matListItemTitle>Migrations</span>
          </a>
          <a mat-list-item routerLink="/repos/new" routerLinkActive="active" [routerLinkActiveOptions]="{exact:true}">
            <mat-icon matListItemIcon>add_circle</mat-icon>
            <span matListItemTitle>New migration</span>
          </a>
        </mat-nav-list>
      </mat-sidenav>

      <mat-sidenav-content>
        <mat-toolbar color="primary" class="toolbar">
          <span class="spacer"></span>
          <button mat-button [matMenuTriggerFor]="userMenu">
            <mat-icon>account_circle</mat-icon>
            <span class="email">{{ auth.user()?.email }}</span>
          </button>
          <mat-menu #userMenu="matMenu">
            <button mat-menu-item (click)="logout()">
              <mat-icon>logout</mat-icon>
              <span>Log out</span>
            </button>
          </mat-menu>
        </mat-toolbar>

        <main class="content">
          <router-outlet></router-outlet>
        </main>
      </mat-sidenav-content>
    </mat-sidenav-container>
  `,
  styles: [`
    .container { height: 100vh; }
    .sidenav {
      width: 240px;
      background: var(--lf-bg-elev);
      border-right: 1px solid var(--lf-border);
    }
    .brand {
      display: flex; align-items: center; gap: 8px;
      padding: 20px 16px; font-size: 1.15rem; font-weight: 600;
      border-bottom: 1px solid var(--lf-border);
    }
    .brand mat-icon { color: var(--lf-accent); }
    .toolbar { position: sticky; top: 0; z-index: 10; }
    .spacer { flex: 1; }
    .email { margin-left: 6px; }
    .content { padding: 32px; min-height: calc(100vh - 64px); }
    .active { background: rgba(255, 140, 66, 0.12); }
  `],
})
export class ShellComponent {
  auth = inject(AuthService);
  private router = inject(Router);

  logout(): void {
    this.auth.logout().subscribe(() => this.router.navigateByUrl('/login'));
  }
}
