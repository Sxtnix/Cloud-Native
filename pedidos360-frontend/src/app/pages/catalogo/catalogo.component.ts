import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { ProductoService } from '../../services/producto.service';
import { CarritoService } from '../../services/carrito.service';
import { MonitoreoService, Recomendacion } from '../../services/monitoreo.service';
import { ToastService } from '../../services/toast.service';
import { Producto } from '../../models/producto.model';
import { imagenDe, mensajeDeError } from '../../shared/utilidades';

@Component({
  selector: 'app-catalogo',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './catalogo.component.html'
})
export class CatalogoComponent implements OnInit {

  productos: Producto[] = [];
  categorias: string[] = [];
  categoriaSeleccionada: string | null = null;
  terminoBusqueda = '';
  cargando = false;
  error: string | null = null;
  agregando: number | null = null;
  detalle: Producto | null = null;

  recomendaciones: Recomendacion[] = [];

  constructor(
    private productoService: ProductoService,
    private carritoService: CarritoService,
    private monitoreoService: MonitoreoService,
    private toastService: ToastService,
    private route: ActivatedRoute,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.route.queryParamMap.subscribe((params) => {
      this.terminoBusqueda = params.get('q') ?? '';
      this.categoriaSeleccionada = params.get('categoria');
      this.cargarProductos();
      this.cargarRecomendaciones();
    });

    this.productoService.categorias().subscribe({
      next: (cats) => (this.categorias = cats),
      error: () => (this.categorias = [])
    });
  }

  cargarProductos(): void {
    this.cargando = true;
    this.error = null;

    this.productoService.buscar(this.terminoBusqueda, this.categoriaSeleccionada).subscribe({
      next: (data) => {
        this.productos = data;
        this.cargando = false;
      },
      error: (err) => {
        this.error = mensajeDeError(err,
          'No se pudo cargar el catálogo. Verifica que ms-productos esté disponible.');
        this.cargando = false;
      }
    });
  }

  cargarRecomendaciones(): void {
    this.monitoreoService.obtenerRecomendaciones(undefined, this.categoriaSeleccionada || undefined).subscribe({
      next: (recs) => (this.recomendaciones = recs),
      error: () => (this.recomendaciones = [])
    });
  }

  filtrarCategoria(categoria: string | null): void {
    this.categoriaSeleccionada = categoria;
    this.sincronizarUrl();
  }

  buscar(): void {
    this.sincronizarUrl();
  }

  limpiarFiltros(): void {
    this.terminoBusqueda = '';
    this.categoriaSeleccionada = null;
    this.sincronizarUrl();
  }

  agregarAlCarrito(producto: Producto | Recomendacion): void {
    const id = 'productoId' in producto ? producto.productoId : producto.id;
    if (!id || this.agregando === id) return;

    this.agregando = id;
    this.carritoService.agregarItem({
      productoId: id,
      nombreProducto: producto.nombre,
      precioUnitario: producto.precio,
      cantidad: 1
    }).subscribe({
      next: () => {
        this.toastService.exito(`"${producto.nombre}" se agregó al carrito.`);
        this.agregando = null;
      },
      error: (err) => {
        this.toastService.error(mensajeDeError(err, 'No se pudo agregar el producto al carrito.'));
        this.agregando = null;
      }
    });
  }

  /**
   * Abre el detalle del producto. GET /productos/{id} es también el punto que
   * publica el evento VER_PRODUCTO en Kafka (clickstream -> DynamoDB).
   */
  verDetalle(producto: Producto): void {
    const id = producto.id;
    if (!id) return;

    this.productoService.obtenerPorId(id).subscribe({
      next: (completo) => (this.detalle = completo),
      error: (err) => this.toastService.error(
        mensajeDeError(err, 'No se pudo cargar el detalle del producto.'))
    });
  }

  cerrarDetalle(): void {
    this.detalle = null;
  }

  private sincronizarUrl(): void {
    const queryParams: { [k: string]: string } = {};
    if (this.terminoBusqueda.trim()) {
      queryParams['q'] = this.terminoBusqueda.trim();
    }
    if (this.categoriaSeleccionada) {
      queryParams['categoria'] = this.categoriaSeleccionada;
    }
    this.router.navigate(['/catalogo'], { queryParams });
  }

  imagenDe = imagenDe;
}
