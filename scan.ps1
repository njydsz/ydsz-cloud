[System.Text.Encoding]::RegisterProvider([System.Text.CodePagesEncodingProvider]::Instance)
 = .java,.xml,.yml,.yaml,.properties,.md,.sql,.vue,.ts,.js,.json,.txt,.conf
 = @()
Get-ChildItem d:\Code\ydsz\ydsz-pmis -Recurse -Include  -ErrorAction SilentlyContinue | Where-Object { .FullName -notmatch node_modules|\.git|target|dist|build|\.idea|\.vscode|run-logs } | ForEach-Object { try {  = [System.IO.File]::ReadAllBytes(.FullName); [System.Text.Encoding]::UTF8.GetString() | Out-Null } catch {  += .FullName } }
 | Set-Content nonUTF8_files.txt -Encoding utf8
Write-Host (Non-UTF-8 files:  + .Count)