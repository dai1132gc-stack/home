package com.example.realestate.modules.file.service;

import com.example.realestate.common.enums.ErrorCode;
import com.example.realestate.common.exception.BusinessException;
import com.example.realestate.common.util.ImageHeaderDetector;
import com.example.realestate.modules.file.config.StorageProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/**
 * 私有文件存储：落盘在 app.storage.private-root，不注册静态映射，
 * 只能通过受鉴权的 Controller 读取。数据库只保存相对 key，不保存可公开访问的 URL。
 * 覆盖：合同 PDF、报修图片、政策附件。
 */
@Component
public class PrivateStorageService {

    private static final Logger log = LoggerFactory.getLogger(PrivateStorageService.class);
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyyMM");

    private final StorageProperties properties;
    private final Path root;

    public PrivateStorageService(StorageProperties properties) {
        this.properties = properties;
        this.root = Paths.get(properties.privateRootOrDefault()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new IllegalStateException("无法创建私有存储目录: " + root, e);
        }
        log.info("私有文件存储已启用 root={}", root);
    }

    /**
     * 保存合同 PDF 并返回相对 key（contract/yyyyMM/uuid.pdf）。
     * 文件名由服务端生成，不接受客户端输入，避免目录穿越与同名覆盖。
     */
    public String storePdf(byte[] content) {
        if (content == null || content.length == 0) {
            throw new BusinessException(ErrorCode.UNEXPECTED, "PDF 内容为空");
        }
        return storeBytes("contract", "pdf", content);
    }

    /**
     * 保存报修图片：仅 JPG/PNG/GIF/WEBP，真实文件头校验，单图大小沿用 app.storage.max-image-size-bytes。
     * 返回私有相对 key（repair/yyyyMM/uuid.ext）。
     */
    public String storeRepairImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.FILE_INVALID, "请选择要上传的图片");
        }
        if (file.getSize() > properties.maxImageSizeBytesOrDefault()) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE,
                    "图片大小不能超过 " + (properties.maxImageSizeBytesOrDefault() / 1024 / 1024) + "MB");
        }
        String extension = detectImageExtension(file);
        try {
            return storeBytes("repair", extension, file.getBytes());
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.FILE_INVALID, "读取上传图片失败");
        }
    }

    /** 政策附件：仅 PDF，真实文件头校验，返回 policy/yyyyMM/uuid.pdf */
    public String storePolicyAttachment(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.FILE_INVALID, "请选择要上传的附件");
        }
        if (file.getSize() > properties.maxImageSizeBytesOrDefault()) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE,
                    "附件大小不能超过 " + (properties.maxImageSizeBytesOrDefault() / 1024 / 1024) + "MB");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.FILE_INVALID, "读取上传附件失败");
        }
        if (!ImageHeaderDetector.isPdf(bytes, Math.min(bytes.length, 4))) {
            throw new BusinessException(ErrorCode.FILE_INVALID, "政策附件仅支持 PDF 文件");
        }
        return storeBytes("policy", "pdf", bytes);
    }

    public boolean exists(String key) {
        Path path = resolveSafely(key);
        return path != null && Files.isRegularFile(path);
    }

    public byte[] read(String key) {
        return read(key, "文件不存在");
    }

    public byte[] read(String key, String notFoundMessage) {
        Path path = resolveSafely(key);
        if (path == null || !Files.isRegularFile(path)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, notFoundMessage);
        }
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            log.error("读取私有文件失败 key={}", key, e);
            throw new BusinessException(ErrorCode.UNEXPECTED, "文件读取失败");
        }
    }

    /** 删除私有对象；文件不存在视为成功，仅记录警告 */
    public void delete(String key) {
        Path path = resolveSafely(key);
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("删除私有文件失败 key={}: {}", key, e.getMessage());
        }
    }

    /** 由相对 key 推断 Content-Type（报修图片流式返回用） */
    public String contentType(String key) {
        if (key == null) {
            return "application/octet-stream";
        }
        int dot = key.lastIndexOf('.');
        return ImageHeaderDetector.contentTypeOf(dot < 0 ? null : key.substring(dot + 1));
    }

    private String storeBytes(String category, String extension, byte[] content) {
        String relativeDir = category + "/" + LocalDate.now().format(MONTH);
        String filename = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        Path targetDir = root.resolve(relativeDir).normalize();
        if (!targetDir.startsWith(root)) {
            throw new BusinessException(ErrorCode.FILE_INVALID);
        }
        try {
            Files.createDirectories(targetDir);
            Files.write(targetDir.resolve(filename), content);
        } catch (IOException e) {
            log.error("保存私有文件失败 dir={}", targetDir, e);
            throw new BusinessException(ErrorCode.UNEXPECTED, "文件保存失败，请稍后重试");
        }
        return relativeDir + "/" + filename;
    }

    private String detectImageExtension(MultipartFile file) {
        String extension;
        try (InputStream in = file.getInputStream()) {
            extension = ImageHeaderDetector.detectExtension(in);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.FILE_INVALID);
        }
        if (extension != null) {
            return extension;
        }
        String lower = file.getOriginalFilename() == null
                ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        log.warn("拒绝非图片报修上传 filename={} contentType={}", lower, file.getContentType());
        throw new BusinessException(ErrorCode.FILE_INVALID, "仅支持 JPG/PNG/GIF/WEBP 图片");
    }

    /** key 只允许相对路径，规范化后必须仍在私有根目录内 */
    private Path resolveSafely(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        Path path = root.resolve(key).normalize();
        return path.startsWith(root) ? path : null;
    }
}
