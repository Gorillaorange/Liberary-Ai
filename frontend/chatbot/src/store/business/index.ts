import { defineStore } from 'pinia'

import { sleep } from '@/utils/request'
import * as GlobalAPI from '@/api'


import * as TransformUtils from '@/components/MarkdownPreview/transform'

import { defaultModelName, modelMappingList } from '@/components/MarkdownPreview/models'

export interface BusinessState {
  systemModelName: string
}

export const useBusinessStore = defineStore('business-store', {
  state: (): BusinessState => {
    return {
      systemModelName: defaultModelName
    }
  },
  getters: {
    currentModelItem (state) {
      return modelMappingList.find(v => v.modelName === state.systemModelName)
    }
  },
  actions: {
    /**
     * Event Stream 调用大模型接口
     */
    async createAssistantWriterStylized(data): Promise<{error: number, reader: ReadableStreamDefaultReader<string> | null}> {
      // 记录调试信息
      console.log("调用AI接口 - 参数:", data);

      // 调用当前模型的接口
      return new Promise((resolve) => {
        if (!this.currentModelItem?.chatFetch) {
          console.error("未找到可用的模型API");
          resolve({
            error: 1,
            reader: null
          });
          return;
        }
        
        // 确保文本内容不为空
        const userMessage = data.text?.trim() || '';
        if (!userMessage) {
          console.error("用户消息为空");
          resolve({
            error: 1,
            reader: null
          });
          return;
        }
        
        // 调用模型接口
        this.currentModelItem.chatFetch(userMessage, data.sessionId)
          .then((res) => {
            if (res.body) {
              // 检查内容类型
              const contentType = res.headers?.get('content-type') || '';
              console.log("响应内容类型:", contentType);
              
              // 根据响应类型选择合适的分隔符
              let splitDelimiter = '\n'; // 默认换行符
              
              // 如果是SSE格式，可能需要特殊处理
              if (contentType.includes('text/event-stream')) {
                console.log("检测到SSE流");
                // 有些后端实现会使用\n\n分隔事件
                splitDelimiter = '\n';
              } else if (contentType.includes('application/json')) {
                console.log("检测到JSON响应");
              }
              
              // 使用改进后的处理流程
              // 1. 先解码文本
              // 2. 使用统一的splitStream处理SSE格式
              // 3. 获取流读取器
              console.log("使用分隔符:", splitDelimiter);
              const reader = res.body
                .pipeThrough(new TextDecoderStream())
                .pipeThrough(TransformUtils.splitStream(splitDelimiter))
                .getReader();

              resolve({
                error: 0,
                reader
              });
            } else {
              console.error("API响应无body");
              resolve({
                error: 1,
                reader: null
              });
            }
          })
          .catch((err) => {
            console.error('Chat API调用失败:', err);
            resolve({
              error: 1,
              reader: null
            });
          });
      });
    }
  }
})
