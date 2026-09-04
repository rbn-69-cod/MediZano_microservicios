import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from './api.service';
import { Orden, CrearOrdenRequest } from '../models/orden.model';

@Injectable({
  providedIn: 'root'
})
export class OrdenService {
  constructor(private apiService: ApiService) {}

  listarOrdenes(): Observable<Orden[]> {
    return this.apiService.get<Orden[]>('/v1/ordenes');
  }

  obtenerPorId(id: number): Observable<Orden> {
    return this.apiService.get<Orden>(`/v1/ordenes/${id}`);
  }

  crearOrden(request: CrearOrdenRequest): Observable<Orden> {
    return this.apiService.post<Orden>('/v1/ordenes', request);
  }

  confirmarPago(id: number, referenciaPago?: string): Observable<Orden> {
    const url = referenciaPago 
      ? `/v1/ordenes/${id}/confirmar-pago?referenciaPago=${encodeURIComponent(referenciaPago)}`
      : `/v1/ordenes/${id}/confirmar-pago`;
    return this.apiService.post<Orden>(url, {});
  }

  cancelarOrden(id: number): Observable<Orden> {
    return this.apiService.post<Orden>(`/v1/ordenes/${id}/cancelar`, {});
  }
}
