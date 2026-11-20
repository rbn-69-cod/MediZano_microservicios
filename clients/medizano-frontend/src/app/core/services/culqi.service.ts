import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface CulqiConfig { publicKey: string; currency: string; }
export interface CulqiOrder { pagoId: number; ordenId: number; numeroOrden: string; culqiOrderId: string; status: string; amount: number; expiresAt: string; }
export interface CulqiCharge { pagoId: number; ordenId: number; transactionId: string; status: string; amount: number; orderConfirmed: boolean; }

@Injectable({ providedIn: 'root' })
export class CulqiService {
  private readonly api = `${environment.apiUrl}/api/v1/pagos`;
  constructor(private http: HttpClient) {}
  config(): Observable<CulqiConfig> { return this.http.get<CulqiConfig>(`${this.api}/config`); }
  createQrOrder(ordenId: number): Observable<CulqiOrder> { return this.http.post<CulqiOrder>(`${this.api}/culqi/qr/order`, { ordenId }); }
  chargeYape(ordenId: number, tokenId: string): Observable<CulqiCharge> { return this.http.post<CulqiCharge>(`${this.api}/culqi/yape/charge`, { ordenId, tokenId }); }
}
