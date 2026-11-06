package com.njydsz.agent.domain.ocr;

import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ImageFormat} 单元测试。
 *
 * <p>覆盖：MIME 解析（大小写不敏感）、扩展名解析（含 jpg 别名、去点、大小写）、未知/null 兜底。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
@DisplayName("ImageFormat 图片格式枚举")
class ImageFormatTest {

  @Nested
  @DisplayName("MIME 解析")
  class FromMimeTypeTest {

    @ParameterizedTest(name = "fromMimeType(\"{0}\") 应返回 {1}")
    @CsvSource({
      "image/png, PNG",
      "image/jpeg, JPEG",
      "image/bmp, BMP",
      "image/tiff, TIFF",
      "IMAGE/PNG, PNG",
      "Image/Jpeg, JPEG",
    })
    @DisplayName("大小写不敏感解析应成功")
    void shouldParseCaseInsensitive(String mime, ImageFormat expected) {
      assertThat(ImageFormat.fromMimeType(mime)).isEqualTo(expected);
    }

    @Test
    @DisplayName("null 应返回 null")
    void shouldReturnNullForNull() {
      assertThat(ImageFormat.fromMimeType(null)).isNull();
    }

    @ParameterizedTest(name = "fromMimeType(\"{0}\") 应返回 null")
    @ValueSource(strings = {"image/gif", "image/webp", "application/octet-stream"})
    @DisplayName("未知 MIME 应返回 null")
    void shouldReturnNullForUnknown(String mime) {
      assertThat(ImageFormat.fromMimeType(mime)).isNull();
    }
  }

  @Nested
  @DisplayName("扩展名解析")
  class FromExtensionTest {

    @ParameterizedTest(name = "fromExtension(\"{0}\") 应返回 {1}")
    @CsvSource({
      "png, PNG",
      "jpeg, JPEG",
      "bmp, BMP",
      "tiff, TIFF",
      "jpg, JPEG",
      "JPG, JPEG",
      "PNG, PNG",
      ".png, PNG",
      ".jpg, JPEG",
    })
    @DisplayName("扩展名解析应成功（含 jpg 别名与去点）")
    void shouldParseExtension(String ext, ImageFormat expected) {
      assertThat(ImageFormat.fromExtension(ext)).isEqualTo(expected);
    }

    @Test
    @DisplayName("null 应返回 null")
    void shouldReturnNullForNull() {
      assertThat(ImageFormat.fromExtension(null)).isNull();
    }

    @ParameterizedTest(name = "fromExtension(\"{0}\") 应返回 null")
    @ValueSource(strings = {"gif", "webp", "svg", ""})
    @DisplayName("未知扩展名应返回 null")
    void shouldReturnNullForUnknown(String ext) {
      assertThat(ImageFormat.fromExtension(ext)).isNull();
    }
  }

  @Nested
  @DisplayName("元数据")
  class MetaTest {

    @Test
    @DisplayName("extension 应为小写且唯一")
    void shouldHaveLowercaseUniqueExtensions() {
      long distinct = Arrays.stream(ImageFormat.values())
          .map(ImageFormat::getExtension)
          .distinct()
          .count();
      assertThat(distinct).isEqualTo(ImageFormat.values().length);
      for (ImageFormat format : ImageFormat.values()) {
        assertThat(format.getExtension()).isEqualTo(format.getExtension().toLowerCase());
      }
    }

    @Test
    @DisplayName("mimeType 应以 image/ 开头")
    void shouldHaveImageMimeType() {
      for (ImageFormat format : ImageFormat.values()) {
        assertThat(format.getMimeType()).startsWith("image/");
      }
    }
  }
}
