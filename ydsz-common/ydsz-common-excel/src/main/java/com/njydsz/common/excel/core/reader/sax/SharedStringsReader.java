package com.njydsz.common.excel.core.reader.sax;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 共享字符串表读取器 - 流式解析SST，懒加载+LRU缓存
 *
 * <p>优化策略：
 *
 * <ul>
 *   <li>保留原始字节数组，仅记录每个字符串的偏移量和长度
 *   <li>按需解码字符串（懒加载），避免一次性解码所有字符串
 *   <li>使用LRU缓存最近访问的字符串，减少重复解码开销
 * </ul>
 *
 * 对于包含百万级共享字符串的大文件，可显著降低内存占用。
 *
 * <h3>rich text 多 run 支持（深度完善·方案 B）</h3>
 *
 * <p>SST 条目形态：纯文本 {@code <si><t>文本</t></si>} 与 rich text
 * {@code <si><r><t>run1</t></r><r><t>run2</t></r></si>}。此前解析只取第一个
 * {@code <t>}，多 run 条目静默丢失第二个 run 起的全部内容；富文本单元格（POI
 * RichTextString、Excel 分段着色）读取结果不完整。现按 {@code <si>} 边界拼接全部
 * run 文本。
 *
 * <h3>phonetic（注音）过滤</h3>
 *
 * <p>日文注音条目含 {@code <rPh sb="0" eb="2"><t>とうきょう</t></rPh>}（假名注音，
 * 辅助输入而非显示内容）。{@code <rPh>} 区间内的 {@code <t>} 不参与拼接，
 * 与 POI SharedStringsTable 的 phonetic 语义一致（取 t 文本、忽略注音）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class SharedStringsReader {
  /** LRU缓存容量 */
  private static final int LRU_CAPACITY = 1024;

  /** 原始字节数据（懒加载模式保留） */
  private byte[] rawData;

  /** 每个字符串内容区间在rawData中的起始偏移量（si 内容起点） */
  private int[] offsets;

  /** 每个字符串内容区间的字节长度 */
  private int[] lengths;

  /** 字符串总数 */
  private int stringCount = 0;

  /** LRU缓存：最近访问的已解码字符串 */
  private final LinkedHashMap<Integer, String> lruCache;

  /** 预加载模式下的字符串列表（小文件使用） */
  private String[] preloadedStrings;

  /** 是否使用预加载模式 */
  private boolean usePreload = true;

  public SharedStringsReader() {
    lruCache =
        new LinkedHashMap<Integer, String>(LRU_CAPACITY, 0.75f, true) {
          @Override
          protected boolean removeEldestEntry(Map.Entry<Integer, String> eldest) {
            return size() > LRU_CAPACITY;
          }
        };
  }

  void parse(InputStream is) throws IOException {
    rawData = readAllBytesDirect(is);
    int len = rawData.length;

    // 第一遍：统计字符串数量（<si> 或自闭合 <si/>）
    int count = 0;
    int pos = 0;
    while (pos < len) {
      int siStart = findTagStart(rawData, pos, len, "si");
      if (siStart == -1) {
        break;
      }
      count++;
      pos = siStart + 3;
    }

    if (count == 0) {
      offsets = new int[0];
      lengths = new int[0];
      return;
    }

    // 小文件（<=8192个字符串）使用预加载模式，一次性解码所有字符串
    if (count <= 8192) {
      preloadedStrings = new String[count];
      offsets = null;
      lengths = null;
      usePreload = true;

      pos = 0;
      int idx = 0;
      while (pos < len) {
        int siStart = findTagStart(rawData, pos, len, "si");
        if (siStart == -1) {
          break;
        }
        preloadedStrings[idx++] = extractSiText(siStart);
        pos = advancePastSi(siStart, len);
      }
      stringCount = idx;
      // 释放原始数据
      rawData = null;
      return;
    }

    // 大文件：懒加载模式，记录每个 si 内容区间（多 run 拼接推迟到 getString）
    usePreload = false;
    offsets = new int[count];
    lengths = new int[count];

    pos = 0;
    int idx = 0;
    while (pos < len) {
      int siStart = findTagStart(rawData, pos, len, "si");
      if (siStart == -1) {
        break;
      }
      int contentStart = siContentStart(siStart, len);
      int siEnd = siContentEnd(siStart, len);
      // 自闭合 <si/> 仍占一个 SST 索引（以空区间占位），保证后续字符串索引不错位
      if (contentStart < 0) {
        contentStart = Math.min(siEnd, len);
        siEnd = contentStart;
      }
      offsets[idx] = contentStart;
      lengths[idx] = Math.max(0, siEnd - contentStart);
      idx++;
      pos = advancePastSi(siStart, len);
    }
    stringCount = idx;
  }

  /**
   * 提取指定 si 条目的完整文本（多 run 拼接，phonetic 过滤）。
   *
   * @param siStart si 起始标签位置（{@code <si}）
   * @return 拼接后的文本；自闭合或无文本 run 时为空串
   */
  private String extractSiText(int siStart) {
    int contentStart = siContentStart(siStart, rawData.length);
    int siEnd = siContentEnd(siStart, rawData.length);
    if (contentStart < 0 || siEnd < contentStart) {
      return "";
    }
    return extractRunsText(rawData, contentStart, siEnd);
  }

  /** si 起始标签的内容起点（{@code <si>} 后）；自闭合时返回 -1。 */
  private int siContentStart(int siStart, int len) {
    int tagEnd = findChar(rawData, siStart, len, '>');
    if (tagEnd < 0) {
      return -1;
    }
    if (rawData[tagEnd - 1] == '/') {
      return -1;
    }
    return tagEnd + 1;
  }

  /** si 内容终点（{@code </si>} 位置）；未闭合时返回 len。 */
  private int siContentEnd(int siStart, int len) {
    int tagEnd = findChar(rawData, siStart, len, '>');
    if (tagEnd < 0) {
      return len;
    }
    if (rawData[tagEnd - 1] == '/') {
      return tagEnd - 1;
    }
    int close = findSubstring(rawData, tagEnd, len, "</si>");
    return close >= 0 ? close : len;
  }

  /** 返回下一个扫描起点（跨过当前 si，避免 si 内文本干扰下一轮 si 定位）。 */
  private int advancePastSi(int siStart, int len) {
    int siEnd = siContentEnd(siStart, len);
    return Math.max(siStart + 3, siEnd + 5);
  }

  /**
   * 提取区间内全部文本 run 并拼接（多 run 支持），跳过 phonetic（{@code <rPh>}）区间。
   *
   * <p>同时供 {@link SheetXmlReader} 处理 inlineStr 富文本单元格（{@code <is><r><t>…</t></r>…</is>}
   * 复用——SST 条目与 inlineStr 的 run 结构同构。
   *
   * @param data 原始字节数组
   * @param from 区间起点（含）
   * @param to 区间终点（不含）
   * @return 拼接后的文本；无文本 run 时为空串
   */
  static String extractRunsText(byte[] data, int from, int to) {
    if (from >= to) {
      return "";
    }
    StringBuilder sb = new StringBuilder(Math.min(64, to - from));
    int pos = from;
    while (pos < to) {
      int tStart = findTagStart(data, pos, to, "t");
      if (tStart < 0) {
        break;
      }
      int tagEnd = findChar(data, tStart, to, '>');
      if (tagEnd < 0) {
        break;
      }
      pos = tagEnd + 1;
      // 自闭合 <t/>：内容为空
      if (data[tagEnd - 1] == '/') {
        continue;
      }
      int tEnd = findSubstring(data, pos, to, "</t>");
      if (tEnd < 0) {
        break;
      }
      if (!inPhoneticRegion(data, from, to, tStart)) {
        sb.append(decodeUtf8(data, pos, tEnd - pos));
      }
      pos = tEnd + 4;
    }
    return sb.toString();
  }

  /** 判断指定位置是否落在某个 {@code <rPh>…</rPh>}（phonetic）区间内。 */
  private static boolean inPhoneticRegion(byte[] data, int from, int to, int position) {
    int pos = from;
    while (pos < to) {
      int phStart = findSubstring(data, pos, to, "<rPh");
      if (phStart < 0) {
        return false;
      }
      int phTagEnd = findChar(data, phStart, to, '>');
      if (phTagEnd < 0) {
        return false;
      }
      // 自闭合 <rPh/>：无注音内容
      if (data[phTagEnd - 1] == '/') {
        pos = phTagEnd + 1;
        continue;
      }
      int phEnd = findSubstring(data, phTagEnd, to, "</rPh>");
      if (phEnd < 0) {
        return false;
      }
      if (position > phStart && position < phEnd) {
        return true;
      }
      pos = phEnd + 6;
    }
    return false;
  }

  private byte[] readAllBytesDirect(InputStream is) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream(32768);
    byte[] buffer = new byte[8192];
    int len;
    while ((len = is.read(buffer)) > 0) {
      baos.write(buffer, 0, len);
    }
    return baos.toByteArray();
  }

  /**
   * 定位标签起点（{@code <name} 且下一字符为空白、{@code >} 或 {@code /}），
   * 避免误匹配同前缀的其他标签（如 {@code <si>} 与假想的 {@code <size>}）。
   */
  private static int findTagStart(byte[] data, int start, int len, String tagName) {
    byte[] prefix = ("<" + tagName).getBytes(StandardCharsets.UTF_8);
    int plen = prefix.length;
    for (int i = start; i <= len - plen; i++) {
      boolean match = true;
      for (int j = 0; j < plen; j++) {
        if (data[i + j] != prefix[j]) {
          match = false;
          break;
        }
      }
      if (match) {
        byte next = (i + plen < len) ? data[i + plen] : 0;
        if (next == ' ' || next == '>' || next == '/' || next == '\n' || next == '\r'
            || next == '\t') {
          return i;
        }
      }
    }
    return -1;
  }

  private static int findSubstring(byte[] data, int start, int len, String pattern) {
    byte[] patternBytes = pattern.getBytes(StandardCharsets.UTF_8);
    int plen = patternBytes.length;
    for (int i = start; i <= len - plen; i++) {
      boolean match = true;
      for (int j = 0; j < plen; j++) {
        if (data[i + j] != patternBytes[j]) {
          match = false;
          break;
        }
      }
      if (match) {
        return i;
      }
    }
    return -1;
  }

  private static int findChar(byte[] data, int start, int len, char ch) {
    for (int i = start; i < len; i++) {
      if (data[i] == (byte) ch) {
        return i;
      }
    }
    return -1;
  }

  private static String decodeUtf8(byte[] data, int start, int len) {
    StringBuilder buffer = new StringBuilder(len + 8);
    int end = start + len;
    int i = start;
    while (i < end) {
      byte b = data[i];
      if ((b & 0x80) == 0) {
        if (b == '&') {
          String entity = decodeEntity(data, i, end);
          if (entity != null) {
            buffer.append(entity);
            i += getEntityLength(data, i, end);
            continue;
          }
        }
        buffer.append((char) b);
        i++;
      } else if ((b & 0xE0) == 0xC0) {
        int c = ((b & 0x1F) << 6) | (data[i + 1] & 0x3F);
        buffer.append((char) c);
        i += 2;
      } else if ((b & 0xF0) == 0xE0) {
        int c = ((b & 0x0F) << 12) | ((data[i + 1] & 0x3F) << 6) | (data[i + 2] & 0x3F);
        buffer.append((char) c);
        i += 3;
      } else {
        i++;
      }
    }
    return buffer.toString();
  }

  private static String decodeEntity(byte[] data, int start, int end) {
    if (start + 5 <= end
        && data[start + 1] == 'a'
        && data[start + 2] == 'm'
        && data[start + 3] == 'p'
        && data[start + 4] == ';') {
      return "&";
    }
    if (start + 4 <= end
        && data[start + 1] == 'l'
        && data[start + 2] == 't'
        && data[start + 3] == ';') {
      return "<";
    }
    if (start + 4 <= end
        && data[start + 1] == 'g'
        && data[start + 2] == 't'
        && data[start + 3] == ';') {
      return ">";
    }
    if (start + 6 <= end
        && data[start + 1] == 'q'
        && data[start + 2] == 'u'
        && data[start + 3] == 'o'
        && data[start + 4] == 't'
        && data[start + 5] == ';') {
      return "\"";
    }
    if (start + 6 <= end
        && data[start + 1] == 'a'
        && data[start + 2] == 'p'
        && data[start + 3] == 'o'
        && data[start + 4] == 's'
        && data[start + 5] == ';') {
      return "'";
    }
    return null;
  }

  private static int getEntityLength(byte[] data, int start, int end) {
    for (int i = start + 1; i < end && i < start + 10; i++) {
      if (data[i] == ';') {
        return i - start + 1;
      }
    }
    return 1;
  }

  String getString(int index) {
    if (index < 0) {
      return null;
    }

    // 预加载模式：直接从数组获取
    if (usePreload) {
      if (index < stringCount) {
        return preloadedStrings[index];
      }
      return null;
    }

    // 懒加载模式：先查LRU缓存
    String cached = lruCache.get(index);
    if (cached != null) {
      return cached;
    }

    // 按需解码（多 run 拼接 + phonetic 过滤）
    if (index >= stringCount || rawData == null) {
      return null;
    }

    String decoded =
        extractRunsText(rawData, offsets[index], offsets[index] + lengths[index]);
    lruCache.put(index, decoded);
    return decoded;
  }
}
