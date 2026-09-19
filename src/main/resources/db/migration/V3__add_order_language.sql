-- Idioma de la web al hacer el pedido: el email y el PDF se generan después, al llegar el webhook de Stripe,
-- y en ese momento ya no hay petición del usuario de la que sacar el idioma.
ALTER TABLE orders
    ADD COLUMN language VARCHAR(5) NOT NULL DEFAULT 'es';
