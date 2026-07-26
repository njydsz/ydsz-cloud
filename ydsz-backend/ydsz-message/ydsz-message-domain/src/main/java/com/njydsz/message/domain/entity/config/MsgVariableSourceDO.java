package com.njydsz.message.domain.entity.config;

import java.io.Serial;

import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.common.jdbc.entity.MpBaseEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 消息变量数据源绑定表。
 *
 * <p>P0-4: 模板变量可绑定到数据源(BEAN/SQL/HTTP/STATIC),
 * 渲染前自动拉取变量值,免除调用方手动传入所有参数。
 *
 * @author ydsz-team
 * @since 1.5.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_msg_variable_source")
public class MsgVariableSourceDO extends MpBaseEntity<String> {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 模板编码 */
    private String templateCode;

    /** 变量名(与模板 ${var} 对应) */
    private String variableName;

    /** 数据源类型: BEAN/SQL/HTTP/STATIC */
    private String sourceType;

    /** 数据源表达式 */
    private String sourceExpr;

    /** 缓存有效期(秒),0=不缓存 */
    private Integer cacheTtl;

    /** 描述说明 */
    private String description;

    /** 租户 ID */
    private String tenantId;
}
