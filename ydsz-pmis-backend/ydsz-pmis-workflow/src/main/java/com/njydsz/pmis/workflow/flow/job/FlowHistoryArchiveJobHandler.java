package com.njydsz.ppackage com.njydsz.pmis.workflow.flow.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.njydsz.pmispackage com.njydsz.pmis.workflow.flow.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.njydsz.pmis.common.job.JobHandler;
import com.njydsz.pmis.workflow.flow.entity.FlowHisTaskpackage com.njydsz.pmis.workflow.flow.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.njydsz.pmis.common.job.JobHandler;
import com.njydsz.pmis.workflow.flow.entity.FlowHisTaskDO;
import com.njydsz.pmis.workflow.flow.entity.FlowInstanceDO;
import com.njydsz.pmis.workflow.flow.entity.FlowTaskDO;
import com.njydsz.pmis.workflow.flow.enums.FlowInstanceStatus;
package com.njydsz.pmis.workflow.flow.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.njydsz.pmis.common.job.JobHandler;
import com.njydsz.pmis.workflow.flow.entity.FlowHisTaskDO;
import com.njydsz.pmis.workflow.flow.entity.FlowInstanceDO;
import com.njydsz.pmis.workflow.flow.entity.FlowTaskDO;
import com.njydsz.pmis.workflow.flow.enums.FlowInstanceStatus;
import com.njydsz.pmis.workflow.flow.mapper.FlowHisTaskMapper;
import com.njydsz.pmis.workflow.flow.mapper.FlowInstanceMapper;
import com.njydsz.pmis.workflow.flow.mapper.FlowTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4jpackage com.njydsz.pmis.workflow.flow.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.njydsz.pmis.common.job.JobHandler;
import com.njydsz.pmis.workflow.flow.entity.FlowHisTaskDO;
import com.njydsz.pmis.workflow.flow.entity.FlowInstanceDO;
import com.njydsz.pmis.workflow.flow.entity.FlowTaskDO;
import com.njydsz.pmis.workflow.flow.enums.FlowInstanceStatus;
import com.njydsz.pmis.workflow.flow.mapper.FlowHisTaskMapper;
import com.njydsz.pmis.workflow.flow.mapper.FlowInstanceMapper;
import com.njydsz.pmis.workflow.flow.mapper.FlowTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

package com.njydsz.pmis.workflow.flow.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.njydsz.pmis.common.job.JobHandler;
import com.njydsz.pmis.workflow.flow.entity.FlowHisTaskDO;
import com.njydsz.pmis.workflow.flow.entity.FlowInstanceDO;
import com.njydsz.pmis.workflow.flow.entity.FlowTaskDO;
import com.njydsz.pmis.workflow.flow.enums.FlowInstanceStatus;
import com.njydsz.pmis.workflow.flow.mapper.FlowHisTaskMapper;
import com.njydsz.pmis.workflow.flow.mapper.FlowInstanceMapper;
import com.njydsz.pmis.workflow.flow.mapper.FlowTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * GAP-P1: 历史数据归档任务处理器
 *
 * <p>package com.njydsz.pmis.workflow.flow.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.njydsz.pmis.common.job.JobHandler;
import com.njydsz.pmis.workflow.flow.entity.FlowHisTaskDO;
import com.njydsz.pmis.workflow.flow.entity.FlowInstanceDO;
import com.njydsz.pmis.workflow.flow.entity.FlowTaskDO;
import com.njydsz.pmis.workflow.flow.enums.FlowInstanceStatus;
import com.njydsz.pmis.workflow.flow.mapper.FlowHisTaskMapper;
import com.njydsz.pmis.workflow.flow.mapper.FlowInstanceMapper;
import com.njydsz.pmis.workflow.flow.mapper.FlowTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * GAP-P1: 历史数据归档任务处理器
 *
 * <p>定时扫描已结束（COMPLETED/TERMINATED/REJECTED）且结束时间超过阈值（默认 30 天）的流程实例，
 * 校验其全部任务已归档至 pmis_flow_his_task，并记录归档统计。
 *
 * <p>当前版本仅做校验与日志记录（生产环境package com.njydsz.pmis.workflow.flow.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.njydsz.pmis.common.job.JobHandler;
import com.njydsz.pmis.workflow.flow.entity.FlowHisTaskDO;
import com.njydsz.pmis.workflow.flow.entity.FlowInstanceDO;
import com.njydsz.pmis.workflow.flow.entity.FlowTaskDO;
import com.njydsz.pmis.workflow.flow.enums.FlowInstanceStatus;
import com.njydsz.pmis.workflow.flow.mapper.FlowHisTaskMapper;
import com.njydsz.pmis.workflow.flow.mapper.FlowInstanceMapper;
import com.njydsz.pmis.workflow.flow.mapper.FlowTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * GAP-P1: 历史数据归档任务处理器
 *
 * <p>定时扫描已结束（COMPLETED/TERMINATED/REJECTED）且结束时间超过阈值（默认 30 天）的流程实例，
 * 校验其全部任务已归档至 pmis_flow_his_task，并记录归档统计。
 *
 * <p>当前版本仅做校验与日志记录（生产环境会将冷数据迁移至归档/冷存储表）。
 *
 * <p>容错package com.njydsz.pmis.workflow.flow.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.njydsz.pmis.common.job.JobHandler;
import com.njydsz.pmis.workflow.flow.entity.FlowHisTaskDO;
import com.njydsz.pmis.workflow.flow.entity.FlowInstanceDO;
import com.njydsz.pmis.workflow.flow.entity.FlowTaskDO;
import com.njydsz.pmis.workflow.flow.enums.FlowInstanceStatus;
import com.njydsz.pmis.workflow.flow.mapper.FlowHisTaskMapper;
import com.njydsz.pmis.workflow.flow.mapper.FlowInstanceMapper;
import com.njydsz.pmis.workflow.flow.mapper.FlowTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * GAP-P1: 历史数据归档任务处理器
 *
 * <p>定时扫描已结束（COMPLETED/TERMINATED/REJECTED）且结束时间超过阈值（默认 30 天）的流程实例，
 * 校验其全部任务已归档至 pmis_flow_his_task，并记录归档统计。
 *
 * <p>当前版本仅做校验与日志记录（生产环境会将冷数据迁移至归档/冷存储表）。
 *
 * <p>容错策略：每个实例独立 try-catch，单个实例处理失败不影响其余实例。
 *
 * <p>Bean 名称 = {@code flowHistoryArchiveJobHandler}，
 * 可在 pmis_job 表配置：handler=flowHistoryArchiveJobHandler, cron="0 0 3 * * ?"（每日 03:00 执行）。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Slf4j
@Component("package com.njydsz.pmis.workflow.flow.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.njydsz.pmis.common.job.JobHandler;
import com.njydsz.pmis.workflow.flow.entity.FlowHisTaskDO;
import com.njydsz.pmis.workflow.flow.entity.FlowInstanceDO;
import com.njydsz.pmis.workflow.flow.entity.FlowTaskDO;
import com.njydsz.pmis.workflow.flow.enums.FlowInstanceStatus;
import com.njydsz.pmis.workflow.flow.mapper.FlowHisTaskMapper;
import com.njydsz.pmis.workflow.flow.mapper.FlowInstanceMapper;
import com.njydsz.pmis.workflow.flow.mapper.FlowTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * GAP-P1: 历史数据归档任务处理器
 *
 * <p>定时扫描已结束（COMPLETED/TERMINATED/REJECTED）且结束时间超过阈值（默认 30 天）的流程实例，
 * 校验其全部任务已归档至 pmis_flow_his_task，并记录归档统计。
 *
 * <p>当前版本仅做校验与日志记录（生产环境会将冷数据迁移至归档/冷存储表）。
 *
 * <p>容错策略：每个实例独立 try-catch，单个实例处理失败不影响其余实例。
 *
 * <p>Bean 名称 = {@code flowHistoryArchiveJobHandler}，
 * 可在 pmis_job 表配置：handler=flowHistoryArchiveJobHandler, cron="0 0 3 * * ?"（每日 03:00 执行）。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Slf4j
@Component("flowHistoryArchiveJobHandler")
@RequiredArgsConstructor
public class FlowHistoryArchiveJobHandler implements JobHandler {

    /** 默认归档阈值天数 */
    private static final int DEFAULT_ARCHIVE_DAYS = 30;

    private final FlowInstanceMapper instanceMapper;
    private final FlowHisTaskMapper hisTaskMapper;
    private final FlowTaskMapper taskMapper;

    /**
     * 扫描并归档历史实例
     *
     * @parampackage com.njydsz.pmis.workflow.flow.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.njydsz.pmis.common.job.JobHandler;
import com.njydsz.pmis.workflow.flow.entity.FlowHisTaskDO;
import com.njydsz.pmis.workflow.flow.entity.FlowInstanceDO;
import com.njydsz.pmis.workflow.flow.entity.FlowTaskDO;
import com.njydsz.pmis.workflow.flow.enums.FlowInstanceStatus;
import com.njydsz.pmis.workflow.flow.mapper.FlowHisTaskMapper;
import com.njydsz.pmis.workflow.flow.mapper.FlowInstanceMapper;
import com.njydsz.pmis.workflow.flow.mapper.FlowTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * GAP-P1: 历史数据归档任务处理器
 *
 * <p>定时扫描已结束（COMPLETED/TERMINATED/REJECTED）且结束时间超过阈值（默认 30 天）的流程实例，
 * 校验其全部任务已归档至 pmis_flow_his_task，并记录归档统计。
 *
 * <p>当前版本仅做校验与日志记录（生产环境会将冷数据迁移至归档/冷存储表）。
 *
 * <p>容错策略：每个实例独立 try-catch，单个实例处理失败不影响其余实例。
 *
 * <p>Bean 名称 = {@code flowHistoryArchiveJobHandler}，
 * 可在 pmis_job 表配置：handler=flowHistoryArchiveJobHandler, cron="0 0 3 * * ?"（每日 03:00 执行）。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Slf4j
@Component("flowHistoryArchiveJobHandler")
@RequiredArgsConstructor
public class FlowHistoryArchiveJobHandler implements JobHandler {

    /** 默认归档阈值天数 */
    private static final int DEFAULT_ARCHIVE_DAYS = 30;

    private final FlowInstanceMapper instanceMapper;
    private final FlowHisTaskMapper hisTaskMapper;
    private final FlowTaskMapper taskMapper;

    /**
     * 扫描并归档历史实例
     *
     * @param paramsJson 参数 JSON，可包含 days（归档阈值天数，默认 30），可空package com.njydsz.pmis.workflow.flow.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.njydsz.pmis.common.job.JobHandler;
import com.njydsz.pmis.workflow.flow.entity.FlowHisTaskDO;
import com.njydsz.pmis.workflow.flow.entity.FlowInstanceDO;
import com.njydsz.pmis.workflow.flow.entity.FlowTaskDO;
import com.njydsz.pmis.workflow.flow.enums.FlowInstanceStatus;
import com.njydsz.pmis.workflow.flow.mapper.FlowHisTaskMapper;
import com.njydsz.pmis.workflow.flow.mapper.FlowInstanceMapper;
import com.njydsz.pmis.workflow.flow.mapper.FlowTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * GAP-P1: 历史数据归档任务处理器
 *
 * <p>定时扫描已结束（COMPLETED/TERMINATED/REJECTED）且结束时间超过阈值（默认 30 天）的流程实例，
 * 校验其全部任务已归档至 pmis_flow_his_task，并记录归档统计。
 *
 * <p>当前版本仅做校验与日志记录（生产环境会将冷数据迁移至归档/冷存储表）。
 *
 * <p>容错策略：每个实例独立 try-catch，单个实例处理失败不影响其余实例。
 *
 * <p>Bean 名称 = {@code flowHistoryArchiveJobHandler}，
 * 可在 pmis_job 表配置：handler=flowHistoryArchiveJobHandler, cron="0 0 3 * * ?"（每日 03:00 执行）。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Slf4j
@Component("flowHistoryArchiveJobHandler")
@RequiredArgsConstructor
public class FlowHistoryArchiveJobHandler implements JobHandler {

    /** 默认归档阈值天数 */
    private static final int DEFAULT_ARCHIVE_DAYS = 30;

    private final FlowInstanceMapper instanceMapper;
    private final FlowHisTaskMapper hisTaskMapper;
    private final FlowTaskMapper taskMapper;

    /**
     * 扫描并归档历史实例
     *
     * @param paramsJson 参数 JSON，可包含 days（归档阈值天数，默认 30），可空
     * @return 执行结果摘要：archived/verified/missing 等计数
     */
    @Override
    public Object execute(String paramsJson) throws Exception {
        long startpackage com.njydsz.pmis.workflow.flow.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.njydsz.pmis.common.job.JobHandler;
import com.njydsz.pmis.workflow.flow.entity.FlowHisTaskDO;
import com.njydsz.pmis.workflow.flow.entity.FlowInstanceDO;
import com.njydsz.pmis.workflow.flow.entity.FlowTaskDO;
import com.njydsz.pmis.workflow.flow.enums.FlowInstanceStatus;
import com.njydsz.pmis.workflow.flow.mapper.FlowHisTaskMapper;
import com.njydsz.pmis.workflow.flow.mapper.FlowInstanceMapper;
import com.njydsz.pmis.workflow.flow.mapper.FlowTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * GAP-P1: 历史数据归档任务处理器
 *
 * <p>定时扫描已结束（COMPLETED/TERMINATED/REJECTED）且结束时间超过阈值（默认 30 天）的流程实例，
 * 校验其全部任务已归档至 pmis_flow_his_task，并记录归档统计。
 *
 * <p>当前版本仅做校验与日志记录（生产环境会将冷数据迁移至归档/冷存储表）。
 *
 * <p>容错策略：每个实例独立 try-catch，单个实例处理失败不影响其余实例。
 *
 * <p>Bean 名称 = {@code flowHistoryArchiveJobHandler}，
 * 可在 pmis_job 表配置：handler=flowHistoryArchiveJobHandler, cron="0 0 3 * * ?"（每日 03:00 执行）。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Slf4j
@Component("flowHistoryArchiveJobHandler")
@RequiredArgsConstructor
public class FlowHistoryArchiveJobHandler implements JobHandler {

    /** 默认归档阈值天数 */
    private static final int DEFAULT_ARCHIVE_DAYS = 30;

    private final FlowInstanceMapper instanceMapper;
    private final FlowHisTaskMapper hisTaskMapper;
    private final FlowTaskMapper taskMapper;

    /**
     * 扫描并归档历史实例
     *
     * @param paramsJson 参数 JSON，可包含 days（归档阈值天数，默认 30），可空
     * @return 执行结果摘要：archived/verified/missing 等计数
     */
    @Override
    public Object execute(String paramsJson) throws Exception {
        long start = System.currentTimeMillis();
        int days = parseDays(paramsJson);

        log.info("[FlowHistoryArchive] 开始归档历史数据 days={}", dayspackage com.njydsz.pmis.workflow.flow.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.njydsz.pmis.common.job.JobHandler;
import com.njydsz.pmis.workflow.flow.entity.FlowHisTaskDO;
import com.njydsz.pmis.workflow.flow.entity.FlowInstanceDO;
import com.njydsz.pmis.workflow.flow.entity.FlowTaskDO;
import com.njydsz.pmis.workflow.flow.enums.FlowInstanceStatus;
import com.njydsz.pmis.workflow.flow.mapper.FlowHisTaskMapper;
import com.njydsz.pmis.workflow.flow.mapper.FlowInstanceMapper;
import com.njydsz.pmis.workflow.flow.mapper.FlowTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * GAP-P1: 历史数据归档任务处理器
 *
 * <p>定时扫描已结束（COMPLETED/TERMINATED/REJECTED）且结束时间超过阈值（默认 30 天）的流程实例，
 * 校验其全部任务已归档至 pmis_flow_his_task，并记录归档统计。
 *
 * <p>当前版本仅做校验与日志记录（生产环境会将冷数据迁移至归档/冷存储表）。
 *
 * <p>容错策略：每个实例独立 try-catch，单个实例处理失败不影响其余实例。
 *
 * <p>Bean 名称 = {@code flowHistoryArchiveJobHandler}，
 * 可在 pmis_job 表配置：handler=flowHistoryArchiveJobHandler, cron="0 0 3 * * ?"（每日 03:00 执行）。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Slf4j
@Component("flowHistoryArchiveJobHandler")
@RequiredArgsConstructor
public class FlowHistoryArchiveJobHandler implements JobHandler {

    /** 默认归档阈值天数 */
    private static final int DEFAULT_ARCHIVE_DAYS = 30;

    private final FlowInstanceMapper instanceMapper;
    private final FlowHisTaskMapper hisTaskMapper;
    private final FlowTaskMapper taskMapper;

    /**
     * 扫描并归档历史实例
     *
     * @param paramsJson 参数 JSON，可包含 days（归档阈值天数，默认 30），可空
     * @return 执行结果摘要：archived/verified/missing 等计数
     */
    @Override
    public Object execute(String paramsJson) throws Exception {
        long start = System.currentTimeMillis();
        int days = parseDays(paramsJson);

        log.info("[FlowHistoryArchive] 开始归档历史数据 days={}", days);

        // 查询已结束且结束时间超过阈值的实例（deleted=0 由package com.njydsz.pmis.workflow.flow.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.njydsz.pmis.common.job.JobHandler;
import com.njydsz.pmis.workflow.flow.entity.FlowHisTaskDO;
import com.njydsz.pmis.workflow.flow.entity.FlowInstanceDO;
import com.njydsz.pmis.workflow.flow.entity.FlowTaskDO;
import com.njydsz.pmis.workflow.flow.enums.FlowInstanceStatus;
import com.njydsz.pmis.workflow.flow.mapper.FlowHisTaskMapper;
import com.njydsz.pmis.workflow.flow.mapper.FlowInstanceMapper;
import com.njydsz.pmis.workflow.flow.mapper.FlowTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * GAP-P1: 历史数据归档任务处理器
 *
 * <p>定时扫描已结束（COMPLETED/TERMINATED/REJECTED）且结束时间超过阈值（默认 30 天）的流程实例，
 * 校验其全部任务已归档至 pmis_flow_his_task，并记录归档统计。
 *
 * <p>当前版本仅做校验与日志记录（生产环境会将冷数据迁移至归档/冷存储表）。
 *
 * <p>容错策略：每个实例独立 try-catch，单个实例处理失败不影响其余实例。
 *
 * <p>Bean 名称 = {@code flowHistoryArchiveJobHandler}，
 * 可在 pmis_job 表配置：handler=flowHistoryArchiveJobHandler, cron="0 0 3 * * ?"（每日 03:00 执行）。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Slf4j
@Component("flowHistoryArchiveJobHandler")
@RequiredArgsConstructor
public class FlowHistoryArchiveJobHandler implements JobHandler {

    /** 默认归档阈值天数 */
    private static final int DEFAULT_ARCHIVE_DAYS = 30;

    private final FlowInstanceMapper instanceMapper;
    private final FlowHisTaskMapper hisTaskMapper;
    private final FlowTaskMapper taskMapper;

    /**
     * 扫描并归档历史实例
     *
     * @param paramsJson 参数 JSON，可包含 days（归档阈值天数，默认 30），可空
     * @return 执行结果摘要：archived/verified/missing 等计数
     */
    @Override
    public Object execute(String paramsJson) throws Exception {
        long start = System.currentTimeMillis();
        int days = parseDays(paramsJson);

        log.info("[FlowHistoryArchive] 开始归档历史数据 days={}", days);

        // 查询已结束且结束时间超过阈值的实例（deleted=0 由 MyBatis-Plus 逻辑删除自动过滤）
        LocalDateTime threshold = LocalDateTime.now().minusDayspackage com.njydsz.pmis.workflow.flow.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.njydsz.pmis.common.job.JobHandler;
import com.njydsz.pmis.workflow.flow.entity.FlowHisTaskDO;
import com.njydsz.pmis.workflow.flow.entity.FlowInstanceDO;
import com.njydsz.pmis.workflow.flow.entity.FlowTaskDO;
import com.njydsz.pmis.workflow.flow.enums.FlowInstanceStatus;
import com.njydsz.pmis.workflow.flow.mapper.FlowHisTaskMapper;
import com.njydsz.pmis.workflow.flow.mapper.FlowInstanceMapper;
import com.njydsz.pmis.workflow.flow.mapper.FlowTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * GAP-P1: 历史数据归档任务处理器
 *
 * <p>定时扫描已结束（COMPLETED/TERMINATED/REJECTED）且结束时间超过阈值（默认 30 天）的流程实例，
 * 校验其全部任务已归档至 pmis_flow_his_task，并记录归档统计。
 *
 * <p>当前版本仅做校验与日志记录（生产环境会将冷数据迁移至归档/冷存储表）。
 *
 * <p>容错策略：每个实例独立 try-catch，单个实例处理失败不影响其余实例。
 *
 * <p>Bean 名称 = {@code flowHistoryArchiveJobHandler}，
 * 可在 pmis_job 表配置：handler=flowHistoryArchiveJobHandler, cron="0 0 3 * * ?"（每日 03:00 执行）。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Slf4j
@Component("flowHistoryArchiveJobHandler")
@RequiredArgsConstructor
public class FlowHistoryArchiveJobHandler implements JobHandler {

    /** 默认归档阈值天数 */
    private static final int DEFAULT_ARCHIVE_DAYS = 30;

    private final FlowInstanceMapper instanceMapper;
    private final FlowHisTaskMapper hisTaskMapper;
    private final FlowTaskMapper taskMapper;

    /**
     * 扫描并归档历史实例
     *
     * @param paramsJson 参数 JSON，可包含 days（归档阈值天数，默认 30），可空
     * @return 执行结果摘要：archived/verified/missing 等计数
     */
    @Override
    public Object execute(String paramsJson) throws Exception {
        long start = System.currentTimeMillis();
        int days = parseDays(paramsJson);

        log.info("[FlowHistoryArchive] 开始归档历史数据 days={}", days);

        // 查询已结束且结束时间超过阈值的实例（deleted=0 由 MyBatis-Plus 逻辑删除自动过滤）
        LocalDateTime threshold = LocalDateTime.now().minusDays(days);
        LambdaQueryWrapper<FlowInstanceDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.inpackage com.njydsz.pmis.workflow.flow.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.njydsz.pmis.common.job.JobHandler;
import com.njydsz.pmis.workflow.flow.entity.FlowHisTaskDO;
import com.njydsz.pmis.workflow.flow.entity.FlowInstanceDO;
import com.njydsz.pmis.workflow.flow.entity.FlowTaskDO;
import com.njydsz.pmis.workflow.flow.enums.FlowInstanceStatus;
import com.njydsz.pmis.workflow.flow.mapper.FlowHisTaskMapper;
import com.njydsz.pmis.workflow.flow.mapper.FlowInstanceMapper;
import com.njydsz.pmis.workflow.flow.mapper.FlowTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * GAP-P1: 历史数据归档任务处理器
 *
 * <p>定时扫描已结束（COMPLETED/TERMINATED/REJECTED）且结束时间超过阈值（默认 30 天）的流程实例，
 * 校验其全部任务已归档至 pmis_flow_his_task，并记录归档统计。
 *
 * <p>当前版本仅做校验与日志记录（生产环境会将冷数据迁移至归档/冷存储表）。
 *
 * <p>容错策略：每个实例独立 try-catch，单个实例处理失败不影响其余实例。
 *
 * <p>Bean 名称 = {@code flowHistoryArchiveJobHandler}，
 * 可在 pmis_job 表配置：handler=flowHistoryArchiveJobHandler, cron="0 0 3 * * ?"（每日 03:00 执行）。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Slf4j
@Component("flowHistoryArchiveJobHandler")
@RequiredArgsConstructor
public class FlowHistoryArchiveJobHandler implements JobHandler {

    /** 默认归档阈值天数 */
    private static final int DEFAULT_ARCHIVE_DAYS = 30;

    private final FlowInstanceMapper instanceMapper;
    private final FlowHisTaskMapper hisTaskMapper;
    private final FlowTaskMapper taskMapper;

    /**
     * 扫描并归档历史实例
     *
     * @param paramsJson 参数 JSON，可包含 days（归档阈值天数，默认 30），可空
     * @return 执行结果摘要：archived/verified/missing 等计数
     */
    @Override
    public Object execute(String paramsJson) throws Exception {
        long start = System.currentTimeMillis();
        int days = parseDays(paramsJson);

        log.info("[FlowHistoryArchive] 开始归档历史数据 days={}", days);

        // 查询已结束且结束时间超过阈值的实例（deleted=0 由 MyBatis-Plus 逻辑删除自动过滤）
        LocalDateTime threshold = LocalDateTime.now().minusDays(days);
        LambdaQueryWrapper<FlowInstanceDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(FlowInstanceDO::getFlowStatus,
                        FlowInstanceStatus.COMPLETED.name(),
                        FlowInstanceStatus.TERMINATED.name(),
                        FlowInstancepackage com.njydsz.pmis.workflow.flow.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.njydsz.pmis.common.job.JobHandler;
import com.njydsz.pmis.workflow.flow.entity.FlowHisTaskDO;
import com.njydsz.pmis.workflow.flow.entity.FlowInstanceDO;
import com.njydsz.pmis.workflow.flow.entity.FlowTaskDO;
import com.njydsz.pmis.workflow.flow.enums.FlowInstanceStatus;
import com.njydsz.pmis.workflow.flow.mapper.FlowHisTaskMapper;
import com.njydsz.pmis.workflow.flow.mapper.FlowInstanceMapper;
import com.njydsz.pmis.workflow.flow.mapper.FlowTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * GAP-P1: 历史数据归档任务处理器
 *
 * <p>定时扫描已结束（COMPLETED/TERMINATED/REJECTED）且结束时间超过阈值（默认 30 天）的流程实例，
 * 校验其全部任务已归档至 pmis_flow_his_task，并记录归档统计。
 *
 * <p>当前版本仅做校验与日志记录（生产环境会将冷数据迁移至归档/冷存储表）。
 *
 * <p>容错策略：每个实例独立 try-catch，单个实例处理失败不影响其余实例。
 *
 * <p>Bean 名称 = {@code flowHistoryArchiveJobHandler}，
 * 可在 pmis_job 表配置：handler=flowHistoryArchiveJobHandler, cron="0 0 3 * * ?"（每日 03:00 执行）。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Slf4j
@Component("flowHistoryArchiveJobHandler")
@RequiredArgsConstructor
public class FlowHistoryArchiveJobHandler implements JobHandler {

    /** 默认归档阈值天数 */
    private static final int DEFAULT_ARCHIVE_DAYS = 30;

    private final FlowInstanceMapper instanceMapper;
    private final FlowHisTaskMapper hisTaskMapper;
    private final FlowTaskMapper taskMapper;

    /**
     * 扫描并归档历史实例
     *
     * @param paramsJson 参数 JSON，可包含 days（归档阈值天数，默认 30），可空
     * @return 执行结果摘要：archived/verified/missing 等计数
     */
    @Override
    public Object execute(String paramsJson) throws Exception {
        long start = System.currentTimeMillis();
        int days = parseDays(paramsJson);

        log.info("[FlowHistoryArchive] 开始归档历史数据 days={}", days);

        // 查询已结束且结束时间超过阈值的实例（deleted=0 由 MyBatis-Plus 逻辑删除自动过滤）
        LocalDateTime threshold = LocalDateTime.now().minusDays(days);
        LambdaQueryWrapper<FlowInstanceDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(FlowInstanceDO::getFlowStatus,
                        FlowInstanceStatus.COMPLETED.name(),
                        FlowInstanceStatus.TERMINATED.name(),
                        FlowInstanceStatus.REJECTED.name())
                .lt(FlowInstanceDO::getEndAt, threshold)
                .orderByAsc(FlowInstanceDO::getEndpackage com.njydsz.pmis.workflow.flow.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.njydsz.pmis.common.job.JobHandler;
import com.njydsz.pmis.workflow.flow.entity.FlowHisTaskDO;
import com.njydsz.pmis.workflow.flow.entity.FlowInstanceDO;
import com.njydsz.pmis.workflow.flow.entity.FlowTaskDO;
import com.njydsz.pmis.workflow.flow.enums.FlowInstanceStatus;
import com.njydsz.pmis.workflow.flow.mapper.FlowHisTaskMapper;
import com.njydsz.pmis.workflow.flow.mapper.FlowInstanceMapper;
import com.njydsz.pmis.workflow.flow.mapper.FlowTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * GAP-P1: 历史数据归档任务处理器
 *
 * <p>定时扫描已结束（COMPLETED/TERMINATED/REJECTED）且结束时间超过阈值（默认 30 天）的流程实例，
 * 校验其全部任务已归档至 pmis_flow_his_task，并记录归档统计。
 *
 * <p>当前版本仅做校验与日志记录（生产环境会将冷数据迁移至归档/冷存储表）。
 *
 * <p>容错策略：每个实例独立 try-catch，单个实例处理失败不影响其余实例。
 *
 * <p>Bean 名称 = {@code flowHistoryArchiveJobHandler}，
 * 可在 pmis_job 表配置：handler=flowHistoryArchiveJobHandler, cron="0 0 3 * * ?"（每日 03:00 执行）。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Slf4j
@Component("flowHistoryArchiveJobHandler")
@RequiredArgsConstructor
public class FlowHistoryArchiveJobHandler implements JobHandler {

    /** 默认归档阈值天数 */
    private static final int DEFAULT_ARCHIVE_DAYS = 30;

    private final FlowInstanceMapper instanceMapper;
    private final FlowHisTaskMapper hisTaskMapper;
    private final FlowTaskMapper taskMapper;

    /**
     * 扫描并归档历史实例
     *
     * @param paramsJson 参数 JSON，可包含 days（归档阈值天数，默认 30），可空
     * @return 执行结果摘要：archived/verified/missing 等计数
     */
    @Override
    public Object execute(String paramsJson) throws Exception {
        long start = System.currentTimeMillis();
        int days = parseDays(paramsJson);

        log.info("[FlowHistoryArchive] 开始归档历史数据 days={}", days);

        // 查询已结束且结束时间超过阈值的实例（deleted=0 由 MyBatis-Plus 逻辑删除自动过滤）
        LocalDateTime threshold = LocalDateTime.now().minusDays(days);
        LambdaQueryWrapper<FlowInstanceDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(FlowInstanceDO::getFlowStatus,
                        FlowInstanceStatus.COMPLETED.name(),
                        FlowInstanceStatus.TERMINATED.name(),
                        FlowInstanceStatus.REJECTED.name())
                .lt(FlowInstanceDO::getEndAt, threshold)
                .orderByAsc(FlowInstanceDO::getEndAt);

        List<FlowInstanceDO> oldInstancespackage com.njydsz.pmis.workflow.flow.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.njydsz.pmis.common.job.JobHandler;
import com.njydsz.pmis.workflow.flow.entity.FlowHisTaskDO;
import com.njydsz.pmis.workflow.flow.entity.FlowInstanceDO;
import com.njydsz.pmis.workflow.flow.entity.FlowTaskDO;
import com.njydsz.pmis.workflow.flow.enums.FlowInstanceStatus;
import com.njydsz.pmis.workflow.flow.mapper.FlowHisTaskMapper;
import com.njydsz.pmis.workflow.flow.mapper.FlowInstanceMapper;
import com.njydsz.pmis.workflow.flow.mapper.FlowTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * GAP-P1: 历史数据归档任务处理器
 *
 * <p>定时扫描已结束（COMPLETED/TERMINATED/REJECTED）且结束时间超过阈值（默认 30 天）的流程实例，
 * 校验其全部任务已归档至 pmis_flow_his_task，并记录归档统计。
 *
 * <p>当前版本仅做校验与日志记录（生产环境会将冷数据迁移至归档/冷存储表）。
 *
 * <p>容错策略：每个实例独立 try-catch，单个实例处理失败不影响其余实例。
 *
 * <p>Bean 名称 = {@code flowHistoryArchiveJobHandler}，
 * 可在 pmis_job 表配置：handler=flowHistoryArchiveJobHandler, cron="0 0 3 * * ?"（每日 03:00 执行）。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Slf4j
@Component("flowHistoryArchiveJobHandler")
@RequiredArgsConstructor
public class FlowHistoryArchiveJobHandler implements JobHandler {

    /** 默认归档阈值天数 */
    private static final int DEFAULT_ARCHIVE_DAYS = 30;

    private final FlowInstanceMapper instanceMapper;
    private final FlowHisTaskMapper hisTaskMapper;
    private final FlowTaskMapper taskMapper;

    /**
     * 扫描并归档历史实例
     *
     * @param paramsJson 参数 JSON，可包含 days（归档阈值天数，默认 30），可空
     * @return 执行结果摘要：archived/verified/missing 等计数
     */
    @Override
    public Object execute(String paramsJson) throws Exception {
        long start = System.currentTimeMillis();
        int days = parseDays(paramsJson);

        log.info("[FlowHistoryArchive] 开始归档历史数据 days={}", days);

        // 查询已结束且结束时间超过阈值的实例（deleted=0 由 MyBatis-Plus 逻辑删除自动过滤）
        LocalDateTime threshold = LocalDateTime.now().minusDays(days);
        LambdaQueryWrapper<FlowInstanceDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(FlowInstanceDO::getFlowStatus,
                        FlowInstanceStatus.COMPLETED.name(),
                        FlowInstanceStatus.TERMINATED.name(),
                        FlowInstanceStatus.REJECTED.name())
                .lt(FlowInstanceDO::getEndAt, threshold)
                .orderByAsc(FlowInstanceDO::getEndAt);

        List<FlowInstanceDO> oldInstances;
        try {
            oldInstances = instanceMapper.selectList(wrapper);
        } catch (Exception e) {
            log.error("[FlowHistoryArchive]package com.njydsz.pmis.workflow.flow.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.njydsz.pmis.common.job.JobHandler;
import com.njydsz.pmis.workflow.flow.entity.FlowHisTaskDO;
import com.njydsz.pmis.workflow.flow.entity.FlowInstanceDO;
import com.njydsz.pmis.workflow.flow.entity.FlowTaskDO;
import com.njydsz.pmis.workflow.flow.enums.FlowInstanceStatus;
import com.njydsz.pmis.workflow.flow.mapper.FlowHisTaskMapper;
import com.njydsz.pmis.workflow.flow.mapper.FlowInstanceMapper;
import com.njydsz.pmis.workflow.flow.mapper.FlowTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * GAP-P1: 历史数据归档任务处理器
 *
 * <p>定时扫描已结束（COMPLETED/TERMINATED/REJECTED）且结束时间超过阈值（默认 30 天）的流程实例，
 * 校验其全部任务已归档至 pmis_flow_his_task，并记录归档统计。
 *
 * <p>当前版本仅做校验与日志记录（生产环境会将冷数据迁移至归档/冷存储表）。
 *
 * <p>容错策略：每个实例独立 try-catch，单个实例处理失败不影响其余实例。
 *
 * <p>Bean 名称 = {@code flowHistoryArchiveJobHandler}，
 * 可在 pmis_job 表配置：handler=flowHistoryArchiveJobHandler, cron="0 0 3 * * ?"（每日 03:00 执行）。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Slf4j
@Component("flowHistoryArchiveJobHandler")
@RequiredArgsConstructor
public class FlowHistoryArchiveJobHandler implements JobHandler {

    /** 默认归档阈值天数 */
    private static final int DEFAULT_ARCHIVE_DAYS = 30;

    private final FlowInstanceMapper instanceMapper;
    private final FlowHisTaskMapper hisTaskMapper;
    private final FlowTaskMapper taskMapper;

    /**
     * 扫描并归档历史实例
     *
     * @param paramsJson 参数 JSON，可包含 days（归档阈值天数，默认 30），可空
     * @return 执行结果摘要：archived/verified/missing 等计数
     */
    @Override
    public Object execute(String paramsJson) throws Exception {
        long start = System.currentTimeMillis();
        int days = parseDays(paramsJson);

        log.info("[FlowHistoryArchive] 开始归档历史数据 days={}", days);

        // 查询已结束且结束时间超过阈值的实例（deleted=0 由 MyBatis-Plus 逻辑删除自动过滤）
        LocalDateTime threshold = LocalDateTime.now().minusDays(days);
        LambdaQueryWrapper<FlowInstanceDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(FlowInstanceDO::getFlowStatus,
                        FlowInstanceStatus.COMPLETED.name(),
                        FlowInstanceStatus.TERMINATED.name(),
                        FlowInstanceStatus.REJECTED.name())
                .lt(FlowInstanceDO::getEndAt, threshold)
                .orderByAsc(FlowInstanceDO::getEndAt);

        List<FlowInstanceDO> oldInstances;
        try {
            oldInstances = instanceMapper.selectList(wrapper);
        } catch (Exception e) {
            log.error("[FlowHistoryArchive] 查询历史实例失败: {}", e.getMessage(), e);
            Map<String, Object> err = new HashMap<>();
            err.put("ok",package com.njydsz.pmis.workflow.flow.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.njydsz.pmis.common.job.JobHandler;
import com.njydsz.pmis.workflow.flow.entity.FlowHisTaskDO;
import com.njydsz.pmis.workflow.flow.entity.FlowInstanceDO;
import com.njydsz.pmis.workflow.flow.entity.FlowTaskDO;
import com.njydsz.pmis.workflow.flow.enums.FlowInstanceStatus;
import com.njydsz.pmis.workflow.flow.mapper.FlowHisTaskMapper;
import com.njydsz.pmis.workflow.flow.mapper.FlowInstanceMapper;
import com.njydsz.pmis.workflow.flow.mapper.FlowTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * GAP-P1: 历史数据归档任务处理器
 *
 * <p>定时扫描已结束（COMPLETED/TERMINATED/REJECTED）且结束时间超过阈值（默认 30 天）的流程实例，
 * 校验其全部任务已归档至 pmis_flow_his_task，并记录归档统计。
 *
 * <p>当前版本仅做校验与日志记录（生产环境会将冷数据迁移至归档/冷存储表）。
 *
 * <p>容错策略：每个实例独立 try-catch，单个实例处理失败不影响其余实例。
 *
 * <p>Bean 名称 = {@code flowHistoryArchiveJobHandler}，
 * 可在 pmis_job 表配置：handler=flowHistoryArchiveJobHandler, cron="0 0 3 * * ?"（每日 03:00 执行）。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Slf4j
@Component("flowHistoryArchiveJobHandler")
@RequiredArgsConstructor
public class FlowHistoryArchiveJobHandler implements JobHandler {

    /** 默认归档阈值天数 */
    private static final int DEFAULT_ARCHIVE_DAYS = 30;

    private final FlowInstanceMapper instanceMapper;
    private final FlowHisTaskMapper hisTaskMapper;
    private final FlowTaskMapper taskMapper;

    /**
     * 扫描并归档历史实例
     *
     * @param paramsJson 参数 JSON，可包含 days（归档阈值天数，默认 30），可空
     * @return 执行结果摘要：archived/verified/missing 等计数
     */
    @Override
    public Object execute(String paramsJson) throws Exception {
        long start = System.currentTimeMillis();
        int days = parseDays(paramsJson);

        log.info("[FlowHistoryArchive] 开始归档历史数据 days={}", days);

        // 查询已结束且结束时间超过阈值的实例（deleted=0 由 MyBatis-Plus 逻辑删除自动过滤）
        LocalDateTime threshold = LocalDateTime.now().minusDays(days);
        LambdaQueryWrapper<FlowInstanceDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(FlowInstanceDO::getFlowStatus,
                        FlowInstanceStatus.COMPLETED.name(),
                        FlowInstanceStatus.TERMINATED.name(),
                        FlowInstanceStatus.REJECTED.name())
                .lt(FlowInstanceDO::getEndAt, threshold)
                .orderByAsc(FlowInstanceDO::getEndAt);

        List<FlowInstanceDO> oldInstances;
        try {
            oldInstances = instanceMapper.selectList(wrapper);
        } catch (Exception e) {
            log.error("[FlowHistoryArchive] 查询历史实例失败: {}", e.getMessage(), e);
            Map<String, Object> err = new HashMap<>();
            err.put("ok", false);
            err.put("error", e.getMessage());
            return err;
        }

        if (oldInstances == null || oldInstances.isEmpty()) {
package com.njydsz.pmis.workflow.flow.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.njydsz.pmis.common.job.JobHandler;
import com.njydsz.pmis.workflow.flow.entity.FlowHisTaskDO;
import com.njydsz.pmis.workflow.flow.entity.FlowInstanceDO;
import com.njydsz.pmis.workflow.flow.entity.FlowTaskDO;
import com.njydsz.pmis.workflow.flow.enums.FlowInstanceStatus;
import com.njydsz.pmis.workflow.flow.mapper.FlowHisTaskMapper;
import com.njydsz.pmis.workflow.flow.mapper.FlowInstanceMapper;
import com.njydsz.pmis.workflow.flow.mapper.FlowTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * GAP-P1: 历史数据归档任务处理器
 *
 * <p>定时扫描已结束（COMPLETED/TERMINATED/REJECTED）且结束时间超过阈值（默认 30 天）的流程实例，
 * 校验其全部任务已归档至 pmis_flow_his_task，并记录归档统计。
 *
 * <p>当前版本仅做校验与日志记录（生产环境会将冷数据迁移至归档/冷存储表）。
 *
 * <p>容错策略：每个实例独立 try-catch，单个实例处理失败不影响其余实例。
 *
 * <p>Bean 名称 = {@code flowHistoryArchiveJobHandler}，
 * 可在 pmis_job 表配置：handler=flowHistoryArchiveJobHandler, cron="0 0 3 * * ?"（每日 03:00 执行）。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Slf4j
@Component("flowHistoryArchiveJobHandler")
@RequiredArgsConstructor
public class FlowHistoryArchiveJobHandler implements JobHandler {

    /** 默认归档阈值天数 */
    private static final int DEFAULT_ARCHIVE_DAYS = 30;

    private final FlowInstanceMapper instanceMapper;
    private final FlowHisTaskMapper hisTaskMapper;
    private final FlowTaskMapper taskMapper;

    /**
     * 扫描并归档历史实例
     *
     * @param paramsJson 参数 JSON，可包含 days（归档阈值天数，默认 30），可空
     * @return 执行结果摘要：archived/verified/missing 等计数
     */
    @Override
    public Object execute(String paramsJson) throws Exception {
        long start = System.currentTimeMillis();
        int days = parseDays(paramsJson);

        log.info("[FlowHistoryArchive] 开始归档历史数据 days={}", days);

        // 查询已结束且结束时间超过阈值的实例（deleted=0 由 MyBatis-Plus 逻辑删除自动过滤）
        LocalDateTime threshold = LocalDateTime.now().minusDays(days);
        LambdaQueryWrapper<FlowInstanceDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(FlowInstanceDO::getFlowStatus,
                        FlowInstanceStatus.COMPLETED.name(),
                        FlowInstanceStatus.TERMINATED.name(),
                        FlowInstanceStatus.REJECTED.name())
                .lt(FlowInstanceDO::getEndAt, threshold)
                .orderByAsc(FlowInstanceDO::getEndAt);

        List<FlowInstanceDO> oldInstances;
        try {
            oldInstances = instanceMapper.selectList(wrapper);
        } catch (Exception e) {
            log.error("[FlowHistoryArchive] 查询历史实例失败: {}", e.getMessage(), e);
            Map<String, Object> err = new HashMap<>();
            err.put("ok", false);
            err.put("error", e.getMessage());
            return err;
        }

        if (oldInstances == null || oldInstances.isEmpty()) {
            log.info("FlowHistoryArchiveJobHandler: archived 0 instances older than {} days", days);
            Map<String, Object> empty = new HashMap<>();
            empty.put("ok", true);
            empty.put("archived", 0);
package com.njydsz.pmis.workflow.flow.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.njydsz.pmis.common.job.JobHandler;
import com.njydsz.pmis.workflow.flow.entity.FlowHisTaskDO;
import com.njydsz.pmis.workflow.flow.entity.FlowInstanceDO;
import com.njydsz.pmis.workflow.flow.entity.FlowTaskDO;
import com.njydsz.pmis.workflow.flow.enums.FlowInstanceStatus;
import com.njydsz.pmis.workflow.flow.mapper.FlowHisTaskMapper;
import com.njydsz.pmis.workflow.flow.mapper.FlowInstanceMapper;
import com.njydsz.pmis.workflow.flow.mapper.FlowTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * GAP-P1: 历史数据归档任务处理器
 *
 * <p>定时扫描已结束（COMPLETED/TERMINATED/REJECTED）且结束时间超过阈值（默认 30 天）的流程实例，
 * 校验其全部任务已归档至 pmis_flow_his_task，并记录归档统计。
 *
 * <p>当前版本仅做校验与日志记录（生产环境会将冷数据迁移至归档/冷存储表）。
 *
 * <p>容错策略：每个实例独立 try-catch，单个实例处理失败不影响其余实例。
 *
 * <p>Bean 名称 = {@code flowHistoryArchiveJobHandler}，
 * 可在 pmis_job 表配置：handler=flowHistoryArchiveJobHandler, cron="0 0 3 * * ?"（每日 03:00 执行）。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Slf4j
@Component("flowHistoryArchiveJobHandler")
@RequiredArgsConstructor
public class FlowHistoryArchiveJobHandler implements JobHandler {

    /** 默认归档阈值天数 */
    private static final int DEFAULT_ARCHIVE_DAYS = 30;

    private final FlowInstanceMapper instanceMapper;
    private final FlowHisTaskMapper hisTaskMapper;
    private final FlowTaskMapper taskMapper;

    /**
     * 扫描并归档历史实例
     *
     * @param paramsJson 参数 JSON，可包含 days（归档阈值天数，默认 30），可空
     * @return 执行结果摘要：archived/verified/missing 等计数
     */
    @Override
    public Object execute(String paramsJson) throws Exception {
        long start = System.currentTimeMillis();
        int days = parseDays(paramsJson);

        log.info("[FlowHistoryArchive] 开始归档历史数据 days={}", days);

        // 查询已结束且结束时间超过阈值的实例（deleted=0 由 MyBatis-Plus 逻辑删除自动过滤）
        LocalDateTime threshold = LocalDateTime.now().minusDays(days);
        LambdaQueryWrapper<FlowInstanceDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(FlowInstanceDO::getFlowStatus,
                        FlowInstanceStatus.COMPLETED.name(),
                        FlowInstanceStatus.TERMINATED.name(),
                        FlowInstanceStatus.REJECTED.name())
                .lt(FlowInstanceDO::getEndAt, threshold)
                .orderByAsc(FlowInstanceDO::getEndAt);

        List<FlowInstanceDO> oldInstances;
        try {
            oldInstances = instanceMapper.selectList(wrapper);
        } catch (Exception e) {
            log.error("[FlowHistoryArchive] 查询历史实例失败: {}", e.getMessage(), e);
            Map<String, Object> err = new HashMap<>();
            err.put("ok", false);
            err.put("error", e.getMessage());
            return err;
        }

        if (oldInstances == null || oldInstances.isEmpty()) {
            log.info("FlowHistoryArchiveJobHandler: archived 0 instances older than {} days", days);
            Map<String, Object> empty = new HashMap<>();
            empty.put("ok", true);
            empty.put("archived", 0);
            empty.put("days", days);
            empty.put("costMs", System.currentTimeMillis() - start);
            return empty;
        }

        int archivedpackage com.njydsz.pmis.workflow.flow.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.njydsz.pmis.common.job.JobHandler;
import com.njydsz.pmis.workflow.flow.entity.FlowHisTaskDO;
import com.njydsz.pmis.workflow.flow.entity.FlowInstanceDO;
import com.njydsz.pmis.workflow.flow.entity.FlowTaskDO;
import com.njydsz.pmis.workflow.flow.enums.FlowInstanceStatus;
import com.njydsz.pmis.workflow.flow.mapper.FlowHisTaskMapper;
import com.njydsz.pmis.workflow.flow.mapper.FlowInstanceMapper;
import com.njydsz.pmis.workflow.flow.mapper.FlowTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * GAP-P1: 历史数据归档任务处理器
 *
 * <p>定时扫描已结束（COMPLETED/TERMINATED/REJECTED）且结束时间超过阈值（默认 30 天）的流程实例，
 * 校验其全部任务已归档至 pmis_flow_his_task，并记录归档统计。
 *
 * <p>当前版本仅做校验与日志记录（生产环境会将冷数据迁移至归档/冷存储表）。
 *
 * <p>容错策略：每个实例独立 try-catch，单个实例处理失败不影响其余实例。
 *
 * <p>Bean 名称 = {@code flowHistoryArchiveJobHandler}，
 * 可在 pmis_job 表配置：handler=flowHistoryArchiveJobHandler, cron="0 0 3 * * ?"（每日 03:00 执行）。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Slf4j
@Component("flowHistoryArchiveJobHandler")
@RequiredArgsConstructor
public class FlowHistoryArchiveJobHandler implements JobHandler {

    /** 默认归档阈值天数 */
    private static final int DEFAULT_ARCHIVE_DAYS = 30;

    private final FlowInstanceMapper instanceMapper;
    private final FlowHisTaskMapper hisTaskMapper;
    private final FlowTaskMapper taskMapper;

    /**
     * 扫描并归档历史实例
     *
     * @param paramsJson 参数 JSON，可包含 days（归档阈值天数，默认 30），可空
     * @return 执行结果摘要：archived/verified/missing 等计数
     */
    @Override
    public Object execute(String paramsJson) throws Exception {
        long start = System.currentTimeMillis();
        int days = parseDays(paramsJson);

        log.info("[FlowHistoryArchive] 开始归档历史数据 days={}", days);

        // 查询已结束且结束时间超过阈值的实例（deleted=0 由 MyBatis-Plus 逻辑删除自动过滤）
        LocalDateTime threshold = LocalDateTime.now().minusDays(days);
        LambdaQueryWrapper<FlowInstanceDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(FlowInstanceDO::getFlowStatus,
                        FlowInstanceStatus.COMPLETED.name(),
                        FlowInstanceStatus.TERMINATED.name(),
                        FlowInstanceStatus.REJECTED.name())
                .lt(FlowInstanceDO::getEndAt, threshold)
                .orderByAsc(FlowInstanceDO::getEndAt);

        List<FlowInstanceDO> oldInstances;
        try {
            oldInstances = instanceMapper.selectList(wrapper);
        } catch (Exception e) {
            log.error("[FlowHistoryArchive] 查询历史实例失败: {}", e.getMessage(), e);
            Map<String, Object> err = new HashMap<>();
            err.put("ok", false);
            err.put("error", e.getMessage());
            return err;
        }

        if (oldInstances == null || oldInstances.isEmpty()) {
            log.info("FlowHistoryArchiveJobHandler: archived 0 instances older than {} days", days);
            Map<String, Object> empty = new HashMap<>();
            empty.put("ok", true);
            empty.put("archived", 0);
            empty.put("days", days);
            empty.put("costMs", System.currentTimeMillis() - start);
            return empty;
        }

        int archived = 0;
        int missing = 0;
        int errors = 0;
        forpackage com.njydsz.pmis.workflow.flow.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.njydsz.pmis.common.job.JobHandler;
import com.njydsz.pmis.workflow.flow.entity.FlowHisTaskDO;
import com.njydsz.pmis.workflow.flow.entity.FlowInstanceDO;
import com.njydsz.pmis.workflow.flow.entity.FlowTaskDO;
import com.njydsz.pmis.workflow.flow.enums.FlowInstanceStatus;
import com.njydsz.pmis.workflow.flow.mapper.FlowHisTaskMapper;
import com.njydsz.pmis.workflow.flow.mapper.FlowInstanceMapper;
import com.njydsz.pmis.workflow.flow.mapper.FlowTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * GAP-P1: 历史数据归档任务处理器
 *
 * <p>定时扫描已结束（COMPLETED/TERMINATED/REJECTED）且结束时间超过阈值（默认 30 天）的流程实例，
 * 校验其全部任务已归档至 pmis_flow_his_task，并记录归档统计。
 *
 * <p>当前版本仅做校验与日志记录（生产环境会将冷数据迁移至归档/冷存储表）。
 *
 * <p>容错策略：每个实例独立 try-catch，单个实例处理失败不影响其余实例。
 *
 * <p>Bean 名称 = {@code flowHistoryArchiveJobHandler}，
 * 可在 pmis_job 表配置：handler=flowHistoryArchiveJobHandler, cron="0 0 3 * * ?"（每日 03:00 执行）。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Slf4j
@Component("flowHistoryArchiveJobHandler")
@RequiredArgsConstructor
public class FlowHistoryArchiveJobHandler implements JobHandler {

    /** 默认归档阈值天数 */
    private static final int DEFAULT_ARCHIVE_DAYS = 30;

    private final FlowInstanceMapper instanceMapper;
    private final FlowHisTaskMapper hisTaskMapper;
    private final FlowTaskMapper taskMapper;

    /**
     * 扫描并归档历史实例
     *
     * @param paramsJson 参数 JSON，可包含 days（归档阈值天数，默认 30），可空
     * @return 执行结果摘要：archived/verified/missing 等计数
     */
    @Override
    public Object execute(String paramsJson) throws Exception {
        long start = System.currentTimeMillis();
        int days = parseDays(paramsJson);

        log.info("[FlowHistoryArchive] 开始归档历史数据 days={}", days);

        // 查询已结束且结束时间超过阈值的实例（deleted=0 由 MyBatis-Plus 逻辑删除自动过滤）
        LocalDateTime threshold = LocalDateTime.now().minusDays(days);
        LambdaQueryWrapper<FlowInstanceDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(FlowInstanceDO::getFlowStatus,
                        FlowInstanceStatus.COMPLETED.name(),
                        FlowInstanceStatus.TERMINATED.name(),
                        FlowInstanceStatus.REJECTED.name())
                .lt(FlowInstanceDO::getEndAt, threshold)
                .orderByAsc(FlowInstanceDO::getEndAt);

        List<FlowInstanceDO> oldInstances;
        try {
            oldInstances = instanceMapper.selectList(wrapper);
        } catch (Exception e) {
            log.error("[FlowHistoryArchive] 查询历史实例失败: {}", e.getMessage(), e);
            Map<String, Object> err = new HashMap<>();
            err.put("ok", false);
            err.put("error", e.getMessage());
            return err;
        }

        if (oldInstances == null || oldInstances.isEmpty()) {
            log.info("FlowHistoryArchiveJobHandler: archived 0 instances older than {} days", days);
            Map<String, Object> empty = new HashMap<>();
            empty.put("ok", true);
            empty.put("archived", 0);
            empty.put("days", days);
            empty.put("costMs", System.currentTimeMillis() - start);
            return empty;
        }

        int archived = 0;
        int missing = 0;
        int errors = 0;
        for (FlowInstanceDO instance : oldInstances) {
            try {
                if (verifyAndArchive(instancepackage com.njydsz.pmis.workflow.flow.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.njydsz.pmis.common.job.JobHandler;
import com.njydsz.pmis.workflow.flow.entity.FlowHisTaskDO;
import com.njydsz.pmis.workflow.flow.entity.FlowInstanceDO;
import com.njydsz.pmis.workflow.flow.entity.FlowTaskDO;
import com.njydsz.pmis.workflow.flow.enums.FlowInstanceStatus;
import com.njydsz.pmis.workflow.flow.mapper.FlowHisTaskMapper;
import com.njydsz.pmis.workflow.flow.mapper.FlowInstanceMapper;
import com.njydsz.pmis.workflow.flow.mapper.FlowTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * GAP-P1: 历史数据归档任务处理器
 *
 * <p>定时扫描已结束（COMPLETED/TERMINATED/REJECTED）且结束时间超过阈值（默认 30 天）的流程实例，
 * 校验其全部任务已归档至 pmis_flow_his_task，并记录归档统计。
 *
 * <p>当前版本仅做校验与日志记录（生产环境会将冷数据迁移至归档/冷存储表）。
 *
 * <p>容错策略：每个实例独立 try-catch，单个实例处理失败不影响其余实例。
 *
 * <p>Bean 名称 = {@code flowHistoryArchiveJobHandler}，
 * 可在 pmis_job 表配置：handler=flowHistoryArchiveJobHandler, cron="0 0 3 * * ?"（每日 03:00 执行）。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Slf4j
@Component("flowHistoryArchiveJobHandler")
@RequiredArgsConstructor
public class FlowHistoryArchiveJobHandler implements JobHandler {

    /** 默认归档阈值天数 */
    private static final int DEFAULT_ARCHIVE_DAYS = 30;

    private final FlowInstanceMapper instanceMapper;
    private final FlowHisTaskMapper hisTaskMapper;
    private final FlowTaskMapper taskMapper;

    /**
     * 扫描并归档历史实例
     *
     * @param paramsJson 参数 JSON，可包含 days（归档阈值天数，默认 30），可空
     * @return 执行结果摘要：archived/verified/missing 等计数
     */
    @Override
    public Object execute(String paramsJson) throws Exception {
        long start = System.currentTimeMillis();
        int days = parseDays(paramsJson);

        log.info("[FlowHistoryArchive] 开始归档历史数据 days={}", days);

        // 查询已结束且结束时间超过阈值的实例（deleted=0 由 MyBatis-Plus 逻辑删除自动过滤）
        LocalDateTime threshold = LocalDateTime.now().minusDays(days);
        LambdaQueryWrapper<FlowInstanceDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(FlowInstanceDO::getFlowStatus,
                        FlowInstanceStatus.COMPLETED.name(),
                        FlowInstanceStatus.TERMINATED.name(),
                        FlowInstanceStatus.REJECTED.name())
                .lt(FlowInstanceDO::getEndAt, threshold)
                .orderByAsc(FlowInstanceDO::getEndAt);

        List<FlowInstanceDO> oldInstances;
        try {
            oldInstances = instanceMapper.selectList(wrapper);
        } catch (Exception e) {
            log.error("[FlowHistoryArchive] 查询历史实例失败: {}", e.getMessage(), e);
            Map<String, Object> err = new HashMap<>();
            err.put("ok", false);
            err.put("error", e.getMessage());
            return err;
        }

        if (oldInstances == null || oldInstances.isEmpty()) {
            log.info("FlowHistoryArchiveJobHandler: archived 0 instances older than {} days", days);
            Map<String, Object> empty = new HashMap<>();
            empty.put("ok", true);
            empty.put("archived", 0);
            empty.put("days", days);
            empty.put("costMs", System.currentTimeMillis() - start);
            return empty;
        }

        int archived = 0;
        int missing = 0;
        int errors = 0;
        for (FlowInstanceDO instance : oldInstances) {
            try {
                if (verifyAndArchive(instance)) {
                    archived++;
                } else {
                    missing++;
                }
            } catch (Exception epackage com.njydsz.pmis.workflow.flow.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.njydsz.pmis.common.job.JobHandler;
import com.njydsz.pmis.workflow.flow.entity.FlowHisTaskDO;
import com.njydsz.pmis.workflow.flow.entity.FlowInstanceDO;
import com.njydsz.pmis.workflow.flow.entity.FlowTaskDO;
import com.njydsz.pmis.workflow.flow.enums.FlowInstanceStatus;
import com.njydsz.pmis.workflow.flow.mapper.FlowHisTaskMapper;
import com.njydsz.pmis.workflow.flow.mapper.FlowInstanceMapper;
import com.njydsz.pmis.workflow.flow.mapper.FlowTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * GAP-P1: 历史数据归档任务处理器
 *
 * <p>定时扫描已结束（COMPLETED/TERMINATED/REJECTED）且结束时间超过阈值（默认 30 天）的流程实例，
 * 校验其全部任务已归档至 pmis_flow_his_task，并记录归档统计。
 *
 * <p>当前版本仅做校验与日志记录（生产环境会将冷数据迁移至归档/冷存储表）。
 *
 * <p>容错策略：每个实例独立 try-catch，单个实例处理失败不影响其余实例。
 *
 * <p>Bean 名称 = {@code flowHistoryArchiveJobHandler}，
 * 可在 pmis_job 表配置：handler=flowHistoryArchiveJobHandler, cron="0 0 3 * * ?"（每日 03:00 执行）。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Slf4j
@Component("flowHistoryArchiveJobHandler")
@RequiredArgsConstructor
public class FlowHistoryArchiveJobHandler implements JobHandler {

    /** 默认归档阈值天数 */
    private static final int DEFAULT_ARCHIVE_DAYS = 30;

    private final FlowInstanceMapper instanceMapper;
    private final FlowHisTaskMapper hisTaskMapper;
    private final FlowTaskMapper taskMapper;

    /**
     * 扫描并归档历史实例
     *
     * @param paramsJson 参数 JSON，可包含 days（归档阈值天数，默认 30），可空
     * @return 执行结果摘要：archived/verified/missing 等计数
     */
    @Override
    public Object execute(String paramsJson) throws Exception {
        long start = System.currentTimeMillis();
        int days = parseDays(paramsJson);

        log.info("[FlowHistoryArchive] 开始归档历史数据 days={}", days);

        // 查询已结束且结束时间超过阈值的实例（deleted=0 由 MyBatis-Plus 逻辑删除自动过滤）
        LocalDateTime threshold = LocalDateTime.now().minusDays(days);
        LambdaQueryWrapper<FlowInstanceDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(FlowInstanceDO::getFlowStatus,
                        FlowInstanceStatus.COMPLETED.name(),
                        FlowInstanceStatus.TERMINATED.name(),
                        FlowInstanceStatus.REJECTED.name())
                .lt(FlowInstanceDO::getEndAt, threshold)
                .orderByAsc(FlowInstanceDO::getEndAt);

        List<FlowInstanceDO> oldInstances;
        try {
            oldInstances = instanceMapper.selectList(wrapper);
        } catch (Exception e) {
            log.error("[FlowHistoryArchive] 查询历史实例失败: {}", e.getMessage(), e);
            Map<String, Object> err = new HashMap<>();
            err.put("ok", false);
            err.put("error", e.getMessage());
            return err;
        }

        if (oldInstances == null || oldInstances.isEmpty()) {
            log.info("FlowHistoryArchiveJobHandler: archived 0 instances older than {} days", days);
            Map<String, Object> empty = new HashMap<>();
            empty.put("ok", true);
            empty.put("archived", 0);
            empty.put("days", days);
            empty.put("costMs", System.currentTimeMillis() - start);
            return empty;
        }

        int archived = 0;
        int missing = 0;
        int errors = 0;
        for (FlowInstanceDO instance : oldInstances) {
            try {
                if (verifyAndArchive(instance)) {
                    archived++;
                } else {
                    missing++;
                }
            } catch (Exception e) {
                errors++;
                log.error("[FlowHistoryArchive] 归档实例异常 instanceId={} err={}",
                        instance.getId(), e.getMessage(), e);
            }
        }

        log.infopackage com.njydsz.pmis.workflow.flow.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.njydsz.pmis.common.job.JobHandler;
import com.njydsz.pmis.workflow.flow.entity.FlowHisTaskDO;
import com.njydsz.pmis.workflow.flow.entity.FlowInstanceDO;
import com.njydsz.pmis.workflow.flow.entity.FlowTaskDO;
import com.njydsz.pmis.workflow.flow.enums.FlowInstanceStatus;
import com.njydsz.pmis.workflow.flow.mapper.FlowHisTaskMapper;
import com.njydsz.pmis.workflow.flow.mapper.FlowInstanceMapper;
import com.njydsz.pmis.workflow.flow.mapper.FlowTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * GAP-P1: 历史数据归档任务处理器
 *
 * <p>定时扫描已结束（COMPLETED/TERMINATED/REJECTED）且结束时间超过阈值（默认 30 天）的流程实例，
 * 校验其全部任务已归档至 pmis_flow_his_task，并记录归档统计。
 *
 * <p>当前版本仅做校验与日志记录（生产环境会将冷数据迁移至归档/冷存储表）。
 *
 * <p>容错策略：每个实例独立 try-catch，单个实例处理失败不影响其余实例。
 *
 * <p>Bean 名称 = {@code flowHistoryArchiveJobHandler}，
 * 可在 pmis_job 表配置：handler=flowHistoryArchiveJobHandler, cron="0 0 3 * * ?"（每日 03:00 执行）。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Slf4j
@Component("flowHistoryArchiveJobHandler")
@RequiredArgsConstructor
public class FlowHistoryArchiveJobHandler implements JobHandler {

    /** 默认归档阈值天数 */
    private static final int DEFAULT_ARCHIVE_DAYS = 30;

    private final FlowInstanceMapper instanceMapper;
    private final FlowHisTaskMapper hisTaskMapper;
    private final FlowTaskMapper taskMapper;

    /**
     * 扫描并归档历史实例
     *
     * @param paramsJson 参数 JSON，可包含 days（归档阈值天数，默认 30），可空
     * @return 执行结果摘要：archived/verified/missing 等计数
     */
    @Override
    public Object execute(String paramsJson) throws Exception {
        long start = System.currentTimeMillis();
        int days = parseDays(paramsJson);

        log.info("[FlowHistoryArchive] 开始归档历史数据 days={}", days);

        // 查询已结束且结束时间超过阈值的实例（deleted=0 由 MyBatis-Plus 逻辑删除自动过滤）
        LocalDateTime threshold = LocalDateTime.now().minusDays(days);
        LambdaQueryWrapper<FlowInstanceDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(FlowInstanceDO::getFlowStatus,
                        FlowInstanceStatus.COMPLETED.name(),
                        FlowInstanceStatus.TERMINATED.name(),
                        FlowInstanceStatus.REJECTED.name())
                .lt(FlowInstanceDO::getEndAt, threshold)
                .orderByAsc(FlowInstanceDO::getEndAt);

        List<FlowInstanceDO> oldInstances;
        try {
            oldInstances = instanceMapper.selectList(wrapper);
        } catch (Exception e) {
            log.error("[FlowHistoryArchive] 查询历史实例失败: {}", e.getMessage(), e);
            Map<String, Object> err = new HashMap<>();
            err.put("ok", false);
            err.put("error", e.getMessage());
            return err;
        }

        if (oldInstances == null || oldInstances.isEmpty()) {
            log.info("FlowHistoryArchiveJobHandler: archived 0 instances older than {} days", days);
            Map<String, Object> empty = new HashMap<>();
            empty.put("ok", true);
            empty.put("archived", 0);
            empty.put("days", days);
            empty.put("costMs", System.currentTimeMillis() - start);
            return empty;
        }

        int archived = 0;
        int missing = 0;
        int errors = 0;
        for (FlowInstanceDO instance : oldInstances) {
            try {
                if (verifyAndArchive(instance)) {
                    archived++;
                } else {
                    missing++;
                }
            } catch (Exception e) {
                errors++;
                log.error("[FlowHistoryArchive] 归档实例异常 instanceId={} err={}",
                        instance.getId(), e.getMessage(), e);
            }
        }

        log.info("FlowHistoryArchiveJobHandler: archived {} instances older than {} days",
                archived, days);
