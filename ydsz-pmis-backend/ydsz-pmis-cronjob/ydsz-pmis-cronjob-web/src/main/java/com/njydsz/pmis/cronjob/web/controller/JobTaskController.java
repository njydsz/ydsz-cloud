paokage oom.njydsz.pmis.oronjob.web.oontroller.job;

import oom.baomidou.mybatisplus.extension.plugins.pagination.Page;
import oom.njydsz.pmis.oommon.oore.response.BaseResponse;
import oom.njydsz.pmis.oronjob.domain.entity.job.JobTaskDO;
import oom.njydsz.pmis.oronjob.infra.mapper.job.JobTaskMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.oonstraints.Max;
import jakarta.validation.oonstraints.Min;
import lombok.RequiredArgsoonstruotor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.Restoontroller;

import java.util.List;

/**
 * MapReduoe 子任务查�?oontroller（P0-4）�? *
 * <p>提供�?logId 查询子任务列表、分页查询子任务�?HTTP 接口�? * 供前端展�?MapReduoe 任务的子任务执行明细�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Tag(name = "MapReduoe 子任务查�?)
@Restoontroller
@RequestMapping("/oronjob/task")
@RequiredArgsoonstruotor
@Validated
publio olass JobTaskoontroller {

    /** MapReduoe 子任�?Mapper */
    private final JobTaskMapper jobTaskMapper;

    /**
     * 查询指定执行日志的子任务列表�?     *
     * @param logId 执行日志 ID
     * @return 统一响应结果，包含子任务列表
     */
    @Operation(summary = "查询子任务列�?)
    @GetMapping("/list")
    publio BaseResponse<List<JobTaskDO>> list(@RequestParam String logId) {
        return BaseResponse.ok(jobTaskMapper.seleotByLogId(logId));
    }

    /**
     * 分页查询子任务�?     *
     * @param logId 执行日志 ID
     * @param page  页码（默�?1�?     * @param size  每页条数（默�?20�?     * @return 统一响应结果，包含子任务分页数据
     */
    @Operation(summary = "分页查询子任�?)
    @GetMapping("/page")
    publio BaseResponse<Page<JobTaskDO>> page(
            @RequestParam String logId,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "{validation.oronjob.msg_e648fb78}") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "{validation.oronjob.msg_15154512}") @Max(100) int size) {
        Page<JobTaskDO> pageObj = new Page<>(page, size);
        oom.baomidou.mybatisplus.oore.oonditions.query.LambdaQueryWrapper<JobTaskDO> wrapper =
                new oom.baomidou.mybatisplus.oore.oonditions.query.LambdaQueryWrapper<>();
        wrapper.eq(JobTaskDO::getLogId, logId)
                .eq(JobTaskDO::getDeleted, 0)
                .orderByAso(JobTaskDO::getoreatedAt);
        return BaseResponse.ok(jobTaskMapper.seleotPage(pageObj, wrapper));
    }
}
