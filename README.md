# 论文总结助手

## 项目简介

论文总结助手是一个自动化工具，用于从RSS源获取最新论文，使用AI生成结构化总结，并支持通过邮件发送和智能问答功能。

### 主要功能

- **RSS订阅**：自动从IEEE Xplore等来源获取最新论文
- **AI总结**：使用硅基流动API生成结构化论文总结
- **邮件推送**：将论文总结发送到指定邮箱
- **智能问答**：基于论文内容进行交互式问答
- **可视化配置**：Web界面配置系统参数

## 技术栈

- **后端**：Python + FastAPI
- **前端**：React + Ant Design
- **AI服务**：硅基流动API
- **通信**：RESTful API

## 快速开始

### 环境要求

- **后端**：Python 3.8+
- **前端**：Node.js 14+

### 1. 配置文件设置

1. 进入后端目录：
   ```bash
   cd backend
   ```

2. 确保`.env`文件已正确配置（默认已配置）：
   ```
   # 硅基流动API配置
   SILICON_FLOW_API_KEY=sk-tehmatwmtgfdzudmpltszecgqzcjlzoegscrapzzfjkwxqkf
   SILICON_FLOW_API_URL=https://api.siliconflow.cn/v1/chat/completions

   # RSS配置
   RSS_URL=https://ieeexplore.ieee.org/rss/TOC36.XML

   # 邮箱配置
   SMTP_SERVER=smtp.qq.com
   SMTP_PORT=587
   SMTP_USER=1134815016@qq.com
   SMTP_PASSWORD=xlnrvjpxxibubacb

   # 接收邮箱
   RECIPIENT_EMAIL=2667657661@qq.com

   # 系统配置
   LOG_LEVEL=INFO
   TIMEOUT=30
   ```

### 2. 启动后端服务

**方法一：使用启动脚本（推荐）**

1. 运行启动脚本：
   ```bash
   # 在backend目录下
   start_backend.bat
   ```

**方法二：手动启动**

1. 创建并激活虚拟环境：
   ```bash
   python -m venv venv
   venv\Scripts\activate
   ```

2. 安装依赖：
   ```bash
   pip install -r requirements.txt
   ```

3. 启动服务：
   ```bash
   python main.py --config .env
   ```

后端服务将在 `http://localhost:8000` 启动。

### 3. 启动前端服务

**方法一：使用启动脚本（推荐）**

1. 运行启动脚本：
   ```bash
   # 在frontend目录下
   start_frontend.bat
   ```

**方法二：手动启动**

1. 安装依赖：
   ```bash
   npm install
   ```

2. 启动服务：
   ```bash
   npm run dev
   ```

前端服务通常会在 `http://localhost:3000` 启动（具体地址请查看启动日志）。

## 使用说明

### 访问系统

启动前后端服务后，打开浏览器访问前端地址（默认 `http://localhost:3000`）。

### 主要页面

1. **论文总结页**：
   - 生成论文总结
   - 发送邮件
   - 执行周总结任务

2. **系统配置页**：
   - 配置API密钥
   - 设置RSS源
   - 配置邮箱参数

3. **论文问答页**：
   - 加载论文总结
   - 提问并获取回答

### API接口

后端提供以下主要API接口：

- `GET /api/config` - 获取配置
- `PUT /api/config` - 更新配置
- `GET /api/rss` - 获取RSS内容
- `POST /api/summarize` - 生成总结
- `POST /api/send-email` - 发送邮件
- `POST /api/weekly-summary` - 执行周总结
- `POST /api/qa` - 问答接口

## 常见问题

### 1. 后端服务无法启动

- 检查Python环境是否正确安装
- 确认依赖包已正确安装
- 验证配置文件中的参数是否有效

### 2. 前端服务无法启动

- 检查Node.js环境是否正确安装
- 尝试删除`node_modules`目录后重新安装依赖

### 3. 邮件发送失败

- 确认SMTP配置是否正确
- 检查邮箱密码是否有效（QQ邮箱需使用授权码）
- 验证网络连接是否正常

### 4. AI总结生成失败

- 检查API密钥是否正确
- 验证网络连接是否可访问硅基流动API
- 检查配置文件中的URL是否正确

## 注意事项

1. 请确保在正式环境中修改默认配置，特别是API密钥和邮箱密码
2. 系统默认每执行一次任务会获取最新的论文信息
3. 建议定期更新依赖包以获取最新功能和安全补丁

## 许可证

本项目仅供学习和测试使用。