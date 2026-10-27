-- ======================================================================
-- V2__extend_batches_fefo.sql
-- Control de vencimientos FEFO, Estados de Lote y Trazabilidad de Factura
-- ======================================================================

ALTER TABLE batches
    ADD COLUMN IF NOT EXISTS estado VARCHAR(30) DEFAULT 'DISPONIBLE' NOT NULL,
    ADD COLUMN IF NOT EXISTS proveedor_id BIGINT,
    ADD COLUMN IF NOT EXISTS numero_factura_compra VARCHAR(50),
    ADD COLUMN IF NOT EXISTS fecha_ultima_evaluacion TIMESTAMP;

-- Constraint de validación para los estados permitidos
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_batches_estado'
    ) THEN
        ALTER TABLE batches
            ADD CONSTRAINT chk_batches_estado
            CHECK (estado IN ('DISPONIBLE', 'ALERTA_CANJE', 'RETIRAR_MOSTRADOR', 'VENCIDO_BAJA', 'DEVUELTO'));
    END IF;
END $$;

-- Índice compuesto optimizado para despacho FEFO en POS y ventas
CREATE INDEX IF NOT EXISTS idx_batches_fefo_pos 
    ON batches (medicine_id, estado, expiry_date ASC, quantity_available);

-- Índice compuesto optimizado para cron diario de evaluación de vencimientos
CREATE INDEX IF NOT EXISTS idx_batches_cron_eval 
    ON batches (expiry_date, estado, quantity_available);

