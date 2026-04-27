import axios from 'axios';
import * as SecureStore from 'expo-secure-store';
import { Platform } from 'react-native';
import Constants from 'expo-constants';

// Expo dev 모드에서는 Metro 가 알려주는 호스트(=PC LAN IP)를 자동으로 사용한다.
// 안드로이드 에뮬레이터는 호스트 PC 를 10.0.2.2 로 봐야 하므로 별도 처리.
function resolveDevHost(): string {
  if (Platform.OS === 'android') {
    // 에뮬레이터에서 hostUri 가 127.0.0.1 또는 10.x.x.x 처럼 잡힐 수 있어
    // 안드로이드 에뮬레이터 표준 매핑인 10.0.2.2 를 우선 사용한다.
    const hostUri = Constants.expoConfig?.hostUri ?? '';
    const host = hostUri.split(':')[0];
    if (!host || host === 'localhost' || host.startsWith('127.')) {
      return '10.0.2.2';
    }
    return host;
  }

  // iOS 시뮬레이터 / 웹 / 실제 디바이스 모두 Metro 호스트와 같은 네트워크
  const hostUri = Constants.expoConfig?.hostUri ?? '';
  const host = hostUri.split(':')[0];
  return host || 'localhost';
}

const BASE_URL = __DEV__
  ? `http://${resolveDevHost()}:8080`
  : 'https://api.shadowfit.com'; // 추후 프로덕션 URL

// 백엔드 컨트롤러 prefix가 /api/v1 이 아니라 /member, /exercises, /reports 등이라
// baseURL 에 prefix 를 붙이지 않는다.
const api = axios.create({
  baseURL: BASE_URL,
  timeout: 10000,
  headers: { 'Content-Type': 'application/json' },
});

// 디버깅용: 실제 어디로 붙는지 부팅 시 한 번 출력
if (__DEV__) {
  // eslint-disable-next-line no-console
  console.log('[api] baseURL =', BASE_URL);
}

// 요청 인터셉터: JWT accessToken 자동 첨부
api.interceptors.request.use(async (config) => {
  const token = await SecureStore.getItemAsync('accessToken');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// 응답 인터셉터: 401 시 토큰 정리
api.interceptors.response.use(
  (response) => response,
  async (error) => {
    if (error.response?.status === 401) {
      await SecureStore.deleteItemAsync('accessToken');
      await SecureStore.deleteItemAsync('refreshToken');
    }
    return Promise.reject(error);
  }
);

export default api;
