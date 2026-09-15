package org.iesalixar.daw2.GarikAsatryan.Valkyria.services;

import org.iesalixar.daw2.GarikAsatryan.valkyria.exceptions.AppException;
import org.iesalixar.daw2.GarikAsatryan.valkyria.services.FileService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileServiceTest {

    private static final String FOLDER = "artists";

    @TempDir
    Path uploadDir;

    private final FileService fileService = new FileService();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(fileService, "uploadDirectory", uploadDir.toString());
    }

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    // ─── saveFile ──────────────────────────────────────────────────────────────

    @Test
    void saveFile_validImage_writesFullAndThumbnailWebp() throws IOException {
        String baseName = fileService.saveFile(image("logo.png", "image/png", png(800, 600)), FOLDER);

        assertThat(files(baseName)).allMatch(Files::exists);
    }

    @Test
    void saveFile_textDisguisedAsImage_returns400() {
        MockMultipartFile fake = image("logo.png", "image/png", "no soy una imagen".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> fileService.saveFile(fake, FOLDER))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("msg.file.not-an-image")
                .extracting("status").isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void saveFile_nonImageContentType_returns400() {
        MockMultipartFile pdf = image("doc.pdf", "application/pdf", new byte[]{1, 2, 3});

        assertThatThrownBy(() -> fileService.saveFile(pdf, FOLDER))
                .isInstanceOf(AppException.class)
                .extracting("status").isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void saveFile_hugeDimensions_isRejectedWithoutDecoding() {
        // PNG de pocos bytes que declara 50000x50000 píxeles (decodificarlo necesitaría ~10 GB)
        MockMultipartFile bomb = image("bomb.png", "image/png", pngHeaderOnly(50_000, 50_000));

        assertThatThrownBy(() -> fileService.saveFile(bomb, FOLDER))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("msg.file.image-too-large")
                .extracting("status").isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void saveFile_corruptImageData_returns400() {
        MockMultipartFile corrupt = image("corrupt.png", "image/png", pngHeaderOnly(100, 100));

        assertThatThrownBy(() -> fileService.saveFile(corrupt, FOLDER))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("msg.file.not-an-image");
    }

    @Test
    void saveFile_emptyFile_returnsNull() {
        assertThat(fileService.saveFile(image("empty.png", "image/png", new byte[0]), FOLDER)).isNull();
    }

    @Test
    void saveFile_transactionRolledBack_deletesNewFiles() throws IOException {
        TransactionSynchronizationManager.initSynchronization();
        String baseName = fileService.saveFile(image("logo.png", "image/png", png(100, 100)), FOLDER);
        assertThat(files(baseName)).allMatch(Files::exists);

        completeTransaction(TransactionSynchronization.STATUS_ROLLED_BACK);

        assertThat(files(baseName)).noneMatch(Files::exists);
    }

    // ─── deleteFile ────────────────────────────────────────────────────────────

    @Test
    void deleteFile_insideTransaction_waitsForCommit() throws IOException {
        String baseName = fileService.saveFile(image("logo.png", "image/png", png(100, 100)), FOLDER);

        TransactionSynchronizationManager.initSynchronization();
        fileService.deleteFile(baseName, FOLDER);
        assertThat(files(baseName)).allMatch(Files::exists);

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

        assertThat(files(baseName)).noneMatch(Files::exists);
    }

    @Test
    void deleteFile_transactionRolledBack_keepsFiles() throws IOException {
        String baseName = fileService.saveFile(image("logo.png", "image/png", png(100, 100)), FOLDER);

        TransactionSynchronizationManager.initSynchronization();
        fileService.deleteFile(baseName, FOLDER);
        completeTransaction(TransactionSynchronization.STATUS_ROLLED_BACK);

        assertThat(files(baseName)).allMatch(Files::exists);
    }

    @Test
    void deleteFile_withoutTransaction_deletesImmediately() throws IOException {
        String baseName = fileService.saveFile(image("logo.png", "image/png", png(100, 100)), FOLDER);

        fileService.deleteFile(baseName, FOLDER);

        assertThat(files(baseName)).noneMatch(Files::exists);
    }

    // ─── helpers ───────────────────────────────────────────────────────────────

    private void completeTransaction(int status) {
        TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCompletion(status));
    }

    private Path[] files(String baseName) {
        Path folder = uploadDir.resolve(FOLDER);
        return new Path[]{folder.resolve(baseName + "_full.webp"), folder.resolve(baseName + "_thumb.webp")};
    }

    private MockMultipartFile image(String name, String contentType, byte[] content) {
        return new MockMultipartFile("file", name, contentType, content);
    }

    private byte[] png(int width, int height) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }

    // Firma PNG + cabecera IHDR válida, sin datos de imagen
    private byte[] pngHeaderOnly(int width, int height) {
        ByteBuffer ihdr = ByteBuffer.allocate(13).putInt(width).putInt(height)
                .put((byte) 8).put((byte) 2).put((byte) 0).put((byte) 0).put((byte) 0);
        byte[] type = "IHDR".getBytes(StandardCharsets.US_ASCII);
        CRC32 crc = new CRC32();
        crc.update(type);
        crc.update(ihdr.array());

        return ByteBuffer.allocate(8 + 4 + 4 + 13 + 4)
                .put(new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'})
                .putInt(13).put(type).put(ihdr.array()).putInt((int) crc.getValue())
                .array();
    }
}
