package com.classroom.ai.modules.course.util;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 符合 RFC 4180 规范的 CSV 流式解析器
 * 核心特性：
 * 1. 自动剥离 UTF-8 BOM (\uFEFF)；
 * 2. 支持双引号包裹字段，字段内可安全包含英文逗号 (,) 与换行符；
 * 3. 支持转义双引号 ("" 转为 ")；
 * 4. 支持 Windows (CRLF) 与 Unix/Linux (LF) 格式换行。
 */
public class CsvParserUtil {

    public static List<List<String>> parseCsv(InputStream in) throws IOException {
        return parseCsv(in, StandardCharsets.UTF_8);
    }

    public static List<List<String>> parseCsv(InputStream in, Charset charset) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, charset));
        List<List<String>> records = new ArrayList<>();
        List<String> currentRecord = new ArrayList<>();
        StringBuilder currentField = new StringBuilder();

        boolean inQuotes = false;
        boolean hasStarted = false;

        int ch;

        while ((ch = reader.read()) != -1) {
            // 剥离首个字符可能出现的 UTF-8 BOM
            if (!hasStarted) {
                hasStarted = true;
                if (ch == '\uFEFF') {
                    continue;
                }
            }

            if (inQuotes) {
                if (ch == '"') {
                    // 查看下一个字符是否也是双引号 (转义 "")
                    reader.mark(1);
                    int next = reader.read();
                    if (next == '"') {
                        currentField.append('"');
                    } else {
                        reader.reset();
                        inQuotes = false;
                    }
                } else {
                    currentField.append((char) ch);
                }
            } else {
                if (ch == '"') {
                    inQuotes = true;
                } else if (ch == ',') {
                    currentRecord.add(currentField.toString());
                    currentField.setLength(0);
                } else if (ch == '\r') {
                    currentRecord.add(currentField.toString());
                    currentField.setLength(0);
                    if (!isRecordBlank(currentRecord)) {
                        records.add(new ArrayList<>(currentRecord));
                    }
                    currentRecord.clear();

                    reader.mark(1);
                    int next = reader.read();
                    if (next != '\n') {
                        reader.reset();
                    }
                } else if (ch == '\n') {
                    currentRecord.add(currentField.toString());
                    currentField.setLength(0);
                    if (!isRecordBlank(currentRecord)) {
                        records.add(new ArrayList<>(currentRecord));
                    }
                    currentRecord.clear();
                } else {
                    currentField.append((char) ch);
                }
            }
        }

        // 文件末尾残留内容处理
        if (currentField.length() > 0 || !currentRecord.isEmpty()) {
            currentRecord.add(currentField.toString());
            if (!isRecordBlank(currentRecord)) {
                records.add(currentRecord);
            }
        }

        return records;
    }

    private static boolean isRecordBlank(List<String> record) {
        if (record == null || record.isEmpty()) return true;
        for (String field : record) {
            if (field != null && !field.trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }
}
