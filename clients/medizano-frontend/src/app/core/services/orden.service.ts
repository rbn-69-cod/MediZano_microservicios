import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from './api.service';
import { Orden, CrearOrdenRequest, ValidarRecojoRequest } from '../models/orden.model';

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

  cancelarOrden(id: number): Observable<Orden> {
    return this.apiService.post<Orden>(`/v1/ordenes/${id}/cancelar`, {});
  }

  validarRecojo(request: ValidarRecojoRequest): Observable<Orden> {
    return this.apiService.post<Orden>('/v1/ordenes/recojo/validar', request);
  }

  consultarRecojo(codigoRecojo: string): Observable<Orden> {
    return this.apiService.get<Orden>(`/v1/ordenes/recojo/${encodeURIComponent(codigoRecojo.trim())}`);
  }
}
