package com.njydsz.pmis.nextwiki.server.service;

import java.io.InputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * 病毒扫描应用服务
 * <p>
 * 集成 ClamAV 进行文件病毒扫描，支持 INSTREAM 模式。
 *
 * <p><b>ClamAV 集成：</b>
 * <ul>
 *   <li>通过 TCP 连接 ClamAV daemon（默认端口 3310）</li>
 *   <li>使用 INSTREAM 命令流式扫描文件内容</li>
 *   <li>返回扫描结果：OK / FOUND / ERROR</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
@Slf4j
@Service
public class VirusScanApplicationService {

    @Value("${nextwiki.virus-scan.enabled:false}")
    private boolean enabled;

    @Value("${nextwiki.virus-scan.host:localhost}")
    private String host;

    @Value("${nextwiki.virus-scan.port:3310}")
    private int port;

    /** 最大文件大小限制（100MB） */
    private static final long MAX_FILE_SIZE = 100L * 1024 * 1024;

    /** ClamAV 协议常量 */
    private static final byte[] CMD_INSTREAM = zCommand("zINSTREAM\u0000");
    private static final int CHUNK_SIZE = 4096;

    /**
     * 扫描输入流
     *
     * @param inputStream 文件输入流
     * @param fileSize    文件大小
     * @return 扫描结果
     */
    public ScanResult scan(InputStream inputStream, long fileSize) {
        if (!enabled) {
            return ScanResult.skipped("病毒扫描未启用");
        }

        if (fileSize > MAX_FILE_SIZE) {
            log.warn("[VirusScanApplicationService] 文件超过大小限制，跳过扫描: size={}", fileSize);
            return ScanResult.skipped("文件超过大小限制");
        }

        try {
            return doScan(inputStream);
        } catch (Exception e) {
            log.error("[VirusScanApplicationService] 病毒扫描异常", e);
            return ScanResult.error("扫描异常: " + e.getMessage());
        }
    }

    /**
     * 执行 ClamAV INSTREAM 扫描
     */
    private ScanResult doScan(InputStream inputStream) throws Exception {
        try (Socket socket = new Socket(host, port)) {
            var out = socket.getOutputStream();
            var in = socket.getInputStream();

            // 发送 INSTREAM 命令
            out.write(CMD_INSTREAM);
            out.flush();

            // 流式发送文件内容
            byte[] buffer = new byte[CHUNK_SIZE];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                // 发送 chunk 长度（4 字节，大端序）
                byte[] chunkSize = ByteBuffer.allocate(4)
                        .order(ByteOrder.BIG_ENDIAN)
                        .putInt(bytesRead)
                        .array();
                out.write(chunkSize);
                out.write(buffer, 0, bytesRead);
            }

            // 发送结束标记（0 长度 chunk）
            out.write(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(0).array());
            out.flush();

            // 读取响应
            byte[] response = in.readAllBytes();
            String result = new String(response).trim();

            if (result.contains("OK")) {
                log.info("[VirusScanApplicationService] 扫描通过: {}", result);
                return ScanResult.clean();
            } else if (result.contains("FOUND")) {
                String virusName = result.replaceAll(".*FOUND: ", "").trim();
                log.warn("[VirusScanApplicationService] 检测到病毒: {}", virusName);
                return ScanResult.infected(virusName);
            } else {
                log.error("[VirusScanApplicationService] 扫描错误: {}", result);
                return ScanResult.error(result);
            }
        }
    }

    /**
     * 构建 ClamAV z 命令
     */
    private static byte[] zCommand(String command) {
        byte[] cmdBytes = command.getBytes();
        byte[] result = new byte[cmdBytes.length];
        System.arraycopy(cmdBytes, 0, result, 0, cmdBytes.length);
        return result;
    }

    /**
     * 扫描结果
     */
    @lombok.Data
    @lombok.Builder
    public static class ScanResult {
        private boolean clean;
        private boolean infected;
        private boolean skipped;
        private boolean error;
        private String message;

        public static ScanResult clean() {
            return ScanResult.builder().clean(true).message("OK").build();
        }

        public static ScanResult infected(String virusName) {
            return ScanResult.builder().infected(true).message("FOUND: " + virusName).build();
        }

        public static ScanResult skipped(String reason) {
            return ScanResult.builder().skipped(true).message(reason).build();
        }

        public static ScanResult error(String message) {
            return ScanResult.builder().error(true).message(message).build();
        }
    }
}
