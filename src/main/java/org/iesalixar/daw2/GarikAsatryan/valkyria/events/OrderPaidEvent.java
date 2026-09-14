package org.iesalixar.daw2.GarikAsatryan.valkyria.events;

/**
 * Se publica cuando un pedido pasa a PAID. Solo lleva el ID: el listener carga el pedido
 * en su propia transacción, una vez confirmada la del pago.
 */
public record OrderPaidEvent(Long orderId) {
}
