package com.njydsz.common.jdbc.entity;

import java.io.Serializable;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * MyBatis-Plus 增强版主键基础实体
 *
 * <p>仅含主键 ID 的实体基类，主键使用雪花算法自动生成（{@link IdType#ASSIGN_ID}）。
 *
 * <p><b>1.0.0</b>：不再继承 common-domain 的 BaseIdEntity，字段内联自洽， 业务模块实体仅依赖 ydsz-common-jdbc 一个模块。
 *
 * @param <T> 主键ID类型
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@SuppressWarnings("unchecked")
public class MpBaseIdEntity<T extends Serializable> implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 主键ID，使用雪花算法自动生成 */
  @TableId(type = IdType.ASSIGN_ID)
  private T id;
}
