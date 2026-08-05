package com.remisoft.common.util.io;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;

/**
 * 一流双写输出流（Tee OutputStream）。
 *
 * <p>将写入的数据同时复制到两个输出流，参考 Unix {@code tee} 命令。
 * 典型用途：边写边计算摘要、同时写入本地文件与远程存储、调试截获流量等。
 *
 * <p><b>线程安全：</b>本类非线程安全。若多线程并发写入同一 TeeOutputStream 实例，
 * 需外部同步，否则两个输出流的字节顺序可能不一致。
 *
 * <p><b>关闭语义：</b>{@link #close()} 会依次关闭 output1、output2。
 * 若 output1 关闭抛异常，仍会在 finally 块中尝试关闭 output2（output2 的异常会被吞没）。
 *
 * <pre>{@code
 * try (TeeOutputStream tee = new TeeOutputStream(fileOut, md5Out)) {
 *     IOUtils.copy(input, tee);
 * }
 * // 同一份数据同时写入 fileOut 和 md5Out
 * }</pre>
 *
 * @author remi-team
 * @since 1.3.0
 */
public class TeeOutputStream extends OutputStream {

    private final OutputStream output1;
    private final OutputStream output2;

    /**
     * 构造 TeeOutputStream。
     *
     * @param output1 第一个输出流，不可为 null
     * @param output2 第二个输出流，不可为 null
     */
    public TeeOutputStream(OutputStream output1, OutputStream output2) {
        this.output1 = Objects.requireNonNull(output1, "output1 cannot be null");
        this.output2 = Objects.requireNonNull(output2, "output2 cannot be null");
    }

    @Override
    public void write(int b) throws IOException {
        output1.write(b);
        output2.write(b);
    }

    @Override
    public void write(byte[] b) throws IOException {
        output1.write(b);
        output2.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        output1.write(b, off, len);
        output2.write(b, off, len);
    }

    @Override
    public void flush() throws IOException {
        output1.flush();
        output2.flush();
    }

    @Override
    public void close() throws IOException {
        try {
            output1.close();
        } finally {
            output2.close();
        }
    }

    /**
     * 获取第一个输出流。
     *
     * @return 第一个输出流
     */
    public OutputStream getOutput1() {
        return output1;
    }

    /**
     * 获取第二个输出流。
     *
     * @return 第二个输出流
     */
    public OutputStream getOutput2() {
        return output2;
    }
}
