import { useState } from 'react';
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  KeyboardAvoidingView,
  Platform,
  Alert,
  TouchableOpacity,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useRouter } from 'expo-router';
import { COLORS, FONT_SIZE, SPACING, RADIUS } from '@/constants/Colors';
import TabSwitch from '@/components/ui/TabSwitch';
import Input from '@/components/ui/Input';
import Button from '@/components/ui/Button';
import { useAuthStore } from '@/stores/authStore';
import {
  Mail,
  Lock,
  User as UserIcon,
  Dumbbell,
  Home,
  BarChart3,
  Film,
  UserCircle2,
  Rocket,
  ClipboardList,
  Megaphone,
  HelpCircle,
  KeyRound,
  type LucideIcon,
} from 'lucide-react-native';

const DEV_ROUTES: { label: string; Icon: LucideIcon; path: string }[] = [
  { label: '홈', Icon: Home, path: '/(tabs)' },
  { label: '활동', Icon: BarChart3, path: '/(tabs)/activity' },
  { label: '운동', Icon: Film, path: '/(tabs)/exercise' },
  { label: '마이페이지', Icon: UserCircle2, path: '/(tabs)/mypage' },
  { label: '온보딩', Icon: Rocket, path: '/(onboarding)' },
  { label: '운동 보고서', Icon: ClipboardList, path: '/report/1' },
  { label: '공지사항', Icon: Megaphone, path: '/board/notice' },
  { label: 'Q&A', Icon: HelpCircle, path: '/board/qna' },
  { label: 'PW재설정', Icon: KeyRound, path: '/(auth)/reset-password' },
];

export default function LoginScreen() {
  const [activeTab, setActiveTab] = useState(0);
  const router = useRouter();

  return (
    <SafeAreaView style={styles.container}>
      <KeyboardAvoidingView
        style={styles.flex}
        behavior={Platform.OS === 'ios' ? 'padding' : undefined}
      >
        <ScrollView
          contentContainerStyle={styles.scrollContent}
          keyboardShouldPersistTaps="handled"
          showsVerticalScrollIndicator={false}
        >
          {/* 로고 */}
          <View style={styles.logoArea}>
            <View style={styles.logoIconWrap}>
              <Dumbbell size={42} color={COLORS.primary} strokeWidth={2.2} />
            </View>
            <Text style={styles.logoTitle}>ShadowFit</Text>
            <Text style={styles.logoSubtitle}>AI 자세 교정 트레이너</Text>
          </View>

          {/* 탭 전환 */}
          <TabSwitch
            tabs={['로그인', '회원가입']}
            activeIndex={activeTab}
            onTabChange={setActiveTab}
          />

          <View style={styles.formArea}>
            {activeTab === 0 ? <LoginForm /> : <SignupForm />}
          </View>

          {/* 개발용 바로가기 */}
          {__DEV__ && (
            <View style={styles.devSection}>
              <Text style={styles.devTitle}>DEV 바로가기</Text>
              <View style={styles.devGrid}>
                {DEV_ROUTES.map(({ label, Icon, path }) => (
                  <TouchableOpacity
                    key={path}
                    style={styles.devBtn}
                    onPress={() => router.push(path as any)}
                    activeOpacity={0.7}
                  >
                    <Icon size={18} color={COLORS.textSecondary} strokeWidth={2} />
                    <Text style={styles.devBtnLabel}>{label}</Text>
                  </TouchableOpacity>
                ))}
              </View>
            </View>
          )}
        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

// ─── 로그인 폼 ───────────────────────────────────
function LoginForm() {
  const router = useRouter();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [errors, setErrors] = useState<{ email?: string; password?: string }>({});
  const [loading, setLoading] = useState(false);
  const login = useAuthStore((s) => s.login);

  const validate = () => {
    const newErrors: typeof errors = {};
    if (!email.trim()) newErrors.email = '이메일을 입력해주세요';
    else if (!/\S+@\S+\.\S+/.test(email)) newErrors.email = '올바른 이메일 형식이 아닙니다';
    if (!password) newErrors.password = '비밀번호를 입력해주세요';
    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleLogin = async () => {
    if (!validate()) return;
    setLoading(true);
    try {
      await login({ email: email.trim(), password });
    } catch (e: any) {
      const msg = e.response?.data?.message || '로그인에 실패했습니다';
      Alert.alert('로그인 실패', msg);
    } finally {
      setLoading(false);
    }
  };

  return (
    <>
      <Input
        label="이메일"
        icon={Mail}
        placeholder="example@email.com"
        value={email}
        onChangeText={setEmail}
        error={errors.email}
        keyboardType="email-address"
        autoCapitalize="none"
        autoComplete="email"
        containerStyle={styles.inputGap}
      />
      <Input
        label="비밀번호"
        icon={Lock}
        placeholder="6자 이상 입력"
        value={password}
        onChangeText={setPassword}
        error={errors.password}
        isPassword
        autoComplete="password"
        containerStyle={styles.inputGap}
      />
      <Button
        title="로그인  →"
        onPress={handleLogin}
        loading={loading}
        style={styles.submitBtn}
      />
      <TouchableOpacity onPress={() => router.push('/(auth)/reset-password' as any)}>
        <Text style={styles.forgotText}>
          비밀번호를 잊으셨나요? <Text style={styles.forgotLink}>재설정</Text>
        </Text>
      </TouchableOpacity>
    </>
  );
}

// ─── 회원가입 폼 ──────────────────────────────────
type SexOption = 'MALE' | 'FEAMALE' | 'NONE';

const SEX_OPTIONS: { key: SexOption; label: string }[] = [
  { key: 'MALE', label: '남성' },
  { key: 'FEAMALE', label: '여성' },
  { key: 'NONE', label: '선택안함' },
];

function SignupForm() {
  const [username, setUsername] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [passwordConfirm, setPasswordConfirm] = useState('');
  const [sex, setSex] = useState<SexOption>('NONE');
  const [errors, setErrors] = useState<{
    username?: string;
    email?: string;
    password?: string;
    passwordConfirm?: string;
  }>({});
  const [loading, setLoading] = useState(false);
  const signup = useAuthStore((s) => s.signup);

  const validate = () => {
    const newErrors: typeof errors = {};
    if (!username.trim()) newErrors.username = '아이디를 입력해주세요';
    else if (username.trim().length < 3) newErrors.username = '3자 이상 입력해주세요';
    if (!email.trim()) newErrors.email = '이메일을 입력해주세요';
    else if (!/\S+@\S+\.\S+/.test(email)) newErrors.email = '올바른 이메일 형식이 아닙니다';
    if (!password) newErrors.password = '비밀번호를 입력해주세요';
    else if (password.length < 6) newErrors.password = '6자 이상 입력해주세요';
    if (password !== passwordConfirm)
      newErrors.passwordConfirm = '비밀번호가 일치하지 않습니다';
    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleSignup = async () => {
    if (!validate()) return;
    setLoading(true);
    try {
      await signup({
        username: username.trim(),
        email: email.trim(),
        password,
        sex,
        role: 'USER',
      });
    } catch (e: any) {
      console.error('[signup] status=', e.response?.status, 'data=', e.response?.data);
      const msg =
        e.response?.data?.message ||
        e.response?.data ||
        e.message ||
        '회원가입에 실패했습니다';
      Alert.alert(
        `회원가입 실패 (${e.response?.status ?? 'no-response'})`,
        typeof msg === 'string' ? msg : JSON.stringify(msg),
      );
    } finally {
      setLoading(false);
    }
  };

  return (
    <>
      <Input
        label="아이디"
        icon={UserIcon}
        placeholder="3자 이상"
        value={username}
        onChangeText={setUsername}
        error={errors.username}
        autoCapitalize="none"
        autoComplete="username"
        containerStyle={styles.inputGap}
      />
      <Input
        label="이메일"
        icon={Mail}
        placeholder="example@email.com"
        value={email}
        onChangeText={setEmail}
        error={errors.email}
        keyboardType="email-address"
        autoCapitalize="none"
        autoComplete="email"
        containerStyle={styles.inputGap}
      />
      <Input
        label="비밀번호"
        icon={Lock}
        placeholder="6자 이상 입력"
        value={password}
        onChangeText={setPassword}
        error={errors.password}
        isPassword
        autoComplete="new-password"
        containerStyle={styles.inputGap}
      />
      <Input
        label="비밀번호 확인"
        icon={Lock}
        placeholder="비밀번호를 다시 입력"
        value={passwordConfirm}
        onChangeText={setPasswordConfirm}
        error={errors.passwordConfirm}
        isPassword
        autoComplete="new-password"
        containerStyle={styles.inputGap}
      />

      <Text style={styles.fieldLabel}>성별</Text>
      <View style={styles.sexRow}>
        {SEX_OPTIONS.map((opt) => (
          <TouchableOpacity
            key={opt.key}
            style={[styles.sexChip, sex === opt.key && styles.sexChipActive]}
            onPress={() => setSex(opt.key)}
            activeOpacity={0.7}
          >
            <Text
              style={[
                styles.sexChipLabel,
                sex === opt.key && styles.sexChipLabelActive,
              ]}
            >
              {opt.label}
            </Text>
          </TouchableOpacity>
        ))}
      </View>

      <Button
        title="회원가입  →"
        onPress={handleSignup}
        loading={loading}
        style={styles.submitBtn}
      />
    </>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: COLORS.background,
  },
  flex: {
    flex: 1,
  },
  scrollContent: {
    flexGrow: 1,
    paddingHorizontal: SPACING.xxl,
    paddingBottom: SPACING.xxxl,
  },

  // 로고
  logoArea: {
    alignItems: 'center',
    marginTop: 60,
    marginBottom: 40,
  },
  logoIconWrap: {
    marginBottom: SPACING.md,
  },
  logoTitle: {
    fontSize: FONT_SIZE.title,
    fontWeight: '800',
    color: COLORS.text,
    letterSpacing: 1,
  },
  logoSubtitle: {
    fontSize: FONT_SIZE.sm,
    color: COLORS.textSecondary,
    marginTop: SPACING.xs,
  },

  // 폼
  formArea: {
    marginTop: SPACING.xxl,
  },
  inputGap: {
    marginBottom: SPACING.lg,
  },
  submitBtn: {
    marginTop: SPACING.lg,
  },
  forgotText: {
    textAlign: 'center',
    fontSize: FONT_SIZE.sm,
    color: COLORS.textSecondary,
    marginTop: SPACING.xl,
  },
  forgotLink: {
    color: COLORS.primary,
    fontWeight: '600',
  },

  // 성별 선택
  fieldLabel: {
    fontSize: FONT_SIZE.sm,
    color: COLORS.textSecondary,
    fontWeight: '600',
    marginBottom: SPACING.sm,
  },
  sexRow: {
    flexDirection: 'row',
    gap: SPACING.sm,
    marginBottom: SPACING.lg,
  },
  sexChip: {
    flex: 1,
    paddingVertical: SPACING.md,
    borderRadius: RADIUS.md,
    borderWidth: 1,
    borderColor: COLORS.border,
    backgroundColor: COLORS.surface,
    alignItems: 'center',
  },
  sexChipActive: {
    borderColor: COLORS.primary,
    backgroundColor: COLORS.primaryDim ?? COLORS.surface,
  },
  sexChipLabel: {
    fontSize: FONT_SIZE.sm,
    color: COLORS.textSecondary,
    fontWeight: '600',
  },
  sexChipLabelActive: {
    color: COLORS.primary,
    fontWeight: '700',
  },

  // DEV 바로가기
  devSection: {
    marginTop: 40,
    paddingTop: SPACING.lg,
    borderTopWidth: 1,
    borderTopColor: COLORS.border,
  },
  devTitle: {
    fontSize: FONT_SIZE.xs,
    color: COLORS.warning,
    fontWeight: '700',
    textAlign: 'center',
    marginBottom: SPACING.md,
  },
  devGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: SPACING.sm,
  },
  devBtn: {
    width: '31%',
    backgroundColor: COLORS.surface,
    borderRadius: RADIUS.sm,
    borderWidth: 1,
    borderColor: COLORS.border,
    paddingVertical: SPACING.md,
    alignItems: 'center',
    gap: 4,
  },
  devBtnLabel: {
    fontSize: FONT_SIZE.xs,
    color: COLORS.textSecondary,
  },
});
