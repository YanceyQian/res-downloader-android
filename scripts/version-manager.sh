#!/bin/bash

VERSION_FILE="VERSION"
CHANGELOG_FILE="CHANGELOG.md"

function show_usage() {
    echo "Usage: $0 <command>"
    echo ""
    echo "Commands:"
    echo "  bump-major    - 递增主版本号 (X.0.0)"
    echo "  bump-minor    - 递增次版本号 (0.X.0)"
    echo "  bump-patch    - 递增修订版本号 (0.0.X)"
    echo "  show          - 显示当前版本号"
    echo "  check         - 检查版本号与CHANGELOG一致性"
    echo "  update-log    - 更新CHANGELOG模板"
    echo ""
    echo "版本号调整规则:"
    echo "  主版本号(X.0.0): 重大功能新增、架构调整、不兼容API变更"
    echo "  次版本号(0.X.0): 功能优化、次要功能添加、改进"
    echo "  修订版本号(0.0.X): bug修复、性能优化、小范围改进"
    echo "  保持不变: 仅文档修改、注释完善"
}

function get_current_version() {
    if [ -f "$VERSION_FILE" ]; then
        cat "$VERSION_FILE"
    else
        echo "1.0.0"
    fi
}

function bump_version() {
    local current_version=$(get_current_version)
    local part=$1

    IFS='.' read -r major minor patch <<< "$current_version"

    case $part in
        major)
            major=$((major + 1))
            minor=0
            patch=0
            ;;
        minor)
            minor=$((minor + 1))
            patch=0
            ;;
        patch)
            patch=$((patch + 1))
            ;;
    esac

    echo "$major.$minor.$patch"
}

function update_version_file() {
    local new_version=$1
    echo "$new_version" > "$VERSION_FILE"
    echo "版本号已更新为: $new_version"
}

function check_changelog_consistency() {
    local current_version=$(get_current_version)
    local latest_log_version=$(head -n 10 "$CHANGELOG_FILE" | grep -E '^\[.*\]' | head -n 1 | sed 's/^\[\([^]]*\)\].*/\1/')

    echo "当前版本号: $current_version"
    echo "CHANGELOG最新版本: $latest_log_version"

    if [ "$current_version" == "$latest_log_version" ]; then
        echo "✓ 版本号与CHANGELOG一致"
        return 0
    else
        echo "✗ 版本号与CHANGELOG不一致！"
        return 1
    fi
}

function update_changelog_template() {
    local current_version=$(get_current_version)
    local today=$(date +%Y-%m-%d)
    local template="## [$current_version] - $today\n\n### 新增功能\n\n\n### 功能优化\n\n\n### 问题修复\n\n\n### 文档更新\n\n\n"

    if [ ! -f "$CHANGELOG_FILE" ]; then
        echo "# Changelog\n\nAll notable changes to this project will be documented in this file.\n\nThe format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),\nand this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).\n\n$template" > "$CHANGELOG_FILE"
    else
        local existing_content=$(cat "$CHANGELOG_FILE")
        local first_line=$(head -n 1 "$CHANGELOG_FILE")

        if [ "$first_line" == "# Changelog" ]; then
            local after_header=$(sed '1,7d' "$CHANGELOG_FILE")
            echo "# Changelog\n\nAll notable changes to this project will be documented in this file.\n\nThe format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),\nand this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).\n\n$template$after_header" > "$CHANGELOG_FILE"
        else
            echo "$template$existing_content" > "$CHANGELOG_FILE"
        fi
    fi

    echo "CHANGELOG模板已更新，版本: $current_version"
}

case $1 in
    bump-major)
        new_version=$(bump_version major)
        update_version_file "$new_version"
        update_changelog_template
        ;;
    bump-minor)
        new_version=$(bump_version minor)
        update_version_file "$new_version"
        update_changelog_template
        ;;
    bump-patch)
        new_version=$(bump_version patch)
        update_version_file "$new_version"
        update_changelog_template
        ;;
    show)
        get_current_version
        ;;
    check)
        check_changelog_consistency
        ;;
    update-log)
        update_changelog_template
        ;;
    *)
        show_usage
        ;;
esac