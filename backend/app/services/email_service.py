from bisect import insort
import smtplib
import os
from email.mime.text import MIMEText
from email.mime.multipart import MIMEMultipart
from email.header import Header

from app.config.config import get_settings, Settings, update_settings
from app.services import rss_service, summary_service
import time
import asyncio
import logging

# 配置日志
logger = logging.getLogger(__name__)

# 定时通知
async def schedule_notification():
    """异步执行的定时通知函数"""
    try:
        logger.info("启动定时通知服务")
        
        # 直接获取设置，不使用Depends
        settings = get_settings()
        
        while True:
            logger.info("开始执行定时通知任务")
            
            try:
                # 获取所有论文和本周论文
                all_papers, this_week_papers = rss_service.parse_rss(settings.rss_url)
                logger.info(f"成功获取RSS内容，本周共 {len(this_week_papers)} 篇新论文")
                
                # 构建符合summary_service预期的格式
                rss_content_for_summary = {
                    "entries": this_week_papers
                }
                
                # 生成总结
                summary = summary_service.generate_summary(rss_content_for_summary, settings.silicon_flow_api_key)
                logger.info("成功生成论文总结")
                
                # 发送通知邮件
                await inform("论文总结报告", summary, len(this_week_papers))
                logger.info("成功发送通知邮件")
                
            except Exception as e:
                logger.error(f"执行定时通知任务时发生错误: {e}", exc_info=True)
            
            # 等待24小时后再次执行
            logger.info("定时通知任务完成，等待24小时后再次执行")
            await asyncio.sleep(60*60*24)  # 异步等待24小时
            
    except Exception as e:
        logger.error(f"定时通知服务启动失败: {e}", exc_info=True)
        # 重新尝试启动
        await asyncio.sleep(60)  # 等待1分钟后重试
        await schedule_notification()

async def inform(subject: str, content: str, paper_count: int = 0):
    """异步发送通知"""
    # 直接获取设置，不使用Depends
    settings = get_settings()
    
    try:
        logger.info(f"准备发送邮件通知，主题: {subject}")
        await asyncio.to_thread(
            send_email, 
            settings.smtp_user,  # 修正：使用smtp_user而不是email_sender
            settings.smtp_password,  # 修正：使用smtp_password而不是email_password
            settings.recipient_email, 
            subject, 
            content, 
            paper_count
        )
        logger.info("邮件通知发送成功")
        return True
    except Exception as e:
        logger.error(f"发送邮件通知失败: {e}", exc_info=True)
        return False

def send_email(sender_email: str, sender_password: str, receiver_email: str, subject: str, content: str, papers_count: int = 0) -> bool:
    """
    发送邮件
    
    Args:
        sender_email: 发送者邮箱
        sender_password: 发送者邮箱密码（QQ邮箱需要使用授权码）
        receiver_email: 接收者邮箱
        subject: 邮件主题
        content: 邮件内容
        papers_count: 论文数量（可选，用于格式化邮件内容）
        
    Returns:
        bool: 发送是否成功
    """
    try:
        # 如果提供了论文数量，使用格式化的邮件内容
        if papers_count > 0:
            email_content = format_email_content(content, papers_count)
        else:
            email_content = content
        
        # 创建邮件对象
        message = MIMEMultipart()
        message['From'] = sender_email  # 不使用Header类，直接使用邮箱地址
        message['To'] = receiver_email  # 不使用Header类，直接使用邮箱地址
        message['Subject'] = Header(subject, 'utf-8')
        
        # 添加邮件正文
        message.attach(MIMEText(email_content, 'plain', 'utf-8'))
        
        # 配置SMTP服务器
        smtp_server = 'smtp.qq.com'
        smtp_port = 587
        
        # 连接SMTP服务器
        server = smtplib.SMTP(smtp_server, smtp_port)
        server.starttls()  # 启用TLS加密
        
        # 登录邮箱
        server.login(sender_email, sender_password)
        
        # 发送邮件
        server.sendmail(sender_email, receiver_email, message.as_string())
        
        # 关闭连接
        server.quit()
        
        logger.info(f"邮件已成功发送到 {receiver_email}")
        return True
    
    except Exception as e:
        logger.error(f"发送邮件失败: {e}", exc_info=True)
        raise

def format_email_content(summary: str, papers_count: int) -> str:
    """
    格式化邮件内容
    
    Args:
        summary: 论文总结
        papers_count: 论文数量
        
    Returns:
        str: 格式化后的邮件内容
    """
    email_content = f"尊敬的用户：\n\n"
    email_content += f"这是您订阅的本周论文总结报告。本周共有 {papers_count} 篇新论文。\n\n"
    email_content += "========================================\n"
    email_content += "论文总结详情：\n"
    email_content += "========================================\n\n"
    email_content += summary + "\n\n"
    email_content += "========================================\n"
    email_content += "您可以通过论文总结助手的前端页面查看更多详情或进行问答。\n"
    email_content += "感谢您使用论文总结助手！\n"
    
    return email_content

if __name__ == "__main__":
    sender_email = ""
    sender_password = ""
    receiver_email = ""
    subject = "论文总结报告"
    content = "这是测试邮件内容。"
    papers_count = 5
    
    send_email(sender_email, sender_password, receiver_email, subject, content, papers_count)