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
      console.log("createAssistantWriterStylized 接收的参数:", data);

      // 调用当前模型的接口
      return new Promise((resolve) => {
        if (!this.currentModelItem?.chatFetch) {
          return {
            error: 1,
            reader: null
          }
        }
        
        // 确保文本内容不为空
        const userMessage = data.text?.trim() || '';
        if (!userMessage) {
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
              // 处理流式响应
              const reader = res.body
                .pipeThrough(new TextDecoderStream())
                .pipeThrough(TransformUtils.splitStream('\n'))
                .getReader();

              resolve({
                error: 0,
                reader
              });
            } else {
              resolve({
                error: 1,
                reader: null
              });
            }
          })
          .catch((err) => {
            console.error('Chat API error:', err);
            resolve({
              error: 1,
              reader: null
            });
          });
      });
    }
  }
})
