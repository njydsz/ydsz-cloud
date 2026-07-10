# 最终 import 修复：修复所有模块中所有残留的旧 import
$allBackend = "d:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend"

# 完整的类名到新包的映射
$classToPkg = @{
    # workflow
    'FlowAssigneeLeaveHandler' = 'com.njydsz.pmis.workflow.service.instance'
    'FlowOfflineAutoForwardService' = 'com.njydsz.pmis.workflow.service.delegate'
    'FlowAssigneeLeaveHandlerImpl' = 'com.njydsz.pmis.workflow.service.impl.instance'
    'FlowOfflineAutoForwardServiceImpl' = 'com.njydsz.pmis.workflow.service.impl.delegate'
    'FlowTodoCountPushService' = 'com.njydsz.pmis.workflow.service.instance'
    'FlowTodoCountPushServiceImpl' = 'com.njydsz.pmis.workflow.service.impl.instance'
    'FlowDeployProcessDTO' = 'com.njydsz.pmis.workflow.dto.definition'
    'FlowAssigneeDTO' = 'com.njydsz.pmis.workflow.dto.instance'
    'EmbeddedApprovalActionDTO' = 'com.njydsz.pmis.workflow.dto.integration'
    'EmbeddedApprovalViewDTO' = 'com.njydsz.pmis.workflow.dto.integration'
    
    # message - service
    'DedupService' = 'com.njydsz.pmis.message.service.core'
    'DeliveryTimeOptimizer' = 'com.njydsz.pmis.message.service.core'
    'MsgLogArchiveService' = 'com.njydsz.pmis.message.service.core'
    'RateLimitService' = 'com.njydsz.pmis.message.service.core'
    'ReachStrategyService' = 'com.njydsz.pmis.message.service.core'
    'SmsProviderStrategyService' = 'com.njydsz.pmis.message.service.core'
    # message - service.impl
    'DedupServiceImpl' = 'com.njydsz.pmis.message.service.impl.core'
    'DeliveryTimeOptimizerImpl' = 'com.njydsz.pmis.message.service.impl.core'
    'MsgLogArchiveServiceImpl' = 'com.njydsz.pmis.message.service.impl.core'
    'RateLimitServiceImpl' = 'com.njydsz.pmis.message.service.impl.core'
    'ReachStrategyServiceImpl' = 'com.njydsz.pmis.message.service.impl.core'
    'RealtimeStatsService' = 'com.njydsz.pmis.message.service.impl.core'
    'RetryScanner' = 'com.njydsz.pmis.message.service.impl.core'
    'ScheduledMessageScanner' = 'com.njydsz.pmis.message.service.impl.core'
    'SmsProviderStrategyServiceImpl' = 'com.njydsz.pmis.message.service.impl.core'
    # message - entity
    'MsgLogDO' = 'com.njydsz.pmis.message.entity.core'
    'MsgNotificationDO' = 'com.njydsz.pmis.message.entity.core'
    'MsgTemplateDO' = 'com.njydsz.pmis.message.entity.template'
    'MsgTemplateVersionDO' = 'com.njydsz.pmis.message.entity.template'
    'MsgReceiptDO' = 'com.njydsz.pmis.message.entity.receipt'
    'MsgBatchDO' = 'com.njydsz.pmis.message.entity.batch'
    'MsgAggregateDO' = 'com.njydsz.pmis.message.entity.batch'
    'MsgCanaryDO' = 'com.njydsz.pmis.message.entity.canary'
    'MsgRouteRuleDO' = 'com.njydsz.pmis.message.entity.config'
    'MsgPreferenceDO' = 'com.njydsz.pmis.message.entity.config'
    'MsgSubscriptionDO' = 'com.njydsz.pmis.message.entity.config'
    'MsgOfflineDO' = 'com.njydsz.pmis.message.entity.config'
    'MsgFeedbackDO' = 'com.njydsz.pmis.message.entity.config'
    'MsgTraceDO' = 'com.njydsz.pmis.message.entity.config'
    # message - mapper
    'MsgLogMapper' = 'com.njydsz.pmis.message.mapper.core'
    'MsgNotificationMapper' = 'com.njydsz.pmis.message.mapper.core'
    'MsgTemplateMapper' = 'com.njydsz.pmis.message.mapper.template'
    'MsgTemplateVersionMapper' = 'com.njydsz.pmis.message.mapper.template'
    'MsgReceiptMapper' = 'com.njydsz.pmis.message.mapper.receipt'
    'MsgBatchMapper' = 'com.njydsz.pmis.message.mapper.batch'
    'MsgAggregateMapper' = 'com.njydsz.pmis.message.mapper.batch'
    'MsgCanaryMapper' = 'com.njydsz.pmis.message.mapper.canary'
    'MsgRouteRuleMapper' = 'com.njydsz.pmis.message.mapper.config'
    'MsgPreferenceMapper' = 'com.njydsz.pmis.message.mapper.config'
    'MsgSubscriptionMapper' = 'com.njydsz.pmis.message.mapper.config'
    'MsgOfflineMapper' = 'com.njydsz.pmis.message.mapper.config'
    'MsgFeedbackMapper' = 'com.njydsz.pmis.message.mapper.config'
    'MsgTraceMapper' = 'com.njydsz.pmis.message.mapper.config'
    # message - enums
    'AggregateBatchStatusEnum' = 'com.njydsz.pmis.message.enums.batch'
    'MessageChannelEnum' = 'com.njydsz.pmis.message.enums.core'
    'MessagePriorityEnum' = 'com.njydsz.pmis.message.enums.core'
    'MessageStatusEnum' = 'com.njydsz.pmis.message.enums.core'
    'RecallStatusEnum' = 'com.njydsz.pmis.message.enums.receipt'
    'ReceiptStatusEnum' = 'com.njydsz.pmis.message.enums.receipt'
    'ReceiptTypeEnum' = 'com.njydsz.pmis.message.enums.receipt'
    'SubscriptionStatusEnum' = 'com.njydsz.pmis.message.enums.config'
    'TemplateAuditStatusEnum' = 'com.njydsz.pmis.message.enums.template'
    # message - dto
    'UserReachProfileDTO' = 'com.njydsz.pmis.message.dto.core'
    'RichMediaContent' = 'com.njydsz.pmis.message.dto.core'
    'FunnelStatsVO' = 'com.njydsz.pmis.message.dto.core'
    'CostStatsVO' = 'com.njydsz.pmis.message.dto.core'
    'ChannelStatsVO' = 'com.njydsz.pmis.message.dto.core'
    
    # userinfo
    'PoolType' = 'com.njydsz.pmis.userinfo.enums.resource'
    'TagType' = 'com.njydsz.pmis.userinfo.enums.user'
    'JwtSimpleBuilder' = 'com.njydsz.pmis.userinfo.service.impl.auth'
    'PasswordResetDTO' = 'com.njydsz.pmis.userinfo.dto.auth'
    'PasswordChangeDTO' = 'com.njydsz.pmis.userinfo.dto.auth'
    
    # agent
    'ValidationResult' = 'com.njydsz.pmis.agent.service.agent'
    'AgentAlertLevel' = 'com.njydsz.pmis.agent.enums.agent'
    'AgentRunStatus' = 'com.njydsz.pmis.agent.enums.agent'
    'AgentType' = 'com.njydsz.pmis.agent.enums.agent'
    'HitlApprovalStatus' = 'com.njydsz.pmis.agent.enums.hitl'
    'DefaultTokenQuotaService' = 'com.njydsz.pmis.agent.service.impl.tool'
}

# 模块名到层的映射，用于构建旧 import 路径
$moduleLayers = @{
    'workflow' = @('controller', 'service', 'service.impl', 'entity', 'dto', 'mapper', 'enums')
    'message' = @('controller', 'service', 'service.impl', 'entity', 'dto', 'mapper', 'enums')
    'userinfo' = @('controller', 'service', 'service.impl', 'entity', 'dto', 'mapper', 'enums')
    'cronjob' = @('controller', 'service', 'service.impl', 'entity', 'dto', 'mapper', 'enums')
    'agent' = @('controller', 'service', 'service.impl', 'entity', 'dto', 'mapper', 'enums')
    'system' = @('controller', 'service', 'service.impl', 'entity', 'dto', 'mapper', 'enums')
}

# 从新包路径提取模块名
function Get-ModuleName($pkg) {
    if ($pkg -match 'com\.njydsz\.pmis\.([a-z]+)\.') { return $matches[1] }
    return $null
}

# 从新包路径提取层名
function Get-LayerName($pkg) {
    if ($pkg -match 'com\.njydsz\.pmis\.[a-z]+\.((?:service\.impl|controller|service|entity|dto|mapper|enums)(?:\.[a-z]+)?)') { return $matches[1] }
    # 提取 service.impl 部分
    if ($pkg -match 'com\.njydsz\.pmis\.[a-z]+\.(service\.impl)') { return 'service.impl' }
    if ($pkg -match 'com\.njydsz\.pmis\.[a-z]+\.([a-z]+)') { return $matches[1] }
    return $null
}

$allModules = @('message', 'userinfo', 'cronjob', 'agent', 'system', 'workflow', 'project', 'common')
$totalFixed = 0

foreach ($modName in $allModules) {
    $modPath = "$allBackend\ydsz-pmis-$modName\src"
    if (!(Test-Path $modPath)) { continue }
    
    $files = Get-ChildItem -Path $modPath -Recurse -Filter "*.java"
    foreach ($f in $files) {
        $content = [System.IO.File]::ReadAllText($f.FullName)
        $original = $content
        
        foreach ($className in $classToPkg.Keys) {
            $newPkg = $classToPkg[$className]
            $modNameFromPkg = Get-ModuleName $newPkg
            if (!$modNameFromPkg) { continue }
            
            # 获取层名（不含子域）
            $layerName = $null
            if ($newPkg -match "com\.njydsz\.pmis\.$modNameFromPkg\.((?:service\.impl|controller|service|entity|dto|mapper|enums))\.") {
                $layerName = $matches[1]
            }
            
            if ($layerName) {
                # 旧 import: import com.njydsz.pmis.{mod}.{layer}.{className};
                $oldImport = "import com.njydsz.pmis.$modNameFromPkg.$layerName.$className;"
                $newImport = "import $newPkg.$className;"
                $content = $content.Replace($oldImport, $newImport)
            }
        }
        
        if ($content -ne $original) {
            [System.IO.File]::WriteAllText($f.FullName, $content, (New-Object System.Text.UTF8Encoding($false)))
            $totalFixed++
        }
    }
}

Write-Host "Total files with final import fixes: $totalFixed"
