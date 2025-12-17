// src/utils/markdownParser.js
import { marked } from 'marked';
import DOMPurify from 'dompurify';

// 配置 marked
marked.setOptions({
  breaks: true,
  gfm: true,
  headerIds: true,
  highlight: function (code, lang) {
    // 可以在这里集成代码高亮，如 Prism.js 或 Highlight.js
    return code;
  }
});

/**
 * 将 Markdown 文本转换为安全的 HTML
 * @param {string} markdown - Markdown 文本
 * @param {Object} options - 解析选项
 * @returns {string} 安全的 HTML
 */
export function parseMarkdown(markdown, options = {}) {
  if (!markdown) return '';
  
  try {
    const html = marked(markdown, options);
    return DOMPurify.sanitize(html);
  } catch (error) {
    console.error('Markdown parsing error:', error);
    return markdown;
  }
}

/**
 * 获取 Markdown 的纯文本摘要（去除标记）
 * @param {string} markdown - Markdown 文本
 * @param {number} maxLength - 最大长度
 * @returns {string} 纯文本摘要
 */
export function getPlainTextSummary(markdown, maxLength = 200) {
  if (!markdown) return '';
  
  // 简单的标记去除
  const plainText = markdown
    .replace(/#{1,6}\s?/g, '') // 移除标题标记
    .replace(/\*\*(.*?)\*\*/g, '$1') // 移除加粗
    .replace(/\*(.*?)\*/g, '$1') // 移除斜体
    .replace(/\[(.*?)\]\(.*?\)/g, '$1') // 移除链接，保留文字
    .replace(/`(.*?)`/g, '$1') // 移除行内代码
    .replace(/```[\s\S]*?```/g, '') // 移除代码块
    .trim();
  
  if (plainText.length <= maxLength) return plainText;
  return plainText.substring(0, maxLength) + '...';
}