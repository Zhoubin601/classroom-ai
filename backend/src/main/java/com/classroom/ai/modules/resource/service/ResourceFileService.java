package com.classroom.ai.modules.resource.service;

import com.classroom.ai.config.UploadPaths;
import com.classroom.ai.modules.auth.vo.UserVO;
import com.classroom.ai.modules.resource.entity.CourseResource;
import com.classroom.ai.modules.resource.entity.ResourceAccessLog;
import com.classroom.ai.modules.resource.repository.ResourceAccessLogRepository;
import com.classroom.ai.modules.resource.repository.CourseResourceRepository;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class ResourceFileService {
    private final CourseResourceRepository resources;
    private final ResourceAccessLogRepository logs;
    private final ResourceAccessService access;
    private final Map<String, Ticket> tickets = new ConcurrentHashMap<>();

    @Value("${classroom.upload-dir:}") private String uploadDir;
    @Value("${classroom.resource.preview-minutes:5}") private long previewMinutes;
    @Value("${classroom.resource.soffice:soffice}") private String soffice;

    private record Ticket(Long resourceId, Long viewerId, String username, LocalDateTime expiresAt) {}

    public String createTicket(Long id, UserVO viewer) {
        CourseResource resource = resources.findById(id).orElseThrow(() -> new IllegalArgumentException("资源不存在"));
        access.requireRead(resource);
        tickets.entrySet().removeIf(e -> e.getValue().expiresAt().isBefore(LocalDateTime.now()));
        String token = UUID.randomUUID().toString() + UUID.randomUUID().toString();
        tickets.put(token, new Ticket(id, viewer.getId(), viewer.getUsername(), LocalDateTime.now().plusMinutes(previewMinutes)));
        return token;
    }

    public byte[] preview(String token) throws IOException {
        Ticket ticket = tickets.get(token);
        if (ticket == null || !LocalDateTime.now().isBefore(ticket.expiresAt())) {
            tickets.remove(token);
            throw new IllegalArgumentException("预览链接已过期，请重新申请");
        }
        CourseResource resource = resources.findById(ticket.resourceId()).orElseThrow(() -> new IllegalArgumentException("资源不存在"));
        Path file = resolveStored(resource);
        Path temp = Files.createTempDirectory("classroom-preview-");
        try {
            Path pdf;
            if (file.getFileName().toString().toLowerCase().endsWith(".pdf")) {
                pdf = file;
            } else {
                Process process;
                try {
                    process = new ProcessBuilder(soffice, "-env:UserInstallation=" + temp.resolve("profile").toUri(),
                            "--headless", "--convert-to", "pdf", "--outdir", temp.toString(), file.toString())
                            .redirectErrorStream(true).redirectOutput(temp.resolve("conversion.log").toFile()).start();
                } catch (IOException ex) {
                    throw new IOException("预览转换服务不可用", ex);
                }
                boolean finished;
                try { finished = process.waitFor(90, TimeUnit.SECONDS); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException("预览转换被中断", e); }
                if (!finished) { process.destroyForcibly(); throw new IOException("预览转换超时"); }
                pdf = temp.resolve(file.getFileName().toString().replaceFirst("(?i)\\.[^.]+$", ".pdf"));
                if (process.exitValue() != 0 || !Files.isRegularFile(pdf)) throw new IOException("文件转换失败，请检查文件是否损坏");
            }
            try (PDDocument document = PDDocument.load(pdf.toFile()); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                String mark = "PREVIEW " + ticket.username().replaceAll("[^A-Za-z0-9_.-]", "_") + " " + LocalDateTime.now();
                for (var page : document.getPages()) {
                    try (PDPageContentStream stream = new PDPageContentStream(document, page,
                            PDPageContentStream.AppendMode.APPEND, true, true)) {
                        stream.setNonStrokingColor(190, 190, 190);
                        stream.beginText();
                        stream.setFont(PDType1Font.HELVETICA_BOLD, 17);
                        stream.newLineAtOffset(36, page.getMediaBox().getHeight() / 2);
                        stream.showText(mark);
                        stream.endText();
                    }
                }
                document.save(out);
                audit(resource.getId(), ticket.viewerId(), ticket.username(), "PREVIEW");
                return out.toByteArray();
            }
        } finally {
            try (var paths = Files.walk(temp)) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
            }
        }
    }

    public Path download(Long id, UserVO viewer) {
        CourseResource resource = resources.findById(id).orElseThrow(() -> new IllegalArgumentException("资源不存在"));
        access.requireRead(resource);
        Path path = resolveStored(resource);
        audit(id, viewer.getId(), viewer.getUsername(), "DOWNLOAD");
        return path;
    }

    private Path resolveStored(CourseResource resource) {
        String url = resource.getFileUrl();
        String prefix = "/uploads/resources/";
        if (url == null || !url.startsWith(prefix)) throw new IllegalArgumentException("该资源没有可用的本地文件");
        String name = url.substring(prefix.length());
        if (name.contains("/") || name.contains("\\") || name.equals(".") || name.equals(".."))
            throw new IllegalArgumentException("资源路径无效");
        Path root = UploadPaths.resolveResources(uploadDir).toAbsolutePath().normalize();
        Path path = root.resolve(name).normalize();
        if (!path.startsWith(root) || !Files.isRegularFile(path)) throw new IllegalArgumentException("资源文件不存在");
        return path;
    }

    private void audit(Long id, Long viewerId, String username, String action) {
        ResourceAccessLog log = new ResourceAccessLog();
        log.setResourceId(id);
        log.setViewerId(viewerId);
        log.setViewerUsername(username);
        log.setAction(action);
        log.setAccessedAt(LocalDateTime.now());
        logs.save(log);
    }
}
