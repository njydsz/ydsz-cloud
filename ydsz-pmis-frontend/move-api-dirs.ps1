$apiBase = "d:\Code\ydsz\ydsz-pmis\ydsz-pmis-frontend\src\api"

# Create new domain directories
$newDirs = @("opportunity", "initiation", "contract", "resource", "finance", "closure", "after-sales", "report", "rule-engine", "alert")
foreach ($dir in $newDirs) {
    $path = Join-Path $apiBase $dir
    if (-not (Test-Path $path)) {
        New-Item -ItemType Directory -Path $path -Force | Out-Null
        Write-Host "Created: $path"
    }
}

# Move directories
$moves = @(
    # Opportunity
    @{ from = "$apiBase\project\opportunity"; to = "$apiBase\opportunity" },
    # Initiation
    @{ from = "$apiBase\project\initiation"; to = "$apiBase\initiation" },
    # Change (under initiation)
    @{ from = "$apiBase\project\change"; to = "$apiBase\initiation\change" },
    # Contract
    @{ from = "$apiBase\project\contract"; to = "$apiBase\contract" },
    # Resource
    @{ from = "$apiBase\execution\rate-card"; to = "$apiBase\resource\rate-card" },
    @{ from = "$apiBase\execution\rate-internal"; to = "$apiBase\resource\rate-internal" },
    @{ from = "$apiBase\execution\utilization"; to = "$apiBase\resource\utilization" },
    # Finance
    @{ from = "$apiBase\execution\expense"; to = "$apiBase\finance\expense" },
    @{ from = "$apiBase\execution\payment"; to = "$apiBase\finance\payment" },
    @{ from = "$apiBase\execution\invoice"; to = "$apiBase\finance\invoice" },
    @{ from = "$apiBase\execution\profit"; to = "$apiBase\finance\profit" },
    @{ from = "$apiBase\execution\profit-simulation"; to = "$apiBase\finance\profit-simulation" },
    @{ from = "$apiBase\execution\reconcile"; to = "$apiBase\finance\reconcile" },
    @{ from = "$apiBase\execution\customer-credit"; to = "$apiBase\finance\credit" },
    # Closure
    @{ from = "$apiBase\execution\closure"; to = "$apiBase\closure" },
    # After-sales
    @{ from = "$apiBase\execution\aftersales"; to = "$apiBase\after-sales" },
    # Report
    @{ from = "$apiBase\execution\report"; to = "$apiBase\report\base" },
    @{ from = "$apiBase\execution\cockpit"; to = "$apiBase\report\cockpit" },
    # Rule engine
    @{ from = "$apiBase\execution\rule-engine"; to = "$apiBase\rule-engine" },
    # Alert
    @{ from = "$apiBase\execution\alert"; to = "$apiBase\alert" }
)

foreach ($move in $moves) {
    if (Test-Path $move.from) {
        if (Test-Path $move.to) {
            # Merge contents if target exists
            Get-ChildItem -Path $move.from -Recurse | ForEach-Object {
                $dest = $_.FullName.Replace($move.from, $move.to)
                $destDir = Split-Path $dest -Parent
                if (-not (Test-Path $destDir)) {
                    New-Item -ItemType Directory -Path $destDir -Force | Out-Null
                }
                if (-not (Test-Path $dest)) {
                    Move-Item $_.FullName $dest
                }
            }
            Remove-Item $move.from -Recurse -Force
        } else {
            $parentDir = Split-Path $move.to -Parent
            if (-not (Test-Path $parentDir)) {
                New-Item -ItemType Directory -Path $parentDir -Force | Out-Null
            }
            Move-Item $move.from $move.to
        }
        Write-Host "Moved: $($move.from) -> $($move.to)"
    } else {
        Write-Host "Skip (not found): $($move.from)"
    }
}

# Clean up empty directories
$projectDir = "$apiBase\project"
if (Test-Path $projectDir) {
    $remaining = Get-ChildItem $projectDir -Recurse
    if (-not $remaining) {
        Remove-Item $projectDir -Force
        Write-Host "Removed empty: $projectDir"
    }
}

Write-Host "API directory restructuring complete."
