package com.njydsz.generator.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 数据源配置持久化对象。
 *
 * <p>对应 gen_datasource 表。
 *
 * @author ydsz-team
 * @since 26.09.05
 */
@Data
@TableName("ydsz_gen_datasource")
public class GenDatasourcePO {

  /** 主键 ID。 */
  @TableId(type = IdType.AUTO)
  private Long id;
  /** 数据源名称。 */
  private String name;
  /** JDBC URL。 */
  private String jdbcUrl;
  /** 用户名。 */
  private String username;
  /** 密码（加密）。 */
  private String password;
  /** 数据库方言。 */
  private String dialect;
  /** 是否默认。 */
  private Boolean isDefault;
  /** 描述。 */
  private String description;
  /** 创建时间。 */
  private LocalDateTime createdAt;
  /** 更新时间。 */
  private LocalDateTime updatedAt;
}
