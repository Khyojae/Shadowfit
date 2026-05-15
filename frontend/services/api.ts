import axios from 'axios';
import * as SecureStore from 'expo-secure-store';
import { Platform } from 'react-native';
import Constants from 'expo-constants';
import { router } from 'expo-router';

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

// 응답 인터셉터: 401 시 토큰 정리 + 강제 로그아웃 (가드가 로그인 화면으로 보냄)
// 단, "로그인/회원가입 자체"의 401 은 잘못된 비번을 알려야 하니 처리하지 않음
//   → Authorization 헤더가 실제로 첨부됐던 요청에서만 forceLogout 트리거
api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const status = error.response?.status;
    const hadAuthHeader = !!error.config?.headers?.Authorization;

    if (status === 401 && hadAuthHeader) {
      // 동적 import 로 store ↔ api 순환 의존 회피
      const { useAuthStore } = require('@/stores/authStore');
      await useAuthStore.getState().forceLogout();
      // _layout 가드는 __DEV__ 에서 자동 redirect 를 안 시키므로
      // 토큰 만료 케이스만큼은 명시적으로 로그인 화면으로 이동
      router.replace('/(auth)/login');
    }
    return Promise.reject(error);
  }
);

export default api;
