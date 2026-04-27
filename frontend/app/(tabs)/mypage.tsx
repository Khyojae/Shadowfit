import { useState, useCallback } from 'react';
import { View, Text, StyleSheet, ScrollView, TouchableOpacity, Alert } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useRouter, useFocusEffect } from 'expo-router';
import FontAwesome from '@expo/vector-icons/FontAwesome';
import { COLORS, FONT_SIZE, SPACING, RADIUS } from '@/constants/Colors';
import { useAuthStore } from '@/stores/authStore';
import { memberService } from '@/services/memberService';
import type { OnboardingResponse } from '@/types/user';

// 백엔드 WorkoutLevel: STARTER < BEGINNER < INTERMEDIATE < ADVANCED < EXPERT
const LEVEL_MAP: Record<string, { label: string; desc: string }> = {
  STARTER: { label: '입문', desc: '운동 자세, 운동 루틴 등 아무것도 몰라요' },
  BEGINNER: { label: '초급', desc: '자세는 조금 알지만 무슨 운동을 해야 할지 몰라요' },
  INTERMEDIATE: { label: '중급', desc: '운동 자세를 잘 알고, 나만의 루틴이 있어요' },
  ADVANCED: { label: '고급', desc: '운동을 직업으로 삼을 만큼의 지식이 있어요' },
  EXPERT: { label: '전문가', desc: '운동 선수급의 지식과 경험을 갖고 있어요' },
};

// 백엔드 SelectedPersona: BEGINNER, ADVANCED, DIET, REHAB
const PERSONA_MAP: Record<string, { icon: string; label: string; desc: string }> = {
  BEGINNER: { icon: '🐣', label: '헬린이', desc: '친절하고 격려하는 초보 친화 코치' },
  ADVANCED: { icon: '🫡', label: 'FM 교관', desc: '엄격하고 체계적인 군대식 트레이너' },
  DIET: { icon: '🥗', label: '다이어터', desc: '체중 관리에 특화된 식단·운동 코치' },
  REHAB: { icon: '🏥', label: '재활 전문', desc: '안전 최우선, 부상 방지 중심 가이드' },
};

export default function MyPageScreen() {
  const router = useRouter();
  const logout = useAuthStore((s) => s.logout);
  const user = useAuthStore((s) => s.user);

  const [profile, setProfile] = useState<OnboardingResponse | null>(null);

  // 화면 진입 / 재포커스 마다 최신 데이터 조회 (수정 후 돌아올 때도 갱신)
  useFocusEffect(
    useCallback(() => {
      if (!user?.email) return;
      memberService
        .getOnboarding(user.email)
        .then((res) => setProfile(res.data))
        .catch(() => {
          // 조회 실패 시 기존 표시 유지
        });
    }, [user?.email]),
  );

  const level = profile?.workoutLevel ? LEVEL_MAP[profile.workoutLevel] : null;
  const persona = profile?.selectedPersona ? PERSONA_MAP[profile.selectedPersona] : null;

  const handleLogout = () => {
    Alert.alert('로그아웃', '정말 로그아웃하시겠습니까?', [
      { text: '취소', style: 'cancel' },
      {
        text: '로그아웃',
        style: 'destructive',
        onPress: async () => {
          await logout();
          router.replace('/(auth)/login');
        },
      },
    ]);
  };

  return (
    <SafeAreaView style={styles.container}>
      <ScrollView showsVerticalScrollIndicator={false}>
        {/* 헤더 */}
        <View style={styles.header}>
          <TouchableOpacity onPress={() => router.back()}>
            <FontAwesome name="chevron-left" size={16} color={COLORS.text} />
          </TouchableOpacity>
          <Text style={styles.title}>마이페이지</Text>
          <View style={{ width: 16 }} />
        </View>

        {/* 프로필 카드 */}
        <View style={styles.profileCard}>
          <View style={styles.avatar}>
            <Text style={styles.avatarIcon}>💪</Text>
          </View>
          <View>
            <Text style={styles.email}>{profile?.username || user?.email || ''}</Text>
            <Text style={styles.memberLabel}>{user?.email || 'ShadowFit 회원'}</Text>
          </View>
        </View>

        {/* 페르소나 설정 */}
        <View style={styles.sectionHeader}>
          <Text style={styles.sectionTitle}>페르소나 설정</Text>
          <TouchableOpacity onPress={() => router.push('/(onboarding)' as any)}>
            <Text style={styles.editBtn}>✏️ 수정</Text>
          </TouchableOpacity>
        </View>

        <InfoCard
          icon="💪"
          label="운동 수준"
          title={level?.label ?? '미설정'}
          desc={level?.desc}
        />
        <InfoCard
          icon="📏"
          label="키"
          title={profile?.height != null ? `${profile.height}cm` : '미설정'}
        />
        <InfoCard
          icon="🎯"
          label="목표 몸무게"
          title={profile?.weight != null ? `${profile.weight}kg` : '미설정'}
        />
        <InfoCard
          icon={persona?.icon ?? '🙂'}
          label="트레이너 페르소나"
          title={persona ? `${persona.icon} ${persona.label}` : '미설정'}
          desc={persona?.desc}
        />
        <InfoCard
          icon="📹"
          label="기준 영상"
          title={profile?.preferredUrl || '미설정'}
        />

        {/* 게시판 */}
        <View style={styles.sectionHeader}>
          <Text style={styles.sectionTitle}>게시판</Text>
          <View />
        </View>

        <TouchableOpacity
          style={styles.menuItem}
          activeOpacity={0.7}
          onPress={() => router.push('/board/notice' as any)}
        >
          <View style={styles.menuLeft}>
            <FontAwesome name="bullhorn" size={16} color={COLORS.primary} />
            <Text style={styles.menuText}>공지사항</Text>
          </View>
          <FontAwesome name="chevron-right" size={12} color={COLORS.textMuted} />
        </TouchableOpacity>

        <TouchableOpacity
          style={styles.menuItem}
          activeOpacity={0.7}
          onPress={() => router.push('/board/qna' as any)}
        >
          <View style={styles.menuLeft}>
            <FontAwesome name="question-circle" size={16} color={COLORS.primary} />
            <Text style={styles.menuText}>Q&A</Text>
          </View>
          <FontAwesome name="chevron-right" size={12} color={COLORS.textMuted} />
        </TouchableOpacity>

        {/* 로그아웃 */}
        <TouchableOpacity style={styles.logoutBtn} onPress={handleLogout} activeOpacity={0.7}>
          <FontAwesome name="sign-out" size={16} color={COLORS.textSecondary} />
          <Text style={styles.logoutText}>로그아웃</Text>
        </TouchableOpacity>

        <View style={{ height: 100 }} />
      </ScrollView>
    </SafeAreaView>
  );
}

function InfoCard({ icon, label, title, desc }: {
  icon: string;
  label: string;
  title: string;
  desc?: string;
}) {
  return (
    <View style={styles.infoCard}>
      <Text style={styles.infoIcon}>{icon}</Text>
      <Text style={styles.infoLabel}>{label}</Text>
      <Text style={styles.infoTitle}>{title}</Text>
      {desc && <Text style={styles.infoDesc}>{desc}</Text>}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: COLORS.background },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: SPACING.xxl,
    paddingVertical: SPACING.md,
  },
  title: { fontSize: FONT_SIZE.lg, fontWeight: '800', color: COLORS.text },

  profileCard: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: SPACING.lg,
    backgroundColor: COLORS.card,
    marginHorizontal: SPACING.xxl,
    marginTop: SPACING.md,
    padding: SPACING.xl,
    borderRadius: RADIUS.lg,
    borderWidth: 1,
    borderColor: COLORS.cardBorder,
  },
  avatar: {
    width: 56,
    height: 56,
    borderRadius: 28,
    backgroundColor: COLORS.primaryDim,
    justifyContent: 'center',
    alignItems: 'center',
  },
  avatarIcon: { fontSize: 24 },
  email: { fontSize: FONT_SIZE.md, fontWeight: '700', color: COLORS.text },
  memberLabel: { fontSize: FONT_SIZE.sm, color: COLORS.textSecondary, marginTop: 2 },

  sectionHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: SPACING.xxl,
    marginTop: SPACING.xxl,
    marginBottom: SPACING.md,
  },
  sectionTitle: { fontSize: FONT_SIZE.md, fontWeight: '700', color: COLORS.textSecondary },
  editBtn: { fontSize: FONT_SIZE.sm, color: COLORS.primary },

  infoCard: {
    backgroundColor: COLORS.card,
    marginHorizontal: SPACING.xxl,
    marginBottom: SPACING.md,
    padding: SPACING.lg,
    borderRadius: RADIUS.md,
    borderWidth: 1,
    borderColor: COLORS.cardBorder,
  },
  infoIcon: { fontSize: 14, marginBottom: 4 },
  infoLabel: { fontSize: FONT_SIZE.xs, color: COLORS.textMuted, marginBottom: 4 },
  infoTitle: { fontSize: FONT_SIZE.md, fontWeight: '700', color: COLORS.text },
  infoDesc: { fontSize: FONT_SIZE.sm, color: COLORS.textSecondary, marginTop: 2 },

  menuItem: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    backgroundColor: COLORS.card,
    marginHorizontal: SPACING.xxl,
    marginBottom: SPACING.md,
    padding: SPACING.lg,
    borderRadius: RADIUS.md,
    borderWidth: 1,
    borderColor: COLORS.cardBorder,
  },
  menuLeft: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: SPACING.md,
  },
  menuText: {
    fontSize: FONT_SIZE.md,
    fontWeight: '600',
    color: COLORS.text,
  },

  logoutBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: SPACING.sm,
    backgroundColor: COLORS.card,
    marginHorizontal: SPACING.xxl,
    marginTop: SPACING.xl,
    paddingVertical: SPACING.lg,
    borderRadius: RADIUS.md,
    borderWidth: 1,
    borderColor: COLORS.cardBorder,
  },
  logoutText: { fontSize: FONT_SIZE.md, color: COLORS.textSecondary },
});
