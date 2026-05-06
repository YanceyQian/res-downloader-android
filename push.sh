#!/bin/bash

# ========================================
# Res Downloader Android - One-click Push
# ========================================

echo "========================================"
echo "  Starting one-click setup..."
echo "========================================"
echo ""

# Change to correct directory
cd /e/res-download/res-downloader-android || {
    echo "[ERROR] Cannot find project directory!"
    exit 1
}

echo "[OK] Changed to project directory"
pwd
echo ""

# Configure Git identity
echo "[1/6] Configuring Git identity..."
git config user.name "YanceyQian"
git config user.email "YanceyQian@users.noreply.github.com"
echo "[OK] Git identity configured"
echo ""

# Add all files
echo "[2/6] Adding files..."
git add .
echo "[OK] Files added"
echo ""

# Commit
echo "[3/6] Committing changes..."
git commit -m "Initial commit: res-downloader Android version"
echo "[OK] Commit complete"
echo ""

# Set branch
echo "[4/6] Setting branch to main..."
git branch -M main
echo "[OK] Branch set"
echo ""

# Set remote
echo "[5/6] Setting remote repository..."
git remote remove origin 2>/dev/null
git remote add origin https://github.com/YanceyQian/res-downloader-android.git
echo "[OK] Remote configured"
echo ""

# Push to GitHub
echo "[6/6] Pushing to GitHub..."
echo "Please follow the authentication prompts if needed"
echo ""
git push -u origin main

echo ""
echo "========================================"
echo "  Done! Check your GitHub repository!"
echo "========================================"
echo ""
echo "Next steps:"
echo "1. Check GitHub: https://github.com/YanceyQian/res-downloader-android"
echo "2. Build APK in Android Studio"
echo "3. Create a release on GitHub and upload the APK"
echo ""
