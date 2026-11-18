import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface SolicitudPago {
  pedidoId: number;
  usuarioId: string;
  monto: number;
  metodoPago: string;
  simularRechazo?: boolean;
  numeroTarjeta?: string;
  titular?: string;
  vencimiento?: string;
  cvv?: string;
  tokenWebpay?: string;
  /** Correo del cliente: ms-carrito lo usa para enviar el comprobante vía RabbitMQ. */
  emailCliente?: string;
}

export interface PagoResponse {
  id: number;
  pedidoId: number;
  usuarioId: string;
  monto: number;
  metodoPago: string;
  estado: 'APROBADO' | 'RECHAZADO' | 'BLOQUEADO_POR_FRAUDE' | 'PENDIENTE_WEBPAY';
  motivo?: string;
  tarjetaEnmascarada?: string;
  transaccionId?: string;
  fecha: string;
}

export interface AlertaFraude {
  id: number;
  tarjetaEnmascarada: string;
  usuarioId: string;
  pedidoId: number;
  monto: number;
  intentosEn10Segundos: number;
  fecha: string;
  descripcion: string;
}

export interface WebpayInitResult {
  token: string;
  url: string;
}

/**
 * Servicio de conexión DIRECTA con ms-pagos (puerto 8085).
 * Elimina completamente la necesidad de un proxy en el backend.
 */
@Injectable({
  providedIn: 'root'
})
export class PagoService {

  private readonly baseUrl = environment.apiPagosUrl;

  constructor(private http: HttpClient) {}

  /**
   * Envía la transacción directamente al microservicio de pagos y antifraude.
   */
  procesarPago(solicitud: SolicitudPago): Observable<PagoResponse> {
    return this.http.post<PagoResponse>(`${this.baseUrl}/pagos/procesar`, solicitud);
  }

  /**
   * Consulta el pago asociado a un pedido.
   */
  obtenerPagoPorPedido(pedidoId: number): Observable<PagoResponse> {
    return this.http.get<PagoResponse>(`${this.baseUrl}/pagos/pedido/${pedidoId}`);
  }

  /**
   * Obtiene la lista de alertas de fraude detectadas en tiempo real.
   */
  obtenerAlertasFraude(): Observable<AlertaFraude[]> {
    return this.http.get<AlertaFraude[]>(`${this.baseUrl}/pagos/alertas-fraude`);
  }

  /**
   * Inicia simulación de Webpay Plus (+5 puntos extra).
   */
  iniciarWebpay(pedidoId: number, monto: number): Observable<WebpayInitResult> {
    return this.http.post<WebpayInitResult>(`${this.baseUrl}/pagos/webpay/iniciar`, {
      pedidoId,
      monto,
      returnUrl: window.location.origin + '/checkout'
    });
  }

  /**
   * Confirma transacción Webpay Plus (+5 puntos extra).
   */
  confirmarWebpay(token: string): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/pagos/webpay/confirmar`, { token });
  }
}
