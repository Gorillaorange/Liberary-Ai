/**
 * 统一的SSE流处理工具
 * 处理服务器发送的事件流，确保数据仅被处理一次
 */

// 标准化结果类型
type StreamResult = {
  // 实际内容
  content?: string;
  // 完成信号
  done?: boolean;
  // 等待队列状态
  isWaitQueuing?: boolean;
}

/**
 * 核心流处理器 - 处理并解析SSE数据
 * 
 * @param buffer 当前缓冲区
 * @param controller 流控制器
 * @param splitOn 分隔符
 * @returns 剩余未处理的缓冲数据
 */
const processStream = (buffer, controller, splitOn) => {
  // 清理常见的异常格式
  // 修复连续的data:前缀
  buffer = buffer.replace(/data:"?\s*\/?"?data\s?:"/g, 'data:"');
  // 清除多余的event:标记
  buffer = buffer.replace(/event:(chunk|message)/g, '');
  
  // 按分隔符拆分数据块
  const parts = buffer.split(splitOn)
  const lastPart = parts.pop()

  for (const part of parts) {
    let trimmedPart = part.trim()
    if (!trimmedPart) continue

    // 标准化JSON格式 - 处理不完整或异常的JSON
    if (trimmedPart.includes('data:') && trimmedPart.includes('"type":"content"')) {
      // 提取标准化的data部分
      const dataMatch = trimmedPart.match(/data:(.*?)(?=data:|$)/s);
      if (dataMatch && dataMatch[1]) {
        trimmedPart = `data:${dataMatch[1].trim()}`;
      }
    }

    // 统一处理SSE格式数据
    if (trimmedPart.startsWith('data:')) {
      let content = trimmedPart.replace(/^data:\s*/, '').trim();
      
      // 处理完成信号 - 不发送给前端显示
      if (content === '[DONE]' || content.includes('[DONE]')) {
        controller.enqueue(JSON.stringify({ done: true }));
        continue;
      }
      
      // 解析JSON数据或直接使用原始内容
      if (content) {
        try {
          // 尝试清理并解析JSON
          // 处理被截断的JSON
          if (content.startsWith('{') && !content.endsWith('}')) {
            content = content + '}';
          }
          
          // 处理JSON中嵌套了另一个data:前缀的情况
          if (content.includes('data:')) {
            const nestedDataMatch = content.match(/"data:(.+?)"/);
            if (nestedDataMatch) {
              content = content.replace(nestedDataMatch[0], `"${nestedDataMatch[1]}"`);
            }
          }
          
          // 尝试解析JSON
          const jsonData = JSON.parse(content);
          
          // 检查完成标记
          if (jsonData.done === true || jsonData.finished === true || jsonData.complete === true) {
            controller.enqueue(JSON.stringify({ done: true }));
            continue;
          }
          
          // 检查等待队列状态
          if (jsonData.isWaitQueuing === true) {
            controller.enqueue(JSON.stringify({ isWaitQueuing: true }));
            continue;
          }
          
          // 如果是思考过程，应该过滤掉特定格式的内容
          if (content.includes('思考过程') && content.includes('嗯，用户是个')) {
            continue;
          }
          
          // 统一处理内容字段
          const result: StreamResult = {};
          
          // 提取实际内容 - 尝试从常见字段中读取
          if (jsonData.type === "content" && jsonData.data) {
            // 处理内容中可能包含的奇怪格式
            let cleanData = jsonData.data;
            // 移除数据中嵌套的data:前缀
            cleanData = cleanData.replace(/data:/g, '');
            // 移除生成完成等结束标记
            cleanData = cleanData.replace(/生成完成/, '');
            
            result.content = cleanData;
          } else if (jsonData.content !== undefined) {
            result.content = jsonData.content;
          } else if (jsonData.text) {
            result.content = jsonData.text;
          } else if (jsonData.message) {
            result.content = jsonData.message;
          } else if (jsonData.delta?.content) {
            result.content = jsonData.delta.content;
            
            // 思考过程处理 (与DeepSeek等模型兼容)
            if (jsonData.delta.reasoning_content) {
              result.content = `<think>${jsonData.delta.reasoning_content}</think>`;
            }
          } else {
            // 无法从JSON提取内容，将整个JSON转为字符串
            result.content = JSON.stringify(jsonData);
          }
          
          // 确保结果有内容再发送
          if (result.content) {
            controller.enqueue(JSON.stringify(result));
          }
        } catch (e) {
          // 不是JSON，尝试清理后发送原始文本
          // 处理：移除可能的思考过程
          if (content.includes('思考过程') && content.includes('嗯，用户是个')) {
            continue;
          }
          
          // 移除[DONE]标记
          content = content.replace(/\[DONE\]/g, '');
          
          // 移除"生成完成"等标记
          content = content.replace(/生成完成/g, '');
          
          // 只有在内容不为空时才发送
          if (content.trim()) {
            controller.enqueue(JSON.stringify({ content }));
          }
        }
      }
    } else {
      // 非SSE格式，检查是否包含[DONE]终止标记
      if (trimmedPart.includes('[DONE]')) {
        controller.enqueue(JSON.stringify({ done: true }));
        continue;
      }
      
      // 过滤思考过程
      if (trimmedPart.includes('思考过程') && trimmedPart.includes('嗯，用户是个')) {
        continue;
      }
      
      // 过滤掉"生成完成"
      if (trimmedPart.includes('生成完成')) {
        continue;
      }
      
      // 确保内容不为空
      if (trimmedPart.trim()) {
        // 非SSE格式，直接包装为内容发送
        controller.enqueue(JSON.stringify({ content: trimmedPart }));
      }
    }
  }

  return lastPart;
}

/**
 * 创建用于处理SSE流的转换流
 * 
 * @param splitOn 数据分隔符，默认为换行符
 * @returns 转换流实例
 */
export const splitStream = (splitOn = '\n') => {
  let buffer = ''

  return new TransformStream({
    transform(chunk, controller) {
      // 追加新数据到缓冲区
      buffer += chunk
      
      // 处理数据流，更新缓冲区
      buffer = processStream(buffer, controller, splitOn)
    },

    flush(controller) {
      // 处理剩余的缓冲数据
      if (buffer.trim() !== '') {
        controller.enqueue(JSON.stringify({ content: buffer.trim() }))
      }
    }
  })
}
