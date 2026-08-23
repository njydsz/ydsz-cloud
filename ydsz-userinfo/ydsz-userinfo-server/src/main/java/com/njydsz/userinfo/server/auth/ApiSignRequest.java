package com.njydsz.userinfo.server.auth;

/**
 * API 签名请求要素值对象。
 *
 * <p>封装签名生成与验证所需的全部请求要素，避免方法参数过多（云顶编码规范 5.4 节参数数量限制）。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @param method HTTP 方法（GET/POST/PUT/DELETE 等）
 * @param path 请求路径（如 /api/internal/user/info）
 * @param query 查询字符串（为空时使用空字符串，不使用 null）
 * @param body 请求体（为空时使用空字符串，不使用 null）
 * @param timestamp 签名时间戳（毫秒 Unix epoch）
 * @param nonce 一次性随机字符串
 */
public record ApiSignRequest(
    String method,
    String path,
    String query,
    String body,
    long timestamp,
    String nonce) {
}
