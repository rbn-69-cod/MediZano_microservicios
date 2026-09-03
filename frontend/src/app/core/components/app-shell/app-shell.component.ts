import { Component, OnInit } from '@angular/core';
import { Router, NavigationEnd } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { UserRole } from '../../models/user.model';
import { filter } from 'rxjs/operators';

interface NavItem {
  label: string;
  route: string;
  icon: string;
  roles: UserRole[];
}

@Component({
  selector: 'app-shell',
  templateUrl: './app-shell.component.html',
  styleUrls: ['./app-shell.component.scss']
})
export class AppShellComponent implements OnInit {
  readonly UserRole = UserRole;
  
  currentRoute = '';
  sidebarExpanded = false;

  navItems: NavItem[] = [
    {
      label: 'Ventas',
      route: '/billing',
      icon: 'billing',
      roles: [UserRole.CASHIER, UserRole.ADMIN]
    },
    {
      label: 'Inventario',
      route: '/inventory',
      icon: 'inventory',
      roles: [UserRole.STOCK_MONITOR, UserRole.ADMIN]
    },
    {
      label: 'Medicamentos',
      route: '/medicines',
      icon: 'medicines',
      roles: [UserRole.STOCK_KEEPER, UserRole.ADMIN]
    },
    {
      label: 'Devoluciones',
      route: '/returns',
      icon: 'returns',
      roles: [UserRole.CUSTOMER_SUPPORT, UserRole.ADMIN]
    },
    {
      label: 'Reportes',
      route: '/reports',
      icon: 'reports',
      roles: [UserRole.ANALYST, UserRole.MANAGER, UserRole.ADMIN]
    },
    {
      label: 'Historial de compras',
      route: '/purchase-history',
      icon: 'purchase-history',
      roles: [UserRole.MANAGER, UserRole.ADMIN]
    },
    {
      label: 'Actividad de usuarios',
      route: '/user-activity',
      icon: 'user-activity',
      roles: [UserRole.ADMIN]
    },
    {
      label: 'Historial de accesos',
      route: '/login-history',
      icon: 'login-history',
      roles: [UserRole.ADMIN]
    },
    {
      label: 'Usuarios',
      route: '/user-management',
      icon: 'user-management',
      roles: [UserRole.ADMIN]
    }
  ];

  constructor(
    public authService: AuthService,
    private router: Router
  ) {}

  ngOnInit(): void {
    // Track current route for active state
    this.router.events
      .pipe(filter(event => event instanceof NavigationEnd))
      .subscribe((event: any) => {
        this.currentRoute = event.url;
      });
    
    this.currentRoute = this.router.url;
  }

  get visibleNavItems(): NavItem[] {
    return this.navItems.filter(item => 
      this.authService.hasAnyRole(item.roles)
    );
  }

  isActive(route: string): boolean {
    return this.currentRoute.startsWith(route);
  }

  getRoleLabel(role: UserRole | string): string {
    const labels: Record<string, string> = {
      ADMIN: 'Administrador',
      CASHIER: 'Cajero',
      STOCK_MONITOR: 'Supervisor de inventario',
      STOCK_KEEPER: 'Encargado de almacén',
      CUSTOMER_SUPPORT: 'Atención al cliente',
      ANALYST: 'Analista',
      MANAGER: 'Gerente'
    };
    return labels[role] || role;
  }

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/auth/login']);
  }
}
