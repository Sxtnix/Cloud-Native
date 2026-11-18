import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subscription } from 'rxjs';
import { MonitoreoService, MetricasEnVivo } from '../../services/monitoreo.service';
import { PagoService, AlertaFraude } from '../../services/pago.service';
import { ToastService } from '../../services/toast.service';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './dashboard.component.html',
  styleUrls: []
})
export class DashboardComponent implements OnInit, OnDestroy {

  metricas: MetricasEnVivo | null = null;
  alertasFraude: AlertaFraude[] = [];
  subWebSocket: Subscription | null = null;

  // Tabs: 'metricas' | 'rabbitmq' | 'kafka'
  tabActiva: 'metricas' | 'rabbitmq' | 'kafka' = 'metricas';

  // RabbitMQ Admin
  nuevaColaNombre = '';
  nuevaColaDurable = true;
  estadoRabbit: any = null;

  // Kafka Admin
  nuevoTopicoNombre = '';
  nuevoTopicoParticiones = 1;
  topicosKafka: any[] = [];

  cargando = true;

  constructor(
    private monitoreoService: MonitoreoService,
    private pagoService: PagoService,
    private toastService: ToastService
  ) {}

  ngOnInit(): void {
    // 1. Conexión WebSocket a ms-monitoreo
    this.subWebSocket = this.monitoreoService.conectarWebSocket().subscribe({
      next: (data) => {
        this.metricas = data;
        this.cargando = false;
      },
      error: (err) => console.error('Error WebSocket:', err)
    });

    // 2. Cargar alertas de fraude desde ms-pagos
    this.cargarAlertasFraude();

    // 3. Cargar información de RabbitMQ y Kafka
    this.cargarInfoRabbit();
    this.cargarInfoKafka();
  }

  ngOnDestroy(): void {
    if (this.subWebSocket) {
      this.subWebSocket.unsubscribe();
    }
  }

  cargarAlertasFraude(): void {
    this.pagoService.obtenerAlertasFraude().subscribe({
      next: (alertas) => (this.alertasFraude = alertas),
      error: () => {}
    });
  }

  cargarInfoRabbit(): void {
    this.monitoreoService.obtenerEstadoRabbit().subscribe({
      next: (info) => (this.estadoRabbit = info),
      error: () => {}
    });
  }

  cargarInfoKafka(): void {
    this.monitoreoService.listarTopicosKafka().subscribe({
      next: (topicos) => (this.topicosKafka = topicos),
      error: () => {}
    });
  }

  crearColaRabbit(): void {
    if (!this.nuevaColaNombre.trim()) {
      this.toastService.error('Debe ingresar un nombre de cola');
      return;
    }
    this.monitoreoService.crearColaRabbit({
      nombre: this.nuevaColaNombre.trim(),
      durable: this.nuevaColaDurable
    }).subscribe({
      next: () => {
        this.toastService.exito(`Cola RabbitMQ '${this.nuevaColaNombre}' creada`);
        this.nuevaColaNombre = '';
        this.cargarInfoRabbit();
      },
      error: (err) => this.toastService.error('Error creando cola RabbitMQ')
    });
  }

  crearTopicoKafka(): void {
    if (!this.nuevoTopicoNombre.trim()) {
      this.toastService.error('Debe ingresar un nombre de tópico');
      return;
    }
    this.monitoreoService.crearTopicoKafka({
      nombre: this.nuevoTopicoNombre.trim(),
      particiones: this.nuevoTopicoParticiones || 1,
      factorReplicacion: 1
    }).subscribe({
      next: () => {
        this.toastService.exito(`Tópico Kafka '${this.nuevoTopicoNombre}' creado`);
        this.nuevoTopicoNombre = '';
        this.cargarInfoKafka();
      },
      error: (err) => this.toastService.error('Error creando tópico Kafka')
    });
  }
}
