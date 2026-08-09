$ErrorActionPreference = 'Stop'
$target = 'D:\Maven\njydsz-maven'

Write-Output "== Query before =="
Write-Output ("Machine MAVEN_HOME=" + [Environment]::GetEnvironmentVariable('MAVEN_HOME','Machine'))
Write-Output ("Machine M2_HOME=" + [Environment]::GetEnvironmentVariable('M2_HOME','Machine'))
Write-Output ("User MAVEN_HOME=" + [Environment]::GetEnvironmentVariable('MAVEN_HOME','User'))
Write-Output ("User M2_HOME=" + [Environment]::GetEnvironmentVariable('M2_HOME','User'))

Write-Output "== Setting (Machine level, requires admin) =="
try {
    [Environment]::SetEnvironmentVariable('MAVEN_HOME', $target, 'Machine')
    [Environment]::SetEnvironmentVariable('M2_HOME', $target, 'Machine')
    Write-Output "Machine-level set: SUCCESS"
} catch {
    Write-Output ("Machine-level set DENIED: " + $_.Exception.Message)
}

Write-Output "== Setting (User level, no admin needed) =="
try {
    [Environment]::SetEnvironmentVariable('MAVEN_HOME', $target, 'User')
    [Environment]::SetEnvironmentVariable('M2_HOME', $target, 'User')
    Write-Output "User-level set: SUCCESS"
} catch {
    Write-Output ("User-level set DENIED: " + $_.Exception.Message)
}

Write-Output "== Query after =="
Write-Output ("Machine MAVEN_HOME=" + [Environment]::GetEnvironmentVariable('MAVEN_HOME','Machine'))
Write-Output ("User MAVEN_HOME=" + [Environment]::GetEnvironmentVariable('MAVEN_HOME','User'))
