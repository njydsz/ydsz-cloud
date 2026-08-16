package com.njydsz.common.seata.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import com.njydsz.common.seata.api.XidPropagator;
import com.njydsz.common.seata.api.XidSigner;
import com.njydsz.common.seata.context.XidContextHolder;

/**
 * 默认 XID 传播器实现
 *
 * <p>使用独立的 {@link XidContextHolder} 存储 XID，支持 HTTP Header 和 MQ 属性的序列化/反序列化。
 * 通过 SPI 注入 {@link XidSigner} 实现签名校验，防止 XID 伪造注入。
 *
 * <p><b>P0-4 修复</b>：集成签名校验机制，当配置签名密钥时对 XID 进行 HMAC-SHA256 签名，
 * 下游服务验证签名有效后才绑定到上下文，防止恶意 XID 注入。
 *
 * <p><b>P2-3 修复</b>：不再委托 {@code AbstractTransactionManager} 包级私有方法，
 * 改为直接依赖 {@link XidContextHolder}，消除循环依赖。
 *
 * <h3>XID 传播流程：</h3>
 * <ol>
 *   <li>上游服务：{@code currentXid()} 获取 XID → {@code serialize(xid)} 序列化（含签名）</li>
 *   <li>HTTP Header：{@code X-XID} 或事务上下文特定 Header 传输</li>
 *   <li>下游服务：{@code deserialize(header)} 反序列化（校验签名）→ {@code bind(xid)} 绑定到线程</li>
 * </ol>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class DefaultXidPropagator implements XidPropagator {

    private static final Logger log = LoggerFactory.getLogger(DefaultXidPropagator.class);

    private final ObjectProvider<XidSigner> signerProvider;

    /**
     * 构造默认 XID 传播器（无签名校验，向后兼容）
     */
    public DefaultXidPropagator() {
        this.signerProvider = null;
    }

    /**
     * 构造默认 XID 传播器（带签名校验）
     *
     * @param signerProvider XID 签名器提供者（可选）
     */
    public DefaultXidPropagator(ObjectProvider<XidSigner> signerProvider) {
        this.signerProvider = signerProvider;
    }

    /**
     * 将 XID 序列化为传输格式
     *
     * <p>当签名器可用时，对 XID 进行签名后再序列化，格式为：
     * {@code base64(xid):base64(timestamp):base64(signature)}。
     * 无签名器时直接返回原值（向后兼容）。
     *
     * @param xid 全局事务 ID
     * @return 序列化后的字符串
     */
    @Override
    public String serialize(String xid) {
        if (xid == null) {
            return null;
        }
        XidSigner signer = getSigner();
        if (signer != null) {
            return signer.sign(xid);
        }
        return xid;
    }

    /**
     * 从传输格式反序列化 XID
     *
     * <p>当签名器可用时，验证签名有效性后才提取 XID。
     * 签名无效返回 null，上层应拒绝绑定。
     * 无签名器时直接返回原值（向后兼容）。
     *
     * @param header 传输内容
     * @return 解析出的 XID，无效时返回 null
     */
    @Override
    public String deserialize(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        XidSigner signer = getSigner();
        if (signer != null) {
            String verified = signer.verify(header);
            if (verified == null) {
                log.warn("XID signature verification failed, rejecting XID binding");
            }
            return verified;
        }
        return header.trim();
    }

    /**
     * 将 XID 绑定到当前线程的 {@link XidContextHolder}
     *
     * @param xid 全局事务 ID
     */
    @Override
    public void bind(String xid) {
        if (xid != null) {
            XidContextHolder.setXid(xid);
            log.debug("XID bound to current thread: {}", xid);
        }
    }

    /**
     * 从当前线程获取 XID
     *
     * @return 当前线程的 XID，无事务上下文时返回 null
     */
    @Override
    public String currentXid() {
        return XidContextHolder.getXid();
    }

    /**
     * 清除当前线程的 XID 绑定
     */
    @Override
    public void unbind() {
        XidContextHolder.remove();
    }

    /**
     * 获取签名器实例（如有）
     *
     * @return 签名器实例，未配置时返回 null
     */
    private XidSigner getSigner() {
        return signerProvider != null ? signerProvider.getIfAvailable() : null;
    }
}
