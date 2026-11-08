package com.example.realestate.modules.file.service.impl;

import com.example.realestate.common.enums.ErrorCode;
import com.example.realestate.common.exception.BusinessException;
import com.example.realestate.modules.file.config.StorageProperties;
import com.example.realestate.modules.file.service.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 本地磁盘存储（dev）。
 * 安全约束：扩展名由文件头推断而非原始文件名；仅允许图片；大小由配置限制；
 * 文件名使用 UUID，目录按 业务/年月 划分，避免同名覆盖与目录穿越。
 */
@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalStorageService implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(LocalStorageService.class);
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyyMM");
    private static final Set<String> ALLOWED_CATEGORIES = Set.of("house", "owner", "repair", "contract");

    private final StorageProperties properties;
    private final Path root;

    public LocalStorageService(StorageProperties properties) {
        this.properties = properties;
        this.root = Paths.get(properties.localRootOrDefault()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new IllegalStateException("无法创建上传目录: " + root, e);
        }
        log.info("本地媒体存储已启用 root={} publicBase={}", root, properties.publicBaseUrlOrDefault());
    }

    @Override
    public StoredFile store(MultipartFile file, String category) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.FILE_INVALID, "请选择要上传的文件");
        }
        if (file.getSize() > properties.maxImageSizeBytesOrDefault()) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE,
                    "图片大小不能超过 " + (properties.maxImageSizeBytesOrDefault() / 1024 / 1024) + "MB");
        }
        String ext = detectImageExtension(file);
        String safeCategory = category != null && ALLOWED_CATEGORIES.contains(category) ? category : "house";
        String relativeDir = safeCategory + "/" + LocalDate.now().format(MONTH);
        String filename = UUID.randomUUID().toString().replace("-", "") + "." + ext;

        Path targetDir = root.resolve(relativeDir).normalize();
        if (!targetDir.startsWith(root)) {
            throw new BusinessException(ErrorCode.FILE_INVALID);
        }
        try {
            Files.createDirectories(targetDir);
            file.transferTo(targetDir.resolve(filename));
        } catch (IOException e) {
            log.error("保存上传文件失败 dir={}", targetDir, e);
            throw new BusinessException(ErrorCode.UNEXPECTED, "文件保存失败，请稍后重试");
        }
        String url = properties.publicBaseUrlOrDefault() + "/" + relativeDir + "/" + filename;
        return new StoredFile(url, file.getContentType(), file.getSize());
    }

    @Override
    public void delete(String url) {
        String base = properties.publicBaseUrlOrDefault();
        if (url == null || !url.startsWith(base + "/")) {
            return;
        }
        Path target = root.resolve(url.substring(base.length() + 1)).normalize();
        if (!target.startsWith(root)) {
            return;
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            log.warn("删除本地媒体失败 url={}: {}", url, e.getMessage());
        }
    }

    /** 依据文件头判断真实图片类型，客户端声明的 Content-Type 与扩展名一律不信任 */
    private String detectImageExtension(MultipartFile file) {
        byte[] header = new byte[12];
        int read;
        try (InputStream in = file.getInputStream()) {
            read = in.readNBytes(header, 0, header.length);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.FILE_INVALID);
        }
        if (read >= 3 && (header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        if (read >= 8 && (header[0] & 0xFF) == 0x89 && header[1] == 'P' && header[2] == 'N' && header[3] == 'G') {
            return "png";
        }
        if (read >= 6 && header[0] == 'G' && header[1] == 'I' && header[2] == 'F' && header[3] == '8') {
            return "gif";
        }
        if (read >= 12 && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P') {
            return "webp";
        }
        String lower = file.getOriginalFilename() == null
                ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        log.warn("拒绝非图片上传 filename={} contentType={}", lower, file.getContentType());
        throw new BusinessException(ErrorCode.FILE_INVALID, "仅支持 JPG/PNG/GIF/WEBP 图片");
    }
}
