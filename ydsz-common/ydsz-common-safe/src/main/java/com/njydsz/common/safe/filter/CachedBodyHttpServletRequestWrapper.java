package com.njydsz.common.safe.filter;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * 缓存请求体的 HTTP 请求包装器
 *
 * <p>将请求体读取到内存中，支持多次读取。解决 ServletInputStream 只能读一次的问题， 供 XSS Filter、SQL 注入 Filter、API 签名 Filter
 * 等多个过滤器共享请求体。
 *
 * <p><b>使用场景：</b>
 *
 * <ul>
 *   <li>XSS Filter 需要读取并清洗 JSON 请求体
 *   <li>SQL 注入 Filter 需要读取并检查请求参数
 *   <li>API 签名 Filter 需要读取请求体计算签名
 * </ul>
 *
 * <p>此前各 Filter 各自实现缓存逻辑（CachedBodyHttpServletRequest / CachedRequestBody）， 此公共类消除了重复实现。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class CachedBodyHttpServletRequestWrapper extends HttpServletRequestWrapper {

  private final byte[] cachedBody;

  /**
   * @param request 原始 HTTP 请求
   * @param cachedBody 已读取的请求体字节数组
   */
  public CachedBodyHttpServletRequestWrapper(HttpServletRequest request, byte[] cachedBody) {
    super(request);
    this.cachedBody = cachedBody != null ? cachedBody : new byte[0];
  }

  @Override
  public ServletInputStream getInputStream() throws IOException {
    return new CachedServletInputStream(cachedBody);
  }

  @Override
  public BufferedReader getReader() throws IOException {
    return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
  }

  /**
   * 获取缓存的请求体字节数组
   *
   * @return 请求体字节数组
   */
  public byte[] getCachedBody() {
    return cachedBody;
  }

  /**
   * 获取缓存的请求体字符串
   *
   * @return 请求体字符串（UTF-8 编码）
   */
  public String getCachedBodyAsString() {
    return new String(cachedBody, StandardCharsets.UTF_8);
  }

  /** 基于 ByteArrayInputStream 的 ServletInputStream 实现 */
  private static class CachedServletInputStream extends ServletInputStream {

    private final ByteArrayInputStream inputStream;

    CachedServletInputStream(byte[] cachedBody) {
      this.inputStream = new ByteArrayInputStream(cachedBody);
    }

    @Override
    public boolean isFinished() {
      return inputStream.available() == 0;
    }

    @Override
    public boolean isReady() {
      return true;
    }

    @Override
    public void setReadListener(ReadListener listener) {}

    @Override
    public int read() throws IOException {
      return inputStream.read();
    }
  }
}
