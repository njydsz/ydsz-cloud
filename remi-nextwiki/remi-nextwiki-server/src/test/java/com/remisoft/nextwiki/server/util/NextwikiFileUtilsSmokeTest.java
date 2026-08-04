package com.remisoft.nextwiki.server.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 最小冒烟级单测，补充 P0 测试覆盖缺口。
 *
 * <p>测试 {@link NextwikiFileUtils} 的文件名处理工具方法：
 * <ul>
 *   <li>{@link NextwikiFileUtils#extractSuffix(String)}: 后缀提取 + 大小写归一</li>
 *   <li>{@link NextwikiFileUtils#sanitizeFileName(String)}: 路径穿越 / 非法字符清洗</li>
 * </ul>
 *
 * <p>纯字符串处理、无外部依赖（DB/Redis/文件系统），直接调用静态方法即可。
 */
class NextwikiFileUtilsSmokeTest {

    @Nested
    @DisplayName("extractSuffix - 后缀提取")
    class ExtractSuffixTests {

        @Test
        @DisplayName("标准文件名提取小写后缀")
        void standardFile_extractsLowercaseSuffix() {
            assertThat(NextwikiFileUtils.extractSuffix("report.pdf")).isEqualTo("pdf");
            assertThat(NextwikiFileUtils.extractSuffix("DATA.XLSX")).isEqualTo("xlsx");
            assertThat(NextwikiFileUtils.extractSuffix("archive.TAR.GZ")).isEqualTo("gz");
        }

        @Test
        @DisplayName("无后缀返回空串")
        void noExtension_returnsEmpty() {
            assertThat(NextwikiFileUtils.extractSuffix("Makefile")).isEmpty();
            assertThat(NextwikiFileUtils.extractSuffix("file.")).isEmpty();
        }

        @Test
        @DisplayName("null / 空串返回空串")
        void nullOrEmpty_returnsEmpty() {
            assertThat(NextwikiFileUtils.extractSuffix(null)).isEmpty();
            assertThat(NextwikiFileUtils.extractSuffix("")).isEmpty();
        }

        @Test
        @DisplayName("隐藏文件 (.gitignore) 视为无后缀")
        void dotfileWithoutExtension_returnsEmpty() {
            assertThat(NextwikiFileUtils.extractSuffix(".gitignore")).isEmpty();
        }

        @Test
        @DisplayName("带路径的文件名仍正确提取后缀")
        void filenameWithPath_extractsSuffix() {
            assertThat(NextwikiFileUtils.extractSuffix("/tmp/uploads/photo.PNG")).isEqualTo("png");
        }
    }

    @Nested
    @DisplayName("sanitizeFileName - 文件名清洗")
    class SanitizeFileNameTests {

        @Test
        @DisplayName("正常文件名保留不变")
        void normalFileName_unchanged() {
            assertThat(NextwikiFileUtils.sanitizeFileName("report.pdf")).isEqualTo("report.pdf");
        }

        @Test
        @DisplayName("null / 空串原样返回")
        void nullOrEmpty_returnsAsIs() {
            assertThat(NextwikiFileUtils.sanitizeFileName(null)).isNull();
            assertThat(NextwikiFileUtils.sanitizeFileName("")).isEmpty();
        }

        @Test
        @DisplayName("路径穿越 ../ 替换为下划线")
        void pathTraversal_replaced() {
            String sanitized = NextwikiFileUtils.sanitizeFileName("../../etc/passwd");
            assertThat(sanitized).doesNotContain("..");
            assertThat(sanitized).doesNotContain("/");
        }

        @Test
        @DisplayName("正斜杠 / 替换为下划线")
        void forwardSlash_replaced() {
            assertThat(NextwikiFileUtils.sanitizeFileName("a/b/c.txt")).isEqualTo("a_b_c.txt");
        }

        @Test
        @DisplayName("反斜杠 \\ 替换为下划线")
        void backslash_replaced() {
            assertThat(NextwikiFileUtils.sanitizeFileName("a\\b.txt")).isEqualTo("a_b.txt");
        }

        @Test
        @DisplayName("非法字符替换为下划线，保留中文、字母、数字、点、下划线、横线")
        void illegalChars_replaced() {
            assertThat(NextwikiFileUtils.sanitizeFileName("report (1).pdf")).isEqualTo("report (1).pdf");
            assertThat(NextwikiFileUtils.sanitizeFileName("my|file.txt")).isEqualTo("my_file.txt");
            assertThat(NextwikiFileUtils.sanitizeFileName("中文文件v2.docx")).isEqualTo("中文文件v2.docx");
        }

        @Test
        @DisplayName("超长文件名被截断至 255 字符以内并保留后缀")
        void veryLongFileName_truncatedWithSuffix() {
            String longBaseName = "a".repeat(300);
            String longName = longBaseName + ".pdf";
            String sanitized = NextwikiFileUtils.sanitizeFileName(longName);
            assertThat(sanitized.length()).isLessThanOrEqualTo(255);
            assertThat(sanitized).endsWith(".pdf");
        }

        @Test
        @DisplayName("清洗后不再含路径穿越风险")
        void sanitizedResult_noPathTraversalRisk() {
            String[] inputs = {"../secret", "/etc/passwd", "..\\windows\\system32", "foo/../../../bar"};
            for (String input : inputs) {
                String sanitized = NextwikiFileUtils.sanitizeFileName(input);
                assertThat(sanitized)
                        .as("input: '%s' should not contain traversal", input)
                        .doesNotContain("..").doesNotContain("/").doesNotContain("\\");
            }
        }
    }

    @Nested
    @DisplayName("suffix classification - 后缀分类集合")
    class SuffixClassificationTests {

        @Test
        @DisplayName("IMAGE_SUFFIXES 包含常见图片格式")
        void imageSuffixes_containsCommonFormats() {
            assertThat(NextwikiFileUtils.IMAGE_SUFFIXES)
                    .contains("jpg", "png", "gif", "webp", "svg");
        }

        @Test
        @DisplayName("TEXT_SUFFIXES 包含文本格式")
        void textSuffixes_containsTextFormats() {
            assertThat(NextwikiFileUtils.TEXT_SUFFIXES)
                    .contains("txt", "md", "csv", "json", "xml");
        }

        @Test
        @DisplayName("OFFICE_SUFFIXES 包含 Office 格式")
        void officeSuffixes_containsOfficeFormats() {
            assertThat(NextwikiFileUtils.OFFICE_SUFFIXES)
                    .contains("doc", "docx", "xls", "xlsx", "ppt", "pptx");
        }
    }
}
