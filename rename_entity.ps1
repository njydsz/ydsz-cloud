# Script to rename all 21 entity files: remove DO suffix, fix Javadoc refs, add @SuppressWarnings

$baseDir = "D:\Code\open\ydsz-cloud\ydsz-userinfo\ydsz-userinfo-infra\src\main\java\com\njydsz\userinfo\infra\entity"

# Define all renames: oldName -> newName
$renames = @(
    @("AuthPolicyDO", "AuthPolicy"),
    @("OAuth2ApplicationDO", "OAuth2Application"),
    @("WebAuthnCredentialDO", "WebAuthnCredential"),
    @("UserRoleDO", "UserRole"),
    @("UserPostDO", "UserPost"),
    @("UserPasswordHistoryDO", "UserPasswordHistory"),
    @("UserLoginHistoryDO", "UserLoginHistory"),
    @("UserDeptDO", "UserDept"),
    @("RolePermissionDO", "RolePermission"),
    @("RoleDO", "Role"),
    @("PostDO", "Post"),
    @("MenuDO", "Menu"),
    @("LanguageDO", "Language"),
    @("DepartmentDO", "Department"),
    @("CompanyDO", "Company"),
    @("CompanyDeptDO", "CompanyDept"),
    @("SocialClientDO", "SocialClient"),
    @("SocialAccountDO", "SocialAccount"),
    @("SecurityAlertDO", "SecurityAlert"),
    @("SamlIdpConfigDO", "SamlIdpConfig"),
    @("UserAccountDO", "UserAccount")
)

foreach ($pair in $renames) {
    $oldName = $pair[0]
    $newName = $pair[1]
    
    $oldFile = Join-Path $baseDir "$oldName.java"
    $newFile = Join-Path $baseDir "$newName.java"
    
    if (-not (Test-Path $oldFile)) {
        Write-Host "WARNING: $oldFile not found, skipping"
        continue
    }
    
    $content = Get-Content $oldFile -Raw -Encoding UTF8
    
    # 1. Remove DO suffix from all entity class name references
    $content = $content -replace 'AuthPolicyDO', 'AuthPolicy'
    $content = $content -replace 'OAuth2ApplicationDO', 'OAuth2Application'
    $content = $content -replace 'WebAuthnCredentialDO', 'WebAuthnCredential'
    $content = $content -replace 'UserRoleDO', 'UserRole'
    $content = $content -replace 'UserPostDO', 'UserPost'
    $content = $content -replace 'UserPasswordHistoryDO', 'UserPasswordHistory'
    $content = $content -replace 'UserLoginHistoryDO', 'UserLoginHistory'
    $content = $content -replace 'UserDeptDO', 'UserDept'
    $content = $content -replace 'RolePermissionDO', 'RolePermission'
    $content = $content -replace 'RoleDO', 'Role'
    $content = $content -replace 'PostDO', 'Post'
    $content = $content -replace 'MenuDO', 'Menu'
    $content = $content -replace 'LanguageDO', 'Language'
    $content = $content -replace 'DepartmentDO', 'Department'
    $content = $content -replace 'CompanyDO', 'Company'
    $content = $content -replace 'CompanyDeptDO', 'CompanyDept'
    $content = $content -replace 'SocialClientDO', 'SocialClient'
    $content = $content -replace 'SocialAccountDO', 'SocialAccount'
    $content = $content -replace 'SecurityAlertDO', 'SecurityAlert'
    $content = $content -replace 'SamlIdpConfigDO', 'SamlIdpConfig'
    $content = $content -replace 'UserAccountDO', 'UserAccount'
    
    # 2. Add @SuppressWarnings("unchecked") if @SuperBuilder is present and not already there
    if ($content -match '@SuperBuilder' -and $content -notmatch '@SuppressWarnings\("unchecked"\)') {
        # Add @SuppressWarnings("unchecked") right before the class declaration
        $content = $content -replace '(public class)', "@SuppressWarnings(""unchecked"")`r`n$1"
    }
    
    # Write new file
    [System.IO.File]::WriteAllText($newFile, $content, [System.Text.Encoding]::UTF8)
    Write-Host "Created: $newFile"
}

# Delete old files
foreach ($pair in $renames) {
    $oldName = $pair[0]
    $oldFile = Join-Path $baseDir "$oldName.java"
    if (Test-Path $oldFile) {
        Remove-Item $oldFile -Force
        Write-Host "Deleted: $oldFile"
    }
}

Write-Host "`nAll entity files renamed successfully!"
