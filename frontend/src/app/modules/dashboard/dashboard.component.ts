import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { ReportService } from '../../core/services/report.service';
import { InventoryService } from '../../core/services/inventory.service';
import { AuditLogService, AuditLogResponse } from '../../core/services/audit-log.service';
import { AuthService } from '../../core/services/auth.service';
import { SalesReportResponse, CashRegisterReportResponse } from '../../core/models/report.model';
import { Medicine } from '../../core/models/medicine.model';
import { Batch } from '../../core/models/batch.model';
import { formatDateTime } from '../../core/utils/date-time.util';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';

@Component({
  selector: 'app-dashboard',
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.scss']
})
export class DashboardComponent implements OnInit {
  isLoading = true;

  // Real KPIs from backend
  salesReport: SalesReportResponse | null = null;
  cashReport: CashRegisterReportResponse | null = null;
  medicines: Medicine[] = [];
  lowStockBatches: Batch[] = [];
  recentActivities: AuditLogResponse[] = [];

  todayFormatted: string = '';

  constructor(
    public authService: AuthService,
    private router: Router,
    private reportService: ReportService,
    private inventoryService: InventoryService,
    private auditLogService: AuditLogService
  ) {
    const today = new Date();
    this.todayFormatted = today.toLocaleDateString('es-PE', {
      weekday: 'long',
      year: 'numeric',
      month: 'long',
      day: 'numeric'
    });
  }

  ngOnInit(): void {
    this.loadDashboardData();
  }

  get todayIso(): string {
    const now = new Date();
    const year = now.getFullYear();
    const month = String(now.getMonth() + 1).padStart(2, '0');
    const day = String(now.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }

  loadDashboardData(): void {
    this.isLoading = true;
    const today = this.todayIso;

    forkJoin({
      sales: this.reportService.getDailySalesReport(today, today).pipe(catchError(() => of(null))),
      cash: this.reportService.getCashRegisterReport(today, today).pipe(catchError(() => of(null))),
      medicines: this.inventoryService.getAllMedicines().pipe(catchError(() => of([]))),
      lowStock: this.inventoryService.getLowStockBatches(10).pipe(catchError(() => of([]))),
      audit: this.auditLogService.getAllAuditLogs().pipe(catchError(() => of([])))
    }).subscribe({
      next: (res) => {
        this.salesReport = res.sales;
        this.cashReport = res.cash;
        this.medicines = res.medicines || [];
        this.lowStockBatches = res.lowStock || [];
        this.recentActivities = (res.audit || []).slice(0, 5);
        this.isLoading = false;
      },
      error: () => {
        this.isLoading = false;
      }
    });
  }

  get totalSalesToday(): number {
    return this.salesReport?.totalSales || 0;
  }

  get totalBillsToday(): number {
    return this.salesReport?.totalBills || 0;
  }

  get cashInRegisterToday(): number {
    return this.cashReport?.totalCash || 0;
  }

  get activeMedicinesCount(): number {
    return this.medicines.filter(m => m.status === 'ACTIVE').length;
  }

  get outOfStockMedicinesCount(): number {
    return this.medicines.filter(m => m.outOfStock).length;
  }

  formatDate(dateString: string): string {
    return formatDateTime(dateString, true);
  }

  navigate(path: string): void {
    this.router.navigate([path]);
  }
}

