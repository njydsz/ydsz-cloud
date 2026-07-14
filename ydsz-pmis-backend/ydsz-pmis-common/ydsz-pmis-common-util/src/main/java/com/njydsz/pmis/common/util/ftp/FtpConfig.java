package com.njydsz.pmis.common.util.ftp;

import lombok.Builder;
import lombok.Getter;

/**
 * FTP 连接配置
 *
 * <p>封装 FTP 服务器的连接参数，基于 Lombok {@code @Builder} 提供流式构建能力。
 * 本类为不可变对象，所有字段均为 final，线程安全。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * FtpConfig config = FtpConfig.builder()
 *     .host("ftp.example.com")
 *     .port(21)
 *     .username("admin")
 *     .password("secret")
 *     .basePath("/upload")
 *     .build();
 *
 * FtpUtils.upload(config, localFile, remotePath);
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Getter
@Builder
public class FtpConfig {
    /**
     * FTP 服务器地址
     *
     * <p>支持 IP 地址或域名，例如 {@code 192.168.1.1} 或 {@code ftp.example.com}。
     */
    private final String host;

    /**
     * FTP 服务器端口
     *
     * <p>默认值为 21（FTP 标准端口），可使用 22（SFTP）或自定义端口。
     */
    @Builder.Default
    private final int port = 21;

    /**
     * FTP 登录用户名
     *
     * <p>用于 FTP 服务器的身份认证，匿名访问时使用 "anonymous"。
     */
    private final String username;

    /**
     * FTP 登录密码
     *
     * <p>与 username 配合完成身份认证。
     */
    private final String password;

    /**
     * 基础路径
     *
     * <p>所有文件操作的基础目录，例如 {@code /upload}。
     * 上传下载时会在此路径下进行拼接。
     */
    private final String basePath;
}
