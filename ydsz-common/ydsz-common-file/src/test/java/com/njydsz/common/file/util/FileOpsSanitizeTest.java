package com.njydsz.common.file.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link FileOps#sanitizeFileName(String)} 与 {@link FileOps#extractSuffix(String)} 的行为钉住测试。
 *
 * <p>本类存在的理由：AGENTS.md 的 SOP-6 把 {@code FileOps.sanitizeFileName} 定为**全平台文件上传的强制入口**（禁止业务侧自实现
 * {@code replace("/", "_")}），而 {@code ydsz-common-file} 此前零测试 —— 安全入口的安全属性从未被验证过。
 *
 * <p>钉住的三类不变量：
 *
 * <ol>
 *   <li>输出不含 {@code / }{@code \ }{@code ..}，即不可能穿越到 {@code namespace} 目录之外；
 *   <li>输出长度恒 ≤255，且**任何**输入都不抛异常（入参 100% 来自 {@code MultipartFile#getOriginalFilename()}，攻击者可控）；
 *   <li>净化只做"逐字符替换"不做"删除"，因此替换本身不会把两个点拼成新的 {@code ..}。
 * </ol>
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@DisplayName("FileOps 文件名净化（上传安全入口）")
class FileOpsSanitizeTest {

  private static String repeat(char c, int times) {
    return IntStream.range(0, times).mapToObj(i -> String.valueOf(c)).collect(Collectors.joining());
  }

  @Nested
  @DisplayName("路径穿越")
  class Traversal {

    @Test
    @DisplayName("斜杠与上级目录全部替换，与 Javadoc 示例一致")
    void should_neutralize_slash_and_dotdot() {
      assertThat(FileOps.sanitizeFileName("../../etc/passwd")).isEqualTo("____etc_passwd");
      assertThat(FileOps.sanitizeFileName("..\\..\\windows\\system32\\cmd.exe"))
          .doesNotContain("..")
          .doesNotContain("\\")
          .doesNotContain("/");
    }

    @Test
    @DisplayName("净化结果作为 path segment 解析后不发生逃逸")
    void should_stay_inside_given_directory() {
      String[] payloads = {
        "../../../etc/shadow",
        "....//....//etc/shadow",
        "..%2f..%2fetc%2fshadow",
        "\u0000\u0000../etc/shadow",
        "/absolute/etc/passwd",
        "a/../../b.txt",
      };
      Path base = Path.of("uploads").toAbsolutePath().normalize();
      for (String payload : payloads) {
        String safe = FileOps.sanitizeFileName(payload);
        assertThat(safe).doesNotContain("..").doesNotContain("/").doesNotContain("\\");
        // 纯路径比较：AssertJ 的 PathAssert#startsWith 会先 toRealPath()，
        // 对"文件本不该存在"的安全名反而制造 NoSuchFileException，故用 java.nio 的比较。
        assertThat(base.resolve(safe).normalize().startsWith(base))
            .as("净化后仍逃逸出 %s：%s", base, safe)
            .isTrue();
      }
    }

    @Test
    @DisplayName("非法字符被替换而非删除，不会把两个点拼成新的 ..")
    void should_not_create_dotdot_by_deleting_chars() {
      // 若净化采用"删除非法字符"实现，下面两个输入会先变成 ".."，再被后续逻辑放过
      assertThat(FileOps.sanitizeFileName(".\u0000.")).doesNotContain("..");
      assertThat(FileOps.sanitizeFileName(".%00.")).doesNotContain("..");
    }
  }

  @Nested
  @DisplayName("长度截断")
  class Truncation {

    @Test
    @DisplayName("普通超长名截断到 255 且保留后缀")
    void should_truncate_and_keep_suffix() {
      String safe = FileOps.sanitizeFileName(repeat('b', 300) + ".jpg");

      assertThat(safe).hasSize(255).endsWith(".jpg");
    }

    @Test
    @DisplayName("超长后缀不得抛 StringIndexOutOfBoundsException，且结果仍 ≤255")
    void should_not_throw_when_suffix_exceeds_budget() {
      // FileOps:104-112 的旧实现按 255 - suffix.length() - 1 计算基名预算，
      // 后缀长度 ≥255 时预算为负 → baseName.substring(0, 负数) 直接抛异常。
      // 入参来自上传文件名，攻击者只需 `curl -F "file=@x;filename=a.<300个y>"` 即可稳定触发 500。
      String longSuffix = repeat('y', 300);

      assertThat(FileOps.sanitizeFileName("x" + "." + longSuffix)).isNotBlank().hasSizeLessThanOrEqualTo(255);
      assertThat(FileOps.sanitizeFileName(repeat('z', 400) + "." + longSuffix))
          .hasSizeLessThanOrEqualTo(255);
      assertThat(FileOps.sanitizeFileName("." + longSuffix)).hasSizeLessThanOrEqualTo(255);
    }

    @Test
    @DisplayName("无后缀的超长名截断后不得凭空多出尾点")
    void should_not_append_trailing_dot_when_no_suffix() {
      // 旧实现把空后缀也走 "baseName + '.' + suffix" 拼接，"a".repeat(256) 会得到 255 个 a + "."，
      // 而 NTFS 会剥掉尾点 —— 净化前后名字不等价，重命名/去重链路会误判。
      String safe = FileOps.sanitizeFileName(repeat('a', 256));

      assertThat(safe).hasSize(255).doesNotEndWith(".");
    }

    @Test
    @DisplayName("截断后仍不得携带穿越序列或非法字符")
    void should_stay_safe_after_truncation() {
      String safe = FileOps.sanitizeFileName("../../" + repeat('a', 300) + ".txt");

      assertThat(safe).doesNotContain("..").doesNotContain("/").hasSizeLessThanOrEqualTo(255);
    }
  }

  @Nested
  @DisplayName("边界输入与后缀提取")
  class Boundaries {

    @Test
    @DisplayName("null 与空串原样返回，不抛异常")
    void should_pass_through_null_and_empty() {
      assertThat(FileOps.sanitizeFileName(null)).isNull();
      assertThat(FileOps.sanitizeFileName("")).isEmpty();
    }

    @Test
    @DisplayName("中文文件名保留（白名单含 CJK 区段）")
    void should_keep_chinese_names() {
      assertThat(FileOps.sanitizeFileName("季度报表 2026.Q3.xlsx")).isEqualTo("季度报表 2026.Q3.xlsx");
    }

    @Test
    @DisplayName("后缀小写归一，无后缀/点在末尾均视为无后缀")
    void should_normalize_suffix() {
      assertThat(FileOps.extractSuffix("photo.JPG")).isEqualTo("jpg");
      assertThat(FileOps.extractSuffix("README")).isEmpty();
      assertThat(FileOps.extractSuffix("file.")).isEmpty();
      assertThat(FileOps.extractSuffix(null)).isEmpty();
      assertThat(FileOps.extractSuffix("archive.tar.gz")).isEqualTo("gz");
    }

    @Test
    @DisplayName("存储键格式：{prefix}/{namespace}/{yyyy/MM/dd}/{id}.{suffix}")
    void should_build_storage_key() {
      String key = FileOps.generateStorageKey("wiki", "user-001", "doc.pdf");

      assertThat(key).matches("wiki/user-001/\\d{4}/\\d{2}/\\d{2}/[^/]+\\.pdf");
      assertThat(FileOps.generateStorageKey("user-001", "noext")).startsWith("file/user-001/");
    }

    @Test
    @DisplayName("存储键的 namespace 未做净化：调用方必须自行保证其为受控标识")
    void should_document_namespace_is_not_sanitized() {
      // 事实钉住而非期望：generateStorageKey 只对 filename 取后缀，namespace 原样拼接。
      // 若把用户可控字符串当 namespace 传入，即可写入任意路径前缀。
      assertThat(FileOps.generateStorageKey("file", "../../escape", "a.txt"))
          .contains("/../../escape/");
    }
  }
}
