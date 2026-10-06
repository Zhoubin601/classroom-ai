package com.classroom.ai.modules.resource.service;

import com.classroom.ai.config.UploadPaths;
import com.classroom.ai.modules.auth.vo.UserVO;
import com.classroom.ai.modules.auth.context.AuthContext;
import com.classroom.ai.common.exception.ForbiddenException;
import com.classroom.ai.modules.resource.entity.CourseResource;
import com.classroom.ai.modules.resource.entity.ResourceAccessLog;
import com.classroom.ai.modules.resource.repository.ResourceAccessLogRepository;
import com.classroom.ai.modules.resource.repository.CourseResourceRepository;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.util.Matrix;
import java.awt.Font;
import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
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

    @Value("${classroom.resource.watermark-organization:东北大学软件学院}") private String organization = "东北大学软件学院";

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
        UserVO viewer = AuthContext.getCurrentUser();
        if (viewer == null || !Objects.equals(viewer.getId(), ticket.viewerId()))
            throw new ForbiddenException("预览链接仅限申请人使用");
        CourseResource resource = resources.findById(ticket.resourceId()).orElseThrow(() -> new IllegalArgumentException("资源不存在"));
        access.requireRead(resource);
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
                String reference = reference(token);
                String mark = "PREVIEW " + ticket.username().replaceAll("[^A-Za-z0-9_.-]", "_")
                        + " " + LocalDateTime.now().withNano(0) + " READ ONLY " + reference;
                var image = LosslessFactory.createFromImage(document, watermarkImage(viewer));
                document.getDocumentInformation().setCustomMetadataValue("PreviewOrganization", organization);
                document.getDocumentInformation().setCustomMetadataValue("PreviewReference", reference);
                for (var page : document.getPages()) {
                    var box = page.getCropBox();
                    float width = Math.min(box.getWidth() * .75f, 480);
                    try (PDPageContentStream stream = new PDPageContentStream(document, page,
                            PDPageContentStream.AppendMode.APPEND, true, true)) {
                        for (float fraction : new float[] { .25f, .55f, .85f }) {
                            stream.saveGraphicsState();
                            var state = new PDExtendedGraphicsState();
                            state.setNonStrokingAlphaConstant(.22f);
                            stream.setGraphicsStateParameters(state);
                            stream.transform(Matrix.getRotateInstance(Math.toRadians(30),
                                    box.getLowerLeftX() + box.getWidth() / 2,
                                    box.getLowerLeftY() + box.getHeight() * fraction));
                            stream.drawImage(image, -width / 2, 0, width, width / 10);
                            stream.setNonStrokingColor(80, 80, 80);
                            stream.beginText();
                            stream.setFont(PDType1Font.HELVETICA, Math.max(5, width / 65));
                            stream.newLineAtOffset(-width / 2, -10);
                            stream.showText(mark);
                            stream.endText();
                            stream.restoreGraphicsState();
                        }
                    }
                }
                // No password prompt: an empty user password permits rendering, while
                // conforming PDF readers reject copy/print/edit operations.
                var permission = new AccessPermission();
                permission.setCanExtractContent(false);
                permission.setCanPrint(false);
                permission.setCanPrintDegraded(false);
                permission.setCanModify(false);
                permission.setCanModifyAnnotations(false);
                permission.setCanAssembleDocument(false);
                permission.setCanFillInForm(false);
                var protection = new StandardProtectionPolicy(UUID.randomUUID().toString(), "", permission);
                protection.setEncryptionKeyLength(256);
                document.protect(protection);
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

    private BufferedImage watermarkImage(UserVO viewer) {
        var image = new BufferedImage(1800, 180, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setColor(new Color(65, 65, 65));
            graphics.setFont(new Font("SansSerif", Font.BOLD, 48));
            String identity = viewer.getRealName() == null ? viewer.getUsername() : viewer.getRealName();
            String text = organization + " · " + identity + " (" + viewer.getUsername() + ") · 只读预览";
            // Fit long organization/account names without dropping watermark fields.
            int measured = graphics.getFontMetrics().stringWidth(text);
            if (measured > 1740) graphics.setFont(graphics.getFont().deriveFont(Math.max(12f, 48f * 1740 / measured)));
            graphics.drawString(text, 30, 110);
        } finally { graphics.dispose(); }
        return image;
    }

    private String reference(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest).substring(0, 12);
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    public Path download(Long id, UserVO viewer) {
        CourseResource resource = resources.findById(id).orElseThrow(() -> new IllegalArgumentException("资源不存在"));
        access.requireCourseWrite(resource.getCourse().getId());
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
