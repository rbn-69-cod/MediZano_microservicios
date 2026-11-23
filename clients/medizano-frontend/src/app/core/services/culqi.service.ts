import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { catchError, tap } from 'rxjs/operators';
import { environment } from '../../../environments/environment';
import { CulqiChargeRequest, CulqiChargeResponse, CulqiOrderRequest, CulqiOrderResponse, PaymentConfigResponse } from '../models/billing.model';

declare global {
  interface Window {
    Culqi?: any;
    culqi?: () => void;
  }
}

@Injectable({
  providedIn: 'root'
})
export class CulqiService {
  private apiUrl = `${environment.apiUrl || '/api'}/v1/pagos`;
  /** Se carga desde pago-ms; no se versionan llaves en el cliente. */
  public publicKey = '';

  constructor(private http: HttpClient) {}

  public setPublicKey(key: string): void {
    if (key) {
      this.publicKey = key;
    }
  }

  /** Obtiene la llave pública activa desde el backend, sin exponer la llave secreta. */
  public getConfig(): Observable<PaymentConfigResponse> {
    return this.http.get<PaymentConfigResponse>(`${this.apiUrl}/config`).pipe(
      tap((config) => this.setPublicKey(config?.culqiPublicKey || '')),
      catchError((error) => {
        console.warn('No se pudo obtener la configuración pública de Culqi:', error);
        return of({ culqiPublicKey: '', culqiCurrency: 'PEN' });
      })
    );
  }

  public procesarCargo(req: CulqiChargeRequest): Observable<CulqiChargeResponse> {
    return this.http.post<CulqiChargeResponse>(`${this.apiUrl}/culqi/charge`, req);
  }

  public crearOrdenQr(req: CulqiOrderRequest): Observable<CulqiOrderResponse> {
    return this.http.post<CulqiOrderResponse>(`${this.apiUrl}/culqi/order`, req);
  }

  public loadCulqiSdk(): Promise<boolean> {
    return new Promise((resolve) => {
      if (typeof window === 'undefined') {
        resolve(false);
        return;
      }
      if (window.Culqi) {
        resolve(true);
        return;
      }
      const existing = document.getElementById('culqi-sdk-v4');
      if (existing) {
        let elapsed = 0;
        const interval = setInterval(() => {
          elapsed += 100;
          if (window.Culqi) {
            clearInterval(interval);
            resolve(true);
          } else if (elapsed >= 3000) {
            clearInterval(interval);
            resolve(false);
          }
        }, 100);
        return;
      }

      const script = document.createElement('script');
      script.id = 'culqi-sdk-v4';
      script.src = 'https://checkout.culqi.com/js/v4';
      script.async = true;
      script.onload = () => {
        let elapsed = 0;
        const interval = setInterval(() => {
          elapsed += 100;
          if (window.Culqi) {
            clearInterval(interval);
            resolve(true);
          } else if (elapsed >= 3000) {
            clearInterval(interval);
            resolve(false);
          }
        }, 100);
      };
      script.onerror = () => resolve(false);
      document.head.appendChild(script);
    });
  }

  public async openCheckout(options: {
    title: string;
    currency: string;
    amount: number;
    description?: string;
    culqiOrderId?: string;
    paymentMethods: {
      tarjeta?: boolean;
      yape?: boolean;
      billetera?: boolean;
    };
  }): Promise<{ type: 'token'; token: string; email: string } | { type: 'order' }> {
    const loaded = await this.loadCulqiSdk();
    if (!loaded || !window.Culqi) {
      throw new Error('El SDK Culqi Checkout v4 no pudo cargarse en el navegador. Revisa que no esté bloqueado por un adblocker o extensión.');
    }

    return new Promise((resolve, reject) => {
      const culqiAmount = Math.round(options.amount * 100);

      window.Culqi.publicKey = this.publicKey;
      window.Culqi.settings({
        title: options.title || 'MediZano Farmacia',
        currency: options.currency || 'PEN',
        amount: culqiAmount,
        ...(options.culqiOrderId ? { order: options.culqiOrderId } : {})
      });
      window.Culqi.options({
        lang: 'es',
        installments: false,
        paymentMethods: {
          tarjeta: Boolean(options.paymentMethods.tarjeta),
          yape: Boolean(options.paymentMethods.yape),
          billetera: Boolean(options.paymentMethods.billetera),
          bancaMovil: false,
          agente: false,
          cuotealo: false
        }
      });

      window.culqi = () => {
        if (window.Culqi.token) {
          const token = window.Culqi.token.id;
          const email = window.Culqi.token.email;
          try {
            window.Culqi.close();
          } catch (e) {
            // ignore
          }
          resolve({ type: 'token', token, email });
        } else if (window.Culqi.order) {
          try {
            window.Culqi.close();
          } catch (e) {
            // ignore
          }
          resolve({ type: 'order' });
        } else if (window.Culqi.error) {
          const err = window.Culqi.error;
          const msg = err.user_message || err.merchant_message || 'Error en pasarela Culqi';
          reject(new Error(msg));
        } else {
          reject(new Error('Operación no completada en Culqi'));
        }
      };

      window.Culqi.open();
    });
  }
}
