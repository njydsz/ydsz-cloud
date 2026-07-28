package com.njydsz.project.domain.entity.satisfaction;

import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.common.jdbc.entity.MpBaseEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 客户满意度实体。
 *
 * <p>对应数据库表 {@code ydsz_satisfaction}，记录客户对项目交付的满意度评价。
 * 通过定期调研收集客户反馈，作为项目管理持续改进的数据支撑。
 *
 * <p><b>评估维度：</b>
 * <ul>
 *   <li>交付质量：产品/服务满足需求的程</li>
 *   <li>沟通协作：项目团队沟通效率与态度</li>
 *   <li>响应速度：问题响应和处理时效</li>
 *   <li>总体评价：综合满意度评分</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see ProjectInitiation 项目立项
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("ydsz_satisfaction")
public class Satisfaction extends MpBaseEntity<String> {


}
