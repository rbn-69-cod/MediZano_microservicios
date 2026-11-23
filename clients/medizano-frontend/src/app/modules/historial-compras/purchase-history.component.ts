import { Component, OnInit } from '@angular/core';
import { BillingService } from '../../core/services/billing.service';
import { DialogService } from '../../core/services/dialog.service';
import { BillPaymentStatus, BillResponse, PaymentStatus } from '../../core/models/billing.model';
import { formatDateTime, parseBackendDate } from '../../core/utils/date-time.util';
import { catchError } from 'rxjs/operators';
import { forkJoin, of } from 'rxjs';

@Component({
  selector: 'app-purchase-history',
  standalone: false,
  templateUrl: './purchase-history.component.html',
  styleUrls: ['./purchase-history.component.scss']
})
export class PurchaseHistoryComponent implements OnInit {
  bills: BillResponse[] = [];
  isLoading = false;
  searchTerm = '';

  constructor(
    private billingService: BillingService,
    private dialogService: DialogService
  ) {}

  ngOnInit(): void {
    this.loadPurchaseHistory();
  }

  loadPurchaseHistory(): void {
    this.isLoading = true;
    forkJoin({
      bills: this.billingService.getAllBills().pipe(catchError(() => of([]))),
      electronicInvoices: this.billingService.getElectronicInvoices().pipe(catchError(() => of([])))
    }).subscribe({
      next: ({ bills, electronicInvoices }) => {
        const electronicBills: BillResponse[] = electronicInvoices.map(invoice => ({
          id: -invoice.id,
          orderId: invoice.ordenId,
          billNumber: invoice.numeroFactura,
          billDate: invoice.createdAt,
          customerName: invoice.clienteNombre || 'Cliente general',
          customerPhone: '',
          customerEmail: '',
          cashierId: 0,
          cashierName: `Orden ${invoice.numeroOrden}`,
          subtotal: invoice.subtotal,
          totalGst: invoice.impuesto,
          totalAmount: invoice.total,
          paymentStatus: invoice.estado === 'EMITIDA' ? BillPaymentStatus.PAID : BillPaymentStatus.PENDING,
          cancelled: false,
          cancellationReason: undefined,
          createdAt: invoice.createdAt,
          items: invoice.items.map(item => ({
            id: 0,
            medicineId: item.productoId,
            medicineName: item.productoNombre,
            batchId: undefined,
            batchNumber: '',
            quantity: item.cantidad,
            unitPrice: item.precioUnitario,
            gstPercentage: 18,
            gstAmount: item.subtotal * 0.18 / 1.18,
            totalAmount: item.subtotal
          })),
          payments: [{
            id: 0,
            paymentReference: invoice.referenciaPago || invoice.numeroOrden,
            mode: invoice.metodoPago as any,
            amount: invoice.total,
            status: PaymentStatus.COMPLETED,
            paymentDate: invoice.createdAt
          }]
        }));
        this.bills = [...bills, ...electronicBills]
          .sort((a, b) => parseBackendDate(b.billDate).getTime() - parseBackendDate(a.billDate).getTime());
        this.isLoading = false;
      },
      error: (error) => {
        console.error('Error loading purchase history:', error);
        this.dialogService.error('Error al cargar el historial de ventas: ' + (error.message || 'Error desconocido'));
        this.isLoading = false;
      }
    });
  }

  get filteredBills(): BillResponse[] {
    if (!this.searchTerm.trim()) {
      return this.bills;
    }
    const term = this.searchTerm.toLowerCase().trim();
    return this.bills.filter(bill =>
      bill.billNumber.toLowerCase().includes(term) ||
      (bill.customerName && bill.customerName.toLowerCase().includes(term)) ||
      (bill.customerPhone && bill.customerPhone.toLowerCase().includes(term)) ||
      bill.items.some(item => item.medicineName.toLowerCase().includes(term))
    );
  }

  get totalSales(): number {
    return this.filteredBills.reduce((sum, bill) => sum + bill.totalAmount, 0);
  }

  get totalBills(): number {
    return this.filteredBills.length;
  }

  get totalItems(): number {
    return this.filteredBills.reduce((sum, bill) => 
      sum + bill.items.reduce((itemSum, item) => itemSum + item.quantity, 0), 0
    );
  }

  trackByBillId(index: number, bill: BillResponse): any {
    return bill.id || index;
  }

  formatDate(dateString: string): string {
    return parseBackendDate(dateString).toLocaleDateString('es-PE', {
      timeZone: 'America/Lima',
      year: 'numeric',
      month: 'short',
      day: 'numeric'
    });
  }

  formatDateTime(dateString: string): string {
    return formatDateTime(dateString);
  }

  getPaymentStatusClass(status: string): string {
    switch (status) {
      case 'PAID':
        return 'badge-success';
      case 'PENDING':
      case 'PARTIALLY_PAID':
        return 'badge-warning';
      case 'REFUNDED':
        return 'badge-error';
      default:
        return 'badge-warning';
    }
  }

  getPaymentStatusLabel(status: string): string {
    switch (status) {
      case 'PAID':
        return 'Pagado';
      case 'PENDING':
        return 'Pendiente';
      case 'PARTIALLY_PAID':
        return 'Pago parcial';
      case 'REFUNDED':
        return 'Devuelto';
      default:
        return status;
    }
  }

  getTotalPaid(bill: BillResponse): number {
    return bill.payments
      .filter(p => p.status === 'COMPLETED')
      .reduce((sum, payment) => sum + payment.amount, 0);
  }

  getRemainingBalance(bill: BillResponse): number {
    const totalPaid = this.getTotalPaid(bill);
    return bill.totalAmount - totalPaid;
  }

  isOverpaid(bill: BillResponse): boolean {
    return this.getRemainingBalance(bill) < 0;
  }

  getOverpaymentAmount(bill: BillResponse): number {
    const remaining = this.getRemainingBalance(bill);
    return remaining < 0 ? Math.abs(remaining) : 0;
  }

  reprintBillPdf(bill: BillResponse): void {
    if (bill.orderId) {
      this.isLoading = true;
      this.billingService.downloadOrderInvoicePdf(bill.orderId).subscribe({
        next: (blob) => this.downloadPdf(blob, bill.billNumber),
        error: (error) => this.handlePdfError(error)
      });
      return;
    }
    if (!bill?.id) {
      this.dialogService.error('No se pudo identificar el comprobante.');
      return;
    }

    this.isLoading = true;
    this.billingService.downloadBillPdf(bill.id).subscribe({
      next: (blob) => this.downloadPdf(blob, bill.billNumber),
      error: (error) => this.handlePdfError(error)
    });
  }

  private downloadPdf(blob: Blob, billNumber: string): void {
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `Comprobante_${billNumber}.pdf`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    window.URL.revokeObjectURL(url);
    this.isLoading = false;
  }

  private handlePdfError(error: any): void {
    console.error('Error reprinting bill PDF:', error);
    this.dialogService.error('Error al reimprimir PDF: ' + (error.message || 'Error desconocido'));
    this.isLoading = false;
  }
}
