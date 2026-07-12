# Restore ALL missing old common module files that don't exist in the new common module
$ErrorActionPreference = "Stop"

$repoRoot = "d:/Code/ydsz/ydsz-pmis"
$oldCommit = "fa6c7acf^"
$commonDir = "$repoRoot/ydsz-pmis-backend/ydsz-pmis-common"

# Step 1: Get all old Java files from git
Write-Host "Getting old common module file list from git..."
$oldFiles = git ls-tree -r --name-only $oldCommit "ydsz-pmis-backend/ydsz-pmis-common/" | Where-Object {
    $_ -like "*.java" -and $_ -notlike "*target*" -and $_ -notlike "*test*"
}
Write-Host "Found $($oldFiles.Count) old Java files"

# Step 2: Get all current Java files in new common module
Write-Host "Scanning new common module for existing files..."
$newFiles = @{}
Get-ChildItem -Path $commonDir -Recurse -Filter "*.java" | Where-Object {
    $_.FullName -notlike "*target*" -and $_.FullName -notlike "*test*"
} | ForEach-Object {
    # Extract the relative Java path (from com/ onwards)
    $rel = $_.FullName -replace ".*\\src\\main\\java\\", ""
    $rel = $rel -replace "\\", "/"
    $newFiles[$rel] = $true
}
Write-Host "Found $($newFiles.Count) existing files in new common module"

# Step 3: Restore old files that don't exist in new common module
$restored = 0
$skipped = 0
$failed = 0

foreach ($oldFile in $oldFiles) {
    # Extract the relative Java path (from com/ onwards)
    $relPath = $oldFile -replace "ydsz-pmis-backend/ydsz-pmis-common/ydsz-pmis-common-[^/]+/src/main/java/", ""

    # Check if this file already exists in new common module
    if ($newFiles.ContainsKey($relPath)) {
        $skipped++
        continue
    }

    # Extract the submodule name
    $relativePath = $oldFile -replace "ydsz-pmis-backend/ydsz-pmis-common/", ""
    $moduleMatch = [regex]::Match($relativePath, "^(ydsz-pmis-common-[^/]+)/(.+)$")
    if (-not $moduleMatch.Success) {
        continue
    }
    $moduleName = $moduleMatch.Groups[1].Value
    $javaRelativePath = $moduleMatch.Groups[2].Value

    # Only restore to modules that exist in the new common
    $moduleDir = Join-Path $commonDir $moduleName
    if (-not (Test-Path $moduleDir)) {
        continue
    }

    # Construct target file path
    $targetFilePath = Join-Path $moduleDir ($javaRelativePath -replace "/", "\")
    $targetDir = Split-Path $targetFilePath -Parent

    # Create directory if needed
    if (-not (Test-Path $targetDir)) {
        New-Item -ItemType Directory -Path $targetDir -Force | Out-Null
    }

    # Use git show with file output to preserve newlines
    $tempFile = [System.IO.Path]::GetTempFileName()
    $gitCmd = "git show `"${oldCommit}:$oldFile`" > `"$tempFile`""
    cmd /c $gitCmd 2>&1 | Out-Null

    if (Test-Path $tempFile) {
        $content = [System.IO.File]::ReadAllText($tempFile)

        # Remove BOM if present
        if ($content.Length -gt 0 -and $content[0] -eq [char]0xFEFF) {
            $content = $content.Substring(1)
        }

        # Write with UTF-8 no BOM
        $utf8NoBom = New-Object System.Text.UTF8Encoding $false
        [System.IO.File]::WriteAllText($targetFilePath, $content, $utf8NoBom)

        Remove-Item $tempFile -Force
        $className = [System.IO.Path]::GetFileNameWithoutExtension($oldFile)
        Write-Host "RESTORED: $className => $moduleName ($relPath)"
        $restored++
    } else {
        Write-Host "FAIL: $oldFile"
        $failed++
    }
}

Write-Host ""
Write-Host "=== Summary ==="
Write-Host "Restored: $restored"
Write-Host "Skipped (already exist): $skipped"
Write-Host "Failed: $failed"
