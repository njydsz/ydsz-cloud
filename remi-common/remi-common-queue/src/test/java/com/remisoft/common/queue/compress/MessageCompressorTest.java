package com.remisoft.common.queue.compress;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 消息压缩工具类测试
 *
 * @author remi-team
 * @since 1.0.0
 */
class MessageCompressorTest {

    @Test
    void testSmallMessageNotCompressed() {
        String data = "hello world";
        String result = MessageCompressor.compressIfNeeded(data, 1024);
        assertEquals(data, result);
        assertFalse(MessageCompressor.isCompressed(result));
    }

    @Test
    void testLargeMessageCompressed() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            sb.append("这是一段测试消息用于验证压缩功能是否正常工作。");
        }
        String data = sb.toString();
        String result = MessageCompressor.compressIfNeeded(data, 1024);
        assertNotEquals(data, result);
        assertTrue(MessageCompressor.isCompressed(result));
        assertTrue(result.startsWith(MessageCompressor.COMPRESS_PREFIX));
        assertTrue(result.length() < data.length());
    }

    @Test
    void testDecompressReversesCompress() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 500; i++) {
            sb.append("Compress test data line ").append(i).append("\n");
        }
        String original = sb.toString();
        String compressed = MessageCompressor.compressIfNeeded(original, 1024);
        assertTrue(MessageCompressor.isCompressed(compressed));
        String decompressed = MessageCompressor.decompressIfNeeded(compressed);
        assertEquals(original, decompressed);
    }

    @Test
    void testNullInputReturnsNull() {
        assertNull(MessageCompressor.compressIfNeeded(null, 1024));
        assertNull(MessageCompressor.decompressIfNeeded(null));
    }

    @Test
    void testEmptyInputReturnsEmpty() {
        assertEquals("", MessageCompressor.compressIfNeeded("", 1024));
        assertEquals("", MessageCompressor.decompressIfNeeded(""));
    }

    @Test
    void testAlreadyCompressedNotRecompressed() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 500; i++) {
            sb.append("重复数据重复数据重复数据");
        }
        String data = sb.toString();
        String compressed = MessageCompressor.compressIfNeeded(data, 1024);
        String doubleCompressed = MessageCompressor.compressIfNeeded(compressed, 1024);
        assertEquals(compressed, doubleCompressed);
    }

    @Test
    void testDecompressUncompressedReturnsOriginal() {
        String data = "plain text without compression";
        String result = MessageCompressor.decompressIfNeeded(data);
        assertEquals(data, result);
    }
}
