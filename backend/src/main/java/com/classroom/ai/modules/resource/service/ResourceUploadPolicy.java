package com.classroom.ai.modules.resource.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.unit.DataSize;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** The same effective file policy is advertised to clients and enforced on every resource upload. */
@Service
@EnableConfigurationProperties(MultipartProperties.class)
public class ResourceUploadPolicy {
    @Value("${classroom.resource.max-file-bytes:104857600}")
    private long configuredMaxFileBytes = 100L * 1024 * 1024;

    @Value("${classroom.resource.allowed-extensions:ppt,pptx,doc,docx,pdf}")
    private String configuredExtensions = "ppt,pptx,doc,docx,pdf";

    @Autowired
    private MultipartProperties multipart = defaultMultipart();

    private static MultipartProperties defaultMultipart() {
        var properties = new MultipartProperties();
        properties.setMaxFileSize(DataSize.ofMegabytes(200));
        properties.setMaxRequestSize(DataSize.ofMegabytes(220));
        return properties;
    }

    public record Policy(long maxFileBytes, List<String> allowedExtensions) {}

    public Policy current() {
        long servletLimit = multipart.getMaxFileSize().toBytes();
        long maximum = Math.max(0, configuredMaxFileBytes);
        if (servletLimit >= 0) maximum = Math.min(maximum, servletLimit);
        // A request can never carry a file larger than its total body ceiling.
        // Servlet parsing still enforces multipart headers and other form fields.
        long requestLimit = multipart.getMaxRequestSize().toBytes();
        if (requestLimit >= 0) maximum = Math.min(maximum, requestLimit);
        var extensions = Arrays.stream(configuredExtensions.split(","))
                .map(ResourceUploadPolicy::normalizeExtension).filter(value -> !value.isEmpty()).distinct().toList();
        return new Policy(maximum, extensions);
    }

    public void validateSize(long bytes) {
        if (bytes < 0) throw new IllegalArgumentException("文件大小不能为负数");
        if (bytes > current().maxFileBytes()) {
            long maximum = current().maxFileBytes();
            String label = maximum > 0 && maximum % (1024 * 1024) == 0
                    ? maximum / (1024 * 1024) + "MB" : maximum + " 字节";
            throw new IllegalArgumentException("上传失败：单文件大小不能超过 " + label + " 限制");
        }
    }

    public String validateExtension(String extension) {
        String normalized = normalizeExtension(extension);
        if (!current().allowedExtensions().contains(normalized))
            throw new IllegalArgumentException("不支持的文件格式，允许: " + String.join(",", current().allowedExtensions()));
        return normalized;
    }

    public String extensionOf(String filename) {
        if (filename == null) return "";
        String clean = filename.trim();
        int dot = clean.lastIndexOf('.');
        return dot < 0 ? "" : normalizeExtension(clean.substring(dot + 1));
    }

    private static String normalizeExtension(String extension) {
        String clean = extension == null ? "" : extension.trim().toLowerCase(Locale.ROOT);
        return clean.startsWith(".") ? clean.substring(1) : clean;
    }
}
