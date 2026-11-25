package com.blackbox.agent.draft;

import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** 서버가 만든 파일을 화면에서 올린 파일과 같은 길(FileVaultService.upload)로 파일 금고에 넣기 위한 감싸개 */
public record GeneratedFile(String fileName, String contentType, byte[] bytes) implements MultipartFile {
    public static final String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    @Override public String getName() { return "file"; }
    @Override public String getOriginalFilename() { return fileName; }
    @Override public String getContentType() { return contentType; }
    @Override public boolean isEmpty() { return bytes.length == 0; }
    @Override public long getSize() { return bytes.length; }
    @Override public byte[] getBytes() { return bytes.clone(); }
    @Override public InputStream getInputStream() { return new ByteArrayInputStream(bytes); }
    @Override public void transferTo(java.io.File dest) throws java.io.IOException { Files.write(dest.toPath(), bytes); }
    @Override public void transferTo(Path dest) throws java.io.IOException { Files.write(dest, bytes); }
}
