package com.classroom.ai.modules.course.service;

import com.classroom.ai.modules.course.dto.ImportConfirmDTO;
import com.classroom.ai.modules.course.vo.ImportPreviewVO;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;

public interface CourseImportService {

    /**
     * 生成并写入带 UTF-8 BOM 的标准课程导入模板
     */
    void downloadTemplate(OutputStream out) throws IOException;

    /**
     * 上传 CSV 文件并执行第一阶段解析与全维度校验预览 (不入库)
     */
    ImportPreviewVO previewImport(MultipartFile file) throws IOException;

    /**
     * 第二阶段：确认导入批次入库 (整批事务原子写入、操作者防越权、一次性消费)
     */
    Map<String, Object> confirmImport(ImportConfirmDTO dto);
}
