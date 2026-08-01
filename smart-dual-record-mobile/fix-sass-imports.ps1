# ===========================================================
# 修复 Sass @import 弃用警告
# 把所有 @import '@/styles/agent-theme.scss';
# 改成      @use   '@/styles/agent-theme.scss' as *;
# ===========================================================

$ErrorActionPreference = "Stop"

$root = $PSScriptRoot
$srcDir = Join-Path $root "src"

Write-Host "[1/4] 扫描所有 .vue / .scss 文件..." -ForegroundColor Cyan
$files = Get-ChildItem -Path $srcDir -Recurse -Include *.vue, *.scss, *.css, *.ts | Where-Object { $_.FullName -notmatch "node_modules" }

$foundCount = 0
$changedCount = 0

foreach ($file in $files) {
    $content = Get-Content $file.FullName -Raw -Encoding UTF8
    $hasImport = $content -match "@import\s+['""](@/?)?styles/agent-theme\.scss['""]"
    if ($hasImport) {
        $foundCount++
        Write-Host "  [FOUND] $($file.FullName.Substring($root.Length + 1))" -ForegroundColor Yellow

        # 替换 @import 为 @use
        $new = $content -replace "@import\s+['""](@/?)?styles/agent-theme\.scss['""];",
                             "@use '$1styles/agent-theme.scss' as *;"

        Set-Content -Path $file.FullName -Value $new -Encoding UTF8 -NoNewline
        $changedCount++
        Write-Host "  [FIXED]" -ForegroundColor Green
    }
}

Write-Host ""
Write-Host "[2/4] 清 vite 缓存..." -ForegroundColor Cyan
$cache = Join-Path $root "node_modules\.vite"
if (Test-Path $cache) {
    Remove-Item -Recurse -Force $cache
    Write-Host "  [OK] .vite cache cleared" -ForegroundColor Green
} else {
    Write-Host "  [SKIP] no .vite cache" -ForegroundColor Gray
}

Write-Host ""
Write-Host "[3/4] 验证全部已修复..." -ForegroundColor Cyan
$remaining = Get-ChildItem -Path $srcDir -Recurse -Include *.vue, *.scss, *.css | Where-Object { $_.FullName -notmatch "node_modules" } | ForEach-Object {
    $c = Get-Content $_.FullName -Raw -Encoding UTF8
    if ($c -match "@import.*agent-theme") { $_ }
}
if ($remaining) {
    Write-Host "  [FAIL] 仍有 $($remaining.Count) 个文件未修复:" -ForegroundColor Red
    $remaining | ForEach-Object { Write-Host "    - $($_.FullName.Substring($root.Length + 1))" -ForegroundColor Red }
} else {
    Write-Host "  [OK] 所有 @import agent-theme 已清零" -ForegroundColor Green
}

Write-Host ""
Write-Host "[4/4] 总结" -ForegroundColor Cyan
Write-Host "  扫描: $($files.Count) 个文件" -ForegroundColor White
Write-Host "  发现 @import: $foundCount 个" -ForegroundColor Yellow
Write-Host "  已修复: $changedCount 个" -ForegroundColor Green

if ($foundCount -gt 0) {
    Write-Host ""
    Write-Host "现在可以重启 dev server:" -ForegroundColor Cyan
    Write-Host "  npm run dev -- --host 0.0.0.0" -ForegroundColor White
}
