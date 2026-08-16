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
 * <ul>
 *   <li>保留原始字节数组，仅记录每个字符串的偏移量和长度</li>
 *   <li>按需解码字符串（懒加载），避免一次性解码所有字符串</li>
 *   <li>使用LRU缓存最近访问的字符串，减少重复解码开销</li>
 * </ul>
 * 对于包含百万级共享字符串的大文件，可显著降低内存占用。</p>
 * @author ydsz-team
 * @since 1.0.0
 */
public class SharedStringsReader {
    /** LRU缓存容量 */
    private static final int LRU_CAPACITY = 1024;

    /** 原始字节数据（懒加载模式保留） */
    private byte[] rawData;

    /** 每个字符串在rawData中的起始偏移量 */
    private int[] offsets;

    /** 每个字符串在rawData中的字节长度 */
    private int[] lengths;

    /** 字符串总数 */
    private int stringCount = 0;

    /** LRU缓存：最近访问的已解码字符串 */
    private final LinkedHashMap<Integer, String> lruCache;

    /** 预加载模式下的字符串列表（小文件使用） */
    private String[] preloadedStrings;

    /** 是否使用预加载模式 */
    private boolean usePreload = true;

    /** 解码用StringBuilder */
    private final StringBuilder buffer = new StringBuilder(256);

    public SharedStringsReader() {
        lruCache = new LinkedHashMap<Integer, String>(LRU_CAPACITY, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Integer, String> eldest) {
                return size() > LRU_CAPACITY;
            }
        };
    }

    void parse(InputStream is) throws IOException {
        rawData = readAllBytesDirect(is);
        int len = rawData.length;

        // 第一遍：统计字符串数量
        int count = 0;
        int pos = 0;
        while (pos < len) {
            int siStart = findSubstring(rawData, pos, len, "<si>");
            if (siStart == -1) break;
            count++;
            pos = siStart + 4;
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
                int siStart = findSubstring(rawData, pos, len, "<si>");
                if (siStart == -1) break;

                int tStart = findSubstring(rawData, siStart + 4, len, "<t");
                if (tStart == -1) {
                    pos = siStart + 4;
                    continue;
                }

                int tContentStart = findChar(rawData, tStart, len, '>');
                if (tContentStart == -1) {
                    pos = tStart + 2;
                    continue;
                }
                tContentStart++;

                int tEnd = findSubstring(rawData, tContentStart, len, "</t>");
                if (tEnd == -1) {
                    pos = tContentStart + 1;
                    continue;
                }

                preloadedStrings[idx++] = decodeUtf8(rawData, tContentStart, tEnd - tContentStart);
                pos = tEnd + 4;
            }
            stringCount = idx;
            // 释放原始数据
            rawData = null;
            return;
        }

        // 大文件：懒加载模式，仅记录偏移量和长度
        usePreload = false;
        offsets = new int[count];
        lengths = new int[count];

        pos = 0;
        int idx = 0;
        while (pos < len) {
            int siStart = findSubstring(rawData, pos, len, "<si>");
            if (siStart == -1) break;

            int tStart = findSubstring(rawData, siStart + 4, len, "<t");
            if (tStart == -1) {
                pos = siStart + 4;
                continue;
            }

            int tContentStart = findChar(rawData, tStart, len, '>');
            if (tContentStart == -1) {
                pos = tStart + 2;
                continue;
            }
            tContentStart++;

            int tEnd = findSubstring(rawData, tContentStart, len, "</t>");
            if (tEnd == -1) {
                pos = tContentStart + 1;
                continue;
            }

            offsets[idx] = tContentStart;
            lengths[idx] = tEnd - tContentStart;
            idx++;

            pos = tEnd + 4;
        }
        stringCount = idx;
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

    private int findSubstring(byte[] data, int start, int len, String pattern) {
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
            if (match) return i;
        }
        return -1;
    }

    private int findChar(byte[] data, int start, int len, char ch) {
        for (int i = start; i < len; i++) {
            if (data[i] == (byte) ch) return i;
        }
        return -1;
    }

    private String decodeUtf8(byte[] data, int start, int len) {
        buffer.setLength(0);
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

    private String decodeEntity(byte[] data, int start, int end) {
        if (start + 5 <= end && data[start + 1] == 'a' && data[start + 2] == 'm'
            && data[start + 3] == 'p' && data[start + 4] == ';') {
            return "&";
        }
        if (start + 4 <= end && data[start + 1] == 'l' && data[start + 2] == 't'
            && data[start + 3] == ';') {
            return "<";
        }
        if (start + 4 <= end && data[start + 1] == 'g' && data[start + 2] == 't'
            && data[start + 3] == ';') {
            return ">";
        }
        if (start + 6 <= end && data[start + 1] == 'q' && data[start + 2] == 'u'
            && data[start + 3] == 'o' && data[start + 4] == 't' && data[start + 5] == ';') {
            return "\"";
        }
        if (start + 6 <= end && data[start + 1] == 'a' && data[start + 2] == 'p'
            && data[start + 3] == 'o' && data[start + 4] == 's' && data[start + 5] == ';') {
            return "'";
        }
        return null;
    }

    private int getEntityLength(byte[] data, int start, int end) {
        for (int i = start + 1; i < end && i < start + 10; i++) {
            if (data[i] == ';') return i - start + 1;
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

        // 按需解码
        if (index >= stringCount || rawData == null) {
            return null;
        }

        String decoded = decodeUtf8(rawData, offsets[index], lengths[index]);
        lruCache.put(index, decoded);
        return decoded;
    }
}
