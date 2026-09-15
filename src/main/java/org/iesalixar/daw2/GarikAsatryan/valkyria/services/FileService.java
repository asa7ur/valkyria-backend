package org.iesalixar.daw2.GarikAsatryan.valkyria.services;

import net.coobird.thumbnailator.Thumbnails;
import org.iesalixar.daw2.GarikAsatryan.valkyria.exceptions.AppException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.UUID;

/**
 * Servicio de gestión de archivos e imágenes.
 * Responsable de guardar, procesar y eliminar imágenes del sistema de archivos.
 * <p>
 * Características principales:
 * - Generación automática de dos versiones de cada imagen (Full y Thumbnail)
 * - Conversión a formato WebP para optimizar tamaño y calidad
 * - Uso de UUIDs para nombres únicos y evitar colisiones
 * - Gestión de subdirectorios por tipo de entidad (artists, sponsors, etc.)
 * - Coordinado con la transacción de BD: los ficheros nuevos se borran si hay rollback
 *   y los antiguos solo se borran cuando el cambio se ha confirmado
 */
@Service
public class FileService {

    private static final Logger logger = LoggerFactory.getLogger(FileService.class);

    // Directorio raíz para uploads, configurable desde application.properties
    @Value("${upload.directory:uploads}")
    private String uploadDirectory;

    // ========== Constantes para estandarizar el procesamiento de imágenes ==========

    // Sufijos de archivo para diferenciar versiones
    private static final String FULL_SUFFIX = "_full.webp";
    private static final String THUMB_SUFFIX = "_thumb.webp";

    // Dimensiones máximas para versión completa (mantiene aspect ratio)
    private static final int FULL_WIDTH = 1200;
    private static final int FULL_HEIGHT = 1200;

    // Dimensiones para versión thumbnail (listados, previews)
    private static final int THUMB_WIDTH = 600;
    private static final int THUMB_HEIGHT = 600;

    // Calidad de compresión WebP (0.0 - 1.0). 0.90 = excelente calidad con buen ratio de compresión
    private static final float OUTPUT_QUALITY = 0.90f;

    // Una imagen se descomprime entera en memoria (4 bytes por píxel): 36 MP ≈ 6000x6000 ≈ 140 MB.
    // Un PNG de pocos KB puede declarar dimensiones enormes y agotar la memoria del servidor.
    static final long MAX_PIXELS = 36_000_000L;

    /**
     * Guarda un archivo de imagen generando automáticamente dos versiones optimizadas (WebP full y thumbnail).
     * <p>
     * La imagen se valida por su contenido real, no por el Content-Type que envía el cliente.
     * Si se llama dentro de una transacción y esta hace rollback, los ficheros creados se eliminan.
     *
     * @param file      Archivo multipart recibido desde el formulario
     * @param subFolder Subdirectorio dentro de uploads (e.g., "artists", "sponsors")
     * @return Nombre base UUID compartido por ambas versiones, o null si el archivo está vacío
     * @throws AppException 400 si no es una imagen válida o es demasiado grande; 500 si falla la escritura
     */
    public String saveFile(MultipartFile file, String subFolder) {
        if (file == null || file.isEmpty()) {
            logger.warn("Intento de guardar archivo vacío o null");
            return null;
        }

        logger.debug("Archivo recibido: {} bytes, tipo declarado: {}", file.getSize(), file.getContentType());

        // Filtro rápido; la comprobación real es validateImage
        if (file.getContentType() != null && !file.getContentType().startsWith("image/")) {
            throw AppException.badRequest("msg.file.not-an-image");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            logger.error("No se pudo leer el archivo subido: {}", e.getMessage(), e);
            throw AppException.internal("msg.file.save-error");
        }

        validateImage(bytes);

        // Se procesa en memoria antes de escribir: un fallo de decodificación es culpa del archivo (400),
        // uno de escritura en disco es del servidor (500)
        byte[] fullImage = resizeToWebp(bytes, FULL_WIDTH, FULL_HEIGHT);
        byte[] thumbImage = resizeToWebp(bytes, THUMB_WIDTH, THUMB_HEIGHT);

        String baseName = UUID.randomUUID().toString();
        Path uploadPath = Paths.get(uploadDirectory, subFolder);
        try {
            Files.createDirectories(uploadPath);
            Files.write(uploadPath.resolve(baseName + FULL_SUFFIX), fullImage);
            Files.write(uploadPath.resolve(baseName + THUMB_SUFFIX), thumbImage);
        } catch (IOException e) {
            logger.error("Error de E/S al guardar imagen en {}/{}: {}", uploadDirectory, subFolder, e.getMessage(), e);
            deleteNow(baseName, subFolder);
            throw AppException.internal("msg.file.save-error");
        }

        // Si la transacción que referencia la imagen falla, el fichero quedaría huérfano
        runOnRollback(() -> deleteNow(baseName, subFolder));

        logger.info("Imágenes WebP generadas. Base: {}, SubFolder: {}", baseName, subFolder);
        return baseName;
    }

    /**
     * Elimina ambas versiones (Full y Thumbnail) de una imagen.
     * <p>
     * Dentro de una transacción, el borrado se aplaza hasta que se confirma: si hace rollback,
     * la BD sigue apuntando a la imagen y esta debe seguir existiendo.
     * Nunca lanza excepciones (un fallo al borrar solo deja un fichero huérfano).
     *
     * @param baseName  Nombre base UUID del archivo (sin sufijos ni extensión)
     * @param subFolder Subdirectorio donde se encuentran los archivos
     */
    public void deleteFile(String baseName, String subFolder) {
        if (baseName == null || baseName.isEmpty()) {
            return;
        }
        runAfterCommit(() -> deleteNow(baseName, subFolder));
    }

    /**
     * Lee solo la cabecera de la imagen para comprobar el formato y las dimensiones sin descomprimirla.
     */
    private void validateImage(byte[] bytes) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = input != null ? ImageIO.getImageReaders(input) : null;
            if (readers == null || !readers.hasNext()) {
                throw AppException.badRequest("msg.file.not-an-image");
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                if (pixels > MAX_PIXELS) {
                    logger.warn("Imagen rechazada por tamaño: {}x{}", reader.getWidth(0), reader.getHeight(0));
                    throw AppException.badRequest("msg.file.image-too-large", MAX_PIXELS / 1_000_000);
                }
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            logger.warn("Cabecera de imagen no válida: {}", e.getMessage());
            throw AppException.badRequest("msg.file.not-an-image");
        }
    }

    /**
     * Redimensiona (manteniendo el aspect ratio y la orientación EXIF) y convierte a WebP.
     */
    private byte[] resizeToWebp(byte[] source, int width, int height) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            Thumbnails.of(new ByteArrayInputStream(source))
                    .size(width, height)
                    .outputFormat("webp")
                    .outputQuality(OUTPUT_QUALITY)
                    .toOutputStream(output);
            return output.toByteArray();
        } catch (IOException e) {
            // Cabecera válida pero datos corruptos o formato que no se puede decodificar
            logger.warn("No se pudo procesar la imagen: {}", e.getMessage(), e);
            throw AppException.badRequest("msg.file.not-an-image");
        }
    }

    private void deleteNow(String baseName, String subFolder) {
        Path folderPath = Paths.get(uploadDirectory, subFolder);
        try {
            boolean fullDeleted = Files.deleteIfExists(folderPath.resolve(baseName + FULL_SUFFIX));
            boolean thumbDeleted = Files.deleteIfExists(folderPath.resolve(baseName + THUMB_SUFFIX));

            if (fullDeleted || thumbDeleted) {
                logger.info("Archivos eliminados: {} (full: {}, thumb: {})", baseName, fullDeleted, thumbDeleted);
            } else {
                logger.warn("Ningún archivo eliminado para base: {}. Posiblemente ya no existían", baseName);
            }
        } catch (IOException e) {
            // No se propaga: la operación de BD ya está hecha; el fichero queda huérfano
            logger.error("Error al eliminar archivos de: {} en {}/{}. Motivo: {}",
                    baseName, uploadDirectory, subFolder, e.getMessage(), e);
        }
    }

    private void runAfterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private void runOnRollback(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    action.run();
                }
            }
        });
    }
}
