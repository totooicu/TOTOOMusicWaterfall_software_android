from fastapi import APIRouter, Depends, HTTPException, Query
from app.config.config import get_settings, Settings, update_settings
from app.services import rss_service, email_service, summary_service, get_rss,knowledge_graph_service
from typing import List, Dict, Any
import logging

# 配置日志
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(name)s - %(levelname)s - %(message)s')
logger = logging.getLogger('routes')

router = APIRouter()

# 获取当前配置
@router.get("/config", response_model=Dict[str, Any])
def get_config(settings: Settings = Depends(get_settings)):
    return {
        "api_key": settings.silicon_flow_api_key,
        "rss_url": settings.rss_url,
        "qq_email": settings.smtp_user,
        "target_email": settings.recipient_email
    }

# 更新配置
@router.post("/config", response_model=Dict[str, Any])
def update_config(config_data: Dict[str, Any], settings: Settings = Depends(get_settings)):
    try:
        # 更新配置并保存到.env文件
        updated_settings = update_settings(config_data)
        
        return {
            "status": "success",
            "updated_config": {
                "api_key": updated_settings.silicon_flow_api_key,
                "rss_url": updated_settings.rss_url,
                "qq_email": updated_settings.smtp_user,
                "target_email": updated_settings.recipient_email
            }
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to update config: {str(e)}")

# 获取RSS内容
@router.get("/rss", response_model=Dict[str, Any])
def get_rss_content(settings: Settings = Depends(get_settings)):
    
    try:
        all_papers, this_week_papers = rss_service.parse_rss(settings.rss_url)
        # 构建符合原有API预期的响应格式
        return {
            "title": "IEEE论文摘要",
            "link": settings.rss_url,
            "description": "IEEE最新论文摘要",
            "entries": this_week_papers,  # 使用本周论文作为entries
            "all_papers": all_papers,      # 添加所有论文
            "this_week_papers": this_week_papers  # 添加本周论文
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to parse RSS: {str(e)}")

@router.get("/rss/raw", response_model=Dict[str, Any])
def get_raw_rss_data(settings: Settings = Depends(get_settings)):
    get_rss.fetch_and_parse_rss("https://ieeexplore.ieee.org/rss/TOC36.XML")
    """获取原始RSS数据（包含所有论文和本周论文）"""
    try:
        # 调用RSS服务解析IEEE RSS源
        all_papers, this_week_papers = rss_service.parse_rss(settings.rss_url)
        
        return {
            "all_papers": all_papers,
            "this_week_papers": this_week_papers,
            "all_papers_count": len(all_papers),
            "this_week_papers_count": len(this_week_papers)
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"获取原始RSS数据失败: {str(e)}")

# 生成论文总结
@router.post("/summarize", response_model=Dict[str, Any])
def generate_summary(settings: Settings = Depends(get_settings)):
    try:
        # 解析RSS
        all_papers, this_week_papers = rss_service.parse_rss(settings.rss_url)
        
        # 构建符合summary_service预期的格式
        rss_content_for_summary = {
            "entries": this_week_papers
        }
        # print(">>>rss_content_for_summary", rss_content_for_summary)
        # 生成总结
        summary = summary_service.generate_summary(rss_content_for_summary, settings.silicon_flow_api_key)
        
        return {
            "status": "success",
            "summary": summary,
            # "summary":"\n\n### 结构化论文总结报告  \n\n---\n\n#### **论文 1**  \n**标题**: MTGAN-KAN: A New Physics-Driven Wasserstein Generative Adversarial Kolmogorov–Arnold Network for 2-D Magnetotelluric Inversion  \n**作者**:  \nFuying Yang; Yunhe Liu; Changchun Yin; Yang Su; Zhiyuan Ke; Xinpeng Ma; Zhihao Rong  \n\n**主要内容摘要**:  \n传统磁测深（MT）反演方法依赖L2范数衡量观测数据与预测数据的差异，但存在数据权重不合理、对异常值敏感、弱异常分辨率低等问题。本文提出MTGAN-KAN方法，以Wasserstein距离替代L2范数，通过Wasserstein生成对抗网络（WGAN）框架实现分布差异的更优量化。其核心创新包括：  \n1. **物理驱动的迭代过程**：用物理正演模型替代WGAN中的神经网络生成器，确保反演流程与传统方法一致。  \n2. **Kolmogorov–Arnold网络（KAN）作为判别器**：相比传统全连接神经网络（FCNN），KAN显著提升了算法的收敛性和稳定性。  \n3. **实验验证**：合成模型和澳大利亚新火山省实测数据表明，MTGAN-KAN在数据拟合精度和分辨率上优于传统方法，尤其在弱异常识别方面表现突出。  \n\n**潜在应用价值**:  \n- **地球物理勘探**：提升地下地质结构成像的分辨率，支持矿产资源勘探和地质灾害监测。  \n- **环境监测**：通过高精度反演分析地壳电性结构，辅助地震活动或地下水分布研究。  \n- **算法优化**：为其他基于反演的地球物理问题（如地震层析成像）提供Wasserstein距离与物理模型结合的范例。  \n\n---\n\n#### **论文 2**  \n**标题**: An Algorithm for Aerosol Optical Properties Retrieval Over the Ocean Accelerated by a Neural Network From Single-View Multispectral Measurements of Intensity and Polarization  \n**作者**:  \nZhe Ji; Zhenqiang Li; Cheng Fan; Cheng Chen; Zhenwei Qiu; Zhenhai Liu; Haoran Gu; Qian Yao; Gerrit de Leeuw  \n\n**主要内容摘要**:  \n海洋气溶胶监测对气候和空气质量研究至关重要，但现有算法依赖多视角极化仪，缺乏针对单视角极化仪的专用方法。本文提出首个针对星载单视角极化仪（如GF-5(02)卫星的POSP仪器）的气溶胶反演算法，核心贡献包括：  \n1. **多光谱极化与机器学习结合**：利用神经网络加速辐射传输（RT）计算，结合季节性聚类的全球气溶胶模型，提升计算效率。  \n2. **高精度验证**：通过AERONET和MAN数据验证，反演结果的RMSE（如AOD550、AE、SSA550）均优于或接近现有算法（如GRASPs）。  \n3. **单视角极化仪的可行性**：证明单视角数据可实现高精度反演，为未来全球海洋气溶胶监测提供新工具。  \n\n**潜在应用价值**:  \n- **气候研究**：支持全球海洋气溶胶分布的长期监测，量化气溶胶对辐射平衡和云形成的影响。  \n- **空气质量评估**：提供海洋区域的气溶胶光学参数（如消光系数、单次散射反照率），辅助大气污染追踪。  \n- **卫星遥感技术**：推动单视角极化仪在地球观测卫星中的应用，降低多视角观测的硬件需求。  \n\n---\n\n### **本周论文整体趋势与亮点**  \n**趋势分析**:  \n1. **深度学习与物理模型的融合**：两篇论文均将机器学习（GAN、神经网络）与物理模型结合，提升传统方法的精度和稳定性。  \n2. **地球科学领域的技术突破**：聚焦地球物理勘探（MT反演）和大气遥感（海洋气溶胶监测），体现AI在环境与地质研究中的关键作用。  \n3. **数据驱动的优化方向**：通过改进损失函数（如Wasserstein距离）或加速计算（如神经网络加速RT），解决传统方法的局限性。  \n\n**亮点总结**:  \n- **MTGAN-KAN**：首次将WGAN与KAN结合，实现物理驱动的高分辨率磁测深反演，为复杂地质结构分析提供新工具。  \n- **单视角极化算法**：突破多视角依赖限制，证明单视角数据可实现高精度海洋气溶胶反演，推动卫星遥感技术发展。  \n- **跨学科方法论**：两篇论文均体现“物理约束+数据驱动”的混合建模思路，为地球科学问题的解决提供了通用框架参考。  \n\n--- \n\n以上总结基于论文摘要及实验结果提炼，具体技术细节需参考原文。",
            # "paper_count": len(this_week_papers)
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to generate summary: {str(e)}")

# 发送邮件
@router.post("/send-email", response_model=Dict[str, Any])
def send_email(settings: Settings = Depends(get_settings)):
    try:
        # 解析RSS并生成总结
        all_papers, this_week_papers = rss_service.parse_rss(settings.rss_url)
        
        # 构建符合summary_service预期的格式
        rss_content_for_summary = {
            "entries": this_week_papers
        }
        
        summary = summary_service.generate_summary(rss_content_for_summary, settings.silicon_flow_api_key)
        
        # 获取论文数量
        paper_count = len(this_week_papers)
        
        # 发送邮件
        email_service.send_email(
            settings.smtp_user,
            settings.smtp_password,
            settings.recipient_email,
            "每周论文总结",
            summary,
            paper_count
        )
        
        return {"status": "success", "message": "邮件发送成功", "paper_count": paper_count}
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to send email: {str(e)}")

# 问答接口
@router.post("/qa", response_model=Dict[str, Any])
def answer_question(qa_data: Dict[str, Any], settings: Settings = Depends(get_settings)):
    try:
        question = qa_data.get("question")
        context = qa_data.get("context")
        
        if not question:
            raise HTTPException(status_code=400, detail="Question is required")
            
        if not context:
            # 如果没有提供上下文，获取最新的RSS内容
            all_papers, this_week_papers = rss_service.parse_rss(settings.rss_url)
            
            # 构建符合summary_service预期的格式
            rss_content_for_summary = {
                "entries": this_week_papers
            }
            
            # generate_summary直接返回字符串摘要，不是包含status字段的字典
            context = summary_service.generate_summary(rss_content_for_summary, settings.silicon_flow_api_key)
        
        answer = summary_service.answer_question(question, context, settings.silicon_flow_api_key)
        
        return {
            "status": "success",
            "question": question,
            "answer": answer
        }
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Failed to answer question: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail=f"Failed to answer question: {str(e)}")

# 执行周总结任务
@router.post("/weekly-summary", response_model=Dict[str, Any])
def weekly_summary(settings: Settings = Depends(get_settings)):
    try:
        # 解析RSS
        all_papers, this_week_papers = rss_service.parse_rss(settings.rss_url)
        
        # 构建符合summary_service预期的格式
        rss_content_for_summary = {
            "entries": this_week_papers
        }
        
        # 生成总结
        summary = summary_service.generate_summary(rss_content_for_summary, settings.silicon_flow_api_key)
        
        # 获取论文数量
        paper_count = len(this_week_papers)
        
        # 发送邮件
        email_service.send_email(
            settings.smtp_user,
            settings.smtp_password,
            settings.recipient_email,
            "每周论文总结报告",
            summary,
            paper_count
        )
        
        return {
            "status": "success",
            "message": "周总结生成并发送成功",
            "paper_count": paper_count
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to generate weekly summary: {str(e)}")

# 测试AI总结功能的端点
@router.post("/test-summary", response_model=Dict[str, Any])
def test_summary(settings: Settings = Depends(get_settings)):
    """测试AI总结功能，输出详细调试信息"""
    try:
        logger.info("=== 开始测试AI总结功能 ===")
        
        # 1. 测试RSS解析
        logger.info("1. 测试RSS解析...")
        all_papers, this_week_papers = rss_service.parse_rss(settings.rss_url)
        logger.info(f"   解析完成: 全部论文 {len(all_papers)} 篇, 本周论文 {len(this_week_papers)} 篇")
        
        # 2. 构建测试数据
        logger.info("2. 构建测试数据...")
        test_rss_content = {
            "entries": this_week_papers[:3]  # 只使用前3篇论文进行测试
        }
        
        # 3. 直接测试AI总结API调用
        logger.info("3. 直接测试AI总结API调用...")
        logger.info(f"   API密钥: {settings.silicon_flow_api_key[:5]}...{settings.silicon_flow_api_key[-5:]}")
        
        # 手动构建一个简单的测试提示词
        test_prompt = "请总结这篇论文：标题是'测试论文'，作者是'测试作者'，摘要内容是'这是一篇测试论文，用于测试AI总结功能。'"
        
        logger.info("   发送API请求...")
        import requests
        import json
        
        # 禁用代理
        session = requests.Session()
        session.trust_env = False
        
        try:
            response = session.post(
                "https://api.siliconflow.cn/v1/chat/completions",
                headers={
                    "Content-Type": "application/json",
                    "Authorization": f"Bearer {settings.silicon_flow_api_key}"
                },
                json={
                    "model": "gpt-3.5-turbo",
                    "messages": [
                        {"role": "system", "content": "你是一位专业的学术论文总结助手。请用中文回复。"},
                        {"role": "user", "content": test_prompt}
                    ],
                    "max_tokens": 1000,
                    "temperature": 0.3
                },
                timeout=30
            )
            
            logger.info(f"   API响应状态码: {response.status_code}")
            logger.info(f"   API响应内容: {response.text}")
            
            if response.status_code == 200:
                api_result = response.json()
                if "choices" in api_result and api_result["choices"]:
                    logger.info("   API调用成功!")
                    api_success = True
                else:
                    logger.warning("   API调用失败: 没有返回有效的choices")
                    api_success = False
            else:
                logger.error(f"   API调用失败: HTTP {response.status_code}")
                api_success = False
                
        except Exception as api_error:
            logger.error(f"   API调用异常: {api_error}", exc_info=True)
            api_success = False
        
        # 4. 测试完整的总结生成
        logger.info("4. 测试完整的总结生成流程...")
        summary = summary_service.generate_summary(test_rss_content, settings.silicon_flow_api_key)
        logger.info(f"   总结生成完成: {'成功' if 'API调用失败' not in summary else '使用备用摘要'}")
        
        logger.info("=== 测试完成 ===")
        
        return {
            "status": "success",
            "test_results": {
                "rss_parsing": {
                    "all_papers_count": len(all_papers),
                    "this_week_papers_count": len(this_week_papers)
                },
                "api_direct_test": {
                    "success": api_success
                },
                "summary_generation": {
                    "uses_fallback": "API调用失败" in summary,
                    "summary_preview": summary[:200] + "..."
                }
            }
        }
        
    except Exception as e:
        logger.error(f"测试过程中发生错误: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail=f"测试失败: {str(e)}")

# 获取知识图谱数据
@router.get("/knowledge-graph", response_model=Dict[str, Any])
def get_knowledge_graph():
    """获取知识图谱数据"""
    try:
        kg_data = knowledge_graph_service.build_knowledge_graph()
        return kg_data
    except Exception as e:
        logger.error(f"获取知识图谱数据失败: {str(e)}", exc_info=True)
        raise HTTPException(status_code=500, detail=f"Failed to get knowledge graph: {str(e)}")

# 解读知识图谱
@router.post("/knowledge-graph/analyze", response_model=Dict[str, Any])
def analyze_knowledge_graph(settings: Settings = Depends(get_settings)):
    """使用AI解读知识图谱"""
    try:
        logger.info("开始解读知识图谱")
        
        # 1. 获取知识图谱数据
        kg_data = knowledge_graph_service.build_knowledge_graph()
        
        # 2. 调用AI分析功能
        analysis = knowledge_graph_service.analyze_knowledge_graph(kg_data, settings.silicon_flow_api_key)
        
        logger.info("知识图谱解读完成")
        return {
            "status": "success",
            "analysis": analysis
        }
    except Exception as e:
        logger.error(f"解读知识图谱失败: {str(e)}", exc_info=True)
        raise HTTPException(status_code=500, detail=f"Failed to analyze knowledge graph: {str(e)}")