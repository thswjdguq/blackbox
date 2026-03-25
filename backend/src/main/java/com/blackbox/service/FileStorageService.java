package com.blackbox.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class FileStorageService {

    @Value("${app.file.upload-dir:/data/uploads}")
    private String baseDir;

    /**
     * 파일을 /{baseDir}/{projectId}/{timestamp}_{originalName} 경로에 저장.
     * @return 저장된 절대 경로 문자열
     */
    public String store(MultipartFile file, UUID projectId) throws IOException {
        Path dir = Paths.get(baseDir, projectId.toString());
        Files.createDirectories(dir);

        String safeName = sanitize(file.getOriginalFilename());
        String storedName = System.currentTimeMillis() + "_" + safeName;
        Path dest = dir.resolve(storedName);
        Files.copy(file.getInputStream(), dest, StandardCopyOption.REPLACE_EXISTING);
        return dest.toAbsolutePath().toString();
    }

    /** 저장된 경로에서 다운로드용 Resource 반환. */
    public Resource load(String storagePath) {
        Resource resource = new FileSystemResource(storagePath);
        if (!resource.exists() || !resource.isReadable()) {
            throw new IllegalStateException("파일을 읽을 수 없습니다: " + storagePath);
        }
        return resource;
    }

    private String sanitize(String name) {
        if (name == null || name.isBlank()) return "unknown";
        // 경로 구분자 제거
        return Paths.get(name).getFileName().toString()
                .replaceAll("[^a-zA-Z0-9._\\-가-힣]", "_");
    }
}
