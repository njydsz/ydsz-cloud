package com.njydsz.message.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import com.njydsz.message.domain.entity.MsgCanary;

/**
 * 灰度实验 Mapper
 *
 * <p>对应数据表 <code>ydsz_msg_canary</code>。提供灰度实验的 CRUD 能力，继承 MyBatis-Plus BaseMapper 获得基础增删改查。
 *
 * <p><b>主要索引：</b>
 *
 * <ul>
 *   <li>uk_canary_key — 实验唯一键唯一索引
 *   <li>idx_template_code — 模板编码过滤索引
 *   <li>idx_status — 状态过滤索引
 * </ul>
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see MsgCanary 灰度实验持久化实体
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
 */
@Mapper
public interface MsgCanaryMapper extends BaseMapper<MsgCanary> {}
