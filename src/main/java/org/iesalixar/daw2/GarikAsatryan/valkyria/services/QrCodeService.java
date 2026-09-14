package org.iesalixar.daw2.GarikAsatryan.valkyria.services;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.UUID;

/**
 * Servicio de generación de códigos QR.
 * Utiliza la librería ZXing (Zebra Crossing) para generar códigos QR en formato imagen.
 * <p>
 * Casos de uso:
 * - Generación de QR para tickets de entrada al festival
 * - Generación de QR para reservas de camping
 * - Códigos únicos para validación en puertas de acceso
 * <p>
 * Los códigos QR generados se incrustan en:
 * - PDFs de confirmación de pedido
 * - Emails de confirmación
 * - Sistema de validación de acceso
 * <p>
 * Características técnicas:
 * - Formato: PNG
 * - Tamaño: 250x250 píxeles (óptimo para escaneo y visualización)
 * - Generación en memoria (no se guarda en disco)
 * - Codificación estándar QR Code (BarcodeFormat.QR_CODE)
 */
@Service
public class QrCodeService {

    private static final Logger logger = LoggerFactory.getLogger(QrCodeService.class);

    // Constantes para estandarizar la generación de QR
    private static final int QR_WIDTH = 250;
    private static final int QR_HEIGHT = 250;
    private static final String IMAGE_FORMAT = "PNG";

    // El código es la credencial de acceso al recinto: 122 bits aleatorios (UUID v4) para que no se pueda adivinar
    public String newTicketCode() {
        return "TKT-" + randomCode();
    }

    public String newCampingCode() {
        return "CMP-" + randomCode();
    }

    private static String randomCode() {
        return UUID.randomUUID().toString().replace("-", "").toUpperCase();
    }

    /**
     * Genera un código QR a partir de un texto y devuelve los bytes de la imagen PNG.
     * El código QR puede contener cualquier texto (IDs, URLs, datos estructurados, etc.).
     * <p>
     * Proceso:
     * 1. Codifica el texto en una matriz de bits (BitMatrix)
     * 2. Convierte la matriz en una imagen PNG
     * 3. Escribe la imagen en un stream de bytes en memoria
     * 4. Retorna el array de bytes para su uso (PDF, email, etc.)
     * <p>
     * Tamaño del QR:
     * - 250x250 píxeles es un tamaño óptimo que permite:
     * * Fácil escaneo con smartphones a distancia media
     * * Buena calidad de impresión
     * * Tamaño de archivo razonable
     *
     * @param text Texto a codificar en el código QR (generalmente un ID único como "TKT-A1B2C3D4")
     * @return Array de bytes de la imagen PNG del código QR
     * @throws Exception si hay error en la codificación o generación de la imagen
     */
    public byte[] generateQrCodeImage(String text) throws Exception {
        logger.debug("Generando código QR para texto: '{}'", text);

        // Paso 1: Crear el writer de códigos QR de ZXing
        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        logger.trace("QRCodeWriter inicializado");

        // Paso 2: Codificar el texto en una matriz de bits (BitMatrix)
        // Parámetros: texto, formato, ancho, alto
        logger.trace("Codificando texto en BitMatrix ({}x{} px)...", QR_WIDTH, QR_HEIGHT);
        BitMatrix bitMatrix = qrCodeWriter.encode(
                text,
                BarcodeFormat.QR_CODE,  // Formato estándar de código QR
                QR_WIDTH,               // Ancho de la imagen
                QR_HEIGHT               // Alto de la imagen
        );
        logger.debug("BitMatrix generada correctamente para '{}'", text);

        // Paso 3: Convertir la matriz de bits a imagen PNG en memoria
        ByteArrayOutputStream pngOutputStream = new ByteArrayOutputStream();
        logger.trace("Escribiendo BitMatrix a stream PNG...");

        // MatrixToImageWriter convierte la BitMatrix en una imagen
        MatrixToImageWriter.writeToStream(bitMatrix, IMAGE_FORMAT, pngOutputStream);

        // Paso 4: Obtener los bytes de la imagen generada
        byte[] qrImageBytes = pngOutputStream.toByteArray();

        logger.debug("✓ Código QR generado exitosamente. Tamaño: {} bytes, Dimensiones: {}x{} px",
                qrImageBytes.length, QR_WIDTH, QR_HEIGHT);

        logger.trace("QR generado para '{}': {} bytes", text, qrImageBytes.length);

        return qrImageBytes;
    }
}