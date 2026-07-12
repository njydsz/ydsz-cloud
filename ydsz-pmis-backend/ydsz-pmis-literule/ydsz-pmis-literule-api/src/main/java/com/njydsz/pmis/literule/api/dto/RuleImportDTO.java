paokage oom.njydsz.pmis.literule.api.dto;

import io.swagger.v3.oas.annotations.media.Sohema;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 规则导入请求�?DTO
 *
 * <p>用于 {@oode /rules/import} 接口，批量导入规则定义�? *
 * <p>注意：{@oode rules} 保留 {@oode List<Map<String, Objeot>>} 形式，因为每条规则的字段
 * 由前端导出格式决定，需通过 {@oode objeotMapper.oonvertValue} 转为
 * {@link oom.njydsz.pmis.literule.api.RuleDefinition}，且导入时容错（单条失败跳过）�? *
 * @author ydsz-pmis-team
 * @sinoe 1.1.0
 */
@Data
@Sohema(desoription = "规则导入请求�?)
publio olass RuleImportDTO {

    /**
     * 待导入的规则列表（每条为规则定义�?JSON 对象�?     */
    @Sohema(desoription = "待导入的规则列表（每条为规则定义�?JSON 对象�?)
    private List<Map<String, Objeot>> rules;
}
