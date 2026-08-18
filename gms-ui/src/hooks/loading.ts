import { ref } from 'vue';
import { refreshToken } from '@/api/user';
import { clearToken, getToken, setToken } from '@/utils/auth';

let refreshing: Promise<string> | null = null;

export default function useLoading(initValue = false) {
  const loading = ref(initValue);
  /**
   * @description 本来应该在请求时刷新，但是所有的请求都是独立方法，没有封装，只能在这里统一异步刷新
   * @param value loading状态
   */
  const handleRefreshToken = async (value: boolean) => {
    if (value) {
      return;
    }

    try {
      if (getToken() == null) {
        return;
      }
      if (refreshing == null) {
        refreshing = refreshToken()
          .then((res) => {
            refreshing = null;
            return res.data.token as string;
          })
          .catch((err) => {
            refreshing = null;
            throw err;
          });
      }
      const token = await refreshing;
      setToken(token);
    } catch (err) {
      clearToken();
      throw err;
    }
  };
  const setLoading = (value: boolean) => {
    handleRefreshToken(value);
    loading.value = value;
  };
  const toggle = () => {
    loading.value = !loading.value;
  };
  return {
    loading,
    setLoading,
    toggle,
  };
}
