# Script to replace all old DO class name references in ydz-userinfo module

$searchDir = "D:\Code\open\ydsz-cloud\ydsz-userinfo"

# Define all renames: oldName -> newName
# Order: longer names first to avoid partial matches (e.g., UserAccountDO before UserRoleDO is not needed since they don't share prefixes)
$renames = @(
    @("UserPasswordHistoryDO", "UserPasswordHistory"),
    @("UserLoginHistoryDO", "UserLoginHistory"),
    @("OAuth2ApplicationDO", "OAuth2Application"),
    @("WebAuthnCredentialDO", "WebAuthnCredential"),
    @("RolePermissionDO", "RolePermission"),
    @("CompanyDeptDO", "CompanyDept"),
    @("SocialClientDO", "SocialClient"),
    @("SocialAccountDO", "SocialAccount"),
    @("SecurityAlertDO", "SecurityAlert"),
    @("SamlIdpConfigDO", "SamlIdpConfig"),
    @("AuthPolicyDO", "AuthPolicy"),
    @("UserRoleDO", "UserRole"),
    @("UserPostDO", "UserPost"),
    @("UserDeptDO", "UserDept"),
    @("UserAccountDO", "UserAccount"),
    @("DepartmentDO", "Department"),
    @("LanguageDO", "Language"),
    @("CompanyDO", "Company"),
    @("RoleDO", "Role"),
    @("PostDO", "Post"),
    @("MenuDO", "Menu")
)

$files = Get-ChildItem -Path $searchDir -Recurse -Filter "*.java"
$totalReplacements = 0

foreach ($file in $files) {
    $content = Get-Content $file.FullName -Raw -Encoding UTF8
    $originalContent = $content
    $fileChanged = $false
    
    foreach ($pair in $renames) {
        $oldName = $pair[0]
        $newName = $pair[1]
        
        if ($content -match [regex]::Escape($oldName)) {
            $count = ([regex]::Matches($content, [regex]::Escape($oldName))).Count
            $content = $content -replace [regex]::Escape($oldName), $newName
            $totalReplacements += $count
            $fileChanged = $true
        }
    }
    
    if ($fileChanged -and $content -ne $originalContent) {
        [System.IO.File]::WriteAllText($file.FullName, $content, [System.Text.Encoding]::UTF8)
        Write-Host "Updated: $($file.FullName.Replace($searchDir, ''))"
    }
}

Write-Host "`nTotal replacements: $totalReplacements"
Write-Host "Done!"
