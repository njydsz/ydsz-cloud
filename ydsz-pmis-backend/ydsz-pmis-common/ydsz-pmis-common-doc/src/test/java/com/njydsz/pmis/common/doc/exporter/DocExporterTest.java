package com.njydsz.pmis.common.doc.exporter;

import com.njydsz.pmis.common.doc.config.DocProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 文档导出器单元测试
 *
 * @author Marvin Lee
 * @version 4.0.0
 */
@DisplayName("DocExporter - API 文档导出器测试")
class DocExporterTest {

    private static final String OPENAPI_JSON = """
            {
              "openapi": "3.0.0",
              "info": {
                "title": "测试 API",
                "description": "用于测试的 API 文档",
                "version": "2.0.0"
              },
              "servers": [{"url": "http://localhost:8080", "description": "本地服务"}],
              "paths": {
                "/users": {
                  "get": {
                    "summary": "查询用户列表",
                    "parameters": [
                      {"name": "page", "in": "query", "schema": {"type": "integer"}, "required": true}
                    ],
                    "responses": {
                      "200": {"description": "成功"}
                    }
                  }
                }
              },
              "components": {
                "schemas": {
                  "User": {
                    "type": "object",
                    "properties": {
                      "id": {"type": "integer"},
                      "name": {"type": "string"}
                    }
                  }
                }
              }
            }
            """;

    private DocProperties docProperties;

    @BeforeEach
    void setUp() {
        docProperties = new DocProperties();
        docProperties.getInfo().setTitle("默认标题");
        docProperties.getInfo().setDescription("默认描述");
        docProperties.getInfo().setVersion("1.0.0");
    }

    // ==================== DefaultDocExporter ====================

    @Test
    @DisplayName("DefaultDocExporter 导出 JSON")
    void shouldExportJsonWithDefaultExporter(@TempDir Path tempDir) throws IOException {
        DefaultDocExporter exporter = new DefaultDocExporter(docProperties);
        File file = exporter.exportToJson(OPENAPI_JSON, tempDir.toString());

        assertTrue(file.exists());
        assertEquals("api-documentation.json", file.getName());
        String content = Files.readString(file.toPath());
        assertTrue(content.contains("\"openapi\": \"3.0.0\""));
    }

    @Test
    @DisplayName("DefaultDocExporter 导出 HTML")
    void shouldExportHtmlWithDefaultExporter(@TempDir Path tempDir) throws IOException {
        DefaultDocExporter exporter = new DefaultDocExporter(docProperties);
        File file = exporter.exportToHtml(OPENAPI_JSON, tempDir.toString());

        assertTrue(file.exists());
        assertEquals("api-documentation.html", file.getName());
        String content = Files.readString(file.toPath());
        assertTrue(content.contains("<html"));
        assertTrue(content.contains("测试 API"));
        assertTrue(content.contains("2.0.0"));
    }

    @Test
    @DisplayName("DefaultDocExporter 导出 Markdown")
    void shouldExportMarkdownWithDefaultExporter(@TempDir Path tempDir) throws IOException {
        DefaultDocExporter exporter = new DefaultDocExporter(docProperties);
        File file = exporter.exportToMarkdown(OPENAPI_JSON, tempDir.toString());

        assertTrue(file.exists());
        assertEquals("api-documentation.md", file.getName());
        String content = Files.readString(file.toPath());
        assertTrue(content.contains("# 测试 API"));
        assertTrue(content.contains("```json"));
    }

    @Test
    @DisplayName("DefaultDocExporter 导出 YAML")
    void shouldExportYamlWithDefaultExporter(@TempDir Path tempDir) throws IOException {
        DefaultDocExporter exporter = new DefaultDocExporter(docProperties);
        File file = exporter.exportToYaml(OPENAPI_JSON, tempDir.toString());

        assertTrue(file.exists());
        assertEquals("api-documentation.yaml", file.getName());
        String content = Files.readString(file.toPath());
        assertTrue(content.contains("openapi:"));
        assertTrue(content.contains("info:"));
    }

    @Test
    @DisplayName("DefaultDocExporter 根据格式分发导出")
    void shouldDispatchExportByFormat(@TempDir Path tempDir) throws IOException {
        DefaultDocExporter exporter = new DefaultDocExporter(docProperties);

        assertEquals("api-documentation.json", exporter.export(OPENAPI_JSON, tempDir.toString(), "json").getName());
        assertEquals("api-documentation.yaml", exporter.export(OPENAPI_JSON, tempDir.toString(), "yml").getName());
        assertEquals("api-documentation.md", exporter.export(OPENAPI_JSON, tempDir.toString(), "md").getName());
        assertEquals("api-documentation.html", exporter.export(OPENAPI_JSON, tempDir.toString(), "html").getName());
    }

    // ==================== MarkdownDocExporter ====================

    @Test
    @DisplayName("MarkdownDocExporter 导出结构化的 Markdown")
    void shouldExportRichMarkdown(@TempDir Path tempDir) throws IOException {
        MarkdownDocExporter exporter = new MarkdownDocExporter(docProperties);
        File file = exporter.exportToMarkdown(OPENAPI_JSON, tempDir.toString());

        assertTrue(file.exists());
        String content = Files.readString(file.toPath());
        assertTrue(content.contains("# 测试 API"));
        assertTrue(content.contains("## API 接口列表"));
        assertTrue(content.contains("`GET` /users"));
        assertTrue(content.contains("## 数据模型"));
    }

    @Test
    @DisplayName("MarkdownDocExporter 导出 HTML 包含样式")
    void shouldExportHtmlWithMarkdownExporter(@TempDir Path tempDir) throws IOException {
        MarkdownDocExporter exporter = new MarkdownDocExporter(docProperties);
        File file = exporter.exportToHtml(OPENAPI_JSON, tempDir.toString());

        assertTrue(file.exists());
        String content = Files.readString(file.toPath());
        assertTrue(content.contains("<html"));
        assertTrue(content.contains("测试 API"));
    }

    // ==================== 公共行为 ====================

    @Test
    @DisplayName("导出器支持格式判断")
    void shouldCheckSupportedFormats() {
        DefaultDocExporter exporter = new DefaultDocExporter(docProperties);

        assertTrue(exporter.isSupportedFormat("json"));
        assertTrue(exporter.isSupportedFormat("JSON"));
        assertTrue(exporter.isSupportedFormat("yaml"));
        assertTrue(exporter.isSupportedFormat("yml"));
        assertTrue(exporter.isSupportedFormat("markdown"));
        assertTrue(exporter.isSupportedFormat("md"));
        assertTrue(exporter.isSupportedFormat("html"));

        assertFalse(exporter.isSupportedFormat("pdf"));
        assertFalse(exporter.isSupportedFormat(null));
    }

    @Test
    @DisplayName("导出器获取支持格式列表")
    void shouldReturnSupportedFormats() {
        DefaultDocExporter exporter = new DefaultDocExporter(docProperties);
        String[] formats = exporter.getSupportedFormats();

        assertEquals(4, formats.length);
        assertArrayEquals(new String[]{"html", "markdown", "yaml", "json"}, formats);
    }

    @Test
    @DisplayName("不支持的格式抛出 IllegalArgumentException")
    void shouldThrowExceptionForUnsupportedFormat(@TempDir Path tempDir) {
        DefaultDocExporter exporter = new DefaultDocExporter(docProperties);

        assertThrows(IllegalArgumentException.class,
                () -> exporter.export(OPENAPI_JSON, tempDir.toString(), "pdf"));
    }

    @Test
    @DisplayName("空格式抛出 IllegalArgumentException")
    void shouldThrowExceptionForEmptyFormat(@TempDir Path tempDir) {
        DefaultDocExporter exporter = new DefaultDocExporter(docProperties);

        assertThrows(IllegalArgumentException.class,
                () -> exporter.export(OPENAPI_JSON, tempDir.toString(), ""));
    }

    @Test
    @DisplayName("导出目录不存在时自动创建")
    void shouldCreateOutputDirectoryWhenNotExists(@TempDir Path tempDir) throws IOException {
        Path nestedDir = tempDir.resolve("nested").resolve("docs");
        DefaultDocExporter exporter = new DefaultDocExporter(docProperties);

        File file = exporter.exportToJson(OPENAPI_JSON, nestedDir.toString());

        assertTrue(nestedDir.toFile().exists());
        assertTrue(file.exists());
    }
}
