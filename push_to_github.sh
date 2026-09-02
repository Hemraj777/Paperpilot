#!/data/data/com.termux/files/usr/bin/env bash
# Paperpilot - Push to GitHub Public Repo & Auto-Build APK
# Usage: ./push_to_github.sh <github-username> [repo-name]
set -e

USERNAME=${1:-}
REPO=${2:-Paperpilot}

if [ -z "$USERNAME" ]; then
  echo "Usage: ./push_to_github.sh <github-username> [repo-name]"
  echo "Example: ./push_to_github.sh anomalyco"
  exit 1
fi

echo ">>> Paperpilot GitHub Push"
echo "User: $USERNAME | Repo: $REPO"
echo ""

# Check gh CLI or git credentials
if ! git remote get-url origin >/dev/null 2>&1; then
  echo "Adding remote..."
  git remote add origin https://github.com/$USERNAME/$REPO.git
else
  echo "Remote exists: $(git remote get-url origin)"
  git remote set-url origin https://github.com/$USERNAME/$REPO.git
fi

echo "Pushing to main..."
git push -u origin main

echo ""
echo "✅ Pushed! Now:"
echo "1. Go to https://github.com/$USERNAME/$REPO/actions"
echo "2. Wait 3-5 min for 'Android CI - Build APK' to finish"
echo "3. Download APK from:"
echo "   - Actions -> Latest run -> Artifacts -> Paperpilot-debug-apk"
echo "   - OR Releases -> v1.0.x -> app-debug.apk"
echo ""
echo "To install: adb install app-debug.apk  or  copy to phone & tap"
