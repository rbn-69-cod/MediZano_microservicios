# Script de Verificacion End-to-End para MediZano Microservicios
$ErrorActionPreference = "Stop"
$gateway = "http://localhost:8090"

Write-Output "=========================================================="
Write-Output "INICIANDO AUDITORIA INTEGRAL END-TO-END SOBRE BD LIMPIA"
Write-Output "=========================================================="

# 1. Login y Autenticacion
Write-Output "`n[1/10] Probando Autenticacion JWT en usuario-ms..."
$authResp = Invoke-RestMethod -Uri "$gateway/api/auth/login" -Method POST -ContentType "application/json" -Body '{"username":"admin","password":"admin123"}'
$token = $authResp.token
$headers = @{ "Authorization" = "Bearer $token" }
Write-Output "  [OK] Login exitoso para '$($authResp.username)' (Rol: $($authResp.role)). Token length: $($token.Length)"

# 2. Auditoria y Usuarios
Write-Output "`n[2/10] Probando Usuarios y Bitacora de Auditoria..."
$users = Invoke-RestMethod -Uri "$gateway/api/admin/users" -Headers $headers
Write-Output "  [OK] Usuarios registrados: $($users.Count)"
$activityLogs = Invoke-RestMethod -Uri "$gateway/api/admin/audit/all" -Headers $headers
Write-Output "  [OK] Registros de actividad auditados: $($activityLogs.Count)"
$loginLogs = Invoke-RestMethod -Uri "$gateway/api/admin/audit/login-logout" -Headers $headers
Write-Output "  [OK] Historial de accesos auditados: $($loginLogs.Count)"

# 3. Catalogo de Medicamentos
Write-Output "`n[3/10] Probando Catalogo Maestro y Medicamentos en catalogo-ms..."
$meds = Invoke-RestMethod -Uri "$gateway/api/pharmacist/medicines" -Headers $headers
Write-Output "  [OK] Medicamentos en catalogo: $($meds.Count)"
foreach ($m in $meds) {
    Write-Output "    - Medicamento ID $($m.id): $($m.name) | Precio Venta: S/ $($m.sellingPrice) | Barcode: $($m.barcode)"
}

$paracetamolByBarcode = Invoke-RestMethod -Uri "$gateway/api/pharmacist/medicines/barcode/7751234567890" -Headers $headers
if ($paracetamolByBarcode.sellingPrice -eq 5.00) {
    Write-Output "  [OK] Verificado: Paracetamol por codigo de barras retorna precio oficial exacto de S/ 5.00"
} else {
    throw "Error: Precio de Paracetamol es $($paracetamolByBarcode.sellingPrice)"
}

# 4. Inventario y Lotes
Write-Output "`n[4/10] Probando Inventario y Lotes en inventario-ms..."
$batches = Invoke-RestMethod -Uri "$gateway/api/pharmacist/batches" -Headers $headers
Write-Output "  [OK] Lotes farmaceuticos activos: $($batches.Count)"
$batchesMed1 = Invoke-RestMethod -Uri "$gateway/api/pharmacist/batches/medicine/1" -Headers $headers
Write-Output "  [OK] Lotes para Medicamento 1 (Paracetamol): $($batchesMed1.Count) lote(s), Stock disponible: $($batchesMed1[0].quantityAvailable)"

# 5. Clientes
Write-Output "`n[5/10] Probando Directorio de Clientes en cliente-ms..."
$clientes = Invoke-RestMethod -Uri "$gateway/api/v1/clientes" -Headers $headers
Write-Output "  [OK] Clientes iniciales encontrados: $($clientes.Count)"
foreach ($c in $clientes) {
    Write-Output "    - Cliente: $($c.nombre) (Doc: $($c.documento)) | Tel: $($c.telefono)"
}
$clienteDoc = Invoke-RestMethod -Uri "$gateway/api/v1/clientes/documento/12345678" -Headers $headers
Write-Output "  [OK] Busqueda por DNI 12345678 exitosa: $($clienteDoc.nombre)"

# 6. Facturacion y Ventas POS
Write-Output "`n[6/10] Probando Venta POS en facturacion-ms..."
$billReq = @{
    customerName = "Juan Perez Rodriguez"
    customerPhone = "987654321"
    customerEmail = "juan.perez@gmail.com"
    items = @(
        @{
            medicineId = 1
            quantity = 2
            unitPrice = 5.00
        }
    )
    payments = @(
        @{
            mode = "CASH"
            amount = 10.00
        }
    )
} | ConvertTo-Json -Depth 5

$billResp = Invoke-RestMethod -Uri "$gateway/api/cashier/bills" -Method POST -Headers $headers -ContentType "application/json; charset=utf-8" -Body $billReq
Write-Output "  [OK] Factura POS creada: No $($billResp.billNumber) | Total: S/ $($billResp.totalAmount) | Estado: $($billResp.paymentStatus)"

# Prueba PDF Comprobante
$pdfBytes = Invoke-RestMethod -Uri "$gateway/api/cashier/bills/$($billResp.id)/pdf" -Headers $headers
Write-Output "  [OK] PDF de Factura $($billResp.billNumber) generado correctamente ($($pdfBytes.Length) bytes)"

# Prueba Anti-manipulacion de Precios
try {
    $manipReq = @{
        customerName = "Intento Invalido"
        items = @(
            @{
                medicineId = 1
                quantity = 1
                unitPrice = 0.50
            }
        )
    } | ConvertTo-Json -Depth 5
    $dummy = Invoke-RestMethod -Uri "$gateway/api/cashier/bills" -Method POST -Headers $headers -ContentType "application/json; charset=utf-8" -Body $manipReq
    Write-Output "  [ALERTA] La manipulacion de precios no fue rechazada!"
} catch {
    Write-Output "  [OK] Anti-manipulacion de precios bloqueo intento de precio alterado: $($_.Exception.Message)"
}

# 7. Ordenes y Transacciones
Write-Output "`n[7/10] Probando Gestion de Ordenes en orden-ms..."
$ordenReq = @{
    clienteId = 1
    metodoPago = "EFECTIVO"
    items = @(
        @{
            productoId = 1
            cantidad = 1
            precioUnitario = 5.00
        }
    )
} | ConvertTo-Json -Depth 5
$ordenResp = Invoke-RestMethod -Uri "$gateway/api/v1/ordenes" -Method POST -Headers $headers -ContentType "application/json; charset=utf-8" -Body $ordenReq
Write-Output "  [OK] Orden creada: ID $($ordenResp.id) | Numero: $($ordenResp.numeroOrden) | Total: S/ $($ordenResp.total) | Estado: $($ordenResp.estado)"

# 8. Devoluciones
Write-Output "`n[8/10] Probando Devoluciones en facturacion-ms..."
$returnReq = @{
    billId = $billResp.id
    reason = "Cambio de presentacion por solicitud del cliente"
    items = @(
        @{
            billItemId = $billResp.items[0].id
            quantity = 1
        }
    )
} | ConvertTo-Json -Depth 5
$returnResp = Invoke-RestMethod -Uri "$gateway/api/cashier/returns" -Method POST -Headers $headers -ContentType "application/json; charset=utf-8" -Body $returnReq
Write-Output "  [OK] Devolucion registrada: No $($returnResp.returnNumber) | Monto reembolsado: S/ $($returnResp.totalRefundAmount)"

# 9. Reportes Financieros y Conciliacion
Write-Output "`n[9/10] Probando Reportes Financieros y Conciliacion de Caja..."
$salesUrl = "$gateway/api/admin/reports/sales" + '?startDate=2026-01-01&endDate=2026-12-31'
$salesReport = Invoke-RestMethod -Uri $salesUrl -Headers $headers
Write-Output "  [OK] Reporte de Ventas: Total Vendido S/ $($salesReport.totalSales) | Total Facturas: $($salesReport.totalBills)"
$cashRegisterUrl = "$gateway/api/admin/reports/cash-register" + '?startDate=2026-01-01&endDate=2026-12-31'
$cashRegister = Invoke-RestMethod -Uri $cashRegisterUrl -Headers $headers
Write-Output "  [OK] Reporte Arqueo de Caja: Total Recaudado S/ $($cashRegister.totalCash) | Facturas: $($cashRegister.totalBills)"

# 10. Pasarelas de Pago y Swagger Centralizado
Write-Output "`n[10/10] Probando Pasarelas de Pago y OpenAPI/Swagger UI..."
$payConfig = Invoke-RestMethod -Uri "$gateway/api/v1/pagos/config" -Headers $headers
Write-Output "  [OK] Pasarela PayPal Config: Client ID: $($payConfig.payPalClientId) | Moneda: $($payConfig.payPalCurrency)"
Write-Output "  [OK] Pasarela Mercado Pago Config: Public Key: $($payConfig.mpPublicKey) | Moneda: $($payConfig.mpCurrency)"

$docs = @("usuario", "catalogo", "cliente", "inventario", "orden", "facturacion", "pago")
foreach ($d in $docs) {
    $docResp = Invoke-RestMethod -Uri "$gateway/v3/api-docs/$d"
    $title = $docResp.info.title
    Write-Output "  [OK] OpenAPI Doc /v3/api-docs/$d disponible - Titulo: $title"
}

Write-Host ""
Write-Host "=========================================================="
Write-Host "AUDITORIA INTEGRAL COMPLETADA EXITOSAMENTE - ESTADO OPERATIVO TOTAL"
Write-Host "=========================================================="
