package org.iesalixar.daw2.GarikAsatryan.valkyria.services;

import net.coobird.thumbnailator.Thumbnails;
import org.iesalixar.daw2.GarikAsatryan.valkyria.exceptions.AppException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
 * - Manejo robusto de errores sin romper transacciones de BD
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

    /**
     * Guarda un archivo de imagen generando automáticamente dos versiones optimizadas.
     * <p>
     * Proceso:
     * 1. Valida que el archivo no esté vacío y sea una imagen
     * 2. Crea el directorio de destino si no existe
     * 3. Genera un UUID único como nombre base
     * 4. Procesa y guarda la versión Full Size (hasta 1200x1200px)
     * 5. Procesa y guarda la versión Thumbnail (hasta 600x600px)
     * <p>
     * Ambas versiones:
     * - Se convierten a formato WebP para optimizar peso
     * - Mantienen el aspect ratio original
     * - Se comprimen con calidad 90%
     *
     * @param file      Archivo multipart recibido desde el formulario
     * @param subFolder Subdirectorio dentro de uploads (e.g., "artists", "sponsors")
     * @return Nombre base UUID compartido por ambas versiones, o null si el archivo está vacío
     * @throws AppException si el archivo no es una imagen o hay error de E/S
     */
    public String saveFile(MultipartFile file, String subFolder) {
        logger.debug("Iniciando proceso de guardado de archivo. SubFolder: {}", subFolder);

        // Validación 1: Verificar que el archivo no esté vacío
        if (file == null || file.isEmpty()) {
            logger.warn("Intento de guardar archivo vacío o null");
            return null;
        }

        logger.debug("Archivo recibido: {} ({} bytes, tipo: {})",
                file.getOriginalFilename(),
                file.getSize(),
                file.getContentType());

        // Validación 2: Verificar que sea una imagen
        if (file.getContentType() != null && !file.getContentType().startsWith("image/")) {
            logger.error("Intento de subir archivo que no es imagen: {}", file.getContentType());
            throw AppException.badRequest("msg.file.not-an-image");
        }

        try {
            // Paso 1: Preparar el directorio de destino
            Path uploadPath = Paths.get(uploadDirectory, subFolder);
            logger.debug("Ruta de destino: {}", uploadPath.toAbsolutePath());

            // Crear directorio si no existe (incluyendo padres)
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
                logger.info("Directorio creado: {}", uploadPath);
            }

            // Paso 2: Generar nombre único usando UUID
            String baseName = UUID.randomUUID().toString();
            logger.debug("UUID generado para archivo: {}", baseName);

            // Paso 3: Leer los bytes del archivo una sola vez
            byte[] bytes = file.getBytes();
            logger.debug("Bytes del archivo leídos: {} bytes", bytes.length);

            // Paso 4: Generar Versión Full Size (1200x1200)
            Path fullPath = uploadPath.resolve(baseName + FULL_SUFFIX);
            logger.debug("Procesando versión Full Size: {}", fullPath.getFileName());
            processImage(bytes, FULL_WIDTH, FULL_HEIGHT, fullPath);
            logger.info("Versión Full Size generada: {} ({}x{})",
                    fullPath.getFileName(), FULL_WIDTH, FULL_HEIGHT);

            // Paso 5: Generar Versión Thumbnail (600x600)
            Path thumbPath = uploadPath.resolve(baseName + THUMB_SUFFIX);
            logger.debug("Procesando versión Thumbnail: {}", thumbPath.getFileName());
            processImage(bytes, THUMB_WIDTH, THUMB_HEIGHT, thumbPath);
            logger.info("Versión Thumbnail generada: {} ({}x{})",
                    thumbPath.getFileName(), THUMB_WIDTH, THUMB_HEIGHT);

            logger.info("Imágenes WebP generadas exitosamente. Base: {}, SubFolder: {}",
                    baseName, subFolder);

            return baseName;

        } catch (IOException e) {
            // Error crítico: no se pudo guardar el archivo
            logger.error("Error crítico de E/S al guardar imagen en {}/{}: {}",
                    uploadDirectory, subFolder, e.getMessage(), e);
            throw AppException.internal("msg.file.save-error");
        }
    }

    /**
     * Método helper privado para procesar y guardar una versión de la imagen.
     * Encapsula la lógica de Thumbnailator para evitar duplicación de código.
     * <p>
     * Thumbnailator automáticamente:
     * - Mantiene el aspect ratio (no distorsiona)
     * - Realiza redimensionado solo si la imagen es más grande que el tamaño objetivo
     * - Aplica algoritmos de alta calidad para el resize
     *
     * @param source      Bytes de la imagen original
     * @param width       Ancho máximo de la imagen resultante
     * @param height      Alto máximo de la imagen resultante
     * @param destination Ruta completa donde guardar el archivo procesado
     * @throws IOException si hay error en el procesamiento o guardado
     */
    private void processImage(byte[] source, int width, int height, Path destination) throws IOException {
        logger.trace("Procesando imagen: {}x{} -> {}", width, height, destination.getFileName());

        Thumbnails.of(new ByteArrayInputStream(source))
                .size(width, height)                    // Dimensiones máximas (mantiene aspect ratio)
                .outputFormat("webp")                   // Formato de salida optimizado
                .outputQuality(OUTPUT_QUALITY)          // Calidad de compresión (0.90 = 90%)
                .toFile(destination.toFile());          // Guardar en disco

        logger.trace("Imagen procesada correctamente: {}", destination.getFileName());
    }

    /**
     * Elimina ambas versiones (Full y Thumbnail) de una imagen del sistema de archivos.
     * <p>
     * Comportamiento robusto:
     * - No lanza excepciones si los archivos no existen (idempotente)
     * - Registra errores pero no rompe transacciones de BD
     * - Intenta eliminar ambas versiones independientemente
     * <p>
     * Este método es invocado durante eliminaciones de entidades (artistas, sponsors, etc.)
     * y debe ser tolerante a fallos para no afectar la consistencia de la base de datos.
     *
     * @param baseName  Nombre base UUID del archivo (sin sufijos ni extensión)
     * @param subFolder Subdirectorio donde se encuentran los archivos
     */
    public void deleteFile(String baseName, String subFolder) {
        // Validación: si no hay nombre base, no hay nada que eliminar
        if (baseName == null || baseName.isEmpty()) {
            logger.debug("DeleteFile llamado con baseName vacío, se omite operación");
            return;
        }

        logger.info("Iniciando eliminación de archivos con base: {} en {}", baseName, subFolder);

        // Construir la ruta del directorio
        Path folderPath = Paths.get(uploadDirectory, subFolder);
        logger.debug("Ruta de búsqueda: {}", folderPath.toAbsolutePath());

        try {
            // Intentar eliminar versión Full Size
            Path fullPath = folderPath.resolve(baseName + FULL_SUFFIX);
            boolean fullDeleted = Files.deleteIfExists(fullPath);
            if (fullDeleted) {
                logger.debug("Archivo Full eliminado: {}", fullPath.getFileName());
            } else {
                logger.debug("Archivo Full no existía: {}", fullPath.getFileName());
            }

            // Intentar eliminar versión Thumbnail
            Path thumbPath = folderPath.resolve(baseName + THUMB_SUFFIX);
            boolean thumbDeleted = Files.deleteIfExists(thumbPath);
            if (thumbDeleted) {
                logger.debug("Archivo Thumb eliminado: {}", thumbPath.getFileName());
            } else {
                logger.debug("Archivo Thumb no existía: {}", thumbPath.getFileName());
            }

            // Log informativo si se eliminó al menos uno
            if (fullDeleted || thumbDeleted) {
                logger.info("Archivos físicos eliminados correctamente: {} (full: {}, thumb: {})",
                        baseName, fullDeleted, thumbDeleted);
            } else {
                logger.warn("Ningún archivo fue eliminado para base: {}. Posiblemente ya no existían",
                        baseName);
            }

        } catch (IOException e) {
            // IMPORTANTE: No lanzamos AppException aquí
            // Razón: Si el archivo no se puede borrar del disco, no queremos romper
            // la transacción de base de datos. La entidad debe poder eliminarse aunque
            // el archivo quede huérfano (se puede limpiar manualmente después).
            logger.error("Error al eliminar archivos físicos de: {} en {}/{}. " +
                            "La operación de BD continuará. Motivo: {}",
                    baseName, uploadDirectory, subFolder, e.getMessage(), e);
        }
    }
}