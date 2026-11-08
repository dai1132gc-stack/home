package com.example.realestate.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("图片/PDF 文件头识别")
class ImageHeaderDetectorTest {

    @Test
    @DisplayName("识别 JPG/PNG/GIF/WEBP")
    void detectsImages() {
        assertThat(ImageHeaderDetector.detectExtension(
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0, 0}, 3)).isEqualTo("jpg");
        assertThat(ImageHeaderDetector.detectExtension(
                new byte[]{(byte) 0x89, 'P', 'N', 'G', 0, 0, 0, 0}, 8)).isEqualTo("png");
        assertThat(ImageHeaderDetector.detectExtension(
                "GIF89a".getBytes(StandardCharsets.US_ASCII), 6)).isEqualTo("gif");
        assertThat(ImageHeaderDetector.detectExtension(
                "RIFF1234WEBP".getBytes(StandardCharsets.US_ASCII), 12)).isEqualTo("webp");
    }

    @Test
    @DisplayName("非图片与伪造扩展名返回 null")
    void rejectsNonImages() {
        assertThat(ImageHeaderDetector.detectExtension("hello".getBytes(StandardCharsets.US_ASCII), 5))
                .isNull();
        assertThat(ImageHeaderDetector.detectExtension(new byte[0], 0)).isNull();
        assertThat(ImageHeaderDetector.detectExtension(null, 0)).isNull();
    }

    @Test
    @DisplayName("PDF 头识别与 Content-Type 映射")
    void pdfAndContentType() {
        assertThat(ImageHeaderDetector.isPdf("%PDF".getBytes(StandardCharsets.US_ASCII), 4)).isTrue();
        assertThat(ImageHeaderDetector.isPdf("PNG".getBytes(StandardCharsets.US_ASCII), 3)).isFalse();
        assertThat(ImageHeaderDetector.contentTypeOf("jpg")).isEqualTo("image/jpeg");
        assertThat(ImageHeaderDetector.contentTypeOf("PDF")).isEqualTo("application/pdf");
        assertThat(ImageHeaderDetector.contentTypeOf(null)).isEqualTo("application/octet-stream");
    }
}
