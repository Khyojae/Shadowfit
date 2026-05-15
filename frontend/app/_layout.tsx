import { DarkTheme, ThemeProvider } from '@react-navigation/native';
import { useFonts } from 'expo-font';
import { Stack, useRouter, useSegments } from 'expo-router';
import * as SplashScreen from 'expo-splash-screen';
import { useEffect } from 'react';
import 'react-native-reanimated';
import { COLORS } from '@/constants/Colors';
import { useAuthStore } from '@/stores/authStore';

export { ErrorBoundary } from 'expo-router';

export const unstable_settings = {
  initialRouteName: '(auth)',
};

SplashScreen.preventAutoHideAsync();

// ShadowFit 다크 테마
const ShadowFitTheme = {
  ...DarkTheme,
  colors: {
    ...DarkTheme.colors,
    primary: COLORS.primary,
    background: COLORS.background,
    card: COLORS.surface,
    text: COLORS.text,
    border: COLORS.border,
  },
};

export default function RootLayout() {
  const [loaded, error] = useFonts({
    SpaceMono: require('../assets/fonts/SpaceMono-Regular.ttf'),
  });

  useEffect(() => {
    if (error) throw error;
  }, [error]);

  useEffect(() => {
    if (loaded) SplashScreen.hideAsync();
  }, [loaded]);

  if (!loaded) return null;

  return <RootLayoutNav />;
}

function RootLayoutNav() {
  const router = useRouter();
  const segments = useSegments();
  const { isAuthenticated, isLoading, onboardingCompleted, restoreSession } =
    useAuthStore();

  useEffect(() => {
    restoreSession();
  }, []);

  useEffect(() => {
    if (isLoading) return;

    const inAuth = segments[0] === '(auth)';
    const inOnboarding = segments[0] === '(onboarding)';

    // 1. 비로그인 + 인증 화면 외 다른 곳 → 로그인으로
    if (!isAuthenticated) {
      if (!inAuth && !__DEV__) {
        router.replace('/(auth)/login');
      }
      return;
    }

    // 2. 로그인 상태인데 온보딩 조회 중이면 대기
    if (onboardingCompleted === null) return;

    // 3. 온보딩 미완료 → 인증 화면이든 어디든 (onboarding) 로 강제
    //    단 (onboarding) 자체엔 머물 수 있어야 함
    if (!onboardingCompleted) {
      if (!inOnboarding) {
        router.replace('/(onboarding)');
      }
      return;
    }

    // 4. 온보딩 완료 + 로그인 화면 → 메인 탭으로
    //    (onboarding) 화면은 마이페이지 수정 진입을 허용해야 하므로 막지 않음
    if (inAuth) {
      router.replace('/(tabs)');
    }
  }, [isAuthenticated, isLoading, onboardingCompleted, segments]);

  return (
    <ThemeProvider value={ShadowFitTheme}>
      <Stack screenOptions={{ headerShown: false }}>
        <Stack.Screen name="(auth)" />
        <Stack.Screen name="(onboarding)" />
        <Stack.Screen name="(tabs)" />
        <Stack.Screen name="report/[id]" />
        <Stack.Screen name="board/notice" />
        <Stack.Screen name="board/qna" />
        <Stack.Screen name="modal" options={{ presentation: 'modal' }} />
      </Stack>
    </ThemeProvider>
  );
}
