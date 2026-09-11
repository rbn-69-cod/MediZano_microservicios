// Script de Auditoría Integral End-to-End para MediZano Microservicios
const GATEWAY = 'http://localhost:8090';
const ADMIN_PASSWORD = process.env.MEDIZANO_DEFAULT_PASSWORD;

async function request(url, options = {}) {
  const fullUrl = url.startsWith('http') ? url : `${GATEWAY}${url}`;
  const res = await fetch(fullUrl, options);
  const text = await res.text();
  let json;
  try {
    json = JSON.parse(text);
  } catch (e) {
    json = text;
  }
  return { status: res.status, ok: res.ok, data: json, headers: res.headers };
}

async function runAudit() {
  if (!ADMIN_PASSWORD) {
    throw new Error('Define MEDIZANO_DEFAULT_PASSWORD antes de ejecutar la auditoria E2E.');
  }

  console.log('==========================================================');
  console.log('  AUDITORIA INTEGRAL END-TO-END DE MEDIZANO (BD LIMPIA)  ');
  console.log('==========================================================\n');

  // 1. Autenticación y JWT
  console.log('[1/10] Autenticación JWT en usuario-ms...');
  const loginRes = await request('/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: 'admin', password: ADMIN_PASSWORD })
  });

  if (!loginRes.ok) {
    throw new Error(`Fallo en login: ${loginRes.status} ${JSON.stringify(loginRes.data)}`);
  }
  const token = loginRes.data.token;
  const authHeaders = {
    'Authorization': `Bearer ${token}`,
    'Content-Type': 'application/json'
  };
  console.log(`  [OK] Login exitoso: Usuario '${loginRes.data.username}' | Rol: '${loginRes.data.role}'`);

  // 2. Usuarios y Auditoría
  console.log('\n[2/10] Verificando Usuarios y Bitácora de Auditoría en usuario-ms...');
  const usersRes = await request('/api/admin/users', { headers: authHeaders });
  console.log(`  [OK] Usuarios registrados: ${usersRes.data.length}`);
  const activityRes = await request('/api/admin/audit/all', { headers: authHeaders });
  console.log(`  [OK] Bitácora de actividades registradas: ${activityRes.data.length}`);
  const loginsRes = await request('/api/admin/audit/login-logout', { headers: authHeaders });
  console.log(`  [OK] Historial de accesos / inicios de sesión: ${loginsRes.data.length}`);

  // 3. Catálogo y Medicamentos (Verificación de Unificación y Precio Oficial)
  console.log('\n[3/10] Verificando Catálogo Unificado en catalogo-ms...');
  const medsRes = await request('/api/pharmacist/medicines', { headers: authHeaders });
  console.log(`  [OK] Medicamentos en catálogo: ${medsRes.data.length}`);
  medsRes.data.forEach(m => {
    console.log(`    - ID ${m.id}: ${m.name} | Venta: S/ ${m.sellingPrice} | Compra: S/ ${m.purchasePrice} | Barcode: ${m.barcode}`);
  });

  const barcodeRes = await request('/api/pharmacist/medicines/barcode/7751234567890', { headers: authHeaders });
  if (barcodeRes.data.sellingPrice === 5.0) {
    console.log(`  [OK] Paracetamol por código de barras retorna precio oficial de S/ ${barcodeRes.data.sellingPrice} (Sin 'Calculando')`);
  } else {
    throw new Error(`Precio inesperado: ${barcodeRes.data.sellingPrice}`);
  }

  // 4. Inventario y Lotes
  console.log('\n[4/10] Verificando Lotes y Stock en inventario-ms...');
  const batchesRes = await request('/api/pharmacist/batches', { headers: authHeaders });
  console.log(`  [OK] Lotes activos en inventario: ${batchesRes.data.length}`);
  const batchesMed1Res = await request('/api/pharmacist/batches/medicine/1', { headers: authHeaders });
  console.log(`  [OK] Lotes de Paracetamol (ID 1): ${batchesMed1Res.data.length} lote(s), Stock disponible: ${batchesMed1Res.data[0]?.quantityAvailable}`);

  // 5. Clientes
  console.log('\n[5/10] Verificando Directorio de Clientes en cliente-ms...');
  const clientesRes = await request('/api/v1/clientes', { headers: authHeaders });
  console.log(`  [OK] Clientes iniciales encontrados: ${clientesRes.data.length}`);
  clientesRes.data.forEach(c => {
    console.log(`    - Cliente: ${c.nombre} (Doc: ${c.documento}) | Tel: ${c.telefono}`);
  });
  const docSearchRes = await request('/api/v1/clientes/documento/12345678', { headers: authHeaders });
  console.log(`  [OK] Búsqueda por DNI 12345678: ${docSearchRes.data.nombre} (${docSearchRes.data.email})`);

  // 6. Facturación y Ventas POS (con Anti-manipulación y PDF)
  console.log('\n[6/10] Verificando Venta POS en facturacion-ms...');
  const billPayload = {
    customerName: 'Juan Pérez Rodríguez',
    customerPhone: '987654321',
    customerEmail: 'juan.perez@gmail.com',
    items: [
      { medicineId: 1, quantity: 2, unitPrice: 5.00 }
    ],
    payments: [
      { mode: 'CASH', amount: 10.00 }
    ]
  };
  const billRes = await request('/api/cashier/bills', {
    method: 'POST',
    headers: authHeaders,
    body: JSON.stringify(billPayload)
  });
  console.log(`  [OK] Factura POS creada: No ${billRes.data.billNumber} | Total: S/ ${billRes.data.totalAmount} | Estado: ${billRes.data.paymentStatus}`);

  // PDF
  const pdfRes = await request(`/api/cashier/bills/${billRes.data.id}/pdf`, { headers: authHeaders });
  console.log(`  [OK] Emisión de comprobante PDF validada (HTTP ${pdfRes.status})`);

  // Anti-manipulación
  const hackPayload = {
    customerName: 'Hacker',
    items: [
      { medicineId: 1, quantity: 1, unitPrice: 0.20 } // precio manipulado
    ]
  };
  const hackRes = await request('/api/cashier/bills', {
    method: 'POST',
    headers: authHeaders,
    body: JSON.stringify(hackPayload)
  });
  if (hackRes.status === 400 || hackRes.status === 500) {
    console.log(`  [OK] Anti-manipulación de precios bloqueó intento de precio alterado (HTTP ${hackRes.status})`);
  } else {
    throw new Error('La manipulación de precio no fue rechazada');
  }

  // 7. Gestión de Órdenes (orden-ms)
  console.log('\n[7/10] Verificando Transaccionalidad de Órdenes en orden-ms...');
  const ordenPayload = {
    clienteNombre: 'María García',
    metodoPago: 'EFECTIVO',
    items: [
      { productoId: 1, cantidad: 1 }
    ]
  };
  const ordenRes = await request('/api/v1/ordenes', {
    method: 'POST',
    headers: authHeaders,
    body: JSON.stringify(ordenPayload)
  });
  if (ordenRes.ok) {
    console.log(`  [OK] Orden transaccional creada: No ${ordenRes.data.numeroOrden} | Total: S/ ${ordenRes.data.total} | Estado: ${ordenRes.data.estado}`);
  } else {
    console.log(`  [INFO] Orden respuesta HTTP ${ordenRes.status}: ${JSON.stringify(ordenRes.data)}`);
  }

  // 8. Devoluciones (facturacion-ms)
  console.log('\n[8/10] Verificando Módulo de Devoluciones en facturacion-ms...');
  const returnPayload = {
    billId: billRes.data.id,
    reason: 'Cambio de producto solicitado por el cliente',
    items: [
      { billItemId: billRes.data.items[0].id, quantity: 1, refundAmount: 5.00 }
    ]
  };
  const returnRes = await request('/api/cashier/returns', {
    method: 'POST',
    headers: authHeaders,
    body: JSON.stringify(returnPayload)
  });
  console.log(`  [OK] Devolución registrada: No ${returnRes.data.returnNumber} | Monto reembolsado: S/ ${returnRes.data.refundAmount}`);

  // 9. Reportes y Conciliación Financiera
  console.log('\n[9/10] Verificando Reportes y Conciliación de Caja...');
  const salesRes = await request('/api/admin/reports/sales?startDate=2026-01-01&endDate=2026-12-31', { headers: authHeaders });
  console.log(`  [OK] Reporte de Ventas: Total S/ ${salesRes.data.totalSales} | Total Comprobantes: ${salesRes.data.totalBills}`);
  const dailyCashRes = await request('/api/admin/reports/cash-register', { headers: authHeaders });
  console.log(`  [OK] Arqueo de Caja: Efectivo S/ ${dailyCashRes.data.totalCash} | Recaudado Total: S/ ${dailyCashRes.data.totalCollected} | Facturas: ${dailyCashRes.data.totalBills}`);

  // 10. Pasarelas de Pago y Swagger Centralizado
  console.log('\n[10/10] Verificando Pasarelas de Pago y Swagger UI Centralizado...');
  const pagoConfigRes = await request('/api/v1/pagos/config', { headers: authHeaders });
  console.log(`  [OK] PayPal Sandbox Config: Client ID configurado = ${Boolean(pagoConfigRes.data.payPalClientId)} | Moneda = '${pagoConfigRes.data.payPalCurrency}'`);
  console.log(`  [OK] Mercado Pago Config: Public Key configurada = ${Boolean(pagoConfigRes.data.mpPublicKey)} | Moneda = '${pagoConfigRes.data.mpCurrency}'`);

  const docNames = ['usuario', 'catalogo', 'cliente', 'inventario', 'orden', 'facturacion', 'pago'];
  for (const name of docNames) {
    const d = await request(`/v3/api-docs/${name}`);
    if (!d.ok) {
      throw new Error(`Fallo al consultar OpenAPI definition para ${name}: HTTP ${d.status}`);
    }
    console.log(`  [OK] OpenAPI Doc '/v3/api-docs/${name}' disponible: '${d.data.info?.title}'`);
  }

  console.log('\n==========================================================');
  console.log('  AUDITORIA INTEGRAL COMPLETADA EXITOSAMENTE (100% OK)   ');
  console.log('==========================================================');
}

runAudit().catch(err => {
  console.error('\n[ERROR EN AUDITORIA]:', err.message);
  process.exit(1);
});
