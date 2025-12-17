@echo off

rem 论文总结助手后端启动脚本

rem 创建虚拟环境（如果不存在）
if not exist "venv" (
    echo 创建虚拟环境...
    python -m venv venv
)

rem 激活虚拟环境
echo 激活虚拟环境...
call venv\Scripts\activate

rem 安装依赖
echo 安装依赖...
pip install -r requirements.txt

rem 启动后端服务
echo 启动后端服务...
python main.py --config .env

echo 后端服务已启动，请访问 http://localhost:8000