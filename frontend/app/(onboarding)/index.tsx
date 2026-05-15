import { useEffect, useState } from 'react';
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  TouchableOpacity,
  Image,
  Alert,
} from 'react-native';
import { SafeAreaView, useSafeAreaInsets } from 'react-native-safe-area-context';
import Slider from '@react-native-community/slider';
import { useRouter } from 'expo-router';
import {
  ChevronLeft,
  ChevronRight,
  ChevronsLeft,
  ChevronsRight,
  ChevronsUp,
  Check,
  Dumbbell,
  Ruler,
  Target,
  UserCog,
  Video,
  Sprout,
  Crosshair,
  Apple,
  HeartPulse,
  Footprints,
  type LucideIcon,
} from 'lucide-react-native';
import { COLORS, FONT_SIZE, SPACING, RADIUS } from '@/constants/Colors';
import Button from '@/components/ui/Button';
import type {
  WorkoutLevel,
  SelectedPersona,
  OnboardingData,
} from '@/types/user';
import { memberService } from '@/services/memberService';
import { useAuthStore } from '@/stores/authStore';

const TOTAL_STEPS = 5;

// ─── 기준 영상 데이터 ────────────────────────────────
interface ReferenceVideo {
  id: string;
  title: string;
  youtubeId: string;
  thumbnail: string;
}

interface ExerciseCategory {
  key: string;
  Icon: LucideIcon;
  label: string;
  videos: ReferenceVideo[];
}

const EXERCISE_VIDEOS: ExerciseCategory[] = [
  {
    key: 'squat',
    Icon: Footprints,
    label: '스쿼트',
    videos: [
      {
        id: 'sq1',
        title: '기본 바벨 스쿼트',
        youtubeId: 'ultWZbUMPL8',
        thumbnail: 'https://img.youtube.com/vi/ultWZbUMPL8/mqdefault.jpg',
      },
      {
        id: 'sq2',
        title: '프론트 스쿼트 자세',
        youtubeId: 'tlfahNdNPPI',
        thumbnail: 'https://img.youtube.com/vi/tlfahNdNPPI/mqdefault.jpg',
      },
      {
        id: 'sq3',
        title: '고블릿 스쿼트',
        youtubeId: 'MeIiIdhvXT4',
        thumbnail: 'https://img.youtube.com/vi/MeIiIdhvXT4/mqdefault.jpg',
      },
      {
        id: 'sq4',
        title: '불가리안 스플릿 스쿼트',
        youtubeId: '2C-uNgKwPLE',
        thumbnail: 'https://img.youtube.com/vi/2C-uNgKwPLE/mqdefault.jpg',
      },
    ],
  },
  {
    key: 'deadlift',
    Icon: Dumbbell,
    label: '데드리프트',
    videos: [
      {
        id: 'dl1',
        title: '컨벤셔널 데드리프트',
        youtubeId: 'op9kVnSso6Q',
        thumbnail: 'https://img.youtube.com/vi/op9kVnSso6Q/mqdefault.jpg',
      },
      {
        id: 'dl2',
        title: '루마니안 데드리프트',
        youtubeId: 'jEy_czb3RKA',
        thumbnail: 'https://img.youtube.com/vi/jEy_czb3RKA/mqdefault.jpg',
      },
      {
        id: 'dl3',
        title: '스모 데드리프트',
        youtubeId: 'widGEAjS_Fs',
        thumbnail: 'https://img.youtube.com/vi/widGEAjS_Fs/mqdefault.jpg',
      },
      {
        id: 'dl4',
        title: '트랩바 데드리프트',
        youtubeId: 'dLhNzjR_kOc',
        thumbnail: 'https://img.youtube.com/vi/dLhNzjR_kOc/mqdefault.jpg',
      },
    ],
  },
  {
    key: 'pullup',
    Icon: ChevronsUp,
    label: '턱걸이',
    videos: [
      {
        id: 'pu1',
        title: '기본 풀업 자세',
        youtubeId: 'eGo4IYlbE5g',
        thumbnail: 'https://img.youtube.com/vi/eGo4IYlbE5g/mqdefault.jpg',
      },
      {
        id: 'pu2',
        title: '친업 (언더그립)',
        youtubeId: 'brhRXlOhWMg',
        thumbnail: 'https://img.youtube.com/vi/brhRXlOhWMg/mqdefault.jpg',
      },
      {
        id: 'pu3',
        title: '네거티브 풀업',
        youtubeId: 'S3gKOkCBjXk',
        thumbnail: 'https://img.youtube.com/vi/S3gKOkCBjXk/mqdefault.jpg',
      },
      {
        id: 'pu4',
        title: '와이드 그립 풀업',
        youtubeId: 'CnEP2VhSaN0',
        thumbnail: 'https://img.youtube.com/vi/CnEP2VhSaN0/mqdefault.jpg',
      },
    ],
  },
];

export default function OnboardingScreen() {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const userEmail = useAuthStore((s) => s.user?.email);
  const markOnboardingCompleted = useAuthStore((s) => s.markOnboardingCompleted);
  const [step, setStep] = useState(0);
  const [submitting, setSubmitting] = useState(false);
  // 백엔드 OnboardingRequestDto 와 키 이름 일치
  const [data, setData] = useState<OnboardingData>({
    workoutLevel: null,
    height: 170,
    weight: 70,
    persona: null,
    preferredUrl: null,
  });

  // 마이페이지에서 수정 진입한 경우 기존 데이터로 초기값 채움
  useEffect(() => {
    if (!userEmail) return;
    memberService
      .getOnboarding(userEmail)
      .then((res) => {
        const existing = res.data;
        setData({
          workoutLevel: existing.workoutLevel ?? null,
          height: existing.height ?? 170,
          weight: existing.weight ?? 70,
          persona: existing.selectedPersona ?? null,
          preferredUrl: existing.preferredUrl ?? null,
        });
      })
      .catch(() => {
        // 신규 가입자는 일부 필드가 null 일 수 있음 → 기본값 유지
      });
  }, [userEmail]);

  const canNext = () => {
    switch (step) {
      case 0: return data.workoutLevel !== null;
      case 1: return true; // 키 (슬라이더, 항상 값 있음)
      case 2: return true; // 몸무게 (슬라이더, 항상 값 있음)
      case 3: return data.persona !== null;
      case 4: return data.preferredUrl !== null;
      default: return false;
    }
  };

  const handleNext = async () => {
    if (step < TOTAL_STEPS - 1) {
      setStep(step + 1);
      return;
    }

    // 마지막 step → 백엔드에 PATCH
    if (!userEmail) {
      Alert.alert('오류', '로그인 정보가 없습니다. 다시 로그인해주세요.');
      router.replace('/(auth)/login');
      return;
    }
    if (!data.workoutLevel || !data.persona || !data.preferredUrl) {
      Alert.alert('오류', '모든 항목을 선택해주세요.');
      return;
    }

    setSubmitting(true);
    try {
      await memberService.updateOnboarding(userEmail, {
        selectedPersona: data.persona,
        workoutLevel: data.workoutLevel,
        height: data.height,
        weight: data.weight,
        preferredUrl: data.preferredUrl,
      });
      markOnboardingCompleted(); // 가드가 (onboarding) 으로 다시 안 빠지게
      router.replace('/(tabs)');
    } catch (e: any) {
      console.error('[onboarding patch] status=', e.response?.status, 'data=', e.response?.data);
      const msg = e.response?.data?.message || '온보딩 저장에 실패했습니다';
      Alert.alert(`온보딩 실패 (${e.response?.status ?? 'no-response'})`, msg);
    } finally {
      setSubmitting(false);
    }
  };

  const handleBack = () => {
    if (step > 0) setStep(step - 1);
  };

  return (
    <SafeAreaView style={styles.container}>
      {/* 상단 프로그레스 바 + 뒤로가기 */}
      <View style={styles.header}>
        {step > 0 ? (
          <TouchableOpacity onPress={handleBack} style={styles.backBtn}>
            <ChevronLeft size={20} color={COLORS.text} strokeWidth={2} />
          </TouchableOpacity>
        ) : (
          <View style={styles.backBtn} />
        )}
        <View style={styles.progressBar}>
          {Array.from({ length: TOTAL_STEPS }).map((_, i) => (
            <View
              key={i}
              style={[styles.progressDot, i <= step && styles.progressDotActive]}
            />
          ))}
        </View>
        <Text style={styles.stepText}>{step + 1}/{TOTAL_STEPS}</Text>
      </View>

      <ScrollView
        contentContainerStyle={styles.scrollContent}
        showsVerticalScrollIndicator={false}
      >
        {step === 0 && <StepLevel data={data} setData={setData} />}
        {step === 1 && <StepHeight data={data} setData={setData} />}
        {step === 2 && <StepWeight data={data} setData={setData} />}
        {step === 3 && <StepPersona data={data} setData={setData} />}
        {step === 4 && <StepVideo data={data} setData={setData} />}
      </ScrollView>

      {/* 하단 버튼 */}
      <View style={[styles.footer, { paddingBottom: SPACING.lg + insets.bottom }]}>
        <Button
          title={step === TOTAL_STEPS - 1 ? '완료' : '다음  >'}
          onPress={handleNext}
          disabled={!canNext() || submitting}
          loading={submitting}
        />
      </View>
    </SafeAreaView>
  );
}

// ─── Step 1: 운동 수준 ──────────────────────────────
// 백엔드 WorkoutLevel: STARTER < BEGINNER < INTERMEDIATE < ADVANCED < EXPERT
const LEVELS: { key: WorkoutLevel; label: string; desc: string }[] = [
  { key: 'STARTER', label: '입문', desc: '운동 자세, 운동 루틴 등 아무것도 몰라요' },
  { key: 'BEGINNER', label: '초급', desc: '자세는 조금 알지만 무슨 운동을 해야 할지 몰라요' },
  { key: 'INTERMEDIATE', label: '중급', desc: '운동 자세를 잘 알고, 나만의 루틴이 있어요' },
  { key: 'ADVANCED', label: '고급', desc: '운동을 직업으로 삼을 만큼의 지식이 있어요' },
  { key: 'EXPERT', label: '전문가', desc: '운동 선수급의 지식과 경험을 갖고 있어요' },
];

function StepLevel({
  data,
  setData,
}: {
  data: OnboardingData;
  setData: React.Dispatch<React.SetStateAction<OnboardingData>>;
}) {
  return (
    <View>
      <View style={styles.stepIconWrap}>
        <Dumbbell size={28} color={COLORS.primary} strokeWidth={2} />
      </View>
      <Text style={styles.stepTitle}>운동 수준이 어떻게 되시나요?</Text>
      <Text style={styles.stepDesc}>적절한 운동 추천에 필요해요! 외부에 공개되지 않아요.</Text>

      {LEVELS.map((level) => (
        <TouchableOpacity
          key={level.key}
          style={[
            styles.optionCard,
            data.workoutLevel === level.key && styles.optionCardActive,
          ]}
          onPress={() => setData({ ...data, workoutLevel: level.key })}
          activeOpacity={0.7}
        >
          <Text style={[styles.optionLabel, data.workoutLevel === level.key && styles.optionLabelActive]}>
            {level.label}
          </Text>
          <Text style={styles.optionDesc}>{level.desc}</Text>
        </TouchableOpacity>
      ))}
    </View>
  );
}

// ─── Step 1: 키 ─────────────────────────────
// 백엔드 OnboardingRequestDto.height 와 매핑
function StepHeight({
  data,
  setData,
}: {
  data: OnboardingData;
  setData: React.Dispatch<React.SetStateAction<OnboardingData>>;
}) {
  return (
    <View>
      <View style={styles.stepIconWrap}>
        <Ruler size={28} color={COLORS.primary} strokeWidth={2} />
      </View>
      <Text style={styles.stepTitle}>키가 어떻게 되시나요?</Text>
      <Text style={styles.stepDesc}>
        정확한 운동 강도와 칼로리 계산에 사용돼요. 외부에 공개되지 않아요.
      </Text>

      <Text style={styles.weightDisplay}>
        {data.height} <Text style={styles.weightUnit}>cm</Text>
      </Text>

      <Slider
        style={styles.slider}
        minimumValue={100}
        maximumValue={220}
        step={1}
        value={data.height}
        onValueChange={(v) => setData({ ...data, height: v })}
        minimumTrackTintColor={COLORS.primary}
        maximumTrackTintColor={COLORS.surfaceLight}
        thumbTintColor={COLORS.primary}
      />
      <View style={styles.sliderLabels}>
        <Text style={styles.sliderLabel}>100cm</Text>
        <Text style={styles.sliderLabel}>220cm</Text>
      </View>
    </View>
  );
}

// ─── Step 2: 몸무게 ─────────────────────────────
// 백엔드 OnboardingRequestDto.weight 와 매핑
function StepWeight({
  data,
  setData,
}: {
  data: OnboardingData;
  setData: React.Dispatch<React.SetStateAction<OnboardingData>>;
}) {
  const getMotivation = (weight: number) => {
    if (weight < 50) return '가벼운 몸을 목표로! 유산소 중심으로 도와드릴게요.';
    if (weight < 70) return '도전적인 목표: 몸무게의 70%로 중량이 목표예요!';
    if (weight < 90) return '탄탄한 체격을 목표로! 근력 운동을 추천해요.';
    return '파워 빌더! 고중량 훈련 루틴을 구성해드릴게요.';
  };

  return (
    <View>
      <View style={styles.stepIconWrap}>
        <Target size={28} color={COLORS.primary} strokeWidth={2} />
      </View>
      <Text style={styles.stepTitle}>목표 몸무게가 어떻게 되시나요?</Text>

      <View style={styles.motivationBox}>
        <Text style={styles.motivationIcon}>🏆</Text>
        <Text style={styles.motivationText}>{getMotivation(data.weight)}</Text>
      </View>

      <Text style={styles.weightDisplay}>
        {data.weight} <Text style={styles.weightUnit}>kg</Text>
      </Text>

      <Slider
        style={styles.slider}
        minimumValue={30}
        maximumValue={150}
        step={1}
        value={data.weight}
        onValueChange={(v) => setData({ ...data, weight: v })}
        minimumTrackTintColor={COLORS.primary}
        maximumTrackTintColor={COLORS.surfaceLight}
        thumbTintColor={COLORS.primary}
      />
      <View style={styles.sliderLabels}>
        <Text style={styles.sliderLabel}>30kg</Text>
        <Text style={styles.sliderLabel}>150kg</Text>
      </View>
    </View>
  );
}

// ─── Step 3: 트레이너 페르소나 ───────────────────────
// 백엔드 SelectedPersona: BEGINNER, ADVANCED, DIET, REHAB
const PERSONAS: { key: SelectedPersona; Icon: LucideIcon; label: string; desc: string }[] = [
  { key: 'BEGINNER', Icon: Sprout, label: '헬린이', desc: '친절하고 격려하는 초보 친화 코치' },
  { key: 'ADVANCED', Icon: Crosshair, label: 'FM 교관', desc: '엄격하고 체계적인 군대식 트레이너' },
  { key: 'DIET', Icon: Apple, label: '다이어터', desc: '체중 관리에 특화된 식단·운동 코치' },
  { key: 'REHAB', Icon: HeartPulse, label: '재활 전문', desc: '안전 최우선, 부상 방지 중심 가이드' },
];

function StepPersona({
  data,
  setData,
}: {
  data: OnboardingData;
  setData: React.Dispatch<React.SetStateAction<OnboardingData>>;
}) {
  return (
    <View>
      <View style={styles.stepIconWrap}>
        <UserCog size={28} color={COLORS.primary} strokeWidth={2} />
      </View>
      <Text style={styles.stepTitle}>어떤 트레이너 스타일을 원하시나요?</Text>
      <Text style={styles.stepDesc}>페르소나에 따라 피드백 방식이 달라집니다.</Text>

      {PERSONAS.map((p) => {
        const isActive = data.persona === p.key;
        return (
          <TouchableOpacity
            key={p.key}
            style={[styles.optionCard, isActive && styles.optionCardActive]}
            onPress={() => setData({ ...data, persona: p.key })}
            activeOpacity={0.7}
          >
            <View style={styles.personaRow}>
              <View style={styles.personaIconWrap}>
                <p.Icon
                  size={28}
                  color={isActive ? COLORS.primary : COLORS.textSecondary}
                  strokeWidth={2}
                />
              </View>
              <View>
                <Text style={[styles.optionLabel, isActive && styles.optionLabelActive]}>
                  {p.label}
                </Text>
                <Text style={styles.optionDesc}>{p.desc}</Text>
              </View>
            </View>
          </TouchableOpacity>
        );
      })}
    </View>
  );
}

// ─── Step 4: 기준 영상 선택 ─────────────────────────
const VIDEOS_PER_PAGE = 4;

function StepVideo({
  data,
  setData,
}: {
  data: OnboardingData;
  setData: React.Dispatch<React.SetStateAction<OnboardingData>>;
}) {
  const [activeKey, setActiveKey] = useState(EXERCISE_VIDEOS[0].key);
  const [page, setPage] = useState(0);

  const activeCategory =
    EXERCISE_VIDEOS.find((c) => c.key === activeKey) ?? EXERCISE_VIDEOS[0];
  const totalPages = Math.max(
    1,
    Math.ceil(activeCategory.videos.length / VIDEOS_PER_PAGE),
  );
  const pageVideos = activeCategory.videos.slice(
    page * VIDEOS_PER_PAGE,
    (page + 1) * VIDEOS_PER_PAGE,
  );

  const handleCategoryChange = (key: string) => {
    setActiveKey(key);
    setPage(0);
  };

  return (
    <View>
      <View style={styles.stepIconWrap}>
        <Video size={28} color={COLORS.primary} strokeWidth={2} />
      </View>
      <Text style={styles.stepTitle}>기준 영상을 선택해주세요</Text>
      <Text style={styles.stepDesc}>
        따라 할 운동 영상을 하나 골라주세요. 나중에 변경할 수 있어요.
      </Text>

      {/* 카테고리 탭 */}
      <ScrollView
        horizontal
        showsHorizontalScrollIndicator={false}
        contentContainerStyle={styles.categoryTabs}
      >
        {EXERCISE_VIDEOS.map((category) => {
          const isActive = category.key === activeKey;
          const TabIcon = category.Icon;
          return (
            <TouchableOpacity
              key={category.key}
              style={[styles.categoryTab, isActive && styles.categoryTabActive]}
              onPress={() => handleCategoryChange(category.key)}
              activeOpacity={0.7}
            >
              <TabIcon
                size={16}
                color={isActive ? COLORS.primary : COLORS.textSecondary}
                strokeWidth={2}
              />
              <Text
                style={[
                  styles.categoryTabText,
                  isActive && styles.categoryTabTextActive,
                ]}
              >
                {category.label}
              </Text>
            </TouchableOpacity>
          );
        })}
      </ScrollView>

      {/* 선택된 카테고리 영상 그리드 */}
      <View style={styles.videoGrid}>
        {pageVideos.map((video) => {
          const videoUrl = `https://www.youtube.com/watch?v=${video.youtubeId}`;
          const isSelected = data.preferredUrl === videoUrl;
          return (
            <TouchableOpacity
              key={video.id}
              style={[styles.videoCard, isSelected && styles.videoCardActive]}
              onPress={() =>
                setData({ ...data, preferredUrl: videoUrl })
              }
              activeOpacity={0.7}
            >
              <View style={styles.thumbnailWrap}>
                <Image
                  source={{ uri: video.thumbnail }}
                  style={styles.thumbnail}
                />
                {isSelected && (
                  <View style={styles.checkOverlay}>
                    <Check size={16} color={COLORS.primaryText} strokeWidth={3} />
                  </View>
                )}
              </View>
              <Text
                style={[
                  styles.videoTitle,
                  isSelected && styles.videoTitleActive,
                ]}
                numberOfLines={1}
              >
                {video.title}
              </Text>
            </TouchableOpacity>
          );
        })}
      </View>

      {/* 페이지네이션 */}
      <View style={styles.pagination}>
        <TouchableOpacity
          style={[styles.pageArrow, page === 0 && styles.pageArrowDisabled]}
          onPress={() => setPage(Math.max(0, page - 10))}
          disabled={page === 0}
        >
          <ChevronsLeft
            size={16}
            color={page === 0 ? COLORS.textMuted : COLORS.text}
            strokeWidth={2}
          />
        </TouchableOpacity>
        <TouchableOpacity
          style={[styles.pageArrow, page === 0 && styles.pageArrowDisabled]}
          onPress={() => page > 0 && setPage(page - 1)}
          disabled={page === 0}
        >
          <ChevronLeft
            size={14}
            color={page === 0 ? COLORS.textMuted : COLORS.text}
            strokeWidth={2}
          />
        </TouchableOpacity>

        <Text style={styles.pageIndicator}>
          {page + 1} / {totalPages}
        </Text>

        <TouchableOpacity
          style={[
            styles.pageArrow,
            page === totalPages - 1 && styles.pageArrowDisabled,
          ]}
          onPress={() => page < totalPages - 1 && setPage(page + 1)}
          disabled={page === totalPages - 1}
        >
          <ChevronRight
            size={14}
            color={page === totalPages - 1 ? COLORS.textMuted : COLORS.text}
            strokeWidth={2}
          />
        </TouchableOpacity>
        <TouchableOpacity
          style={[
            styles.pageArrow,
            page === totalPages - 1 && styles.pageArrowDisabled,
          ]}
          onPress={() => setPage(Math.min(totalPages - 1, page + 10))}
          disabled={page === totalPages - 1}
        >
          <ChevronsRight
            size={16}
            color={page === totalPages - 1 ? COLORS.textMuted : COLORS.text}
            strokeWidth={2}
          />
        </TouchableOpacity>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: COLORS.background },
  scrollContent: { flexGrow: 1, paddingHorizontal: SPACING.xxl, paddingBottom: 100 },

  // Header
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: SPACING.lg,
    paddingVertical: SPACING.md,
  },
  backBtn: { width: 32 },
  progressBar: { flex: 1, flexDirection: 'row', gap: 6, marginHorizontal: SPACING.md },
  progressDot: {
    flex: 1,
    height: 4,
    backgroundColor: COLORS.surfaceLight,
    borderRadius: 2,
  },
  progressDotActive: { backgroundColor: COLORS.primary },
  stepText: { color: COLORS.textSecondary, fontSize: FONT_SIZE.sm, width: 32, textAlign: 'right' },

  // Step common
  stepIcon: { fontSize: 28, marginTop: SPACING.xl, marginBottom: SPACING.md },
  stepIconWrap: { marginTop: SPACING.xl, marginBottom: SPACING.md },
  stepTitle: {
    fontSize: FONT_SIZE.xl,
    fontWeight: '800',
    color: COLORS.text,
    marginBottom: SPACING.sm,
  },
  stepDesc: {
    fontSize: FONT_SIZE.sm,
    color: COLORS.textSecondary,
    marginBottom: SPACING.xxl,
  },

  // Option cards
  optionCard: {
    backgroundColor: COLORS.surface,
    borderRadius: RADIUS.md,
    borderWidth: 1,
    borderColor: COLORS.cardBorder,
    padding: SPACING.lg,
    marginBottom: SPACING.md,
  },
  optionCardActive: {
    borderColor: COLORS.primary,
    backgroundColor: COLORS.primaryDim,
  },
  optionLabel: {
    fontSize: FONT_SIZE.md,
    fontWeight: '700',
    color: COLORS.text,
    marginBottom: 2,
  },
  optionLabelActive: { color: COLORS.primary },
  optionDesc: { fontSize: FONT_SIZE.sm, color: COLORS.textSecondary },

  // Persona
  personaRow: { flexDirection: 'row', alignItems: 'center', gap: SPACING.md },
  personaIconWrap: {
    width: 44,
    height: 44,
    borderRadius: 22,
    backgroundColor: COLORS.surfaceLight,
    justifyContent: 'center',
    alignItems: 'center',
  },

  // Weight
  motivationBox: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: COLORS.surface,
    borderRadius: RADIUS.md,
    padding: SPACING.lg,
    marginBottom: SPACING.xxl,
    gap: SPACING.md,
  },
  motivationIcon: { fontSize: 24 },
  motivationText: { flex: 1, fontSize: FONT_SIZE.sm, color: COLORS.textSecondary, lineHeight: 20 },
  weightDisplay: {
    fontSize: 56,
    fontWeight: '800',
    color: COLORS.text,
    textAlign: 'center',
    marginBottom: SPACING.lg,
  },
  weightUnit: { fontSize: FONT_SIZE.xl, fontWeight: '400', color: COLORS.textSecondary },
  slider: { width: '100%', height: 40 },
  sliderLabels: { flexDirection: 'row', justifyContent: 'space-between' },
  sliderLabel: { fontSize: FONT_SIZE.sm, color: COLORS.textMuted },

  // Video selection
  categoryTabs: {
    flexDirection: 'row',
    gap: SPACING.sm,
    paddingBottom: SPACING.lg,
  },
  categoryTab: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    paddingHorizontal: SPACING.lg,
    paddingVertical: SPACING.sm,
    borderRadius: RADIUS.full,
    borderWidth: 1,
    borderColor: COLORS.cardBorder,
    backgroundColor: COLORS.surface,
  },
  categoryTabActive: {
    borderColor: COLORS.primary,
    backgroundColor: COLORS.primaryDim,
  },
  categoryTabText: {
    fontSize: FONT_SIZE.sm,
    color: COLORS.textSecondary,
    fontWeight: '600',
  },
  categoryTabTextActive: {
    color: COLORS.primary,
    fontWeight: '800',
  },
  pagination: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: SPACING.lg,
    marginTop: SPACING.lg,
  },
  pageArrow: {
    width: 32,
    height: 32,
    borderRadius: 16,
    backgroundColor: COLORS.surface,
    borderWidth: 1,
    borderColor: COLORS.cardBorder,
    justifyContent: 'center',
    alignItems: 'center',
  },
  pageArrowDisabled: {
    opacity: 0.4,
  },
  pageIndicator: {
    fontSize: FONT_SIZE.sm,
    color: COLORS.text,
    fontWeight: '700',
    minWidth: 56,
    textAlign: 'center',
  },
  videoGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: SPACING.md,
  },
  videoCard: {
    width: '47%',
    borderRadius: RADIUS.md,
    overflow: 'hidden',
    backgroundColor: COLORS.surface,
    borderWidth: 2,
    borderColor: COLORS.cardBorder,
  },
  videoCardActive: {
    borderColor: COLORS.primary,
  },
  thumbnailWrap: {
    position: 'relative',
    aspectRatio: 16 / 9,
    backgroundColor: COLORS.surfaceLight,
  },
  thumbnail: {
    width: '100%',
    height: '100%',
    resizeMode: 'cover',
  },
  checkOverlay: {
    position: 'absolute',
    top: 8,
    left: 8,
    width: 28,
    height: 28,
    borderRadius: 6,
    backgroundColor: COLORS.primary,
    justifyContent: 'center',
    alignItems: 'center',
  },
  videoTitle: {
    fontSize: FONT_SIZE.sm,
    color: COLORS.textSecondary,
    padding: SPACING.sm,
    textAlign: 'center',
  },
  videoTitleActive: {
    color: COLORS.primary,
    fontWeight: '700',
  },

  // Footer
  footer: {
    position: 'absolute',
    bottom: 0,
    left: 0,
    right: 0,
    paddingHorizontal: SPACING.xxl,
    paddingVertical: SPACING.lg,
    backgroundColor: COLORS.background,
  },
});
