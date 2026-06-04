import { View, Text, StyleSheet, TouchableOpacity, ScrollView } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useRouter, useFocusEffect } from 'expo-router';
import { Calendar, type DateData } from 'react-native-calendars';
import { useCallback, useState } from 'react';
import {
  CircleUser,
  Flame,
  Target,
  Trophy,
  Camera,
  Ruler,
  PersonStanding,
  Lightbulb,
  Ban,
  type LucideIcon,
} from 'lucide-react-native';
import { COLORS, FONT_SIZE, SPACING, RADIUS } from '@/constants/Colors';
import Button from '@/components/ui/Button';
import { reportService } from '@/services/reportService';
import type { CalendarMainResponse, CalendarDay } from '@/types/report';

// 백엔드 records 를 react-native-calendars 의 markedDates 형식으로 변환
function buildMarkedDates(records: CalendarDay[]): Record<string, any> {
  const marked: Record<string, any> = {};
  for (const r of records) {
    if (!r.hasRecord) continue;
    // 80% 이상 = 정석(primary) / 60~80% = 교정(warning) / <60% = 부상(error)
    const sync = r.dailyAvgSyncRate;
    const dotColor =
      sync == null
        ? COLORS.primary
        : sync >= 80
        ? COLORS.primary
        : sync >= 60
        ? COLORS.warning
        : COLORS.error;
    marked[r.date] = { marked: true, dotColor };
  }
  return marked;
}

export default function HomeScreen() {
  const router = useRouter();
  const today = new Date().toISOString().split('T')[0];
  const [selectedDate, setSelectedDate] = useState('');

  // 캘린더가 가리키는 연/월 (사용자가 ◀▶ 눌러 바꿈)
  const initialDate = new Date();
  const [viewYear, setViewYear] = useState(initialDate.getFullYear());
  const [viewMonth, setViewMonth] = useState(initialDate.getMonth() + 1);

  const [data, setData] = useState<CalendarMainResponse | null>(null);

  // 화면 포커스 / 연·월 변경마다 캘린더 데이터 재조회
  useFocusEffect(
    useCallback(() => {
      reportService
        .getCalendar(viewYear, viewMonth)
        .then((res) => setData(res.data))
        .catch((e) => {
          console.error('[calendar] status=', e.response?.status, 'data=', e.response?.data);
        });
    }, [viewYear, viewMonth]),
  );

  const baseMarked = data ? buildMarkedDates(data.records) : {};
  const markedDates = {
    ...baseMarked,
    ...(selectedDate
      ? {
          [selectedDate]: {
            ...(baseMarked[selectedDate] ?? {}),
            selected: true,
            selectedColor: COLORS.primary,
          },
        }
      : {}),
  };

  const hasRecordOnSelected = selectedDate
    ? !!baseMarked[selectedDate]
    : !!baseMarked[today];

  // 날짜 클릭: 그 날 운동 목록 조회 → 기록 있으면 상세 보고서로 이동
  const handleDayPress = useCallback(
    async (day: DateData) => {
      setSelectedDate(day.dateString);
      try {
        const res = await reportService.getDaily(day.dateString);
        const sessions = res.data.sessions ?? [];
        if (sessions.length > 0) {
          // 스쿼트=하루 단건이 일반적 → 첫 세션 상세로 직행
          router.push(`/report/${sessions[0].sessionId}` as any);
        }
        // 기록 없는 날은 이동 없이 "기록 없음" 텍스트만 갱신
      } catch (e: any) {
        console.error('[daily] status=', e.response?.status, 'data=', e.response?.data);
      }
    },
    [router],
  );

  return (
    <SafeAreaView style={styles.container}>
      <ScrollView showsVerticalScrollIndicator={false} contentContainerStyle={styles.scrollContent}>
        {/* 헤더 */}
        <View style={styles.header}>
          <View>
            <Text style={styles.appTitle}>ShadowFit</Text>
            <Text style={styles.appSubtitle}>AI 자세 교정 트레이너</Text>
          </View>
          <TouchableOpacity onPress={() => router.push('/(tabs)/mypage')}>
            <CircleUser size={28} color={COLORS.textSecondary} strokeWidth={1.75} />
          </TouchableOpacity>
        </View>

        {/* 상단 통계 카드 - 백엔드 CalendarMainResponse 의 monthly* 사용 */}
        <View style={styles.statsRow}>
          <StatCard
            Icon={Flame}
            value={`${data?.monthlyExerciseDays ?? 0}일`}
            label="이번 달"
          />
          <StatCard
            Icon={Target}
            value={`${data?.totalAvgSyncRate ?? 0}%`}
            label="평균 싱크로율"
            highlight
          />
          <StatCard
            Icon={Trophy}
            value={`${data?.consecutiveDays ?? 0}일`}
            label="연속 기록"
          />
        </View>

        {/* 캘린더 */}
        <View style={styles.calendarContainer}>
          <Calendar
            markedDates={markedDates}
            onDayPress={handleDayPress}
            onMonthChange={(d: DateData) => {
              setViewYear(d.year);
              setViewMonth(d.month);
              setSelectedDate('');
            }}
            theme={{
              calendarBackground: COLORS.card,
              textSectionTitleColor: COLORS.textSecondary,
              dayTextColor: COLORS.text,
              todayTextColor: COLORS.primary,
              selectedDayTextColor: COLORS.black,
              monthTextColor: COLORS.text,
              textDisabledColor: COLORS.textMuted,
              arrowColor: COLORS.primary,
              textMonthFontWeight: '700',
              textDayFontSize: 14,
              textMonthFontSize: 16,
            }}
            style={styles.calendar}
          />
        </View>

        {/* 선택 날짜 정보 */}
        <View style={styles.dateInfo}>
          <Text style={styles.dateInfoText}>
            {selectedDate
              ? `${new Date(selectedDate).getMonth() + 1}월 ${new Date(selectedDate).getDate()}일 ${['일', '월', '화', '수', '목', '금', '토'][new Date(selectedDate).getDay()]}요일`
              : `${new Date().getMonth() + 1}월 ${new Date().getDate()}일 ${['일', '월', '화', '수', '목', '금', '토'][new Date().getDay()]}요일`}
          </Text>
          <Text style={styles.noRecordText}>
            {hasRecordOnSelected ? '운동 기록이 있습니다' : '운동 기록이 없습니다'}
          </Text>
        </View>

        {/* 촬영 가이드 */}
        <View style={styles.guideBox}>
          <View style={styles.guideHeaderRow}>
            <Camera size={16} color={COLORS.text} strokeWidth={2} />
            <Text style={styles.guideHeader}>운동 촬영 가이드</Text>
          </View>
          <GuideItem Icon={Ruler} text="정면 또는 측면(45°)에서 촬영" />
          <GuideItem Icon={PersonStanding} text="전신이 보이도록 1.5m 이상 거리 확보" />
          <GuideItem Icon={Lightbulb} text="밝은 조명, 단색 배경 권장" />
          <GuideItem Icon={Ban} text="거울 반사, 여러 사람이 보이는 환경은 피해주세요" />
        </View>

        {/* 운동 시작 버튼 */}
        <Button
          title="▷  운동 시작하기"
          onPress={() => router.push('/(tabs)/exercise')}
          style={styles.startBtn}
        />

        <View style={{ height: 80 }} />
      </ScrollView>
    </SafeAreaView>
  );
}

function StatCard({ Icon, value, label, highlight }: {
  Icon: LucideIcon;
  value: string;
  label: string;
  highlight?: boolean;
}) {
  return (
    <View style={[styles.statCard, highlight && styles.statCardHighlight]}>
      <Icon
        size={20}
        color={highlight ? COLORS.primary : COLORS.textSecondary}
        strokeWidth={2}
      />
      <Text style={[styles.statValue, highlight && styles.statValueHighlight]}>{value}</Text>
      <Text style={styles.statLabel}>{label}</Text>
    </View>
  );
}

function GuideItem({ Icon, text }: { Icon: LucideIcon; text: string }) {
  return (
    <View style={styles.guideItem}>
      <Icon size={16} color={COLORS.primary} strokeWidth={2} />
      <Text style={styles.guideText}>{text}</Text>
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
  appTitle: { fontSize: FONT_SIZE.xl, fontWeight: '800', color: COLORS.primary },
  appSubtitle: { fontSize: FONT_SIZE.xs, color: COLORS.textSecondary, marginTop: 2 },

  statsRow: {
    flexDirection: 'row',
    paddingHorizontal: SPACING.xxl,
    gap: SPACING.md,
    marginVertical: SPACING.md,
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
  statCardHighlight: { borderColor: COLORS.primary },
  statValue: { fontSize: FONT_SIZE.xl, fontWeight: '800', color: COLORS.text, marginTop: 4 },
  statValueHighlight: { color: COLORS.primary },
  statLabel: { fontSize: FONT_SIZE.xs, color: COLORS.textSecondary, marginTop: 2 },

  calendarContainer: {
    marginHorizontal: SPACING.xxl,
    borderRadius: RADIUS.lg,
    overflow: 'hidden',
    borderWidth: 1,
    borderColor: COLORS.cardBorder,
  },
  calendar: { borderRadius: RADIUS.lg },

  dateInfo: { paddingHorizontal: SPACING.xxl, paddingVertical: SPACING.lg },
  dateInfoText: { fontSize: FONT_SIZE.sm, color: COLORS.textSecondary },
  noRecordText: { fontSize: FONT_SIZE.sm, color: COLORS.textMuted, marginTop: 4 },

  scrollContent: {
    paddingBottom: SPACING.xxxl,
  },

  // 촬영 가이드
  guideBox: {
    backgroundColor: COLORS.card,
    borderRadius: RADIUS.md,
    borderWidth: 1,
    borderColor: COLORS.cardBorder,
    marginHorizontal: SPACING.xxl,
    padding: SPACING.lg,
    gap: SPACING.sm,
  },
  guideHeaderRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: SPACING.sm,
    marginBottom: SPACING.xs,
  },
  guideHeader: {
    fontSize: FONT_SIZE.sm,
    fontWeight: '700',
    color: COLORS.text,
  },
  guideItem: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: SPACING.sm,
  },
  guideText: {
    flex: 1,
    fontSize: FONT_SIZE.sm,
    color: COLORS.textSecondary,
    lineHeight: 20,
  },

  startBtn: {
    marginHorizontal: SPACING.xxl,
    marginTop: SPACING.xl,
  },
});
