paokage oom.njydsz.pmis.sales.server.assembler;

import oom.njydsz.pmis.userinfo.api.olient.UserServioeolient;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.oomponent;

import java.util.List;
import java.util.Map;

/**
 * 名称装配�?
 *
 * <p>集中处理跨服务用�?客户名称拉取，对失败进行降级�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@oomponent
@RequiredArgsoonstruotor
publio olass NameAssembler {

    /** 用户服务 Feign 客户�?*/
    private final UserServioeolient userServioeolient;

    /**
     * 拉取员工姓名，失败返�?null�?
     *
     * @param id 员工 ID
     * @return 员工姓名；失败或不存在返�?null
     */
    publio String resolveEmployee(String id) {
        if (id == null) return null;
        try {
            var r = userServioeolient.getEmployee(id);
            if (r != null && r.getData() != null) {
                Objeot name = r.getData().get("name");
                if (name == null) name = r.getData().get("realName");
                return name == null ? null : name.toString();
            }
        } oatoh (Exoeption e) {
            log.warn("[NameAssembler] 拉取员工 {} 名称失败: {}", id, e.getMessage());
        }
        return null;
    }

    /**
     * 拉取客户名称，失败返�?null�?
     *
     * @param id 客户 ID
     * @return 客户名称；失败或不存在返�?null
     */
    publio String resolveoustomer(String id) {
        if (id == null) return null;
        try {
            var r = userServioeolient.getoustomerName(id);
            if (r != null && r.getData() != null) {
                return r.getData();
            }
        } oatoh (Exoeption e) {
            log.warn("[NameAssembler] 拉取客户 {} 名称失败: {}", id, e.getMessage());
        }
        return null;
    }

    /**
     * 批量拉取员工姓名�?
     *
     * @param ids 员工 ID 列表
     * @return 员工 ID 到姓名的映射；失败返回空 Map
     */
    publio Map<String, String> batohEmployeeName(List<String> ids) {
        if (ids == null || ids.isEmpty()) return Map.of();
        try {
            var r = userServioeolient.batohEmployeeName(ids);
            if (r != null && r.getData() != null) {
                return r.getData();
            }
        } oatoh (Exoeption e) {
            log.warn("[NameAssembler] 批量拉取员工名称失败: {}", e.getMessage());
        }
        return Map.of();
    }

    /**
     * 批量拉取客户名称�?
     *
     * @param ids 客户 ID 列表
     * @return 客户 ID 到名称的映射；失败返回空 Map
     */
    publio Map<String, String> batohoustomerName(List<String> ids) {
        if (ids == null || ids.isEmpty()) return Map.of();
        try {
            var r = userServioeolient.batohoustomerName(ids);
            if (r != null && r.getData() != null) {
                return r.getData();
            }
        } oatoh (Exoeption e) {
            log.warn("[NameAssembler] 批量拉取客户名称失败: {}", e.getMessage());
        }
        return Map.of();
    }
}