<#
推送前检查脚本 - Windows PowerShell 版本
验证代码管理规范要求
#>

$VERSION_FILE = "VERSION"
$CHANGELOG_FILE = "CHANGELOG.md"
$allPassed = $true

Write-Host "======================================"
Write-Host "  推送前检查 - 代码管理规范验证"
Write-Host "======================================"

function Check-VersionFile {
    if (-not (Test-Path $VERSION_FILE)) {
        Write-Host "✗ 错误: VERSION 文件不存在"
        Write-Host "  请运行: .\scripts\version-manager.ps1 bump-patch"
        return $false
    }

    $version = Get-Content $VERSION_FILE -Raw | Trim
    if (-not ($version -match '^[0-9]+\.[0-9]+\.[0-9]+$')) {
        Write-Host "✗ 错误: VERSION 文件格式无效: $version"
        Write-Host "  格式要求: X.Y.Z"
        return $false
    }

    Write-Host "✓ VERSION 文件有效: $version"
    return $true
}

function Check-ChangelogUpdated {
    $currentVersion = Get-Content $VERSION_FILE -Raw | Trim
    $content = Get-Content $CHANGELOG_FILE -Raw
    $match = [regex]::Match($content, '^\[([^\]]+)\]')
    $latestLogVersion = $match.Groups[1].Value

    if ($currentVersion -ne $latestLogVersion) {
        Write-Host "✗ 错误: CHANGELOG 未更新到当前版本"
        Write-Host "  当前版本: $currentVersion"
        Write-Host "  CHANGELOG最新版本: $latestLogVersion"
        Write-Host "  请更新 CHANGELOG.md 或运行: .\scripts\version-manager.ps1 update-log"
        return $false
    }

    $versionSection = [regex]::Match($content, "## \[$currentVersion\][\s\S]*?(?=\n## |\z)")
    if (-not $versionSection.Success) {
        Write-Host "✗ 错误: CHANGELOG 中未找到版本 $currentVersion"
        return $false
    }

    $lines = $versionSection.Value -split "`n" | Where-Object { $_ -match '\S' } | Where-Object { -not ($_ -match '^##') }
    if ($lines.Count -eq 0) {
        Write-Host "✗ 错误: CHANGELOG 中版本 $currentVersion 没有更新内容"
        Write-Host "  请在 CHANGELOG.md 中添加更新记录"
        return $false
    }

    Write-Host "✓ CHANGELOG 已更新到版本: $currentVersion"
    return $true
}

function Check-NoUncommittedChanges {
    $diff = git diff
    if ($diff) {
        Write-Host "✗ 错误: 存在未提交的更改"
        Write-Host "  请先提交所有更改后再推送"
        git status
        return $false
    }

    $diffCached = git diff --cached
    if ($diffCached) {
        Write-Host "✗ 错误: 存在未提交的暂存更改"
        Write-Host "  请先提交所有更改后再推送"
        git status
        return $false
    }

    Write-Host "✓ 所有更改已提交"
    return $true
}

function Check-CommitMessageFormat {
    $lastCommit = git log --oneline -1
    if ($lastCommit -match '^(feat|fix|docs|style|refactor|perf|test|chore)\(.*\): .+') {
        Write-Host "✓ 提交信息格式符合规范: $lastCommit"
        return $true
    } else {
        Write-Host "⚠ 警告: 提交信息建议遵循 Conventional Commits 格式"
        Write-Host "  示例: feat(download): 添加抖音下载支持"
        Write-Host "  类型: feat, fix, docs, style, refactor, perf, test, chore"
        return $true
    }
}

function Show-Summary {
    Write-Host ""
    Write-Host "======================================"
    Write-Host "          检查结果汇总"
    Write-Host "======================================"
    Write-Host "版本号: $(Get-Content $VERSION_FILE -Raw | Trim)"
    Write-Host "提交数: $(git log --oneline | Measure-Object -Line).Lines"
    Write-Host "分支: $(git branch --show-current)"
    Write-Host "======================================"
}

if (-not (Check-VersionFile)) { $allPassed = $false }
Write-Host ""
if (-not (Check-ChangelogUpdated)) { $allPassed = $false }
Write-Host ""
if (-not (Check-NoUncommittedChanges)) { $allPassed = $false }
Write-Host ""
Check-CommitMessageFormat
Write-Host ""
Show-Summary

if (-not $allPassed) {
    Write-Host ""
    Write-Host "✗ 检查未通过，请修复以上问题后再推送"
    exit 1
} else {
    Write-Host ""
    Write-Host "✓ 所有检查通过，可以推送"
    exit 0
}