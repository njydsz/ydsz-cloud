package com.njydsz.common.seata.api;

/**
 * XID 签名器接口
 *
 * <p>用于防止 XID 伪造注入攻击，确保跨服务传播的 XID 来自受信任的源。
 *
 * <p><b>P0-4 修复</b>：此前 XID 作为 HTTP Header 明文传输，攻击者可伪造 XID
 * 绑定到全局事务上下文，导致跨租户事务上下文污染和审计日志伪造。
 *
 * <p>实现方式：
 * <ul>
 *   <li>HMAC-SHA256 签名：使用共享密钥对 XID + 时间戳签名，防止篡改</li>
 *   <li>空实现：测试环境可关闭签名校验以提高性能</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.3.0
 */
public interface XidSigner {

    /**
     * 生成 XID 传输格式（包含签名）
     *
     * <p>格式：{@code base64(xid).base64(timestamp).base64(signature)}
     * 接收方通过相同算法验证签名有效性。
     *
     * @param xid 原始全局事务 ID
     * @return 带签名的传输格式，无需签名时返回原值
     */
    String sign(String xid);

    /**
     * 验证并解析 XID 传输格式
     *
     * <p>从带签名的传输格式中验证签名有效性并提取原始 XID。
     * 签名无效或格式错误时返回 null，上层应拒绝绑定。
     *
     * @param signedXid 带签名的传输格式
     * @return 验证通过则返回原始 XID，否则返回 null
     */
    String verify(String signedXid);
}
