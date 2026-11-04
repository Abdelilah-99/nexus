import { OrderStatus } from '../../core/models/order';

const LABELS: Record<OrderStatus, string> = {
  PENDING: 'Pending',
  CONFIRMED: 'Confirmed',
  SHIPPED: 'Shipped',
  DELIVERED: 'Delivered',
  CANCELLED: 'Cancelled',
  IN_PROGRESS: 'In progress',
  COMPLETED: 'Completed with cancellations',
};

const BADGES: Record<OrderStatus, string> = {
  PENDING: 'buy-badge-neutral',
  CONFIRMED: 'buy-badge-primary',
  SHIPPED: 'buy-badge-warning',
  DELIVERED: 'buy-badge-success',
  CANCELLED: 'buy-badge-danger',
  IN_PROGRESS: 'buy-badge-warning',
  COMPLETED: 'buy-badge-success',
};

export function statusLabel(status: OrderStatus): string {
  return LABELS[status];
}

export function statusBadge(status: OrderStatus): string {
  return `buy-badge ${BADGES[status]}`;
}

/** Short reference shown to people instead of the full Mongo id. */
export function shortId(id: string): string {
  return id.slice(-8).toUpperCase();
}
