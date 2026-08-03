package com.njydsz.common.util.http;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;

/**
 * HTTP 响应工具类
 * <p>提供文件流导出到客户端的工具方法。
 *
 * @author ydsz-team
 * @since 1.0.0
 * 
 */
public class ResponseUtils {

    private ResponseUtils() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * 将文件流写出到客户端作为附件下载
     *
     * <p><b>资源管理说明</b>：
     * <ul>
     *   <li>入参 {@link InputStream} 由调用方在 finally 中关闭（try-with-resources 推荐）</li>
     *   <li>{@link ServletOutputStream} 由 Servlet 容器管理，本方法仅 flush 不 close，
     *       手动 close 会干扰容器后续的 commit 与连接复用</li>
     * </ul>
     *
     * @param is         输入流（文件内容来源），调用方负责关闭
     * @param objectName 下载文件时的显示文件名
     * @param response   HttpServletResponse
     * @throws IOException IO 异常
     */
    public static void write(InputStream is, String objectName, HttpServletResponse response) throws IOException {
        ServletOutputStream outputStream = response.getOutputStream();
        response.setContentType("application/x-msdownload");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Content-Disposition", "attachment;fileName=" + URLEncoder.encode(objectName, StandardCharsets.UTF_8));

        byte[] buffer = new byte[4096];
        int bytesRead;
        while ((bytesRead = is.read(buffer)) != -1) {
            outputStream.write(buffer, 0, bytesRead);
        }
        outputStream.flush();
        // 不手动 close ServletOutputStream：容器管理的输出流由容器在响应 commit 时关闭
        // 入参 InputStream 由调用方负责关闭（推荐 try-with-resources）
    }
}
