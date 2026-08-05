package com.remisoft.common.util.io;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * 限制读取字节数的输入流。
 *
 * <p>包装一个底层 InputStream，限制其最大可读取字节数。
 * 常用于防止未经校验的超大输入（DoS 防护）、协议帧截断等场景。
 *
 * <p>当读取到达 limit 后，所有 {@code read()} 方法返回 {@code -1}（EOF）。
 * 底层流不会被自动关闭，调用方需自行管理底层流生命周期。
 *
 * <p><b>线程安全：</b>本类非线程安全，并发读取需外部同步。
 *
 * <pre>{@code
 * // 最多读取 1MB 数据
 * try (InputStream in = new LimitInputStream(rawInput, 1024 * 1024)) {
 *     byte[] data = IOUtils.toByteArray(in);
 * }
 * }</pre>
 *
 * @author remi-team
 * @since 1.3.0
 */
public class LimitInputStream extends InputStream {

    /** 底层输入流 */
    private final InputStream input;

    /** 剩余可读取字节数 */
    private long remaining;

    /** mark 时的 remaining 快照，-1 表示未 mark */
    private long mark = -1;

    /**
     * 构造 LimitInputStream。
     *
     * @param input 原始输入流，不可为 null
     * @param limit 最大读取字节数，必须 >= 0
     * @throws IllegalArgumentException 如果 limit 为负数
     */
    public LimitInputStream(InputStream input, long limit) {
        this.input = Objects.requireNonNull(input, "input cannot be null");
        if (limit < 0) {
            throw new IllegalArgumentException("limit cannot be negative, got: " + limit);
        }
        this.remaining = limit;
    }

    @Override
    public int read() throws IOException {
        if (remaining <= 0) {
            return -1;
        }
        int b = input.read();
        if (b != -1) {
            remaining--;
        }
        return b;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (remaining <= 0) {
            return -1;
        }
        int toRead = (int) Math.min(len, remaining);
        int bytesRead = input.read(b, off, toRead);
        if (bytesRead > 0) {
            remaining -= bytesRead;
        }
        return bytesRead;
    }

    @Override
    public long skip(long n) throws IOException {
        if (remaining <= 0) {
            return 0;
        }
        long toSkip = Math.min(n, remaining);
        long skipped = input.skip(toSkip);
        remaining -= skipped;
        return skipped;
    }

    @Override
    public int available() throws IOException {
        if (remaining <= 0) {
            return 0;
        }
        return (int) Math.min(input.available(), remaining);
    }

    @Override
    public synchronized void mark(int readlimit) {
        mark = remaining;
        input.mark(readlimit);
    }

    @Override
    public synchronized void reset() throws IOException {
        if (mark >= 0) {
            remaining = mark;
            mark = -1;
        }
        input.reset();
    }

    @Override
    public boolean markSupported() {
        return input.markSupported();
    }

    @Override
    public void close() throws IOException {
        input.close();
    }

    /**
     * 获取剩余可读取字节数。
     *
     * @return 剩余字节数；返回 0 表示已达上限
     */
    public long getRemaining() {
        return remaining;
    }

    /**
     * 获取底层输入流。
     *
     * @return 底层 InputStream 实例
     */
    public InputStream getWrappedStream() {
        return input;
    }
}
