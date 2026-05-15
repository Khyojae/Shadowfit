import { useState } from 'react';
import {
  View,
  Text,
  StyleSheet,
  KeyboardAvoidingView,
  Platform,
  Alert,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useRouter } from 'expo-router';
import { ChevronLeft, Mail, Key, Lock } from 'lucide-react-native';
import { COLORS, FONT_SIZE, SPACING, RADIUS } from '@/constants/Colors';
import Input from '@/components/ui/Input';
import Button from '@/components/ui/Button';
import { TouchableOpacity } from 'react-native';

type Step = 'email' | 'code' | 'newPassword';

export default function ResetPasswordScreen() {
  const router = useRouter();
  const [step, setStep] = useState<Step>('email');
  const [email, setEmail] = useState('');
  const [code, setCode] = useState('');
  const [password, setPassword] = useState('');
  const [passwordConfirm, setPasswordConfirm] = useState('');
  const [loading, setLoading] = useState(false);
  const [errors, setErrors] = useState<Record<string, string>>({});

  const handleSendCode = async () => {
    if (!email.trim()) {
      setErrors({ email: '이메일을 입력해주세요' });
      return;
    }
    if (!/\S+@\S+\.\S+/.test(email)) {
      setErrors({ email: '올바른 이메일 형식이 아닙니다' });
      return;
    }
    setErrors({});
    setLoading(true);
    try {
      // TODO: API 호출 - 인증 코드 발송
      await new Promise((r) => setTimeout(r, 1000));
      Alert.alert('인증 코드 발송', '입력하신 이메일로 인증 코드를 보냈습니다.');
      setStep('code');
    } catch {
      Alert.alert('오류', '인증 코드 발송에 실패했습니다.');
    } finally {
      setLoading(false);
    }
  };

  const handleVerifyCode = async () => {
    if (!code.trim()) {
      setErrors({ code: '인증 코드를 입력해주세요' });
      return;
    }
    if (code.length < 6) {
      setErrors({ code: '6자리 코드를 입력해주세요' });
      return;
    }
    setErrors({});
    setLoading(true);
    try {
      // TODO: API 호출 - 인증 코드 확인
      await new Promise((r) => setTimeout(r, 1000));
      setStep('newPassword');
    } catch {
      Alert.alert('오류', '인증 코드가 올바르지 않습니다.');
    } finally {
      setLoading(false);
    }
  };

  const handleResetPassword = async () => {
    const newErrors: Record<string, string> = {};
    if (!password) newErrors.password = '새 비밀번호를 입력해주세요';
    else if (password.length < 6) newErrors.password = '6자 이상 입력해주세요';
    if (password !== passwordConfirm)
      newErrors.passwordConfirm = '비밀번호가 일치하지 않습니다';
    if (Object.keys(newErrors).length > 0) {
      setErrors(newErrors);
      return;
    }
    setErrors({});
    setLoading(true);
    try {
      // TODO: API 호출 - 비밀번호 변경
      await new Promise((r) => setTimeout(r, 1000));
      Alert.alert('비밀번호 변경 완료', '새 비밀번호로 로그인해주세요.', [
        { text: '확인', onPress: () => router.back() },
      ]);
    } catch {
      Alert.alert('오류', '비밀번호 변경에 실패했습니다.');
    } finally {
      setLoading(false);
    }
  };

  const stepConfig = {
    email: {
      icon: '📧',
      title: '비밀번호 재설정',
      desc: '가입 시 사용한 이메일을 입력해주세요.\n인증 코드를 보내드립니다.',
    },
    code: {
      icon: '🔑',
      title: '인증 코드 입력',
      desc: `${email}으로 보낸\n6자리 인증 코드를 입력해주세요.`,
    },
    newPassword: {
      icon: '🔒',
      title: '새 비밀번호 설정',
      desc: '새로운 비밀번호를 입력해주세요.',
    },
  };

  const current = stepConfig[step];

  return (
    <SafeAreaView style={styles.container}>
      <KeyboardAvoidingView
        style={styles.flex}
        behavior={Platform.OS === 'ios' ? 'padding' : undefined}
      >
        {/* 헤더 */}
        <View style={styles.header}>
          <TouchableOpacity
            onPress={() => {
              if (step === 'email') router.back();
              else if (step === 'code') setStep('email');
              else setStep('code');
            }}
          >
            <ChevronLeft size={20} color={COLORS.text} strokeWidth={2} />
          </TouchableOpacity>
          <Text style={styles.headerTitle}>비밀번호 재설정</Text>
          <View style={{ width: 16 }} />
        </View>

        {/* 단계 표시 */}
        <View style={styles.stepIndicator}>
          {(['email', 'code', 'newPassword'] as Step[]).map((s, i) => (
            <View key={s} style={styles.stepRow}>
              <View
                style={[
                  styles.stepDot,
                  (step === s || i < ['email', 'code', 'newPassword'].indexOf(step)) &&
                    styles.stepDotActive,
                ]}
              >
                <Text style={styles.stepDotText}>{i + 1}</Text>
              </View>
              {i < 2 && (
                <View
                  style={[
                    styles.stepLine,
                    i < ['email', 'code', 'newPassword'].indexOf(step) &&
                      styles.stepLineActive,
                  ]}
                />
              )}
            </View>
          ))}
        </View>

        <View style={styles.content}>
          <Text style={styles.icon}>{current.icon}</Text>
          <Text style={styles.title}>{current.title}</Text>
          <Text style={styles.desc}>{current.desc}</Text>

          {step === 'email' && (
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
                containerStyle={styles.inputGap}
              />
              <Button
                title="인증 코드 발송"
                onPress={handleSendCode}
                loading={loading}
                style={styles.btn}
              />
            </>
          )}

          {step === 'code' && (
            <>
              <Input
                label="인증 코드"
                icon={Key}
                placeholder="6자리 숫자 입력"
                value={code}
                onChangeText={setCode}
                error={errors.code}
                keyboardType="number-pad"
                maxLength={6}
                containerStyle={styles.inputGap}
              />
              <Button
                title="확인"
                onPress={handleVerifyCode}
                loading={loading}
                style={styles.btn}
              />
              <TouchableOpacity onPress={handleSendCode} style={styles.resendBtn}>
                <Text style={styles.resendText}>코드를 받지 못하셨나요? 재발송</Text>
              </TouchableOpacity>
            </>
          )}

          {step === 'newPassword' && (
            <>
              <Input
                label="새 비밀번호"
                icon={Lock}
                placeholder="6자 이상 입력"
                value={password}
                onChangeText={setPassword}
                error={errors.password}
                isPassword
                containerStyle={styles.inputGap}
              />
              <Input
                label="새 비밀번호 확인"
                icon={Lock}
                placeholder="비밀번호를 다시 입력"
                value={passwordConfirm}
                onChangeText={setPasswordConfirm}
                error={errors.passwordConfirm}
                isPassword
                containerStyle={styles.inputGap}
              />
              <Button
                title="비밀번호 변경"
                onPress={handleResetPassword}
                loading={loading}
                style={styles.btn}
              />
            </>
          )}
        </View>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: COLORS.background },
  flex: { flex: 1 },

  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: SPACING.xxl,
    paddingVertical: SPACING.md,
  },
  headerTitle: {
    fontSize: FONT_SIZE.lg,
    fontWeight: '800',
    color: COLORS.text,
  },

  stepIndicator: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: SPACING.xxxl,
    paddingVertical: SPACING.lg,
  },
  stepRow: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  stepDot: {
    width: 28,
    height: 28,
    borderRadius: 14,
    backgroundColor: COLORS.surfaceLight,
    justifyContent: 'center',
    alignItems: 'center',
  },
  stepDotActive: {
    backgroundColor: COLORS.primary,
  },
  stepDotText: {
    fontSize: FONT_SIZE.xs,
    fontWeight: '700',
    color: COLORS.text,
  },
  stepLine: {
    width: 40,
    height: 2,
    backgroundColor: COLORS.surfaceLight,
    marginHorizontal: SPACING.xs,
  },
  stepLineActive: {
    backgroundColor: COLORS.primary,
  },

  content: {
    paddingHorizontal: SPACING.xxl,
    paddingTop: SPACING.xl,
  },
  icon: {
    fontSize: 36,
    marginBottom: SPACING.md,
  },
  title: {
    fontSize: FONT_SIZE.xl,
    fontWeight: '800',
    color: COLORS.text,
    marginBottom: SPACING.sm,
  },
  desc: {
    fontSize: FONT_SIZE.sm,
    color: COLORS.textSecondary,
    lineHeight: 20,
    marginBottom: SPACING.xxl,
  },
  inputGap: {
    marginBottom: SPACING.lg,
  },
  btn: {
    marginTop: SPACING.md,
  },
  resendBtn: {
    alignSelf: 'center',
    marginTop: SPACING.xl,
    paddingVertical: SPACING.sm,
  },
  resendText: {
    fontSize: FONT_SIZE.sm,
    color: COLORS.textSecondary,
  },
});
