package com.classroom.ai.modules.course.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CsvParserUtilTest {

    @Test
    @DisplayName("测试标准普通 CSV 解析")
    void testStandardCsv() throws IOException {
        String csv = "a,b,c\n1,2,3\n4,5,6";
        List<List<String>> rows = CsvParserUtil.parseCsv(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));
        assertEquals(3, rows.size());
        assertEquals(List.of("a", "b", "c"), rows.get(0));
        assertEquals(List.of("1", "2", "3"), rows.get(1));
        assertEquals(List.of("4", "5", "6"), rows.get(2));
    }

    @Test
    @DisplayName("测试 UTF-8 BOM 自动剥离")
    void testUtf8BomStripping() throws IOException {
        byte[] bom = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] content = "col1,col2\nval1,val2".getBytes(StandardCharsets.UTF_8);
        byte[] withBom = new byte[bom.length + content.length];
        System.arraycopy(bom, 0, withBom, 0, bom.length);
        System.arraycopy(content, 0, withBom, bom.length, content.length);

        List<List<String>> rows = CsvParserUtil.parseCsv(new ByteArrayInputStream(withBom));
        assertEquals(2, rows.size());
        assertEquals("col1", rows.get(0).get(0));
        assertFalse(rows.get(0).get(0).startsWith("\uFEFF"));
    }

    @Test
    @DisplayName("测试双引号包裹包含逗号的字段")
    void testQuotedCommas() throws IOException {
        String csv = "code,name,prereqs\nCS3001,软件工程,\"CS1001,CS2001\"";
        List<List<String>> rows = CsvParserUtil.parseCsv(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));
        assertEquals(2, rows.size());
        assertEquals(3, rows.get(1).size());
        assertEquals("CS1001,CS2001", rows.get(1).get(2));
    }

    @Test
    @DisplayName("测试转义双引号 (两个双引号转化为一个)")
    void testEscapedQuotes() throws IOException {
        String csv = "code,desc\nCS1001,\"Hello \"\"" + "\"World\"\"" + "\"!\"";
        List<List<String>> rows = CsvParserUtil.parseCsv(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));
        assertEquals(2, rows.size());
        assertEquals("Hello \"World\"!", rows.get(1).get(1));
    }

    @Test
    @DisplayName("测试 Windows CRLF 与空行跳过")
    void testCrlfAndBlankLines() throws IOException {
        String csv = "a,b\r\n\r\n1,2\r\n3,4\r\n";
        List<List<String>> rows = CsvParserUtil.parseCsv(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));
        assertEquals(3, rows.size());
        assertEquals(List.of("a", "b"), rows.get(0));
        assertEquals(List.of("1", "2"), rows.get(1));
        assertEquals(List.of("3", "4"), rows.get(2));
    }
}
