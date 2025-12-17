from pydantic_settings import BaseSettings
import os
import dotenv
from typing import Optional, Dict, Any

class Settings(BaseSettings):
    # 硅基流动API配置
    silicon_flow_api_key: str = ""
    silicon_flow_api_url: str = "https://api.siliconflow.cn/v1/chat/completions"
    
    # RSS配置
    rss_url: str = "https://ieeexplore.ieee.org/rss/TOC36.XML"
    
    # 邮箱配置
    smtp_user: str = ""
    smtp_password: str = ""
    smtp_server: str = "smtp.qq.com"
    smtp_port: str = "587"
    recipient_email: str = ""
    
    # 应用配置
    app_name: str = "Paper Summarizer"
    app_version: str = "1.0.0"
    log_level: str = "INFO"
    timeout: str = "30"
    
    class Config:
        env_file = ".env"
        case_sensitive = False
    
    def save_to_env(self, env_file_path: str = ".env"):
        """将配置保存到.env文件"""
        # 获取当前的环境变量
        env_vars = dotenv.dotenv_values(env_file_path) if os.path.exists(env_file_path) else {}
        
        # 更新环境变量
        config_data = self.model_dump()
        for key, value in config_data.items():
            # 将驼峰命名转换为大写带下划线的命名（环境变量标准格式）
            env_key = ''
            for i, char in enumerate(key):
                if char.isupper() and i > 0:
                    env_key += '_'
                env_key += char.upper()
            env_vars[env_key] = value
        
        # 写入.env文件
        with open(env_file_path, 'w') as f:
            for key, value in env_vars.items():
                f.write(f"{key}={value}\n")
        
        return env_vars

# 创建全局设置实例
def get_settings(config_path: Optional[str] = None) -> Settings:
    if config_path and os.path.exists(config_path):
        # 如果指定了配置文件路径，使用该路径
        return Settings(_env_file=config_path)
    return Settings()

# 更新配置并保存到文件
def update_settings(config_data: Dict[str, Any], config_path: Optional[str] = None) -> Settings:
    """更新配置并保存到.env文件"""
    env_file = config_path or ".env"
    
    # 获取当前设置
    settings = get_settings(env_file)
    
    # 更新配置
    updated_data = {}
    for key, value in config_data.items():
        # 转换键名以匹配Settings类的字段
        if hasattr(settings, key):
            setattr(settings, key, value)
            updated_data[key] = value
    
    # 保存到.env文件
    settings.save_to_env(env_file)
    
    return settings