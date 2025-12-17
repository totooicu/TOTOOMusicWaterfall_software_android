@echo off

rem 论文总结助手前端启动脚本

rem 检查Node.js版本
echo 检查Node.js环境...
node -v >nul 2>nul
if %errorlevel% neq 0 (
    echo 错误: 未找到Node.js环境，请先安装Node.js
    pause
    exit /b 1
)

rem 安装依赖
echo 正在安装Vue3项目依赖...
npm install
if %errorlevel% neq 0 (
    echo 错误：依赖安装失败
    pause
    exit /b 1
)

rem 启动前端服务
echo 依赖安装成功，正在启动Vue3前端服务...
npm run dev

echo 前端服务已启动，请在浏览器中访问提示的地址