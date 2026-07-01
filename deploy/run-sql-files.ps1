$env:Path = "C:\Program Files\PostgreSQL\18\bin;" + $env:Path
$env:PGPASSWORD = "Limw1020"
$env:PGCLIENTENCODING = "UTF8"

$sqlDir = "d:\Code\ydsz\ydsz-pmis\deploy\sql"
$dbHost = "127.0.0.1"
$dbUser = "postgres"
$dbName = "ydsz-pmis"

# Get all V*.sql files sorted by name (version order)
$sqlFiles = Get-ChildItem -Path $sqlDir -Filter "V*.sql" | Sort-Object Name

Write-Host "Found $($sqlFiles.Count) SQL files to execute in order:"
$sqlFiles | ForEach-Object { Write-Host "  - $($_.Name)" }
Write-Host ""

$failed = @()
$success = @()

foreach ($file in $sqlFiles) {
    Write-Host "==> Executing $($file.Name)..." -ForegroundColor Cyan
    $output = & psql -h $dbHost -U $dbUser -d $dbName -v ON_ERROR_STOP=1 -f $file.FullName 2>&1
    if ($LASTEXITCODE -eq 0) {
        Write-Host "    OK: $($file.Name)" -ForegroundColor Green
        $success += $file.Name
    } else {
        Write-Host "    FAILED: $($file.Name)" -ForegroundColor Red
        $output | Select-Object -Last 30 | ForEach-Object { Write-Host "      $_" }
        $failed += $file.Name
        # Continue to next file even on failure to see all errors
    }
}

Write-Host ""
Write-Host "=== Summary ===" -ForegroundColor Yellow
Write-Host "Success: $($success.Count)" -ForegroundColor Green
Write-Host "Failed: $($failed.Count)" -ForegroundColor Red
if ($failed.Count -gt 0) {
    Write-Host "Failed files:"
    $failed | ForEach-Object { Write-Host "  - $_" }
}
