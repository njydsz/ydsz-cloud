package com.njydsz.common.web.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

import java.io.*;
import java.util.zip.GZIPOutputStream;

import com.njydsz.common.web.config.ResponseCompressionProperties;

/**
 * HTTP 响应压缩过滤器
 *
 * <p>对符合条件的响应进行 GZIP 压缩，减少网络传输量。
 * 支持配置最小压缩阈值、MIME 类型过滤、User-Agent 排除等。
 *
 * <p><b>压缩条件：</b>
 * <ul>
 *   <li>响应体大小 >= minResponseSize（默认 2KB）</li>
 *   <li>响应 Content-Type 在 mimeTypes 列表中</li>
 *   <li>客户端 Accept-Encoding 包含 gzip</li>
 *   <li>User-Agent 不在 excludedUserAgents 列表中</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class ResponseCompressionFilter implements Filter {

    private final ResponseCompressionProperties properties;

    public ResponseCompressionFilter(ResponseCompressionProperties properties) {
        this.properties = properties;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        if (!(request instanceof HttpServletRequest) || !(response instanceof HttpServletResponse)) {
            chain.doFilter(request, response);
            return;
        }

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // 检查是否支持 gzip
        if (!acceptsGzipEncoding(httpRequest)) {
            chain.doFilter(request, response);
            return;
        }

        // 检查 User-Agent 是否被排除
        if (isExcludedUserAgent(httpRequest)) {
            chain.doFilter(request, response);
            return;
        }

        // 包装响应以捕获输出
        GzipResponseWrapper responseWrapper = new GzipResponseWrapper(httpResponse);

        try {
            chain.doFilter(request, responseWrapper);
        } finally {
            responseWrapper.finish();
        }
    }

    /**
     * 检查客户端是否接受 gzip 编码
     */
    private boolean acceptsGzipEncoding(HttpServletRequest request) {
        String acceptEncoding = request.getHeader("Accept-Encoding");
        return acceptEncoding != null && acceptEncoding.contains("gzip");
    }

    /**
     * 检查 User-Agent 是否被排除
     */
    private boolean isExcludedUserAgent(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        if (userAgent == null || properties.getExcludedUserAgents() == null) {
            return false;
        }

        for (String pattern : properties.getExcludedUserAgents()) {
            if (userAgent.contains(pattern)) {
                return true;
            }
        }
        return false;
    }

    /**
     * GZIP 响应包装器
     */
    private class GzipResponseWrapper extends HttpServletResponseWrapper {
        private ByteArrayOutputStream byteArrayOutputStream;
        private GZIPOutputStream gzipOutputStream;
        private PrintWriter printWriter;
        private boolean isGzipped = false;

        public GzipResponseWrapper(HttpServletResponse response) {
            super(response);
        }

        @Override
        public ServletOutputStream getOutputStream() throws IOException {
            if (printWriter != null) {
                throw new IllegalStateException("getWriter() has already been called");
            }

            if (gzipOutputStream == null) {
                byteArrayOutputStream = new ByteArrayOutputStream();
                gzipOutputStream = new GZIPOutputStream(byteArrayOutputStream);
            }

            return new ServletOutputStream() {
                @Override
                public void write(int b) throws IOException {
                    gzipOutputStream.write(b);
                }

                @Override
                public void write(byte[] b) throws IOException {
                    gzipOutputStream.write(b);
                }

                @Override
                public void write(byte[] b, int off, int len) throws IOException {
                    gzipOutputStream.write(b, off, len);
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setWriteListener(WriteListener listener) {
                    // No-op
                }
            };
        }

        @Override
        public PrintWriter getWriter() throws IOException {
            if (printWriter == null) {
                if (gzipOutputStream != null) {
                    throw new IllegalStateException("getOutputStream() has already been called");
                }
                byteArrayOutputStream = new ByteArrayOutputStream();
                gzipOutputStream = new GZIPOutputStream(byteArrayOutputStream);
                printWriter = new PrintWriter(new OutputStreamWriter(gzipOutputStream, getCharacterEncoding()));
            }
            return printWriter;
        }

        @Override
        public void flushBuffer() throws IOException {
            if (printWriter != null) {
                printWriter.flush();
            }
            if (gzipOutputStream != null) {
                gzipOutputStream.flush();
            }
        }

        public void finish() throws IOException {
            if (printWriter != null) {
                printWriter.close();
            }
            if (gzipOutputStream != null) {
                gzipOutputStream.finish();
            }

            byte[] compressedData = byteArrayOutputStream != null ? byteArrayOutputStream.toByteArray() : new byte[0];

            HttpServletResponse httpResponse = (HttpServletResponse) getResponse();
            // 检查是否需要压缩
            if (compressedData.length >= properties.getMinResponseSize() && shouldCompress()) {
                isGzipped = true;
                httpResponse.setHeader("Content-Encoding", "gzip");
                httpResponse.setContentLength(compressedData.length);
                httpResponse.getOutputStream().write(compressedData);
            } else if (byteArrayOutputStream != null) {
                // 不压缩，直接输出原始数据
                httpResponse.setContentLength(compressedData.length);
                httpResponse.getOutputStream().write(compressedData);
            }
        }

        private boolean shouldCompress() {
            String contentType = getResponse().getContentType();
            if (contentType == null || properties.getMimeTypes() == null) {
                return false;
            }

            for (String mimeType : properties.getMimeTypes()) {
                if (contentType.contains(mimeType)) {
                    return true;
                }
            }
            return false;
        }
    }
}
