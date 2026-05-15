import { View, Text, StyleSheet, ScrollView, TouchableOpacity, ActivityIndicator } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useRouter, useFocusEffect } from 'expo-router';
import { useCallback, useState } from 'react';
import {
  ChevronLeft,
  ChevronRight,
  Dumbbell,
  Timer,
  Flame,
  type LucideIcon,
} from 'lucide-react-native';
import { COLORS, FONT_SIZE, SPACING, RADIUS } from '@/constants/Colors';
import { reportService } from '@/services/reportService';
import type { WeeklyActivityResponse } from '@/types/report';

export default function ActivityScreen() {
  const router = useRouter();
  const [data, setData] = useState<WeeklyActivityResponse | null>(null);
  const [loading, setLoading] = useState(true);

  useFocusEffect(
    useCallback(() => {
      setLoading(true);
      reportService
        .getWeeklySummary()
        .then((res) => setData(res.data))
        .catch((e) => {
          console.error('[weekly-summary] status=', e.response?.status, 'data=', e.response?.data);
        })
        .finally(() => setLoading(false));
    }, []),
  );

  const dailyLogs = data?.dailyLogs ?? [];
  const maxMin = Math.max(...dailyLogs.map((d) => d.workoutMinutes), 1);
  // 오늘 인덱스 (없으면 첫 번째 항목)
  const todayIdx = dailyLogs.findIndex((d) => d.isToday);
  const detailLabel =
    todayIdx >= 0 && dailyLogs[todayIdx]
      ? `${dailyLogs[todayIdx].dayOfWeek}요일 운동 상세`
      : '오늘 운동 상세';

  return (
    <SafeAreaView style={styles.container}>
      <ScrollView showsVerticalScrollIndicator={false}>
        {/* 헤더 */}
        <View style={styles.header}>
          <Text style={styles.title}>Activity</Text>
        </View>

        {/* 주간 네비게이션 (현재 주만 표시 - 추후 이전/다음 주 기능 확장) */}
        <View style={styles.weekNav}>
          <TouchableOpacity disabled>
            <ChevronLeft size={16} color={COLORS.textMuted} strokeWidth={2} />
          </TouchableOpacity>
          <Text style={styles.weekRange}>{data?.dateRange ?? '이번 주'}</Text>
          <TouchableOpacity disabled>
            <ChevronRight size={16} color={COLORS.textMuted} strokeWidth={2} />
          </TouchableOpacity>
        </View>

        {loading && !data ? (
          <View style={styles.loadingBox}>
            <ActivityIndicator color={COLORS.primary} />
          </View>
        ) : (
          <>
            {/* 주간 통계 */}
            <View style={styles.statsRow}>
              <WeeklyStat Icon={Dumbbell} value={String(data?.totalWorkouts ?? 0)} label="Workouts" />
              <WeeklyStat Icon={Timer} value={String(data?.totalMinutes ?? 0)} label="Min" />
              <WeeklyStat Icon={Flame} value={String(data?.totalCalories ?? 0)} label="Kcal" />
            </View>

            {/* 운동일지 */}
            <View style={styles.section}>
              <Text style={styles.sectionTitle}>운동일지</Text>

              <View style={styles.summaryCard}>
                <Text style={styles.summaryText}>
                  {data?.totalWorkouts ?? 0} Workouts   {data?.totalMinutes ?? 0} Min   {data?.totalCalories ?? 0} Kcal
                </Text>
                <ChevronRight size={14} color={COLORS.textMuted} strokeWidth={2} />
              </View>

              {/* 바 차트 - dailyLogs 기준 */}
              <View style={styles.chartContainer}>
                {dailyLogs.length === 0 ? (
                  <Text style={styles.emptyText}>이번 주 운동 기록이 없습니다</Text>
                ) : (
                  dailyLogs.map((d, i) => (
                    <View key={i} style={styles.chartCol}>
                      <View style={styles.barWrapper}>
                        <View
                          style={[
                            styles.bar,
                            {
                              height: d.workoutMinutes > 0
                                ? Math.max((d.workoutMinutes / maxMin) * 80, 8)
                                : 0,
                              backgroundColor: d.isToday ? COLORS.warning : COLORS.primary,
                            },
                          ]}
                        />
                      </View>
                      <Text style={[styles.chartLabel, d.isToday && styles.chartLabelToday]}>
                        {d.dayOfWeek}
                      </Text>
                    </View>
                  ))
                )}
              </View>
            </View>

            {/* 운동 상세 */}
            <View style={styles.section}>
              <Text style={styles.sectionTitle}>{detailLabel}</Text>
              {(data?.todayDetails ?? []).length === 0 ? (
                <View style={styles.emptyCard}>
                  <Text style={styles.emptyText}>오늘 운동 기록이 없습니다</Text>
                </View>
              ) : (
                data!.todayDetails.map((ex) => (
                  <TouchableOpacity
                    key={ex.sessionId}
                    style={styles.exerciseCard}
                    activeOpacity={0.7}
                    onPress={() => router.push(`/report/${ex.sessionId}` as any)}
                  >
                    <View style={styles.exerciseInfo}>
                      <Text style={styles.exerciseName}>{ex.exerciseName}</Text>
                      <Text style={styles.exerciseSets}>{ex.setSummary}</Text>
                    </View>
                    <View style={styles.syncBadge}>
                      <Text style={[styles.syncValue, { color: getSyncColor(ex.syncRate) }]}>
                        {Math.round(ex.syncRate)}%
                      </Text>
                      <Text style={styles.syncLabel}>싱크로율</Text>
                    </View>
                  </TouchableOpacity>
                ))
              )}
            </View>
          </>
        )}

        <View style={{ height: 80 }} />
      </ScrollView>
    </SafeAreaView>
  );
}

function WeeklyStat({ Icon, value, label }: { Icon: LucideIcon; value: string; label: string }) {
  return (
    <View style={styles.statCard}>
      <Icon size={18} color={COLORS.primary} strokeWidth={2} />
      <Text style={styles.statValue}>{value}</Text>
      <Text style={styles.statLabel}>{label}</Text>
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
  header: { paddingHorizontal: SPACING.xxl, paddingTop: SPACING.md },
  title: { fontSize: FONT_SIZE.title, fontWeight: '800', color: COLORS.text },

  weekNav: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: SPACING.xxl,
    paddingVertical: SPACING.lg,
  },
  weekRange: { fontSize: FONT_SIZE.md, fontWeight: '700', color: COLORS.text },

  statsRow: {
    flexDirection: 'row',
    paddingHorizontal: SPACING.xxl,
    gap: SPACING.md,
  },
  statCard: {
    flex: 1,
    backgroundColor: COLORS.card,
    borderRadius: RADIUS.md,
    borderWidth: 1,
    borderColor: COLORS.cardBorder,
    padding: SPACING.md,
    alignItems: 'center',
  },
  statValue: { fontSize: FONT_SIZE.xxl, fontWeight: '800', color: COLORS.text, marginTop: 4 },
  statLabel: { fontSize: FONT_SIZE.xs, color: COLORS.textSecondary, marginTop: 2 },

  section: { paddingHorizontal: SPACING.xxl, marginTop: SPACING.xxl },
  sectionTitle: { fontSize: FONT_SIZE.lg, fontWeight: '700', color: COLORS.text, marginBottom: SPACING.md },

  summaryCard: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    backgroundColor: COLORS.card,
    borderRadius: RADIUS.md,
    borderWidth: 1,
    borderColor: COLORS.cardBorder,
    padding: SPACING.lg,
    marginBottom: SPACING.lg,
  },
  summaryText: { fontSize: FONT_SIZE.sm, fontWeight: '700', color: COLORS.text },

  chartContainer: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-end',
    height: 120,
    marginBottom: SPACING.lg,
  },
  chartCol: { flex: 1, alignItems: 'center' },
  barWrapper: { height: 80, justifyContent: 'flex-end' },
  bar: {
    width: 20,
    backgroundColor: COLORS.primary,
    borderRadius: 4,
  },
  chartLabel: { fontSize: 11, color: COLORS.textMuted, marginTop: 4 },
  chartLabelToday: { color: COLORS.warning, fontWeight: '700' },

  exerciseCard: {
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
  syncBadge: { alignItems: 'flex-end' },
  syncValue: { fontSize: FONT_SIZE.xl, fontWeight: '800' },
  syncLabel: { fontSize: FONT_SIZE.xs, color: COLORS.textSecondary },

  loadingBox: {
    paddingVertical: SPACING.xxxl,
    alignItems: 'center',
  },
  emptyText: {
    fontSize: FONT_SIZE.sm,
    color: COLORS.textMuted,
    textAlign: 'center',
    paddingVertical: SPACING.xl,
  },
  emptyCard: {
    backgroundColor: COLORS.card,
    borderRadius: RADIUS.md,
    borderWidth: 1,
    borderColor: COLORS.cardBorder,
  },
});
