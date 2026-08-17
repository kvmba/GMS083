#!/bin/sh
set -e
cd "$(dirname "$0")"

# 可选：指定要构建的远端分支，默认远端 HEAD（默认分支）
BRANCH="${GIT_BRANCH:-}"

if [ -z "$BRANCH" ]; then
    SHA_REF=$(git ls-remote origin HEAD | cut -f1)
    BRANCH_DESC="(默认分支)"
else
    SHA_REF=$(git ls-remote origin "refs/heads/${BRANCH}" | cut -f1)
    BRANCH_DESC="(${BRANCH})"
fi

if [ -z "$SHA_REF" ]; then
    echo "[build] 无法获取远端提交，请检查网络或分支: ${BRANCH_DESC}" >&2
    exit 1
fi

echo "[build] 目标提交: ${SHA_REF:0:12} ${BRANCH_DESC}"
echo "[build] 开始构建(仅当远端有新提交时才会重新拉取编译)..."
docker compose build --build-arg "GIT_COMMIT_SHA=${SHA_REF}"
echo "[build] 构建完成，启动: docker compose up -d"
