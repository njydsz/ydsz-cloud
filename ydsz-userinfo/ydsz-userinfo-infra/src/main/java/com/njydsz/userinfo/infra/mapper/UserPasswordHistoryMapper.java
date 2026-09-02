package com.njydsz.userinfo.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import com.njydsz.userinfo.infra.entity.UserPasswordHistory;

/**
 * 密码历史 Mapper 接口
 *
 * <p>对应数据表 {@code ydsz_acct_password_history}，提供密码历史记录的 CRUD 操作。
 *
 * <p><b>主要查询场景：</b>
 *
 * <ul>
 *   <li>修改密码时：查询用户最近 N 条历史密码，校验新密码是否与历史密码重复
 *   <li>清理历史：保留最近 N 条，删除更早的记录
 *   <li>用户删除：按 userId 清理所有历史记录
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see UserPasswordHistory 密码历史实体
 */
@Mapper
public interface UserPasswordHistoryMapper extends BaseMapper<UserPasswordHistory> {}
