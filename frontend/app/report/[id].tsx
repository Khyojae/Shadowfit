import { View, Text, StyleSheet, ScrollView, TouchableOpacity } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useRouter, useLocalSearchParams } from 'expo-router';
import { useEffect, useState } from 'react';
import {
  ChevronLeft,
  Download,
  AlertTriangle,
  Target,
  TrendingUp,
  Timer,
  Flame,
  Sparkles,
  type LucideIcon,
} from 'lucide-react-native';
import { COLORS, FONT_SIZE, SPACING, RADIUS } from '@/constants/Colors';
import { exercisesService } from '@/services/exercisesService';
import type { SessionFeedbackSummary } from '@/types/feedback';
import { FEEDBACK_TYPE_LABEL } from '@/types/feedback';

// TODO: API 연동 후 실제 데이터로 교체
const MOCK_REPORT = {
  date: '2026년 3월 23일 월요일',
  averageSyncRate: 89,
  totalReps: 156,
  totalMinutes: 35,
  totalCalories: 250,
  exercises: [
    { name: '숄더프레스', sets: 4, reps: 10, syncRate: 93 },
    { name: '사이드레터럴레이즈', sets: 3, reps: 15, syncRate: 88 },
    { name: '프론트레이즈', sets: 3, reps: 12, syncRate: 91 },
    { name: '페이스풀', sets: 3, reps: 15, syncRate: 85 },
  ],
  worstMoment: {
    exercise: '페이스풀',
    time: '22:10',
    syncRate: 70,
    issue: '어깨 승모근 과사용',
  },
  aiReport:
    '페이스풀 시 승모근이 과도하게 개입하고 있습니다. 팔꿈치를 높이 유지하고, 후면 삼각근에 집중하세요. 전반적으로 훌륭한 어깨 루틴이었습니다!',
};

export default function ReportScreen() {
  const router = useRouter();
  const { id } = useLocalSearchParams<{ id: string }>();
  const sessionId = id ? Number(id) : NaN;

  // 자세 교정 이벤트 집계 (backend SessionFeedbackController)
  const [feedbackSummary, setFeedbackSummary] = useState<SessionFeedbackSummary | null>(null);

  useEffect(() => {
    if (!Number.isFinite(sessionId)) return;
    exercisesService
      .getSessionFeedbackSummary(sessionId)
      .then((res) => setFeedbackSummary(res.data))
      .catch((e) => {
        console.warn('[feedback-summary] status=', e?.response?.status);
      });
  }, [sessionId]);

  return (
    <SafeAreaView style={styles.container}>
      <ScrollView showsVerticalScrollIndicator={false}>
        {/* 헤더 */}
        <View style={styles.header}>
          <TouchableOpacity
            onPress={() =>
              router.canGoBack() ? router.back() : router.replace('/(tabs)/activity')
            }
          >
            <ChevronLeft size={20} color={COLORS.text} strokeWidth={2} />
          </TouchableOpacity>
          <View>
            <Text style={styles.title}>운동 보고서</Text>
            <Text style={styles.date}>{MOCK_REPORT.date}</Text>
          </View>
          <TouchableOpacity>
            <Download size={18} color={COLORS.textSecondary} strokeWidth={2} />
          </TouchableOpacity>
        </View>

        {/* 요약 카드 */}
        <View style={styles.summaryGrid}>
          <SummaryCard
            Icon={Target}
            value={`${MOCK_REPORT.averageSyncRate}%`}
            label="평균 싱크로율"
            highlight
          />
          <SummaryCard
            Icon={TrendingUp}
            value={String(MOCK_REPORT.totalReps)}
            label="총 운동 횟수"
          />
          <SummaryCard
            Icon={Timer}
            value={`${MOCK_REPORT.totalMinutes}분`}
            label="운동 시간"
          />
          <SummaryCard
            Icon={Flame}
            value={String(MOCK_REPORT.totalCalories)}
            label="소모 칼로리"
          />
        </View>

        {/* 종목별 싱크로율 */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>종목별 싱크로율</Text>
          {MOCK_REPORT.exercises.map((ex) => (
            <View key={ex.name} style={styles.exerciseRow}>
              <View style={styles.exerciseInfo}>
                <Text style={styles.exerciseName}>{ex.name}</Text>
                <Text style={styles.exerciseSets}>{ex.sets}세트 × {ex.reps}회</Text>
              </View>
              <Text style={[styles.exerciseSync, { color: getSyncColor(ex.syncRate) }]}>
                {ex.syncRate}%
              </Text>
            </View>
          ))}
          {/* 바 게이지 */}
          {MOCK_REPORT.exercises.map((ex) => (
            <View key={`bar-${ex.name}`} style={styles.barRow}>
              <Text style={styles.barLabel}>{ex.name}</Text>
              <View style={styles.barBg}>
                <View
                  style={[
                    styles.barFill,
                    { width: `${ex.syncRate}%`, backgroundColor: getSyncColor(ex.syncRate) },
                  ]}
                />
              </View>
              <Text style={[styles.barValue, { color: getSyncColor(ex.syncRate) }]}>
                {ex.syncRate}%
              </Text>
            </View>
          ))}
        </View>

        {/* 자세 교정 집계 - 백엔드 SessionFeedbackSummary */}
        {feedbackSummary && feedbackSummary.totalCount > 0 && (
          <View style={styles.section}>
            <View style={styles.sectionTitleRow}>
              <AlertTriangle size={18} color={COLORS.warning} strokeWidth={2} />
              <Text style={styles.sectionTitle}>
                자세 교정 알림 · 총 {feedbackSummary.totalCount}회
              </Text>
            </View>
            {feedbackSummary.byType.map((bucket) => (
              <View key={bucket.feedbackType} style={styles.feedbackBucket}>
                <View style={styles.feedbackBucketHeader}>
                  <Text style={styles.feedbackBucketLabel}>
                    {FEEDBACK_TYPE_LABEL[bucket.feedbackType] ?? bucket.feedbackType}
                  </Text>
                  <Text style={styles.feedbackBucketCount}>{bucket.count}회</Text>
                </View>
                <Text style={styles.feedbackBucketStat}>
                  평균 싱크로율 {Number(bucket.avgSyncRate).toFixed(1)}%
                  {'  '}·{'  '}최저 {Number(bucket.minSyncRate).toFixed(1)}%
                </Text>
              </View>
            ))}
          </View>
        )}

        {/* Worst 구간 */}
        {MOCK_REPORT.worstMoment && (
          <View style={styles.section}>
            <View style={styles.sectionTitleRow}>
              <AlertTriangle size={18} color={COLORS.warning} strokeWidth={2} />
              <Text style={styles.sectionTitle}>Worst 구간</Text>
            </View>
            <View style={styles.worstCard}>
              <View style={styles.worstHeader}>
                <AlertTriangle size={16} color={COLORS.warning} strokeWidth={2} />
                <Text style={styles.worstTitle}>
                  {MOCK_REPORT.worstMoment.exercise} — {MOCK_REPORT.worstMoment.time}
                </Text>
              </View>
              <Text style={styles.worstDesc}>
                싱크로율 {MOCK_REPORT.worstMoment.syncRate}% · {MOCK_REPORT.worstMoment.issue}
              </Text>
            </View>
          </View>
        )}

        {/* AI 안전 리포트 */}
        {MOCK_REPORT.aiReport && (
          <View style={styles.section}>
            <View style={styles.sectionTitleRow}>
              <Sparkles size={18} color={COLORS.primary} strokeWidth={2} />
              <Text style={styles.sectionTitle}>AI 안전 리포트</Text>
            </View>
            <View style={styles.aiCard}>
              <Text style={styles.aiText}>{MOCK_REPORT.aiReport}</Text>
            </View>
          </View>
        )}

        <View style={{ height: 40 }} />
      </ScrollView>
    </SafeAreaView>
  );
}

function SummaryCard({ Icon, value, label, highlight }: {
  Icon: LucideIcon;
  value: string;
  label: string;
  highlight?: boolean;
}) {
  return (
    <View style={[styles.summaryCard, highlight && styles.summaryCardPrimary]}>
      <Icon
        size={20}
        color={highlight ? COLORS.primary : COLORS.textSecondary}
        strokeWidth={2}
      />
      <Text style={styles.summaryValue}>{value}</Text>
      <Text style={styles.summaryLabel}>{label}</Text>
    </View>
  );
}

// 싱크로율 색상 구간: 80%+ 정석 / 60~80% 교정 필요 / <60% 부상 위험
function getSyncColor(rate: number) {
  if (rate >= 80) return COLORS.primary;
  if (rate >= 60) return COLORS.warning;
  return COLORS.error;
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
  date: { fontSize: FONT_SIZE.xs, color: COLORS.textSecondary, marginTop: 2 },

  // Summary
  summaryGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: SPACING.md,
    paddingHorizontal: SPACING.xxl,
    marginTop: SPACING.md,
  },
  summaryCard: {
    width: '47%',
    backgroundColor: COLORS.card,
    borderRadius: RADIUS.md,
    borderWidth: 1,
    borderColor: COLORS.cardBorder,
    padding: SPACING.lg,
    alignItems: 'center',
  },
  summaryCardPrimary: { borderColor: COLORS.primary },
  summaryValue: { fontSize: FONT_SIZE.xxl, fontWeight: '800', color: COLORS.text, marginTop: 4 },
  summaryLabel: { fontSize: FONT_SIZE.xs, color: COLORS.textSecondary, marginTop: 4 },

  // Section
  section: { paddingHorizontal: SPACING.xxl, marginTop: SPACING.xxl },
  sectionTitle: { fontSize: FONT_SIZE.lg, fontWeight: '700', color: COLORS.text, marginBottom: SPACING.md },
  sectionTitleRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: SPACING.sm,
  },

  // Exercise rows
  exerciseRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    backgroundColor: COLORS.card,
    borderRadius: RADIUS.md,
    borderWidth: 1,
    borderColor: COLORS.cardBorder,
    padding: SPACING.lg,
    marginBottom: SPACING.sm,
  },
  exerciseInfo: { flex: 1 },
  exerciseName: { fontSize: FONT_SIZE.md, fontWeight: '700', color: COLORS.text },
  exerciseSets: { fontSize: FONT_SIZE.sm, color: COLORS.textSecondary, marginTop: 2 },
  exerciseSync: { fontSize: FONT_SIZE.xl, fontWeight: '800' },

  // Bar gauge
  barRow: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: SPACING.md,
    gap: SPACING.sm,
  },
  barLabel: { fontSize: FONT_SIZE.xs, color: COLORS.textSecondary, width: 80 },
  barBg: { flex: 1, height: 8, backgroundColor: COLORS.surfaceLight, borderRadius: 4 },
  barFill: { height: 8, borderRadius: 4 },
  barValue: { fontSize: FONT_SIZE.sm, fontWeight: '700', width: 40, textAlign: 'right' },

  // Worst
  // 자세 교정 집계
  feedbackBucket: {
    backgroundColor: COLORS.card,
    borderRadius: RADIUS.md,
    borderWidth: 1,
    borderColor: COLORS.cardBorder,
    padding: SPACING.lg,
    marginBottom: SPACING.sm,
  },
  feedbackBucketHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 4,
  },
  feedbackBucketLabel: {
    fontSize: FONT_SIZE.md,
    fontWeight: '700',
    color: COLORS.text,
  },
  feedbackBucketCount: {
    fontSize: FONT_SIZE.md,
    fontWeight: '800',
    color: COLORS.warning,
  },
  feedbackBucketStat: {
    fontSize: FONT_SIZE.sm,
    color: COLORS.textSecondary,
  },

  worstCard: {
    backgroundColor: COLORS.card,
    borderRadius: RADIUS.md,
    borderWidth: 1,
    borderColor: COLORS.cardBorder,
    padding: SPACING.lg,
  },
  worstHeader: { flexDirection: 'row', alignItems: 'center', gap: SPACING.sm, marginBottom: SPACING.sm },
  worstTitle: { fontSize: FONT_SIZE.md, fontWeight: '700', color: COLORS.text },
  worstDesc: { fontSize: FONT_SIZE.sm, color: COLORS.textSecondary },

  // AI Report
  aiCard: {
    backgroundColor: COLORS.card,
    borderRadius: RADIUS.md,
    borderWidth: 1,
    borderColor: COLORS.cardBorder,
    padding: SPACING.lg,
  },
  aiText: { fontSize: FONT_SIZE.md, color: COLORS.text, lineHeight: 24 },
});
