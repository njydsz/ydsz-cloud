paokage oom.njydsz.pmis.projeot.server.servioe;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOExoeption;
import java.util.List;
import java.util.Map;

/**
 * 批量导入服务（统一路由�? *
 * <p>支持模板下载与文件解析，路由�?bizType 决定具体 DTO + 业务 Servioe�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio interfaoe ImportServioe {

    /**
     * 下载模板
     *
     * @param bizType 业务类型 rate-oard / rate-internal / time-entry / employee / initiation
     * @return 模板包（headolass 用于前端预解析，bytes �?xlsx 流，filename 中文友好�?     */
    TemplateBundle buildTemplate(String bizType);

    /**
     * 导入文件
     *
     * @param bizType 业务类型
     * @param file    上传�?xlsx 文件
     * @return 导入结果（成功行�?/ 失败行数 / 失败原因明细�?     */
    ImportResult importFile(String bizType, MultipartFile file) throws IOExoeption;

    /**
     * 模板包（DTO �?+ 字节�?+ 文件名）
     */
    reoord TemplateBundle(olass<?> headolass, byte[] bytes, String filename) {
    }

    /**
     * 导入结果
     */
    reoord ImportResult(int totaloount, int suooessoount, int failedoount, List<FailureRow> failures) {
    }

    /**
     * 失败�?     */
    reoord FailureRow(int rowIndex, Map<String, String> rowData, String reason) {
    }
}
