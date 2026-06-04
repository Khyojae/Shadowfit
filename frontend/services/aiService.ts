import axios from 'axios';
import Constants from 'expo-constants';
import { Platform } from 'react-native';
import type { PoseDetectRequest, PoseDetectResponse } from '@/types/pose';

// AI 서버(FastAPI)는 Spring(:8080)이 아니라 별도 :8000 /api/v1 로 직결한다 (분기 H2).
// Metro 호스트(=PC LAN IP)를 그대로 쓰되 포트만 8000. 안드로이드 에뮬레이터는 10.0.2.2 매핑.
function resolveAiBaseUrl(): string {
  const override = process.env.EXPO_PUBLIC_AI_BASE_URL;
  if (override) return override;

  const hostUri = Constants.expoConfig?.hostUri ?? '';
  let host = hostUri.split(':')[0] || 'localhost';
  if (Platform.OS === 'android' && (host === 'localhost' || host.startsWith('127.'))) {
    host = '10.0.2.2';
  }
  return `http://${host}:8000/api/v1`;
}

const aiApi = axios.create({
  baseURL: resolveAiBaseUrl(),
  timeout: 8000,
  headers: { 'Content-Type': 'application/json' },
});

// AI 서버 InternalAuthMiddleware: Authorization: Bearer <INTERNAL_API_TOKEN> 강제.
// 토큰 미설정 시 헤더 없이 보냄(=401) — exercise.tsx 가 토큰 없으면 폴링 자체를 안 함.
aiApi.interceptors.request.use((config) => {
  const token = process.env.EXPO_PUBLIC_INTERNAL_API_TOKEN;
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

export const aiService = {
  // 실시간 포즈 감지 (POST {ai}:8000/api/v1/pose)
  detectPose: (data: PoseDetectRequest) =>
    aiApi.post<PoseDetectResponse>('/pose', data),
};
