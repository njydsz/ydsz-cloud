package com.njydsz.common.file.domain;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 前端直传签名结果
 * <p>封装服务端生成的直传凭证，前端可直接使用该凭证上传文件到云存储。
 *
 * @author ydsz-team
 * @since 1.0.0
 * 
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PolicyResult {

    /**
     * 访问密钥ID（部分云厂商使用）
     */
    private String accessKeyId;

    /**
     * 签名策略（Base64编码的Policy）
     */
    private String policy;

    /**
     * 签名（Policy的HMAC签名）
     */
    private String signature;

    /**
     * 上传目标存储桶
     */
    private String bucket;

    /**
     * 上传目标路径前缀
     */
    private String objectKeyPrefix;

    /**
     * 上传到期时间（Unix时间戳，秒）
     */
    private Long expiration;

    /**
     * 上传区域（部分云厂商需要，如AWS）
     */
    private String region;

    /**
     * 云存储访问端点
     */
    private String endpoint;

    /**
     * 额外自定义参数
     */
    private Map<String, String> extraData;
}