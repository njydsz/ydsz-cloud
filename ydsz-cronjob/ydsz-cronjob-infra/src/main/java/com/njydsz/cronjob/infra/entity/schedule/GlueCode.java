package com.njydsz.cronjob.infra.entity.schedule;

import java.io.Serial;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseEntity;

/**
 * GLUE 在线编码实体（P1-2 GLUE 在线编码，P1-7 多语言支持扩展）。
 *
 * <p>对应 {@code ydsz_job_glue} 表，存储 GLUE 类型任务的在线编辑代码及版本历史。 每次保存产生一个新版本（version 递增），调度执行时取最新版本编译运行，
 * 支持按版本回滚（回滚动作本身也是创建一个新版本）。
 *
 * <h3>语言支持（P1-7 扩展）</h3>
 *
 * <ul>
 *   <li>{@code GROOVY}（默认）: 通过 GroovyClassLoader 动态编译执行
 *   <li>{@code PYTHON}: 通过 SandboxScriptExecutor 在沙箱中执行 Python3 脚本
 *   <li>{@code SHELL}: 通过 SandboxScriptExecutor 在沙箱中执行 Bash 脚本
 *   <li>{@code JAVASCRIPT}: 通过 ScriptEngine（Nashorn/GraalJS）执行
 *   <li>{@code JAVA}: 预留扩展，当前按 GROOVY 处理
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_job_glue")
public class GlueCode extends MpBaseEntity<String> {

  @Serial private static final long serialVersionUID = 1L;

  /** 任务 ID（关联 ydsz_job.id） */
  private String jobId;

  /** 源代码（Groovy/Python/Shell/JavaScript 脚本内容） */
  private String sourceCode;

  /** 语言: GROOVY(默认) / PYTHON / SHELL / JAVASCRIPT / JAVA */
  private String language;

  /** 版本号（从 1 递增） */
  private Integer version;

  /** 版本备注 */
  private String remark;
}
