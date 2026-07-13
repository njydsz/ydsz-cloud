package com.njydsz.pmis.common.cache.export;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.njydsz.pmis.common.cache.api.Cache;
import com.njydsz.pmis.common.cache.internal.concurrent.StripedConcurrentCache;

@DisplayName("CacheExportImport 单元测试")
class CacheExportImportTest {

  @TempDir Path tempDir;

  @Nested
  @DisplayName("对象序列化")
  class ObjectSerialization {

    @Test
    @DisplayName("导出后导入")
    void exportAndImport() throws IOException, ClassNotFoundException {
      Cache<String, String> cache = new StripedConcurrentCache<>(100);
      cache.put("key1", "value1");
      cache.put("key2", "value2");

      File exportFile = tempDir.resolve("cache.dat").toFile();
      CacheExportImport.exportCache(cache, exportFile.getAbsolutePath());

      Cache<String, String> importCache = new StripedConcurrentCache<>(100);
      CacheExportImport.importCache(
          importCache, exportFile.getAbsolutePath(), String.class, String.class);

      assertThat(importCache.getIfPresent("key1")).isEqualTo("value1");
      assertThat(importCache.getIfPresent("key2")).isEqualTo("value2");
    }
  }

  @Nested
  @DisplayName("文本格式")
  class TextFormat {

    @Test
    @DisplayName("文本导出后导入")
    void exportAndImportText() throws IOException {
      Cache<String, String> cache = new StripedConcurrentCache<>(100);
      cache.put("key1", "value1");
      cache.put("key2", "value2");

      File exportFile = tempDir.resolve("cache.txt").toFile();
      CacheExportImport.exportCacheToText(cache, exportFile.getAbsolutePath());

      Cache<String, String> importCache = new StripedConcurrentCache<>(100);
      CacheExportImport.TextParser<String, String> parser =
          new CacheExportImport.TextParser<>() {
            @Override
            public String parseKey(String text) {
              return text;
            }

            @Override
            public String parseValue(String text) {
              return text;
            }
          };
      CacheExportImport.importCacheFromText(importCache, exportFile.getAbsolutePath(), parser);

      assertThat(importCache.getIfPresent("key1")).isEqualTo("value1");
      assertThat(importCache.getIfPresent("key2")).isEqualTo("value2");
    }

    @Test
    @DisplayName("文本导入自定义解析器")
    void importWithCustomParser() throws IOException {
      Cache<String, Integer> cache = new StripedConcurrentCache<>(100);

      File exportFile = tempDir.resolve("cache_custom.txt").toFile();
      java.nio.file.Files.write(exportFile.toPath(), List.of("a\t1", "b\t2"));

      CacheExportImport.TextParser<String, Integer> parser =
          new CacheExportImport.TextParser<>() {
            @Override
            public String parseKey(String text) {
              return text;
            }

            @Override
            public Integer parseValue(String text) {
              return Integer.parseInt(text);
            }
          };
      CacheExportImport.importCacheFromText(cache, exportFile.getAbsolutePath(), parser);

      assertThat(cache.getIfPresent("a")).isEqualTo(1);
      assertThat(cache.getIfPresent("b")).isEqualTo(2);
    }
  }

  @Nested
  @DisplayName("过滤导出")
  class FilteredExport {

    @Test
    @DisplayName("按条件过滤导出")
    void exportWithFilter() throws IOException {
      Cache<String, String> cache = new StripedConcurrentCache<>(100);
      cache.put("user:1", "Alice");
      cache.put("user:2", "Bob");
      cache.put("product:1", "Item1");

      File exportFile = tempDir.resolve("cache_filtered.dat").toFile();
      CacheExportImport.CacheFilter<String, String> filter =
          (key, value) -> key.startsWith("user:");

      int count =
          CacheExportImport.exportCacheWithFilter(cache, exportFile.getAbsolutePath(), filter);

      assertThat(count).isEqualTo(2);
    }
  }

  @Nested
  @DisplayName("限制导入")
  class LimitedImport {

    @Test
    @DisplayName("限制导入条目数")
    void importWithLimit() throws IOException, ClassNotFoundException {
      Cache<String, String> sourceCache = new StripedConcurrentCache<>(100);
      for (int i = 0; i < 10; i++) {
        sourceCache.put("key-" + i, "value-" + i);
      }

      File exportFile = tempDir.resolve("cache_limited.dat").toFile();
      CacheExportImport.exportCache(sourceCache, exportFile.getAbsolutePath());

      Cache<String, String> importCache = new StripedConcurrentCache<>(100);
      int imported =
          CacheExportImport.importCacheWithLimit(
              importCache, exportFile.getAbsolutePath(), 5, String.class, String.class);

      assertThat(imported).isEqualTo(5);
      assertThat(importCache.estimatedSize()).isEqualTo(5);
    }
  }
}
