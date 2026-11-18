import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, Subject } from 'rxjs';
import { environment } from '../../environments/environment';

export interface MetricasEnVivo {
  timestamp: number;
  volumenNavegacion: {
    totalClics: number;
    clicsPorMinuto: number;
    usuariosActivos: number;
    tendencia: string;
  };
  productosMasVistos: Array<{
    id: number;
    nombre: string;
    vistas: number;
    categoria: string;
  }>;
  alertasEnVivo: Array<{
    tipo: string;
    mensaje: string;
    nivel: string;
  }>;
  resumenEventos?: any;
  categoriasPopulares?: Record<string, number>;
}

export interface Recomendacion {
  productoId: number;
  nombre: string;
  categoria: string;
  precio: number;
  imagenUrl: string;
  motivo: string;
  afinidad: number;
}

@Injectable({
  providedIn: 'root'
})
export class MonitoreoService {

  private readonly baseUrl = environment.apiMonitoreoUrl;
  private readonly wsUrl = environment.apiWsUrl;

  private socket: WebSocket | null = null;
  private metricasSubject = new Subject<MetricasEnVivo>();

  constructor(private http: HttpClient) {}

  /**
   * Conecta al stream WebSocket en tiempo real de métricas analíticas.
   */
  conectarWebSocket(): Observable<MetricasEnVivo> {
    if (this.socket && this.socket.readyState === WebSocket.OPEN) {
      return this.metricasSubject.asObservable();
    }

    try {
      this.socket = new WebSocket(this.wsUrl);

      this.socket.onmessage = (event) => {
        try {
          const data: MetricasEnVivo = JSON.parse(event.data);
          this.metricasSubject.next(data);
        } catch (e) {
          console.error('Error parseando métricas WebSocket:', e);
        }
      };

      this.socket.onerror = (err) => {
        console.warn('WebSocket de monitoreo en error. Usando polling de respaldo:', err);
        this.iniciarPollingRespaldo();
      };

      this.socket.onclose = () => {
        console.info('Conexión WebSocket cerrada');
      };
    } catch (e) {
      console.warn('No se pudo inicializar WebSocket, recurriendo a polling:', e);
      this.iniciarPollingRespaldo();
    }

    return this.metricasSubject.asObservable();
  }

  private iniciarPollingRespaldo(): void {
    setInterval(() => {
      this.obtenerMetricasRest().subscribe({
        next: (data) => this.metricasSubject.next(data),
        error: () => {}
      });
    }, 3000);
  }

  obtenerMetricasRest(): Observable<MetricasEnVivo> {
    return this.http.get<MetricasEnVivo>(`${this.baseUrl}/analytics/metricas-en-vivo`);
  }

  obtenerRecomendaciones(usuarioId?: string, categoria?: string): Observable<Recomendacion[]> {
    const params: any = {};
    if (usuarioId) params.usuarioId = usuarioId;
    if (categoria) params.categoria = categoria;
    return this.http.get<Recomendacion[]>(`${this.baseUrl}/analytics/recomendaciones`, { params });
  }

  // --- RabbitMQ Admin API (EP3) ---
  obtenerEstadoRabbit(): Observable<any> {
    return this.http.get<any>(`${this.baseUrl}/admin/rabbitmq/estado`);
  }

  crearColaRabbit(cola: { nombre: string; durable: boolean }): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/admin/rabbitmq/queues`, cola);
  }

  eliminarColaRabbit(nombre: string): Observable<any> {
    return this.http.delete<any>(`${this.baseUrl}/admin/rabbitmq/queues/${nombre}`);
  }

  // --- Kafka Admin API (EP5) ---
  listarTopicosKafka(): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/admin/kafka/topics`);
  }

  crearTopicoKafka(topico: { nombre: string; particiones: number; factorReplicacion: number }): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/admin/kafka/topics`, topico);
  }

  eliminarTopicoKafka(nombre: string): Observable<any> {
    return this.http.delete<any>(`${this.baseUrl}/admin/kafka/topics/${nombre}`);
  }
}
