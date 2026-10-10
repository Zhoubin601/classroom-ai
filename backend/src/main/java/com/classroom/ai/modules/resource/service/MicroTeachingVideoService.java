package com.classroom.ai.modules.resource.service;

import com.classroom.ai.config.UploadPaths;
import com.classroom.ai.modules.course.repository.CourseRepository;
import com.classroom.ai.modules.course.service.CourseAuthorizationService;
import com.classroom.ai.modules.resource.entity.MicroTeachingSlice;
import com.classroom.ai.modules.resource.repository.MicroTeachingSliceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Local micro videos are served only after checking the slice's real course. */
@Service
@RequiredArgsConstructor
public class MicroTeachingVideoService {
    private final MicroTeachingSliceRepository slices;
    private final CourseRepository courses;
    private final CourseAuthorizationService authorization;
    @Value("${classroom.upload-dir:}") private String uploadDir;
    private Path directory() { return UploadPaths.resolve(uploadDir).resolveSibling("micro").toAbsolutePath().normalize(); }

    @Transactional
    public MicroTeachingSlice upload(Long courseId, String title, String stage, Integer duration, MultipartFile file) throws IOException {
        var course = courses.findById(courseId).orElseThrow(() -> new IllegalArgumentException("课程不存在"));
        authorization.validateCourseWrite(course);
        if (title == null || title.isBlank() || title.length() > 255) throw new IllegalArgumentException("请填写视频标题（最多255字）");
        if (duration == null || duration <= 0) throw new IllegalArgumentException("视频时长必须大于0秒");
        if (stage == null || !List.of("B", "O", "P1", "P2", "P3", "S").contains(stage)) throw new IllegalArgumentException("请选择教学环节");
        if (file.isEmpty() || file.getSize() > 104857600) throw new IllegalArgumentException("视频不能为空且不能超过100MB");
        String original = Optional.ofNullable(file.getOriginalFilename()).orElse("").toLowerCase(Locale.ROOT);
        String extension = original.endsWith(".mp4") ? "mp4" : original.endsWith(".webm") ? "webm" : "";
        if (extension.isEmpty()) throw new IllegalArgumentException("仅支持MP4或WebM视频");
        byte[] signature;
        try (var input = file.getInputStream()) { signature = input.readNBytes(16); }
        boolean valid = extension.equals("mp4") ? signature.length >= 12 && new String(signature, 4, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("ftyp")
                : signature.length >= 4 && (signature[0] & 255) == 0x1a && (signature[1] & 255) == 0x45 && (signature[2] & 255) == 0xdf && (signature[3] & 255) == 0xa3;
        if (!valid) throw new IllegalArgumentException("文件内容不是有效的视频容器");
        Files.createDirectories(directory());
        String name = UUID.randomUUID() + "." + extension;
        Path target = directory().resolve(name);
        try {
            file.transferTo(target);
            return slices.save(MicroTeachingSlice.builder().course(course).videoTitle(title.trim()).bopppsStage(stage)
                    .durationSeconds(duration).sliceUrl("/uploads/micro/" + name).sourceAgent("Platform-Upload").build());
        } catch (IOException | RuntimeException e) { Files.deleteIfExists(target); throw e; }
    }

    @Transactional(readOnly = true)
    public ResponseEntity<FileSystemResource> video(Long id) throws IOException {
        var slice = slices.findById(id).orElseThrow(() -> new IllegalArgumentException("微格切片不存在"));
        authorization.validateCourseRead(slice.getCourse().getId());
        String url = slice.getSliceUrl();
        if (url == null || !url.matches("/uploads/micro/[a-f0-9-]{36}\\.(mp4|webm)")) throw new IllegalArgumentException("该切片使用外部视频地址，请通过外部播放器读取");
        Path file = directory().resolve(url.substring("/uploads/micro/".length())).normalize();
        if (!file.startsWith(directory()) || !Files.isRegularFile(file)) throw new IllegalArgumentException("视频文件不存在");
        String mime = url.endsWith(".webm") ? "video/webm" : "video/mp4";
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(mime)).contentLength(Files.size(file))
                .header(HttpHeaders.CACHE_CONTROL, "no-store").header("X-Content-Type-Options", "nosniff")
                .body(new FileSystemResource(file));
    }
}
