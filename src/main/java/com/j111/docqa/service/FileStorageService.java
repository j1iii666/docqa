package com.j111.docqa.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.j111.docqa.common.BizException;
import com.j111.docqa.common.ResultCode;
import com.j111.docqa.parser.TextExtractor;

import jakarta.annotation.PostConstruct;

/**
 * 文件落盘。
 * <p>
 * 两条原则：
 * <ol>
 *   <li>用 UUID 做文件名，原始名只存数据库 —— 否则用户传个 "报告.pdf" 或者同名文件就互相覆盖</li>
 *   <li>目录在启动时就建好，别等到第一次上传才发现没权限</li>
 * </ol>
 */
@Service
public class FileStorageService {

    private final Path uploadDir;
    private final Path textDir;

    public FileStorageService(@Value("${docqa.upload.dir}") String uploadDir,
                              @Value("${docqa.upload.text-dir}") String textDir) {
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.textDir = Paths.get(textDir).toAbsolutePath().normalize();
    }

    @PostConstruct
    void init() throws IOException {
        Files.createDirectories(uploadDir);
        Files.createDirectories(textDir);
    }

    /**
     * 保存上传的文件，返回落盘的绝对路径。
     */
    public Path save(MultipartFile file) {
        String originalName = file.getOriginalFilename();
        String ext = TextExtractor.extensionOf(originalName);
        if (!TextExtractor.SUPPORTED.contains(ext)) {
            throw new BizException(ResultCode.UNSUPPORTED_FILE);
        }
        if (file.isEmpty()) {
            throw new BizException(ResultCode.EMPTY_FILE);
        }

        String storedName = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        Path target = uploadDir.resolve(storedName);
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "保存文件失败: " + e.getMessage());
        }
        return target;
    }

    /** 把解析出的纯文本写盘，返回路径 */
    public Path saveText(long documentId, String text) {
        Path target = textDir.resolve(documentId + ".txt");
        try {
            Files.writeString(target, text);
        } catch (IOException e) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "保存解析文本失败: " + e.getMessage());
        }
        return target;
    }

    /** 读回解析文本（给预览接口用），文件不存在返回 null */
    public String readText(String textPath) {
        if (textPath == null) {
            return null;
        }
        Path path = Paths.get(textPath);
        if (!Files.isReadable(path)) {
            return null;
        }
        try {
            return Files.readString(path);
        } catch (IOException e) {
            return null;
        }
    }

    public Path getUploadDir() {
        return uploadDir;
    }

    public Path getTextDir() {
        return textDir;
    }
}
