@echo off
chcp 65001 >nul
cd /d "%~dp0"

echo ========================================
echo   富豪大转盘 · 后端服务
echo ========================================

if not exist "target\tycoon-wheel-1.0.0.jar" (
    echo [1/2] 首次运行，正在打包（需要联网下载依赖）...
    call mvn -B -DskipTests package
    if errorlevel 1 (
        echo 打包失败，请检查 Maven / JDK 配置。
        pause
        exit /b 1
    )
) else (
    echo [1/2] 已有 jar，跳过打包
)

echo [2/2] 启动服务： http://127.0.0.1:8787
echo      数据库文件： %~dp0data\tycoon.mv.db
echo      按 Ctrl+C 停止
echo.
java -jar target\tycoon-wheel-1.0.0.jar
