export type EstadoPedido =
  | 'PENDIENTE_PAGO'
  | 'CONFIRMADO'
  | 'EN_PREPARACION'
  | 'ENVIADO'
  | 'ENTREGADO'
  | 'CANCELADO';

export type EstadoPago = 'PENDIENTE' | 'APROBADO' | 'RECHAZADO' | 'BLOQUEADO_POR_FRAUDE' | 'PENDIENTE_WEBPAY';

export type MetodoPago = 'TARJETA_CREDITO' | 'TARJETA_DEBITO' | 'TRANSFERENCIA' | 'WEBPAY';

export interface ItemPedido {
  id: number;
  productoId: number;
  nombreProducto: string;
  precioUnitario: number;
  cantidad: number;
  imagenUrl?: string | null;
}

export interface Pedido {
  id: number;
  usuarioId: string;
  total: number;
  estado: EstadoPedido;
  estadoPago: EstadoPago;
  metodoPago: MetodoPago | null;
  fecha: string;
  items: ItemPedido[];
}

export const ETIQUETAS_ESTADO: Record<EstadoPedido, string> = {
  PENDIENTE_PAGO: 'Pendiente de pago',
  CONFIRMADO: 'Pagado',
  EN_PREPARACION: 'En preparación',
  ENVIADO: 'Enviado',
  ENTREGADO: 'Entregado',
  CANCELADO: 'Cancelado'
};

export const ETIQUETAS_PAGO: Record<EstadoPago, string> = {
  PENDIENTE: 'Pendiente',
  APROBADO: 'Aprobado',
  RECHAZADO: 'Rechazado',
  BLOQUEADO_POR_FRAUDE: 'Bloqueado por Antifraude',
  PENDIENTE_WEBPAY: 'Pendiente Webpay'
};

export const ETIQUETAS_METODO_PAGO: Record<MetodoPago, string> = {
  TARJETA_CREDITO: 'Tarjeta de crédito',
  TARJETA_DEBITO: 'Tarjeta de débito',
  TRANSFERENCIA: 'Transferencia bancaria',
  WEBPAY: 'Webpay Plus'
};
