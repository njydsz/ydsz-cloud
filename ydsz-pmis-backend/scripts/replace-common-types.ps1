<#
.SYNOPSIS
  批量替换旧 Common 类型引用为新 Common 层类型
.DESCRIPTION
  Result → BaseResponse
  BizErrorCode → StandardResultCode
  SecurityContext → AuthContext
  PageResult → PageResponse
#>

$backendRoot = "d:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend"

# 获取所有引用旧类型的 Java 文件
$files = Get-ChildItem -Path $backendRoot -Recurse -Filter "*.java" |
    Where-Object {
        $_.FullName -notmatch "\\target\\" -and
        (Select-String -Path $_.FullName -Pattern "import com\.njydsz\.pmis\.common\.api\.(Result|BizErrorCode|PageResult);|import com\.njydsz\.pmis\.common\.security\.SecurityContext;" -Quiet)
    }

Write-Host "Found $($files.Count) files to process"

$count = 0
foreach ($file in $files) {
    $content = Get-Content $file.FullName -Raw -Encoding UTF8
    $modified = $false

    # 1. 替换 import: Result → BaseResponse
    if ($content -match "import com\.njydsz\.pmis\.common\.api\.Result;") {
        $content = $content -replace "import com\.njydsz\.pmis\.common\.api\.Result;", "import com.njydsz.pmis.common.core.response.BaseResponse;"
        $modified = $true
    }

    # 2. 替换 import: BizErrorCode → StandardResultCode
    if ($content -match "import com\.njydsz\.pmis\.common\.api\.BizErrorCode;") {
        $content = $content -replace "import com\.njydsz\.pmis\.common\.api\.BizErrorCode;", "import com.njydsz.pmis.common.core.response.StandardResultCode;"
        $modified = $true
    }

    # 3. 替换 import: PageResult → PageResponse
    if ($content -match "import com\.njydsz\.pmis\.common\.api\.PageResult;") {
        $content = $content -replace "import com\.njydsz\.pmis\.common\.api\.PageResult;", "import com.njydsz.pmis.common.core.response.PageResponse;"
        $modified = $true
    }

    # 4. 替换 import: SecurityContext → AuthContext
    if ($content -match "import com\.njydsz\.pmis\.common\.security\.SecurityContext;") {
        $content = $content -replace "import com\.njydsz\.pmis\.common\.security\.SecurityContext;", "import com.njydsz.pmis.common.auth.context.AuthContext;"
        $modified = $true
    }

    # 5. 替换类型引用: Result< → BaseResponse< (但不替换 ResultCode, ResultSet 等)
    if ($content -match "\bResult<") {
        $content = $content -replace "\bResult<", "BaseResponse<"
        $modified = $true
    }

    # 6. 替换静态方法调用: Result. → BaseResponse.
    if ($content -match "\bResult\.") {
        $content = $content -replace "\bResult\.", "BaseResponse."
        $modified = $true
    }

    # 7. 替换常量引用: Result.CODE_SUCCESS → BaseResponse.SUCCESS, Result.CODE_FAIL → BaseResponse.ERROR
    if ($content -match "BaseResponse\.CODE_SUCCESS") {
        $content = $content -replace "BaseResponse\.CODE_SUCCESS", "BaseResponse.SUCCESS"
        $modified = $true
    }
    if ($content -match "BaseResponse\.CODE_FAIL") {
        $content = $content -replace "BaseResponse\.CODE_FAIL", "BaseResponse.ERROR"
        $modified = $true
    }

    # 8. 替换 BizErrorCode. → StandardResultCode.
    if ($content -match "\bBizErrorCode\.") {
        $content = $content -replace "\bBizErrorCode\.", "StandardResultCode."
        $modified = $true
    }

    # 9. 替换 PageResult< → PageResponse<
    if ($content -match "\bPageResult<") {
        $content = $content -replace "\bPageResult<", "PageResponse<"
        $modified = $true
    }

    # 10. 替换 PageResult. → PageResponse.
    if ($content -match "\bPageResult\.") {
        $content = $content -replace "\bPageResult\.", "PageResponse."
        $modified = $true
    }

    # 11. 替换 SecurityContext. → AuthContext.
    if ($content -match "\bSecurityContext\.") {
        $content = $content -replace "\bSecurityContext\.", "AuthContext."
        $modified = $true
    }

    # 12. 替换 getCode() != 0 → !isSuccess() (旧 Result 用 int 0 表示成功)
    if ($content -match "\.getCode\(\)\s*!=\s*0") {
        $content = $content -replace "\.getCode\(\)\s*!=\s*0", ".isSuccess() == false"
        $modified = $true
    }
    if ($content -match "\.getCode\(\)\s*==\s*0") {
        $content = $content -replace "\.getCode\(\)\s*==\s*0", ".isSuccess()"
        $modified = $true
    }

    if ($modified) {
        # 使用 UTF8 无 BOM 写入
        $utf8NoBom = New-Object System.Text.UTF8Encoding $false
        [System.IO.File]::WriteAllText($file.FullName, $content, $utf8NoBom)
        $count++
        Write-Host "  Updated: $($file.FullName.Substring($backendRoot.Length + 1))"
    }
}

Write-Host "`nDone! Updated $count files."
