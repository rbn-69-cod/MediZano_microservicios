export interface BillItemRequest {
  medicineId?: number;
  barcode?: string;
  quantity: number;
  unitPrice?: number;
  batchId?: number;
  batchNumber?: string;
}

export interface PaymentRequest {
  mode: PaymentMode;
  amount: number;
  paymentReference?: string;
}

export enum PaymentMode {
  CASH = 'CASH',
  /** Tarjetas: disponible únicamente para compradores del catálogo. */
  CULQI = 'CULQI',
  /** Token de Yape: celular y código de aprobación, sin tarjeta. */
  CULQI_YAPE = 'CULQI_YAPE',
  /** Orden Culqi asíncrona para el QR de billeteras móviles. */
  CULQI_QR = 'CULQI_QR',
  PAYPAL = 'PAYPAL',
  MERCADO_PAGO = 'MERCADO_PAGO'
}


export interface CreateBillRequest {
  items: BillItemRequest[];
  customerName?: string;
  customerPhone?: string;
  customerEmail?: string;
  payments?: PaymentRequest[];
}

export interface BillItemResponse {
  id: number;
  medicineId: number;
  medicineName: string;
  batchId?: number;
  batchNumber: string;
  quantity: number;
  unitPrice: number;
  gstPercentage: number;
  gstAmount: number;
  totalAmount: number;
}

export interface PaymentResponse {
  id: number;
  paymentReference: string;
  mode: PaymentMode;
  amount: number;
  status: PaymentStatus;
  paymentDate: string;
}

export enum PaymentStatus {
  PENDING = 'PENDING',
  COMPLETED = 'COMPLETED',
  APPROVED = 'APPROVED',
  FAILED = 'FAILED',
  REFUNDED = 'REFUNDED'
}

export interface BillResponse {
  id: number;
  billNumber: string;
  billDate: string;
  cashierId: number;
  cashierName: string;
  customerName?: string;
  customerPhone?: string;
  customerEmail?: string;
  subtotal: number;
  totalGst: number;
  totalAmount: number;
  paymentStatus: BillPaymentStatus;
  cancelled: boolean;
  cancellationReason?: string;
  items: BillItemResponse[];
  payments: PaymentResponse[];
  createdAt: string;
  /** Presente cuando el comprobante proviene de una orden electrónica. */
  orderId?: number;
}

export interface ElectronicInvoiceResponse {
  id: number;
  numeroFactura: string;
  ordenId: number;
  numeroOrden: string;
  clienteNombre?: string;
  metodoPago?: string;
  referenciaPago?: string;
  subtotal: number;
  impuesto: number;
  total: number;
  estado: string;
  createdAt: string;
  items: Array<{
    productoId: number;
    productoNombre: string;
    cantidad: number;
    precioUnitario: number;
    subtotal: number;
  }>;
}

export enum BillPaymentStatus {
  PENDING = 'PENDING',
  PAID = 'PAID',
  PARTIALLY_PAID = 'PARTIALLY_PAID',
  REFUNDED = 'REFUNDED'
}

// Unified Payment Gateways Config
export interface PaymentConfigResponse {
  payPalClientId?: string;
  payPalCurrency?: string;
  payPalBaseUrl?: string;
  mpPublicKey?: string;
  mpCurrency?: string;
  culqiPublicKey?: string;
  culqiCurrency?: string;
}

// PayPal Sandbox interfaces
export interface PayPalConfigResponse {
  clientId: string;
  currency: string;
  baseUrl: string;
}

export interface PayPalOrderRequest {
  ordenId: number;
  returnUrl?: string;
  cancelUrl?: string;
}

export interface PayPalOrderResponse {
  ordenId: number;
  numeroOrden: string;
  paypalOrderId: string;
  status: string;
  approveUrl: string;
  amountPen: number;
  amountUsd: number;
  currency: string;
  clientId: string;
}

export interface PayPalCaptureResponse {
  pagoId: number;
  ordenId: number;
  numeroOrden: string;
  paypalOrderId: string;
  paypalCaptureId: string;
  status: string;
  amount: number;
  currency: string;
  timestamp: string;
  orderConfirmed?: boolean;
}

// Mercado Pago interfaces
export interface MercadoPagoPreferenceRequest {
  ordenId: number;
  customerEmail?: string;
  backUrl?: string;
}

export interface MercadoPagoPreferenceResponse {
  ordenId: number;
  numeroOrden: string;
  preferenceId: string;
  initPoint: string;
  sandboxInitPoint: string;
  amountPen: number;
  currency: string;
  publicKey?: string;
}

export interface MercadoPagoVerifyRequest {
  ordenId: number;
  paymentId: string;
  preferenceId?: string;
}

export interface MercadoPagoPaymentResponse {
  pagoId: number;
  ordenId: number;
  numeroOrden: string;
  paymentId: string;
  preferenceId?: string;
  status: string;
  statusDetail?: string;
  amount: number;
  currency: string;
  timestamp: string;
  orderConfirmed?: boolean;
}

export interface PaymentStatusRecord {
  id: number;
  ordenId: number;
  numeroOrden: string;
  amount: number;
  currency: string;
  status: string;
  provider: string;
  externalStatus?: string;
  orderConfirmed?: boolean;
  paymentDate?: string;
  createdAt?: string;
}

export interface EmailReceiptItemDTO {
  name: string;
  quantity: number;
  unitPrice: number;
  total: number;
}

export interface EmailReceiptRequest {
  to: string;
  seriesNumber: string;
  customerName?: string;
  customerDoc?: string;
  cashier?: string;
  paymentMode?: string;
  pickupCode?: string;
  date?: string;
  subtotal: number;
  igv: number;
  total: number;
  items: EmailReceiptItemDTO[];
}

export interface EmailReceiptResponse {
  success: boolean;
  recipient: string;
  seriesNumber: string;
  message: string;
  timestamp: string;
}

export interface CulqiChargeRequest {
  ordenId: number;
  token: string;
  email?: string;
}

export interface CulqiOrderRequest {
  ordenId: number;
}

export interface CulqiOrderResponse {
  pagoId: number;
  ordenId: number;
  numeroOrden: string;
  culqiOrderId: string;
  amount: number;
  currency: string;
  status: string;
  expiresAt: string;
}

export interface CulqiChargeResponse {
  pagoId: number;
  ordenId: number;
  numeroOrden: string;
  chargeId: string;
  amount: number;
  currency: string;
  status: string;
  message: string;
  orderConfirmed: boolean;
}
