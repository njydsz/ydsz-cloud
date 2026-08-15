package com.njydsz.common.seata.impl;

import com.njydsz.common.seata.api.XidSigner;

/**
 * 空实现 XID 签名器（不进行签名校验）
 *
 * <p>用于以下场景：
 * <ul>
 *   <li>开发/测试环境，无需安全校验</li>
 *   <li>向后兼容：与旧版本服务交互时，旧版本未实现签名机制</li>
 *   <li>内部可信网络：所有服务部署在受信任的内网环境中</li>
 * </ul>
 *
 * <p><b>注意</b>：生产环境建议使用 {@link HmacXidSigner} 提供签名保护。
 *
 * @author ydsz-team
 * @since 1.3.0
 */
public class NoopXidSigner implements XidSigner {

    /**
     * 不签名，直接返回原值
     *
     * @param xid 原始 XID
     * @return 原值
     */
    @Override
    public String sign(String xid) {
        return xid;
    }

    /**
     * 不校验，直接返回原值
     *
     * @param signedXid 传输格式
     * @return 原值
     */
    @Override
    public String verify(String signedXid) {
        return signedXid;
    }
}
