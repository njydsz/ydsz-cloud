package com.njydsz.workflow.infra.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.workflow.domain.entity.FlowArchiveCursor;

/**
 * 流程归档游标 Mapper。
 *
 * <p>基于 MyBatis-Plus BaseMapper，提供 ydsz_flow_archive_cursor 表的 CRUD 操作。
 *
 * @author ydsz-team
 * @since 26.09.23
 */
@Mapper
public interface FlowArchiveCursorMapper extends BaseMapper<FlowArchiveCursor> {
}
