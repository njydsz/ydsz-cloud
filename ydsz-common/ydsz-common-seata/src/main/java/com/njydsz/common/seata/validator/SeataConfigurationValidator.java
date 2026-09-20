package com.njydsz.common.seata.validator;

import com.njydsz.common.seata.config.SeataProperties;
import java.util.Set;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Seata 启动期配置校验器。
 *
 * <p>在 {@link PostConstruct} 中对 {@link SeataProperties} 进行完整性校验，
 * 发现不合规配置时立即抛出异常（Fail Fast 策略），避免运行期才发现配置错误。
 *
 * <p><b>校验规则：</b>
 * <ul>
 *   <li>AT 模式必须 {@code enable-auto-data-source-proxy=true}</li>
 *   <li>{@code undo-log.serialization} 必须在允许值域内（jackson/kryo/fastjson/protobuf）</li>
 *   <li>{@code data-source-proxy-mode} 必须在允许值域内（AT/TCC/SAGA/XA）</li>
 *   <li>XID 签名密钥长度建议 ≥ 16 位（仅 WARN 非阻断）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.20
 */
public class SeataConfigurationValidator {

    /** 日志实例 */
    private static final Logger LOG = LoggerFactory.getLogger(SeataConfigurationValidator.class);

    /** 允许的数据源代理模式 */
    private static final Set<String> ALLOWED_PROXY_MODES = Set.of("AT", "TCC", "SAGA", "XA");

    /** 允许的 undo_log 序列化方式 */
    private static final Set<String> ALLOWED_SERIALIZATIONS =
        Set.of("jackson", "kryo", "fastjson", "protobuf", "kryo2", "fastjson2");

    /** Seata 配置属性 */
    private final SeataProperties properties;

    /**
     * 构造校验器。
     *
     * @param properties Seata 配置属性
     */
    public SeataConfigurationValidator(SeataProperties properties) {
        this.properties = properties;
    }

    /**
     * 执行配置校验。
     *
     * <p>在 Spring Bean 初始化后立即校验，不合规时抛出 {@link IllegalArgumentException} 阻止启动。
     */
    @PostConstruct
    public void validate() {
        if (!properties.isEnabled()) {
            return;
        }

        LOG.info("Validating Seata configuration...");

        // 校验数据源代理模式
        validateProxyMode();

        // 校验 undo_log 配置
        validateUndoLog();

        // 校验 AT 模式的数据源代理开关
        validateAutoDataSourceProxy();

        // XID 签名密钥强度检查（仅 WARN）
        validateXidSignSecret();

        LOG.info("Seata configuration validated successfully");
    }

    /**
     * 校验数据源代理模式是否在允许值域。
     */
    private void validateProxyMode() {
        String mode = properties.getDataSourceProxyMode();
        if (mode == null || !ALLOWED_PROXY_MODES.contains(mode.toUpperCase())) {
            throw new IllegalArgumentException(
                "ydsz.seata.data-source-proxy-mode 值不合法: " + mode
                    + " 可选值: " + ALLOWED_PROXY_MODES);
        }
    }

    /**
     * 校验 undo_log 序列化方式。
     */
    private void validateUndoLog() {
        String serialization = properties.getUndoLog().getSerialization();
        if (serialization == null
                || !ALLOWED_SERIALIZATIONS.contains(serialization.toLowerCase())) {
            throw new IllegalArgumentException(
                "ydsz.seata.undo-log.serialization 值不合法: " + serialization
                    + " 可选值: " + ALLOWED_SERIALIZATIONS);
        }
    }

    /**
     * 校验 AT 模式下数据源代理必须启用。
     */
    private void validateAutoDataSourceProxy() {
        if ("AT".equalsIgnoreCase(properties.getDataSourceProxyMode())
                && !properties.isEnableAutoDataSourceProxy()) {
            throw new IllegalArgumentException(
                "ydsz.seata.enable-auto-data-source-proxy 必须为 true 当 "
                    + "ydsz.seata.data-source-proxy-mode=AT");
        }
    }

    /**
     * 检查 XID 签名密钥强度（仅警告，不阻断）。
     */
    private void validateXidSignSecret() {
        String secret = properties.getXidSignSecret();
        if (secret != null && secret.length() < 16) {
            LOG.warn("ydsz.seata.xid-sign-secret 长度不足 16 位，生产环境建议增强密钥复杂度");
        }
    }
}
