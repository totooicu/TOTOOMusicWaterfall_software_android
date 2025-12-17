from fastapi import FastAPI, Depends, HTTPException, Query
from fastapi.middleware.cors import CORSMiddleware
from app.config.config import get_settings, Settings
from app.api import routes
import uvicorn
import sys
import os
import asyncio
import logging

# 导入email_service
from app.services import email_service

# 配置日志
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

# 添加项目根目录到Python路径
sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

# 创建FastAPI应用实例
app = FastAPI()

# 配置CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # 在生产环境中应该指定具体的域名
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# 注册路由
app.include_router(routes.router, prefix="/api")


# 根路径
@app.get("/")
def read_root(settings: Settings = Depends(get_settings)):
    return {
        "app_name": settings.app_name,
        "version": settings.app_version,
        "status": "running"
    }


# 应用生命周期事件
@app.on_event("startup")
async def startup_event():
    """应用启动时执行"""
    logger.info("应用启动，开始初始化服务...")
    
    try:
        # 异步启动定时通知服务
        asyncio.create_task(email_service.schedule_notification())
        logger.info("成功启动定时通知服务")
    except Exception as e:
        logger.error(f"启动定时通知服务失败: {e}", exc_info=True)
        # 应用继续运行，但定时通知服务可能无法正常工作


if __name__ == "__main__":
    # 从命令行参数获取配置文件路径
    config_path = None
    if len(sys.argv) > 1:
        config_path = sys.argv[1]

    # 启动服务器（使用8001端口避免冲突）
    uvicorn.run(
        "main:app", 
        host="0.0.0.0", 
        port=8001,
        reload=True
    )