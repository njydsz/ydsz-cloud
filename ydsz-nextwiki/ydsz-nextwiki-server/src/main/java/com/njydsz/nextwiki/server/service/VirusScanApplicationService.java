package com.njydsz.nextwiki.server.service;

import java.io.InputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.njydsz.nextwiki.server.config.NextwikiProperties;

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
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VirusScanApplicationService {

    private final NextwikiProperties properties;

    /** 最大文件大小限制（100MB） */
    private static final long MAX_FILE_SIZE = 100L * 1024 * 1024;

    /** ClamAV 协议常量 */
    // INSTREAM 命令：以 z 前缀长度帧流式发送文件内容（见 ClamAV INSTREAM 协议）
    private static final byte[] CMD_INSTREAM = zCommand("zINSTREAM\u0000");
    /** INSTREAM 单次发送分块大小（字节） */
    private static final int CHUNK_SIZE = 4096;

    /**
     * 扫描输入流（门面方法：前置校验 + 委托 {@link #doScan} + 异常兜底）。
     * <p>未启用 / 文件超过 {@link #MAX_FILE_SIZE} 时返回 skipped；扫描异常被捕获并返回 error，不向上抛。
     *
     * @param inputStream 文件输入流（方法内读取，调用方负责关闭）
     * @param fileSize    文件大小（字节），用于超限快速跳过
     * @return 扫描结果 {@link ScanResult}（clean/infected/skipped/error）
     * @complexity 正常为一次 ClamAV TCP 往返（O(fileSize) 流式传输）；超限/未启用为 O(1)
     * @note 本方法不抛异常，调用方需按 {@link ScanResult#isInfected()} 决策
     * @concurrency 无共享可变状态，线程安全；每次扫描新建独立 Socket
     */
    public ScanResult scan(InputStream inputStream, long fileSize) {
        if (!properties.getVirusScan().isEnabled()) {
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
        String host = properties.getVirusScan().getHost();
        int port = properties.getVirusScan().getPort();
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
    @Data
    @Builder
    public static class ScanResult {
        /** 是否扫描通过（无病毒） */
        private boolean clean;
        /** 是否检出病毒 */
        private boolean infected;
        /** 是否跳过（未启用或超限） */
        private boolean skipped;
        /** 是否扫描出错（连接/协议异常） */
        private boolean error;
        /** 结果描述（OK / 病毒名 / 跳过原因 / 错误信息） */
        private String message;

        /**
         * 构造「扫描通过」结果，即 ClamAV 明确回复 OK。
         *
         * <p>四个状态位中<b>只有本结果</b>代表文件确已被查杀引擎放行。
         * {@code skipped} 与 {@code error} 均<b>未</b>完成实际查杀，调用方
         * 若以「非 infected 即安全」做判断会放过未扫描文件，务必显式判 {@code clean}。
         *
         * @return 通过结果，{@code clean=true}、{@code message="OK"}
         */
        public static ScanResult clean() {
            return ScanResult.builder().clean(true).message("OK").build();
        }

        /**
         * 构造「检出病毒」结果。
         *
         * <p>命中即应阻断上传并留档告警。message 统一格式化为
         * {@code "FOUND: {virusName}"}，与 ClamAV 原始应答保持一致，便于日志检索与对账。
         *
         * @param virusName ClamAV 回报的病毒特征名，从应答行中截取
         * @return 检出结果，{@code infected=true}
         */
        public static ScanResult infected(String virusName) {
            return ScanResult.builder().infected(true).message("FOUND: " + virusName).build();
        }

        /**
         * 构造「跳过扫描」结果。
         *
         * <p>用于扫描开关关闭、或文件超过 {@link VirusScanApplicationService#MAX_FILE_SIZE}
         * 这类<b>主动放弃</b>的场景——超大文件走 ClamAV INSTREAM 会长时间占用连接，
         * 故以放行换吞吐。此时文件<b>未经查杀</b>，安全等级由上层策略自行决定。
         *
         * @param reason 跳过原因（如 {@code "病毒扫描未启用"}、{@code "文件超过大小限制"}）
         * @return 跳过结果，{@code skipped=true}
         */
        public static ScanResult skipped(String reason) {
            return ScanResult.builder().skipped(true).message(reason).build();
        }

        /**
         * 构造「扫描出错」结果。
         *
         * <p>对应 ClamAV 连接失败、协议应答无法识别等异常；异常已在
         * {@link VirusScanApplicationService#scan} 内被吞掉转为本结果，<b>不向上抛出</b>，
         * 避免查杀服务抖动直接压垮上传链路。同样属于「未完成查杀」，不可当作安全。
         *
         * @param message 错误描述，含原始应答或异常摘要
         * @return 错误结果，{@code error=true}
         */
        public static ScanResult error(String message) {
            return ScanResult.builder().error(true).message(message).build();
        }
    }
}
