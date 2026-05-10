#!/bin/bash

set -e

VERSION_FILE="VERSION"
CHANGELOG_FILE="CHANGELOG.md"

echo "======================================"
echo "  推送前检查 - 代码管理规范验证"
echo "======================================"

function check_version_file() {
    if [ ! -f "$VERSION_FILE" ]; then
        echo "✗ 错误: VERSION 文件不存在"
        echo "  请运行: scripts/version-manager.sh bump-patch"
        return 1
    fi
    
    local version=$(cat "$VERSION_FILE")
    if ! echo "$version" | grep -qE '^[0-9]+\.[0-9]+\.[0-9]+$'; then
        echo "✗ 错误: VERSION 文件格式无效: $version"
        echo "  格式要求: X.Y.Z"
        return 1
    fi
    
    echo "✓ VERSION 文件有效: $version"
    return 0
}

function check_changelog_updated() {
    local current_version=$(cat "$VERSION_FILE")
    local latest_log_version=$(head -n 20 "$CHANGELOG_FILE" | grep -E '^\[.*\]' | head -n 1 | sed 's/^\[\([^]]*\)\].*/\1/')
    
    if [ "$current_version" != "$latest_log_version" ]; then
        echo "✗ 错误: CHANGELOG 未更新到当前版本"
        echo "  当前版本: $current_version"
        echo "  CHANGELOG最新版本: $latest_log_version"
        echo "  请更新 CHANGELOG.md 或运行: scripts/version-manager.sh update-log"
        return 1
    fi
    
    local has_changes=$(grep -A 10 "## \[$current_version\]" "$CHANGELOG_FILE" | grep -v "^##" | grep -v "^$" | wc -l)
    if [ "$has_changes" -lt 1 ]; then
        echo "✗ 错误: CHANGELOG 中版本 $current_version 没有更新内容"
        echo "  请在 CHANGELOG.md 中添加更新记录"
        return 1
    fi
    
    echo "✓ CHANGELOG 已更新到版本: $current_version"
    return 0
}

function check_no_uncommitted_changes() {
    if ! git diff --quiet; then
        echo "✗ 错误: 存在未提交的更改"
        echo "  请先提交所有更改后再推送"
        git status
        return 1
    fi
    
    if ! git diff --cached --quiet; then
        echo "✗ 错误: 存在未提交的暂存更改"
        echo "  请先提交所有更改后再推送"
        git status
        return 1
    fi
    
    echo "✓ 所有更改已提交"
    return 0
}

function check_commit_message_format() {
    local last_commit=$(git log --oneline -1)
    
    if echo "$last_commit" | grep -qE '^(feat|fix|docs|style|refactor|perf|test|chore)\(.*\): .+'; then
        echo "✓ 提交信息格式符合规范: $last_commit"
        return 0
    else
        echo "⚠ 警告: 提交信息建议遵循 Conventional Commits 格式"
        echo "  示例: feat(download): 添加抖音下载支持"
        echo "  类型: feat, fix, docs, style, refactor, perf, test, chore"
        return 0
    fi
}

function show_summary() {
    echo ""
    echo "======================================"
    echo "          检查结果汇总"
    echo "======================================"
    echo "版本号: $(cat "$VERSION_FILE")"
    echo "提交数: $(git log --oneline | wc -l)"
    echo "分支: $(git branch --show-current)"
    echo "======================================"
}

all_passed=0

check_version_file || all_passed=1
echo ""
check_changelog_updated || all_passed=1
echo ""
check_no_uncommitted_changes || all_passed=1
echo ""
check_commit_message_format
echo ""
show_summary

if [ "$all_passed" -eq 1 ]; then
    echo ""
    echo "✗ 检查未通过，请修复以上问题后再推送"
    exit 1
else
    echo ""
    echo "✓ 所有检查通过，可以推送"
    exit 0
fi