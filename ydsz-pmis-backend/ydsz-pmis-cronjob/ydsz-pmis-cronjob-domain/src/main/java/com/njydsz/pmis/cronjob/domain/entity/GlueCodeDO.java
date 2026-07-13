package com.njydsz.pmis.cronjob.domain.entity.schedule;

import java.io.Serial;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.pmis.common.domain.entity.BaseDO;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * GLUE 在线编码实体（P1-2 GLUE 在线编码，P1-7 多语言支持扩展）。
 *
 * <p>对应 {@code pmis_job_glue} 表，存储 GLUE 类型任务的在线编辑代码及版本历史。
 * 每次保存产生一个新版本（version 递增），调度执行时取最新版本编译运行，
 * 支持按版本回滚（回滚动作本身也是创建一个新版本）。
 *
 * <h3>语言支持（P1-7 扩展）</h3>
 * <ul>
 *   <li>{@code GROOVY}（默认）: 通过 GroovyClassLoader 动态编译执行</li>
 *   <li>{@code PYTHON}: 通过 SandboxScriptExecutor 在沙箱中执行 Python3 脚本</li>
 *   <li>{@code SHELL}: 通过 SandboxScriptExecutor 在沙箱中执行 Bash 脚本</li>
 *   <li>{@code JAVASCRIPT}: 通过 ScriptEngine（Nashorn/GraalJS）执行</li>
 *   <li>{@code JAVA}: 预留扩展，当前按 GROOVY 处理</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pmis_job_glue")
public class GlueCodeDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 任务 ID（关联 pmis_job.id） */
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
