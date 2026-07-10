# 修复 message 模块中 Msg* 前缀类的 import
$basePath = "d:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend\ydsz-pmis-message\src"

# Msg前缀类的域映射
$msgClassMap = @{
    'MsgLog' = 'core'
    'MsgNotification' = 'core'
    'MsgTemplate' = 'template'
    'MsgTemplateVersion' = 'template'
    'MsgReceipt' = 'receipt'
    'MsgBatch' = 'batch'
    'MsgAggregate' = 'batch'
    'MsgCanary' = 'canary'
    'MsgRouteRule' = 'config'
    'MsgPreference' = 'config'
    'MsgSubscription' = 'config'
    'MsgOffline' = 'config'
    'MsgFeedback' = 'config'
    'MsgTrace' = 'config'
    # Service interfaces that weren't matched
    'DedupService' = 'core'
    'DeliveryTimeOptimizer' = 'core'
    'RateLimitService' = 'core'
    'MessageLogService' = 'core'
    'ChannelRouter' = 'core'
    # DTOs
    'ChannelStats' = 'core'
    'CostStats' = 'core'
    'FunnelStats' = 'core'
    'MessageStats' = 'core'
    'MessageLogQuery' = 'core'
    'MessageSend' = 'core'
    'NotificationQuery' = 'core'
    'NotificationSend' = 'core'
    'RichMediaContent' = 'core'
    'UserReachProfile' = 'core'
    'OrchestrationFlow' = 'core'
    'OrchestrationNode' = 'core'
    'OrchestrationResult' = 'core'
    'BatchSendRequest' = 'batch'
    'BatchSendResult' = 'batch'
    'BatchProgress' = 'batch'
    'CanaryReport' = 'canary'
    'CanaryUpsert' = 'canary'
    'ReceiptCallback' = 'receipt'
    'ReceiptResult' = 'receipt'
    'ReceiptStats' = 'receipt'
    'RecallRequest' = 'recipt'
    'TemplateCreate' = 'template'
    'TemplateAudit' = 'template'
    'TemplatePreview' = 'template'
    'TemplateQuery' = 'template'
    'TemplateTestSend' = 'template'
    'RouteRuleUpsert' = 'config'
    'PreferenceUpsert' = 'config'
    'SubscriptionUpsert' = 'config'
    'UnsubscribeQuery' = 'config'
    'MessageFeedback' = 'config'
}

$files = Get-ChildItem -Path $basePath -Recurse -Filter "*.java"
$count = 0
foreach ($f in $files) {
    $content = [System.IO.File]::ReadAllText($f.FullName)
    $original = $content
    
    foreach ($layer in @('controller', 'service', 'entity', 'dto', 'mapper', 'enums')) {
        # Match Msg* prefixed classes
        $matches = [regex]::Matches($content, "import com\.njydsz\.pmis\.message\.$layer\.([A-Za-z0-9_]+)")
        foreach ($m in $matches) {
            $className = $m.Groups[1].Value
            $domain = $null
            foreach ($key in $msgClassMap.Keys) {
                if ($className.StartsWith($key)) { $domain = $msgClassMap[$key]; break }
            }
            if ($domain) {
                $oldImport = "import com.njydsz.pmis.message.$layer.$className"
                $newImport = "import com.njydsz.pmis.message.$layer.$domain.$className"
                $content = $content.Replace($oldImport, $newImport)
            }
        }
        # Also fix service.impl
        $implMatches = [regex]::Matches($content, "import com\.njydsz\.pmis\.message\.service\.impl\.([A-Za-z0-9_]+)")
        foreach ($m in $implMatches) {
            $className = $m.Groups[1].Value
            $domain = $null
            foreach ($key in $msgClassMap.Keys) {
                if ($className.StartsWith($key)) { $domain = $msgClassMap[$key]; break }
            }
            if ($domain) {
                $oldImport = "import com.njydsz.pmis.message.service.impl.$className"
                $newImport = "import com.njydsz.pmis.message.service.impl.$domain.$className"
                $content = $content.Replace($oldImport, $newImport)
            }
        }
    }
    
    if ($content -ne $original) {
        [System.IO.File]::WriteAllText($f.FullName, $content, (New-Object System.Text.UTF8Encoding($false)))
        $count++
        Write-Host "  Fixed: $($f.Name)"
    }
}
Write-Host "`nTotal: $count files fixed"
