<#
版本号管理脚本 - Windows PowerShell 版本
遵循语义化版本控制规范 (Semantic Versioning)

版本号调整规则:
  主版本号(X.0.0): 重大功能新增、架构调整、不兼容API变更
  次版本号(0.X.0): 功能优化、次要功能添加、改进
  修订版本号(0.0.X): bug修复、性能优化、小范围改进
  保持不变: 仅文档修改、注释完善
#>

param(
    [Parameter(Mandatory=$true)]
    [string]$Command
)

$VERSION_FILE = "VERSION"
$CHANGELOG_FILE = "CHANGELOG.md"

function Get-CurrentVersion {
    if (Test-Path $VERSION_FILE) {
        return Get-Content $VERSION_FILE -Raw | Trim
    }
    return "1.0.0"
}

function Bump-Version {
    param(
        [string]$CurrentVersion,
        [string]$Part
    )

    $parts = $CurrentVersion -split '\.'
    $major = [int]$parts[0]
    $minor = [int]$parts[1]
    $patch = [int]$parts[2]

    switch ($Part) {
        "major" {
            $major++
            $minor = 0
            $patch = 0
        }
        "minor" {
            $minor++
            $patch = 0
        }
        "patch" {
            $patch++
        }
    }

    return "$major.$minor.$patch"
}

function Update-VersionFile {
    param(
        [string]$NewVersion
    )
    $NewVersion | Set-Content $VERSION_FILE -NoNewline
    Write-Host "版本号已更新为: $NewVersion"
}

function Update-ChangelogTemplate {
    param(
        [string]$CurrentVersion
    )

    $today = Get-Date -Format "yyyy-MM-dd"
    $template = @"
## [$CurrentVersion] - $today

### 新增功能

### 功能优化

### 问题修复

### 文档更新

"@

    if (-not (Test-Path $CHANGELOG_FILE)) {
        $header = @"
# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

"@
        ($header + $template) | Set-Content $CHANGELOG_FILE
    } else {
        $content = Get-Content $CHANGELOG_FILE -Raw
        if ($content.StartsWith("# Changelog")) {
            $afterHeader = $content -split "`n", 8 | Select-Object -Skip 7
            $afterHeader = $afterHeader -join "`n"
            $newContent = @"
# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

$template$afterHeader
"@
            $newContent | Set-Content $CHANGELOG_FILE
        } else {
            ($template + $content) | Set-Content $CHANGELOG_FILE
        }
    }

    Write-Host "CHANGELOG模板已更新，版本: $CurrentVersion"
}

function Check-ChangelogConsistency {
    $currentVersion = Get-CurrentVersion
    $content = Get-Content $CHANGELOG_FILE -Raw
    $match = [regex]::Match($content, '^\[([^\]]+)\]')
    $latestLogVersion = $match.Groups[1].Value

    Write-Host "当前版本号: $currentVersion"
    Write-Host "CHANGELOG最新版本: $latestLogVersion"

    if ($currentVersion -eq $latestLogVersion) {
        Write-Host "✓ 版本号与CHANGELOG一致"
        return $true
    } else {
        Write-Host "✗ 版本号与CHANGELOG不一致！"
        return $false
    }
}

switch ($Command) {
    "bump-major" {
        $current = Get-CurrentVersion
        $newVersion = Bump-Version -CurrentVersion $current -Part "major"
        Update-VersionFile -NewVersion $newVersion
        Update-ChangelogTemplate -CurrentVersion $newVersion
        break
    }
    "bump-minor" {
        $current = Get-CurrentVersion
        $newVersion = Bump-Version -CurrentVersion $current -Part "minor"
        Update-VersionFile -NewVersion $newVersion
        Update-ChangelogTemplate -CurrentVersion $newVersion
        break
    }
    "bump-patch" {
        $current = Get-CurrentVersion
        $newVersion = Bump-Version -CurrentVersion $current -Part "patch"
        Update-VersionFile -NewVersion $newVersion
        Update-ChangelogTemplate -CurrentVersion $newVersion
        break
    }
    "show" {
        Get-CurrentVersion
        break
    }
    "check" {
        Check-ChangelogConsistency
        break
    }
    "update-log" {
        $current = Get-CurrentVersion
        Update-ChangelogTemplate -CurrentVersion $current
        break
    }
    default {
        Write-Host "Usage: version-manager.ps1 <command>"
        Write-Host ""
        Write-Host "Commands:"
        Write-Host "  bump-major    - 递增主版本号 (X.0.0)"
        Write-Host "  bump-minor    - 递增次版本号 (0.X.0)"
        Write-Host "  bump-patch    - 递增修订版本号 (0.0.X)"
        Write-Host "  show          - 显示当前版本号"
        Write-Host "  check         - 检查版本号与CHANGELOG一致性"
        Write-Host "  update-log    - 更新CHANGELOG模板"
    }
}