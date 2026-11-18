import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MsalService } from '@azure/msal-angular';
import { CarritoService } from '../../services/carrito.service';
import { PagoService, SolicitudPago } from '../../services/pago.service';
import { ToastService } from '../../services/toast.service';
import { Carrito, ItemCarrito } from '../../models/carrito.model';
import {
  ETIQUETAS_METODO_PAGO,
  EstadoPago,
  MetodoPago,
  Pedido
} from '../../models/pedido.model';
import { IMAGEN_POR_DEFECTO, mensajeDeError } from '../../shared/utilidades';

type Resultado = 'aprobado' | 'rechazado' | 'fraude' | 'fallo';

interface TarjetaDemo {
  numero: string;
  titular: string;
  vencimiento: string;
  cvv: string;
}

/**
 * Checkout directo sin proxy en el backend:
 *   1) POST /carrito/checkout     -> ms-carrito (crea el pedido PENDIENTE_PAGO)
 *   2) POST /pagos/procesar       -> ms-pagos DIRECTO (evalúa antifraude y procesa el pago)
 */
@Component({
  selector: 'app-checkout',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './checkout.component.html'
})
export class CheckoutComponent implements OnInit {

  carrito: Carrito | null = null;
  cargando = true;
  error: string | null = null;

  metodoPago: MetodoPago = 'TARJETA_CREDITO';
  tarjeta: TarjetaDemo = { numero: '4111 1111 1111 1111', titular: 'Matias Gonzalez', vencimiento: '12/28', cvv: '123' };
  simularRechazo = false;

  procesando = false;
  pedido: Pedido | null = null;
  resultado: Resultado | null = null;
  errorPago: string | null = null;

  readonly metodos: MetodoPago[] = ['TARJETA_CREDITO', 'TARJETA_DEBITO', 'TRANSFERENCIA', 'WEBPAY'];
  readonly etiquetas = ETIQUETAS_METODO_PAGO;
  readonly imagenDefecto = IMAGEN_POR_DEFECTO;

  // Webpay Plus simulation (+5 puntos)
  webpayToken: string | null = null;

  constructor(
    private carritoService: CarritoService,
    private pagoService: PagoService,
    private toastService: ToastService,
    private router: Router,
    private msalService: MsalService
  ) {}

  ngOnInit(): void {
    this.carritoService.obtenerCarrito().subscribe({
      next: (carrito) => {
        this.carrito = carrito;
        this.cargando = false;
        if (carrito.items.length === 0 && !this.pedido) {
          this.error = 'Tu carrito está vacío. Agrega productos antes de continuar.';
        }
      },
      error: (err) => {
        this.error = mensajeDeError(err, 'No se pudo cargar el carrito.');
        this.cargando = false;
      }
    });
  }

  get items(): ItemCarrito[] {
    return this.carrito?.items ?? [];
  }

  get total(): number {
    return this.items.reduce((acc, i) => acc + i.precioUnitario * i.cantidad, 0);
  }

  get esTarjeta(): boolean {
    return this.metodoPago === 'TARJETA_CREDITO' || this.metodoPago === 'TARJETA_DEBITO';
  }

  get formularioTarjetaValido(): boolean {
    const digitos = this.tarjeta.numero.replace(/\s/g, '');
    const vence = this.tarjeta.vencimiento.trim();
    return digitos.length === 16
      && this.tarjeta.titular.trim().length >= 3
      && /^\d{2}\/\d{2}$/.test(vence)
      && this.tarjeta.cvv.trim().length === 3;
  }

  get puedePagar(): boolean {
    if (this.procesando || !this.metodoPago) {
      return false;
    }
    if (this.esTarjeta && !this.formularioTarjetaValido) {
      return false;
    }
    return this.total > 0 || !!this.pedido;
  }

  formatearNumero(): void {
    const digitos = this.tarjeta.numero.replace(/\D/g, '').slice(0, 16);
    this.tarjeta.numero = digitos.replace(/(.{4})/g, '$1 ').trim();
  }

  formatearVencimiento(): void {
    const digitos = this.tarjeta.vencimiento.replace(/\D/g, '').slice(0, 4);
    this.tarjeta.vencimiento = digitos.length > 2
      ? `${digitos.slice(0, 2)}/${digitos.slice(2)}`
      : digitos;
  }

  confirmar(): void {
    if (this.procesando) return;
    if (!this.metodoPago) {
      this.errorPago = 'Selecciona un método de pago para continuar.';
      return;
    }
    if (this.esTarjeta && !this.formularioTarjetaValido) {
      this.errorPago = 'Revisa los datos de la tarjeta.';
      return;
    }

    this.errorPago = null;

    if (this.pedido) {
      this.procesarPagoDirecto(this.pedido.id, this.pedido.total);
      return;
    }

    this.procesando = true;
    this.carritoService.checkout(this.metodoPago).subscribe({
      next: (pedido) => {
        this.pedido = pedido;
        this.carritoService.obtenerCarrito().subscribe({ next: (c) => (this.carrito = c), error: () => {} });
        this.procesarPagoDirecto(pedido.id, pedido.total);
      },
      error: (err) => {
        this.procesando = false;
        this.resultado = 'fallo';
        this.errorPago = mensajeDeError(err, 'No se pudo registrar el pedido en ms-carrito.');
        this.toastService.error(this.errorPago);
      }
    });
  }

  /**
   * Correo del cliente autenticado (MSAL). Si no hay sesión se deja indefinido
   * y ms-carrito usa el identificador del pedido como respaldo.
   */
  private correoCliente(): string | undefined {
    const cuenta = this.msalService.instance.getActiveAccount();
    return cuenta?.username && cuenta.username.includes('@') ? cuenta.username : undefined;
  }

  /**
   * Invoca DIRECTAMENTE a ms-pagos (puerto 8085), SIN PROXY EN EL BACKEND.
   */
  private procesarPagoDirecto(pedidoId: number, monto: number): void {
    this.procesando = true;

    const solicitud: SolicitudPago = {
      pedidoId,
      usuarioId: this.pedido?.usuarioId || 'usuario-demo',
      monto,
      metodoPago: this.metodoPago,
      simularRechazo: this.simularRechazo,
      numeroTarjeta: this.tarjeta.numero,
      titular: this.tarjeta.titular,
      vencimiento: this.tarjeta.vencimiento,
      cvv: this.tarjeta.cvv,
      tokenWebpay: this.webpayToken || undefined,
      emailCliente: this.correoCliente()
    };

    this.pagoService.procesarPago(solicitud).subscribe({
      next: (res) => {
        this.procesando = false;
        if (res.estado === 'APROBADO') {
          this.resultado = 'aprobado';
          this.toastService.exito(`¡Pago aprobado directamente en ms-pagos! Pedido #${pedidoId} confirmado.`);
        } else if (res.estado === 'BLOQUEADO_POR_FRAUDE') {
          this.resultado = 'fraude';
          this.errorPago = res.motivo || 'Transacción bloqueada preventivamente por el motor Antifraude.';
          this.toastService.error(this.errorPago);
        } else {
          this.resultado = 'rechazado';
          this.toastService.error(`Pago rechazado por el banco para pedido #${pedidoId}.`);
          this.carritoService.obtenerCarrito().subscribe({ next: (c) => (this.carrito = c), error: () => {} });
        }
      },
      error: (err) => {
        this.procesando = false;
        if (err.status === 422 || err.error?.error === 'FRAUDE_DETECTADO') {
          this.resultado = 'fraude';
          const mensaje = err.error?.mensaje || 'Transacción bloqueada por seguridad: más de 3 intentos en 10 segundos.';
          this.errorPago = mensaje;
          this.toastService.error(mensaje);
        } else {
          this.resultado = 'fallo';
          this.errorPago = mensajeDeError(err, 'Fallo de comunicación directa con ms-pagos.');
          this.toastService.error(this.errorPago);
        }
      }
    });
  }

  /**
   * Prueba en vivo de la regla Antifraude (>3 intentos con la misma tarjeta en 10s)
   */
  probarAtaqueAntifraude(): void {
    this.toastService.info('Disparando 4 solicitudes simultáneas para probar regla antifraude...');
    const tarjetaPrueba = '4111 2222 3333 4444';
    this.tarjeta.numero = tarjetaPrueba;

    // Disparar 4 intentos en rápido intervalo
    for (let i = 1; i <= 4; i++) {
      setTimeout(() => {
        this.pagoService.procesarPago({
          pedidoId: 9000 + i,
          usuarioId: 'tester-fraude',
          monto: 50000,
          metodoPago: 'TARJETA_CREDITO',
          numeroTarjeta: tarjetaPrueba,
          titular: 'Ataque Simulado',
          vencimiento: '12/28',
          cvv: '999'
        }).subscribe({
          next: () => {
            this.toastService.info(`Intento #${i} recibido.`);
          },
          error: (err) => {
            if (err.status === 422 || err.error?.error === 'FRAUDE_DETECTADO') {
              this.resultado = 'fraude';
              this.errorPago = `¡REGLA ANTIFRAUDE ACTIVADA EN INTENTO #${i}!: ${err.error?.mensaje}`;
              this.toastService.error(this.errorPago);
            }
          }
        });
      }, i * 300);
    }
  }

  reiniciar(): void {
    this.resultado = null;
    this.errorPago = null;
    this.pedido = null;
    this.metodoPago = 'TARJETA_CREDITO';
    this.simularRechazo = false;
    this.webpayToken = null;
    this.ngOnInit();
  }
}
