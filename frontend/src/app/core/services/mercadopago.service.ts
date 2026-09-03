import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, BehaviorSubject, of } from 'rxjs';
import { tap, catchError } from 'rxjs/operators';
import { environment } from '../../../environments/environment';
import {
  PaymentConfigResponse,
  MercadoPagoPreferenceRequest,
  MercadoPagoPreferenceResponse,
  MercadoPagoVerifyRequest,
  MercadoPagoPaymentResponse
} from '../models/billing.model';

@Injectable({
  providedIn: 'root'
})
export class MercadoPagoService {
  private apiUrl = `${environment.apiUrl || 'http://localhost:8090/api'}/v1/pagos`;
  private publicKeySubject = new BehaviorSubject<string | null>(null);
  public publicKey$ = this.publicKeySubject.asObservable();

  constructor(private http: HttpClient) {}

  /**
   * Obtiene la configuración de pasarelas desde el backend
   */
  getConfig(): Observable<PaymentConfigResponse> {
    return this.http.get<PaymentConfigResponse>(`${this.apiUrl}/config`).pipe(
      tap((config) => {
        if (config?.mpPublicKey) {
          this.publicKeySubject.next(config.mpPublicKey);
        }
      }),
      catchError((error) => {
        console.warn('No se pudo obtener la configuración pública de Mercado Pago:', error);
        return of({ mpPublicKey: '', mpCurrency: 'PEN' });
      })
    );
  }

  /**
   * Crea una preferencia de pago en Mercado Pago Checkout Pro
   */
  crearPreferencia(request: MercadoPagoPreferenceRequest): Observable<MercadoPagoPreferenceResponse> {
    return this.http.post<MercadoPagoPreferenceResponse>(`${this.apiUrl}/mercadopago/preference`, request);
  }

  /**
   * Verifica y confirma el pago en Mercado Pago mediante el backend
   */
  verificarPago(request: MercadoPagoVerifyRequest): Observable<MercadoPagoPaymentResponse> {
    return this.http.post<MercadoPagoPaymentResponse>(`${this.apiUrl}/mercadopago/verify`, request);
  }

  /**
   * Abre la ventana o redirección hacia Checkout Pro oficial de Mercado Pago
   */
  abrirCheckoutPro(preference: MercadoPagoPreferenceResponse): Promise<void> {
    return new Promise((resolve) => {
      const url = preference.sandboxInitPoint || preference.initPoint;
      if (!url) {
        throw new Error('No se recibió la URL de checkout de Mercado Pago');
      }

      const width = 800;
      const height = 750;
      const left = window.screenX + (window.outerWidth - width) / 2;
      const top = window.screenY + (window.outerHeight - height) / 2;

      window.open(url, 'MercadoPagoCheckoutPro', `width=${width},height=${height},left=${left},top=${top},scrollbars=yes`);
      resolve();
    });
  }
}

