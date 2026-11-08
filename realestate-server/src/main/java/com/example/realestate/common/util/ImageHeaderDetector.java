package com.example.realestate.common.util;

import java.io.IOException;
import java.io.InputStream;

/**
 * 图片真实文件头识别：扩展名与 Content-Type 均由文件头推断，不信任客户端声明。
 * W2 房源图片（公开存储）与 W4 报修图片（私有存储）共用本工具，避免复制校验逻辑。
 */
public final class ImageHeaderDetector {

    /** 读取字节数：WEBP 需要第 1-4 与 9-12 字节 */
    public static final int HEADER_LENGTH = 12;

    private ImageHeaderDetector() {
    }

    /**
     * @param header 文件头字节（最多 12 字节）
     * @param read   实际读取长度
     * @return jpg/png/gif/webp；无法识别返回 null
     */
    public static String detectExtension(byte[] header, int read) {
        if (header == null || read <= 0) {
            return null;
        }
        if (read >= 3 && (header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8
                && (header[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        if (read >= 8 && (header[0] & 0xFF) == 0x89 && header[1] == 'P'
                && header[2] == 'N' && header[3] == 'G') {
            return "png";
        }
        if (read >= 6 && header[0] == 'G' && header[1] == 'I' && header[2] == 'F'
                && header[3] == '8') {
            return "gif";
        }
        if (read >= 12 && header[0] == 'R' && header[1] == 'I' && header[2] == 'F'
                && header[3] == 'F' && header[8] == 'W' && header[9] == 'E'
                && header[10] == 'B' && header[11] == 'P') {
            return "webp";
        }
        return null;
    }

    /** 从流中读取文件头并识别；流由调用方负责关闭 */
    public static String detectExtension(InputStream in) throws IOException {
        byte[] header = new byte[HEADER_LENGTH];
        int read = in.readNBytes(header, 0, HEADER_LENGTH);
        return detectExtension(header, read);
    }

    /** 是否 PDF（政策附件） */
    public static boolean isPdf(byte[] header, int read) {
        return header != null && read >= 4 && header[0] == '%' && header[1] == 'P'
                && header[2] == 'D' && header[3] == 'F';
    }

    /** 按扩展名返回浏览器可用的 Content-Type */
    public static String contentTypeOf(String extension) {
        if (extension == null) {
            return "application/octet-stream";
        }
        return switch (extension.toLowerCase()) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "pdf" -> "application/pdf";
            default -> "application/octet-stream";
        };
    }
}
