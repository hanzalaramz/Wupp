#!/usr/bin/env bash
set -euo pipefail

if [ $# -lt 1 ]; then
  echo "Usage: $0 <version> [changelog-text] [--push]"
  exit 1
fi

RAW_VER="$1"
CHANGELOG_TEXT="${2:-}"
PUSH_FLAG="${3:-}"
CLEAN_VER="${RAW_VER#v}"
CLEAN_VER="${CLEAN_VER#V}"

if [[ ! "$CLEAN_VER" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)(-.+)?$ ]]; then
  echo "Error: Version must follow SemVer like 0.2.1 or 0.3.0-beta.1"
  exit 1
fi

MAJOR="${BASH_REMATCH[1]}"
MINOR="${BASH_REMATCH[2]}"
PATCH="${BASH_REMATCH[3]}"
SUFFIX="${BASH_REMATCH[4]}"

BASE_CODE=$(( (MAJOR * 10000 + MINOR * 100 + PATCH) * 100 ))
if [ -n "$SUFFIX" ]; then
  PRE_DIGITS=$(echo "$SUFFIX" | grep -oE '[0-9]+' | tail -n 1 || echo "1")
  PRE_NUM=$((10#${PRE_DIGITS:-1}))
  [ "$PRE_NUM" -ge 99 ] && PRE_NUM=98
  [ "$PRE_NUM" -lt 1 ] && PRE_NUM=1
else
  PRE_NUM=99
fi

PROPS_CODE=$((BASE_CODE + PRE_NUM))
UNIV_CODE=$((PROPS_CODE * 10))
ARM64_CODE=$((UNIV_CODE + 2))
TAG="v${CLEAN_VER}"

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

sed -i.bak "s/^WUPP_VERSION_NAME=.*/WUPP_VERSION_NAME=${CLEAN_VER}/" gradle.properties
sed -i.bak "s/^WUPP_VERSION_CODE=.*/WUPP_VERSION_CODE=${PROPS_CODE}/" gradle.properties
rm -f gradle.properties.bak

CHANGELOG_DIR="fastlane/metadata/android/en-US/changelogs"
mkdir -p "$CHANGELOG_DIR"
[ -z "$CHANGELOG_TEXT" ] && CHANGELOG_TEXT="- Improvements and bug fixes in Wupp ${CLEAN_VER}"
printf '%s\n' "$CHANGELOG_TEXT" > "${CHANGELOG_DIR}/${UNIV_CODE}.txt"
printf '%s\n' "$CHANGELOG_TEXT" > "${CHANGELOG_DIR}/${ARM64_CODE}.txt"

if ! grep -q "^## \[${CLEAN_VER}\]" CHANGELOG.md; then
  TODAY=$(date -u +%Y-%m-%d)
  python3 - "$CLEAN_VER" "$TODAY" "$CHANGELOG_TEXT" <<'PY'
import sys
ver, today, notes = sys.argv[1:4]
with open("CHANGELOG.md", "r", encoding="utf-8") as f:
    text = f.read()
marker = "## [Unreleased]"
block = f"{marker}\n\n## [{ver}] - {today}\n\n{notes}"
text = text.replace(marker, block, 1) if marker in text else f"## [{ver}] - {today}\n\n{notes}\n\n" + text
with open("CHANGELOG.md", "w", encoding="utf-8") as f:
    f.write(text)
PY
fi

git add gradle.properties CHANGELOG.md fastlane/metadata/android/en-US/changelogs/
git commit -m "chore(release): prepare ${TAG} (versionCode ${PROPS_CODE})"
git tag -a "$TAG" -m "Release ${TAG}"

echo "Prepared ${TAG} (WUPP_VERSION_CODE=${PROPS_CODE}, APK versionCodes=${UNIV_CODE}/${ARM64_CODE})"
if [ "$PUSH_FLAG" = "--push" ]; then
  git push origin HEAD
  git push origin "$TAG"
else
  echo "Run 'git push origin HEAD && git push origin ${TAG}' to trigger Release APK + F-Droid repo sync."
fi
