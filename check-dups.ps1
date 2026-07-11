$files = @('AlertDispatchDTO','AlertEventDTO','ApprovalDTO','AlertDispatchDO','AlertSeverity','ApprovalStatus','ProjectType','WorkflowServiceClient','WorkflowServiceClientFallback','ImportExportController','ImportService','ImportServiceImpl','AlertDispatchMapper','ProjectSearchVO','SearchController','SearchService','SearchServiceImpl','UniversalSearchVO','AlertDispatchService','AlertDispatchServiceImpl')
foreach ($f in $files) {
    $results = Get-ChildItem -Path 'D:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend' -Recurse -Filter "$f.java" -ErrorAction SilentlyContinue
    if ($results.Count -gt 1) {
        Write-Output "DUPLICATE: $f ($($results.Count) copies)"
        foreach ($r in $results) {
            Write-Output "  $($r.FullName)"
        }
    }
}
