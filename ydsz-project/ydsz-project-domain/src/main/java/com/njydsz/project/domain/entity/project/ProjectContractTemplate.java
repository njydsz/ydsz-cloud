package com.njydsz.project.domain.entity.project;

import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.common.jdbc.entity.MpBaseEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 合同模板实体。
 *
 * <p>对应数据库表 {@code ydsz_project_contract_template}，存储合同标准模板。
 * 模板预定义合同条款、费率、履约条件，用于快速创建标准化合同。
 *
 * <p><b>使用场景：</b>
 * <ul>
 *   <li>新建合同时引用模板生成合同草案</li>
 *   <li>标准化合同条款管理（法务审批后固化）</li>
 *   <li>不同业务类型对应不同模板（如软件服务/SaaS/咨询）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see ProjectContract 合同主表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("ydsz_project_contract_template")
public class ProjectContractTemplate extends MpBaseEntity<String> {


}
