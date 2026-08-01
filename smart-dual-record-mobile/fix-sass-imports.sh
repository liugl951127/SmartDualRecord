#!/usr/bin/env bash
# ===========================================================
# 修复 Sass @import 弃用警告
# 把 @import '@/styles/agent-theme.scss'; -> @use '...' as *;
# ===========================================================

set -e

ROOT="$(cd "$(dirname "$0")" && pwd)"
SRC="$ROOT/src"

echo "[1/4] 扫描所有 .vue / .scss / .css 文件..."

# 找所有有 @import agent-theme 的文件
files=$(grep -rln "@import.*agent-theme" "$SRC" 2>/dev/null || true)
count=$(echo -n "$files" | grep -c "." 2>/dev/null || echo 0)

if [ -z "$files" ]; then
    echo "  [OK] 没找到 @import agent-theme"
else
    echo "  [FOUND] $count 个文件:"
    echo "$files" | while read f; do
        echo "    - ${f#$ROOT/}"
    done

    # 替换
    echo ""
    echo "[2/4] 替换 @import -> @use ..."
    echo "$files" | while read f; do
        if [ -n "$f" ]; then
            sed -i.bak -E "s|@import[[:space:]]+['\"](@/?)?styles/agent-theme\.scss['\"];|@use '\1styles/agent-theme.scss' as *;|g" "$f"
            rm -f "$f.bak"
            echo "  [FIXED] ${f#$ROOT/}"
        fi
    done
fi

# 清缓存
echo ""
echo "[3/4] 清 vite 缓存..."
[ -d "$ROOT/node_modules/.vite" ] && rm -rf "$ROOT/node_modules/.vite" && echo "  [OK] cleared" || echo "  [SKIP] no cache"

# 验证
echo ""
echo "[4/4] 验证..."
remaining=$(grep -rln "@import.*agent-theme" "$SRC" 2>/dev/null || true)
if [ -z "$remaining" ]; then
    echo "  [OK] 全部已修复"
else
    echo "  [WARN] 仍有 $(echo "$remaining" | wc -l) 个未修复"
fi
