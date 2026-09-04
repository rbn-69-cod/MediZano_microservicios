import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { OrdenService } from '../../core/services/orden.service';
import { DialogService } from '../../core/services/dialog.service';
import { Orden, DetalleOrden } from '../../core/models/orden.model';
import { formatDateTime } from '../../core/utils/date-time.util';

@Component({
  selector: 'app-ordenes',
  templateUrl: './ordenes.component.html',
  styleUrls: ['./ordenes.component.scss']
})
export class OrdenesComponent implements OnInit {
  ordenes: Orden[] = [];
  filteredOrdenes: Orden[] = [];
  isLoading = false;
  searchTerm = '';
  selectedEstado = 'TODOS';

  // Modal detalle
  showDetailModal = false;
  selectedOrden: Orden | null = null;
  isLoadingDetail = false;

  constructor(
    private ordenService: OrdenService,
    private dialogService: DialogService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.cargarOrdenes();
  }

  cargarOrdenes(): void {
    this.isLoading = true;
    this.ordenService.listarOrdenes().subscribe({
      next: (ordenes) => {
        this.ordenes = ordenes;
        this.filtrar();
        this.isLoading = false;
      },
      error: (err) => {
        console.error('Error al cargar órdenes:', err);
        this.dialogService.error(err.message || 'Error al conectar con orden-ms');
        this.isLoading = false;
      }
    });
  }

  filtrar(): void {
    let result = [...this.ordenes];

    if (this.selectedEstado !== 'TODOS') {
      result = result.filter(o => o.estado === this.selectedEstado);
    }

    const term = this.searchTerm.toLowerCase().trim();
    if (term) {
      result = result.filter(o =>
        o.numeroOrden.toLowerCase().includes(term) ||
        (o.clienteNombre && o.clienteNombre.toLowerCase().includes(term)) ||
        (o.referenciaPago && o.referenciaPago.toLowerCase().includes(term))
      );
    }

    this.filteredOrdenes = result;
  }

  verDetalle(orden: Orden): void {
    this.isLoadingDetail = true;
    this.showDetailModal = true;
    this.ordenService.obtenerPorId(orden.id).subscribe({
      next: (full) => {
        this.selectedOrden = full;
        this.isLoadingDetail = false;
      },
      error: (err) => {
        this.selectedOrden = orden;
        this.isLoadingDetail = false;
      }
    });
  }

  cerrarDetalle(): void {
    this.showDetailModal = false;
    this.selectedOrden = null;
  }

  async confirmarPago(orden: Orden): Promise<void> {
    const confirmed = await this.dialogService.confirm(
      `¿Deseas confirmar el pago de la orden "${orden.numeroOrden}" por S/ ${orden.total.toFixed(2)}? Esto descontará el inventario en inventario-ms.`,
      'Confirmar Pago'
    );
    if (!confirmed) return;

    this.isLoading = true;
    this.ordenService.confirmarPago(orden.id, 'PAGO-MANUAL').subscribe({
      next: (res) => {
        this.dialogService.success(`Orden ${res.numeroOrden} confirmada como PAGADA`);
        this.cargarOrdenes();
        if (this.showDetailModal && this.selectedOrden?.id === orden.id) {
          this.selectedOrden = res;
        }
      },
      error: (err) => {
        this.dialogService.error(err.message || 'Error al confirmar pago de orden');
        this.isLoading = false;
      }
    });
  }

  async cancelarOrden(orden: Orden): Promise<void> {
    const confirmed = await this.dialogService.confirm(
      `¿Deseas cancelar la orden "${orden.numeroOrden}"?`,
      'Cancelar Orden'
    );
    if (!confirmed) return;

    this.isLoading = true;
    this.ordenService.cancelarOrden(orden.id).subscribe({
      next: (res) => {
        this.dialogService.success(`Orden ${res.numeroOrden} CANCELADA`);
        this.cargarOrdenes();
        if (this.showDetailModal && this.selectedOrden?.id === orden.id) {
          this.selectedOrden = res;
        }
      },
      error: (err) => {
        this.dialogService.error(err.message || 'Error al cancelar la orden');
        this.isLoading = false;
      }
    });
  }

  irAPos(): void {
    this.router.navigate(['/billing']);
  }

  formatDate(dateString: string): string {
    return formatDateTime(dateString, true);
  }
}
