import { mockEventStreamText } from '@/data'
import { sleep } from '@/utils/request'

/**
 * 转义处理响应值为 data: 的 json 字符串
 * 如: 科大讯飞星火、Kimi Moonshot 等大模型的 response
 */
export const createParser = () => {
  let keepAliveShown = false

  const resetKeepAliveParser = () => {
    keepAliveShown = false
  }

  const parseJsonLikeData = (content) => {
    // 处理null或undefined输入
    if (!content) {
      return null;
    }

    // 详细记录收到的内容（调试用）
    console.log("收到数据内容:", content);

    // 检查内容是否包含完成信号
    const isDone = 
      content === '[DONE]' || 
      content.includes('[DONE]') || 
      content.includes('event: done') ||
      content.includes('"name":"done"') ||
      content.includes('event:done');

    // 若是终止信号，则直接结束
    if (isDone) {
      // 重置 keepAlive 标志
      keepAliveShown = false
      console.log("收到完成信号，流处理结束:", content);
      return {
        done: true
      }
    }

    if (content.startsWith('data: ')) {
      keepAliveShown = false
      const dataString = content.substring(6).trim()
      
      // 检查data中的[DONE]信号
      if (dataString === '[DONE]' || dataString.includes('[DONE]')) {
        console.log("收到data:[DONE]信号，流处理结束");
        return {
          done: true
        }
      }
      
      // 检查是否包含done事件
      if (content.includes('event:done') || content.includes('event: done') || content.includes('"name":"done"')) {
        console.log("收到done事件，流处理结束");
        return {
          done: true
        }
      }
      
      try {
        // 解析JSON格式数据
        const parsedData = JSON.parse(dataString);
        
        // 检查是否有完成标志
        if (parsedData.done === true || parsedData.finished === true || parsedData.complete === true) {
          console.log("从JSON中检测到完成标志");
          return {
            done: true
          }
        }
        
        return parsedData;
      } catch (error) {
        console.error('JSON 解析错误：', error)
        // 如果JSON解析失败但包含文本数据，则直接返回原始内容
        return {
          content: dataString
        }
      }
    }
    
    // 尝试直接解析 JSON 字符串
    try {
      const trimmedContent = content.trim()

      if (trimmedContent === ': keep-alive') {
        // 如果还没有显示过 keep-alive 提示，则显示
        if (!keepAliveShown) {
          keepAliveShown = true
          return {
            isWaitQueuing: true
          }
        } else {
          return null
        }
      }

      if (!trimmedContent) {
        return null
      }

      if (trimmedContent.startsWith('{') && trimmedContent.endsWith('}')) {
        const parsedData = JSON.parse(trimmedContent);
        
        // 检查是否有完成标志
        if (parsedData.done === true || parsedData.finished === true || parsedData.complete === true) {
          console.log("从直接JSON中检测到完成标志");
          return {
            done: true
          }
        }
        
        return parsedData;
      }
      if (trimmedContent.startsWith('[') && trimmedContent.endsWith(']')) {
        return JSON.parse(trimmedContent)
      }
    } catch (error) {
      console.error('尝试直接解析 JSON 失败：', error)
    }

    // 如果所有处理都失败，返回原始内容
    return {
      content: content
    }
  }
  return {
    resetKeepAliveParser,
    parseJsonLikeData
  }
}

export const createStreamThinkTransformer = () => {
  let isThinking = false

  const resetThinkTransformer = () => {
    isThinking = false
  }

  const transformStreamThinkData = (content) => {
    const stream = parseJsonLikeData(content)

    if (stream && stream.done) {
      return {
        done: true
      }
    }

    // DeepSeek 存在限速问题，这里做一个简单处理
    // https://api-docs.deepseek.com/zh-cn/quick_start/rate_limit
    if (stream && stream.isWaitQueuing) {
      return {
        isWaitQueuing: stream.isWaitQueuing
      }
    }

    if (!stream || !stream.choices || stream.choices.length === 0) {
      return {
        content: ''
      }
    }

    const delta = stream.choices[0].delta
    const contentText = delta.content || ''
    const reasoningText = delta.reasoning_content || ''

    let transformedContent = ''

    // 开始处理推理过程
    if (delta.content === null && delta.reasoning_content !== null) {
      if (!isThinking) {
        transformedContent += '<think>'
        isThinking = true
      }
      transformedContent += reasoningText
    }
    // 当 content 出现时，说明推理结束
    else if (delta.content !== null && delta.reasoning_content === null) {
      if (isThinking) {
        transformedContent += '</think><br><br>'
        isThinking = false
      }
      transformedContent += contentText
    }
    // 当为普通模型，即不包含推理字段时，直接追加 content
    else if (delta.content !== null && delta.reasoning_content === undefined) {
      isThinking = false
      transformedContent += contentText
    }

    return {
      content: transformedContent
    }
  }

  return {
    resetThinkTransformer,
    transformStreamThinkData
  }
}

const { resetKeepAliveParser, parseJsonLikeData } = createParser()
const { resetThinkTransformer, transformStreamThinkData } = createStreamThinkTransformer()


/**
 * 处理大模型调用暂停、异常或结束后触发的操作
 */
export const triggerModelTermination = () => {
  resetKeepAliveParser()
  resetThinkTransformer()
}

type ContentResult = {
  content: any
} | {
  done: boolean
}

type DoneResult = {
  content?: any
  isWaitQueuing?: any
  done?: boolean
}

export type CrossTransformFunction = (readValue: Uint8Array | string, textDecoder: TextDecoder) => DoneResult

export type TransformFunction = (readValue: Uint8Array | string, textDecoder: TextDecoder) => ContentResult

interface TypesModelLLM {
  // 模型昵称
  label: string
  // 模型标识符
  modelName: string
  // Stream 结果转换器
  transformStreamValue: TransformFunction
  // 每个大模型调用的 API 请求
  chatFetch: (text: string, sessionId?: string) => Promise<Response>
}


/** ---------------- 大模型映射列表 & Response Transform 用于处理不同类型流的值转换器 ---------------- */

/**
 * Mock 模拟模型的 name
 */
export const defaultMockModelName = 'local-model'

/**
 * 项目默认使用模型，按需修改此字段即可
 */

// export const defaultModelName = 'spark'
export const defaultModelName = defaultMockModelName

export const modelMappingList: TypesModelLLM[] = [
  {
    label: '模型测试',
    modelName: 'standard',
    transformStreamValue(readValue, textDecoder) {
      let content = ''
      if (readValue instanceof Uint8Array) {
        content = textDecoder.decode(readValue, {
          stream: true
        })
      } else {
        content = readValue
      }
      return {
        content
      }
    },
    // Mock Event Stream 用于模拟读取大模型接口 Mock 数据
    async chatFetch(text): Promise<Response> {
      // 模拟 res.body 的数据
      // 将 mockData 转换为 ReadableStream

      const mockReadableStream = new ReadableStream({
        start(controller) {
          // 将每一行数据作为单独的 chunk
          mockEventStreamText.split('\n').forEach(line => {
            controller.enqueue(new TextEncoder().encode(`${ line }\n`))
          })
          controller.close()
        }
      })
      await sleep(500)

      return new Promise((resolve) => {
        resolve({
          body: mockReadableStream
        } as Response)
      })
    }
  },
  {
    label: '🐋 DeepSeek-V3',
    modelName: 'deepseek-v3',
    transformStreamValue(readValue, textDecoder) {
      const stream = transformStreamThinkData(readValue)
      if (stream.done) {
        return {
          done: true
        }
      }
      if (stream.isWaitQueuing) {
        // 返回等待状态
        return {
          content: null,
          isWaitQueuing: stream.isWaitQueuing
        } as any
      }
      return {
        content: stream.content
      }
    },
    // Event Stream 调用大模型接口 DeepSeek 深度求索 (Fetch 调用)
    async chatFetch(text, sessionId) {
      const url = new URL(`${ location.origin }/deepseek/chat/completions`)
      const params = {
      }
      Object.keys(params).forEach(key => {
        url.searchParams.append(key, params[key])
      })

      const req = new Request(url, {
        method: 'post',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${ import.meta.env.VITE_DEEPSEEK_KEY }`
        },
        body: JSON.stringify({
          // 普通模型 V3
          'model': 'deepseek-chat',
          stream: true,
          messages: [
            {
              'role': 'user',
              'content': text
            }
          ]
        })
      })
      return fetch(req)
    }
  },

  // Replace your current local-model implementation with this corrected version
  
{
    label: '🤖 本地模型',
    modelName: 'local-model',
    transformStreamValue(readValue, textDecoder) {
      // 解码并解析已标准化的数据
      try {
        let content = '';
        
        // 处理不同类型的输入
        if (typeof readValue === 'string') {
          content = readValue;
        } else if (readValue instanceof Uint8Array) {
          content = textDecoder.decode(readValue, { stream: true });
        } else {
          return { content: '' };
        }
        
        // 尝试解析JSON结构
        try {
          const parsed = JSON.parse(content);
          
          // 处理完成信号
          if (parsed.done) {
            return { done: true };
          }
          
          // 处理等待状态
          if (parsed.isWaitQueuing) {
            return { content: null, isWaitQueuing: true };
          }
          
          // 如果内容存在，确保移除任何可能的[DONE]标记
          if (parsed.content) {
            const cleanContent = String(parsed.content)
              .replace(/\[DONE\]/g, '')
              .replace(/生成完成/g, '');
              
            // 只有在有内容的情况下才返回
            if (cleanContent.trim()) {
              return { content: cleanContent };
            }
          }
          
          // 默认空内容
          return { content: '' };
        } catch (e) {
          // 如果JSON解析失败，直接返回原始内容
          if (content.includes('[DONE]')) {
            return { done: true };
          }
          
          // 移除特殊标记
          const cleanedContent = content
            .replace(/\[DONE\]/g, '')
            .replace(/生成完成/g, '');
            
          return { content: cleanedContent.trim() };
        }
      } catch (e) {
        console.warn("数据处理失败:", e);
        return { content: '' };
      }
    },
    async chatFetch(text, sessionId) {
      try {
        console.log("发送本地模型请求:", text);
        
        // 获取存储的JWT令牌
        const token = localStorage.getItem('token');
        
        // 检查是否有token，如果没有token提前处理
        if (!token) {
          console.warn("未找到授权令牌，需要登录");
          
          // 获取当前url，用于登录后重定向回来
          const currentPath = window.location.pathname;
          
          // 添加延迟以确保用户能看到提示
          setTimeout(() => {
            window.location.href = `/login?redirect=${encodeURIComponent(currentPath)}`;
          }, 1500);
          
          // 构造一个模拟的响应，显示授权错误信息
          const errorMessage = "您需要登录才能使用AI助手功能。<br>正在为您跳转到登录页面...";
          const errorStream = new ReadableStream({
            start(controller) {
              const event = `data: {"type":"content","data":"<div style='text-align:center;padding:20px;'><span style='color:#f5222d;font-weight:bold;font-size:16px'>⚠️ ${errorMessage}</span></div>"}\n\n`;
              controller.enqueue(new TextEncoder().encode(event));
              controller.close();
            }
          });
          
          return {
            ok: true,
            status: 200,
            headers: new Headers({ 'content-type': 'text/event-stream' }),
            body: errorStream
          } as Response;
        }

        // 直接调用 /api/chat 接口获取流式响应
        const url = new URL(`${location.origin}/api/chat`);
        if (sessionId && sessionId.trim()) {
          url.searchParams.append('sessionId', sessionId);
        }
        
        console.log(`发送到 ${url.toString()} 的请求:`, text);
        
        const streamResponse = await fetch(url, {
          method: 'POST',
          headers: {
            'Content-Type': 'text/plain',
            'Accept': 'text/event-stream',
            'Authorization': `Bearer ${token}`
          },
          credentials: 'include',
          mode: 'cors',
          body: text // 直接发送纯文本内容
        });

        console.log("流响应状态:", streamResponse.status);
        console.log("Content-Type:", streamResponse.headers.get('content-type'));

        // 处理授权错误
        if (streamResponse.status === 401 || streamResponse.status === 403) {
          console.error("授权失败:", streamResponse.status);
          
          // 清除过期的token
          localStorage.removeItem('token');
          localStorage.removeItem('username');
          
          // 获取当前url，用于登录后重定向回来
          const currentPath = window.location.pathname;
          
          // 添加延迟以确保用户能看到提示
          setTimeout(() => {
            window.location.href = `/login?redirect=${encodeURIComponent(currentPath)}`;
          }, 1500);
          
          // 构造一个模拟的响应，显示友好的错误信息
          const errorMessage = "您的登录已过期，需要重新登录。<br>正在为您跳转到登录页面...";
          const errorStream = new ReadableStream({
            start(controller) {
              const event = `data: {"type":"content","data":"<div style='text-align:center;padding:20px;'><span style='color:#f5222d;font-weight:bold;font-size:16px'>⚠️ ${errorMessage}</span></div>"}\n\n`;
              controller.enqueue(new TextEncoder().encode(event));
              controller.close();
            }
          });
          
          return {
            ok: true,
            status: 200,
            headers: new Headers({ 'content-type': 'text/event-stream' }),
            body: errorStream
          } as Response;
        }

        // 其他错误处理
        if (!streamResponse.ok) {
          const errorBody = await streamResponse.text();
          console.error(`API请求失败: ${streamResponse.status}`, errorBody);
          throw new Error(`请求失败 ${streamResponse.status}: ${errorBody.slice(0, 200)}`);
        }

        return streamResponse;
      } catch (error) {
        console.error("本地模型API错误:", error);
        
        // 构造一个模拟的响应，显示异常信息
        const errorMessage = error instanceof Error 
          ? `连接异常: ${error.message}` 
          : "连接异常: 未知错误";
        
        const errorStream = new ReadableStream({
          start(controller) {
            const event = `data: {"type":"content","data":"<div style='text-align:center;padding:20px;'><span style='color:#f5222d;font-weight:bold;font-size:16px'>⚠️ ${errorMessage}</span></div>"}\n\n`;
            controller.enqueue(new TextEncoder().encode(event));
            controller.close();
          }
        });
        
        return {
          ok: true,
          status: 200,
          headers: new Headers({ 'content-type': 'text/event-stream' }),
          body: errorStream
        } as Response;
      }
    }
  }
]