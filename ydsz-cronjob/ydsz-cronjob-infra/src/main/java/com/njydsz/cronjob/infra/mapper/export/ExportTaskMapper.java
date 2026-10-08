package com.njydsz.cronjob.infra.mapper.export;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.cronjob.domain.entity.export.ExportTask;

/**
 * 异步导出任务 MyBatis-Plus Mapper 接口。
 *
 * <p>YDIZ-DDD-007：Mapper 位于 infra 层，通过 domain 层定义的 Entity 进行 ORM 操作。
 *
 * @author ydsz-team
 * @since 26.10.13
 */
@Mapper
public interface ExportTaskMapper extends BaseMapper<ExportTask> {
}
