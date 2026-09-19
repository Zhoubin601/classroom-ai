package com.classroom.ai.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Value("${classroom.upload-dir:}")
    private String uploadDir;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }


    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 映射本地上传的头像/人脸切片图片静态资源
        String facePath = UploadPaths.resolve(uploadDir).toUri().toString();
        if (!facePath.endsWith("/")) facePath += "/";
        registry.addResourceHandler("/uploads/faces/**")
                .addResourceLocations(facePath);

        // 映射本地教学资源（真实 PPT/DOCX/PDF 课件教案）
        String resourcePath = UploadPaths.resolveResources(uploadDir).toUri().toString();
        if (!resourcePath.endsWith("/")) resourcePath += "/";
        registry.addResourceHandler("/uploads/resources/**")
                .addResourceLocations(resourcePath);
    }
}
