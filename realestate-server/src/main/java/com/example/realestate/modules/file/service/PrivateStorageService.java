package com.example.realestate.modules.file.service;

import com.example.realestate.common.enums.ErrorCode;
import com.example.realestate.common.exception.BusinessException;
import com.example.realestate.modules.file.config.StorageProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 私有文件存储（合同 PDF）：落盘在 app.storage.private-root，不注册静态映射，
 * 只能通过受鉴权的 Controller 读取。数据库只保存相对 key，不保存可公开访问的 URL。
 */
@Component
public class PrivateStorageService {

    private static final Logger log = LoggerFactory.getLogger(PrivateStorageService.class);
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyyMM");

    private final Path root;

    public PrivateStorageService(StorageProperties properties) {
        this.root = Paths.get(properties.privateRootOrDefault()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new IllegalStateException("无法创建私有存储目录: " + root, e);
        }
        log.info("合同 PDF 私有存储已启用 root={}", root);
    }

    /**
     * 保存 PDF 并返回相对 key（contract/yyyyMM/uuid.pdf）。
     * 文件名由服务端生成，不接受客户端输入，避免目录穿越与同名覆盖。
     */
    public String storePdf(byte[] content) {
        if (content == null || content.length == 0) {
            throw new BusinessException(ErrorCode.UNEXPECTED, "PDF 内容为空");
        }
        String relativeDir = "contract/" + LocalDate.now().format(MONTH);
        String filename = UUID.randomUUID().toString().replace("-", "") + ".pdf";
        Path targetDir = root.resolve(relativeDir).normalize();
        if (!targetDir.startsWith(root)) {
            throw new BusinessException(ErrorCode.FILE_INVALID);
        }
        try {
            Files.createDirectories(targetDir);
            Files.write(targetDir.resolve(filename), content);
        } catch (IOException e) {
            log.error("保存合同 PDF 失败 dir={}", targetDir, e);
            throw new BusinessException(ErrorCode.UNEXPECTED, "合同 PDF 保存失败，请稍后重试");
        }
        return relativeDir + "/" + filename;
    }

    public boolean exists(String key) {
        return resolveSafely(key) != null && Files.isRegularFile(resolveSafely(key));
    }

    public byte[] read(String key) {
        Path path = resolveSafely(key);
        if (path == null || !Files.isRegularFile(path)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "合同 PDF 不存在");
        }
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            log.error("读取合同 PDF 失败 key={}", key, e);
            throw new BusinessException(ErrorCode.UNEXPECTED, "合同 PDF 读取失败");
        }
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
