import axios from 'axios';
import type { AxiosRequestConfig, AxiosResponse } from 'axios';
import { Message } from '@arco-design/web-vue';
import { useUserStore } from '@/store';
import { getToken } from '@/utils/auth';

/* eslint-disable no-bitwise */
function generateUUID() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

export interface HttpResponse<T = unknown> {
  status: string;
  message: string;
  code: number;
  data: T;
}

if (import.meta.env.VITE_API_BASE_URL) {
  axios.defaults.baseURL = import.meta.env.VITE_API_BASE_URL;
}

axios.interceptors.request.use(
  (config: AxiosRequestConfig) => {
    // let each request carry token
    // this example using the JWT token
    // Authorization is a custom headers key
    // please modify it according to the actual situation
    const token = getToken();
    if (token) {
      if (!config.headers) {
        config.headers = {};
      }
      config.headers.Authorization = `Bearer ${token}`;
    }
    const isUpload = config.data instanceof FormData || config.headers?.['Content-Type'] === 'multipart/form-data';
    if (config.data && !isUpload) {
      config.data = {
        requestId: generateUUID(),
        data: config.data,
      };
    }
    return config;
  },
  (error) => {
    // do something
    return Promise.reject(error);
  }
);
// add response interceptors
axios.interceptors.response.use(
  (response: AxiosResponse<HttpResponse | Blob>) => {
    if (response.config.responseType === 'blob') {
      const res = response.data as Blob;
      if (response.status !== 200) {
        Message.error({
          content: response.statusText || 'Error',
          duration: 5 * 1000,
        });
        return Promise.reject(new Error(response.statusText || 'Error'));
      }
      // 业务错误会以200+JSON返回,优先嗅探避免把错误信息当文件下载
      if (res.type === 'application/json') {
        res.text().then((text) => {
          try {
            const err = JSON.parse(text);
            Message.error({
              content: err.message || '导出失败',
              duration: 5 * 1000,
            });
          } catch (e) {
            Message.error({
              content: '导出失败',
              duration: 5 * 1000,
            });
          }
        });
        return Promise.reject(new Error('导出失败'));
      }
      const url = window.URL.createObjectURL(new Blob([res]));
      const link = document.createElement('a');
      link.href = url;
      // 从Content-Disposition中获取文件名
      const disposition = response.headers['content-disposition'];
      let filename = 'download';
      if (disposition) {
        const match = disposition.split('filename=')[1];
        if (match) {
          filename = decodeURIComponent(match.replace(/"/g, ''));
        }
      }
      link.setAttribute(
        'download',
        filename
      );
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      // 释放URL对象
      window.URL.revokeObjectURL(url);
      return null;
    }
    const res = response.data as HttpResponse;
    // if the custom code is not 20000, it is judged as an error.
    if (res.code !== 20000) {
      Message.error({
        content: res.message || 'Error',
        duration: 5 * 1000,
      });
      return Promise.reject(new Error(res.message || 'Error'));
    }
    return res;
  },
  (error) => {
    const status = error.response?.status;
    let errorMessage;
    if (error.message === 'Network Error') {
      errorMessage = '无法连接到服务器';
    } else {
      errorMessage = error.message || 'Request Error';
    }
    Message.error({
      content: errorMessage,
      duration: 5 * 1000,
    });
    if (status === 401) {
      const userStore = useUserStore();

      userStore.logoutCallBack();
      // window.location.reload();
      window.location.href = '/';
      return Promise.reject(new Error('登录已过期'));
    }
    return Promise.reject(error);
  }
);
