#!/bin/bash
# GitHub Release 发布脚本
# 使用方法: 运行此脚本前需要先设置 GITHUB_TOKEN 环境变量
# export GITHUB_TOKEN="your_github_token_here"

REPO="YanceyQian/res-downloader-android"
VERSION="1.0.0"
TAG="v${VERSION}"
APK_FILE="app/release/app-release.apk"

# 检查 Token
if [ -z "$GITHUB_TOKEN" ]; then
    echo "错误: 请设置 GITHUB_TOKEN 环境变量"
    echo "export GITHUB_TOKEN=\"your_github_token_here\""
    exit 1
fi

# 检查 APK 文件
if [ ! -f "$APK_FILE" ]; then
    echo "错误: 找不到 APK 文件: $APK_FILE"
    exit 1
fi

# 创建 Release
echo "创建 GitHub Release..."
RELEASE_RESPONSE=$(curl -s -X POST \
    -H "Authorization: token $GITHUB_TOKEN" \
    -H "Accept: application/vnd.github.v3+json" \
    https://api.github.com/repos/${REPO}/releases \
    -d "{
        \"tag_name\": \"${TAG}\",
        \"name\": \"Release v${VERSION}\",
        \"body\": \"## v${VERSION} 更新日志\\n\\n### 新功能\\n- 优化证书下载流程，点击百度网盘链接自动复制提取码\\n- 移除关于页面冗余的应用图标展示\\n- 修复域名规则编辑对话框可能出现的闪退问题\\n\\n### 安装说明\\n1. 下载下方 APK 文件\\n2. 在设备上安装（可能需要开启\"未知来源\"权限）\\n3. 如需抓取 HTTPS 流量，请参考证书安装指南\\n\\n### 下载链接\\n- [GitHub Release 下载](https://github.com/${REPO}/releases/latest)\\n- [百度网盘下载](https://pan.baidu.com/s/1_yuYcNTyrgUuKcylCQ_o1w)（提取码: 7y52）\",
        \"draft\": true
    }")

# 提取 upload_url
UPLOAD_URL=$(echo "$RELEASE_RESPONSE" | grep -o '"upload_url": "[^"]*"' | cut -d'"' -f4 | sed 's/{?name,label}//g')
UPLOAD_URL="${UPLOAD_URL}?name=res-downloader-v${VERSION}-android.apk"

if [ -z "$UPLOAD_URL" ]; then
    echo "创建 Release 失败，响应:"
    echo "$RELEASE_RESPONSE"
    exit 1
fi

echo "上传 APK 文件..."
curl -s -X POST \
    -H "Authorization: token $GITHUB_TOKEN" \
    -H "Content-Type: application/vnd.android.package-archive" \
    --data-binary "@$APK_FILE" \
    "$UPLOAD_URL"

echo ""
echo "Release 创建成功！请访问以下链接确认并发布:"
echo "https://github.com/${REPO}/releases"
