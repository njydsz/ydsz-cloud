package com.njydsz.message.domain.entity;

import java.io.Serial;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseEntity;

/**
 * 消息变量数据源绑定实体，定义模板占位符(var)到外部数据源的映射关系。
 *
 * <p>对应数据库表 {@code ydsz_msg_variable_source}。templateCode + variableName
 * 粒度绑定数据源，sourceType 支持 BEAN/SQL/HTTP/STATIC 四种类型，
 * sourceExpr 为数据源查询表达式，cacheTtl 控制缓存有效期（秒）。
 * 渲染前自动拉取变量值，免除调用方手动传入所有参数。
 *
 * @author ydsz
 * @since 26.09.24
 */@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_msg_variable_source")
public class MsgVariableSource extends MpBaseEntity<String> {

  @Serial private static final long serialVersionUID = 1L;

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
}
