package com.njydsz.project.domain.entity.rate;

import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.common.jdbc.entity.MpBaseEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 费率卡实体。
 *
 * <p>对应数据库表 {@code ydsz_rate_card}，定义项目计费的人天费率标准。
 * 费率卡是商务报价和成本核算的基础，按角色/岗位设置不同费率。
 *
 * <p><b>核心字段：</b>
 * <ul>
 *   <li>{@code roleCode}：角色编码（PM/DEV/QA/SA）</li>
 *   <li>{@code unitPrice}：人天单价</li>
 *   <li>{@code currency}：币种（CNY/USD）</li>
 *   <li>{@code effectiveDate} / {@code expiryDate}：费率有效期</li>
 *   <li>{@code rateType}：费率类型（STANDARD / PREFERENTIAL / OVERSEAS）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("ydsz_rate_card")
public class RateCard extends MpBaseEntity<String> {


}
