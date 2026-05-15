import { useState } from 'react';
import {
  View,
  Text,
  StyleSheet,
  FlatList,
  TouchableOpacity,
  TextInput,
  Modal,
  Alert,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useRouter } from 'expo-router';
import { ChevronLeft, Plus, Clock } from 'lucide-react-native';
import { COLORS, FONT_SIZE, SPACING, RADIUS } from '@/constants/Colors';

interface Notice {
  id: string;
  title: string;
  content: string;
  date: string;
}

// TODO: API 연동 후 실제 데이터로 교체
const INITIAL_NOTICES: Notice[] = [
  {
    id: '1',
    title: '앱 업데이트 안내 (v1.0.1)',
    content: '운동 기록 화면 개선 및 버그 수정이 포함되었습니다.',
    date: '2026.04.02',
  },
  {
    id: '2',
    title: '서비스 점검 안내',
    content: '4월 5일 02:00 ~ 04:00 서버 점검이 예정되어 있습니다.',
    date: '2026.04.01',
  },
];

export default function NoticeScreen() {
  const router = useRouter();
  const [notices, setNotices] = useState<Notice[]>(INITIAL_NOTICES);
  const [modalVisible, setModalVisible] = useState(false);
  const [selectedNotice, setSelectedNotice] = useState<Notice | null>(null);
  const [newTitle, setNewTitle] = useState('');
  const [newContent, setNewContent] = useState('');

  const handleAdd = () => {
    if (!newTitle.trim()) {
      Alert.alert('알림', '제목을 입력해주세요');
      return;
    }
    const notice: Notice = {
      id: Date.now().toString(),
      title: newTitle.trim(),
      content: newContent.trim(),
      date: new Date().toISOString().slice(0, 10).replace(/-/g, '.'),
    };
    setNotices([notice, ...notices]);
    setNewTitle('');
    setNewContent('');
    setModalVisible(false);
  };

  const renderItem = ({ item }: { item: Notice }) => (
    <TouchableOpacity
      style={styles.card}
      activeOpacity={0.7}
      onPress={() => setSelectedNotice(item)}
    >
      <Text style={styles.cardTitle}>{item.title}</Text>
      <View style={styles.cardMeta}>
        <Clock size={12} color={COLORS.textMuted} strokeWidth={2} />
        <Text style={styles.cardDate}>{item.date}</Text>
      </View>
    </TouchableOpacity>
  );

  return (
    <SafeAreaView style={styles.container}>
      {/* 헤더 */}
      <View style={styles.header}>
        <TouchableOpacity onPress={() => router.back()}>
          <ChevronLeft size={20} color={COLORS.text} strokeWidth={2} />
        </TouchableOpacity>
        <Text style={styles.title}>공지사항</Text>
        <TouchableOpacity onPress={() => setModalVisible(true)}>
          <Plus size={20} color={COLORS.primary} strokeWidth={2} />
        </TouchableOpacity>
      </View>

      <FlatList
        data={notices}
        renderItem={renderItem}
        keyExtractor={(item) => item.id}
        contentContainerStyle={styles.list}
        showsVerticalScrollIndicator={false}
        ListEmptyComponent={
          <Text style={styles.emptyText}>등록된 공지사항이 없습니다</Text>
        }
      />

      {/* 상세 보기 모달 */}
      <Modal visible={!!selectedNotice} transparent animationType="fade">
        <View style={styles.modalOverlay}>
          <View style={styles.modalContent}>
            <Text style={styles.modalTitle}>{selectedNotice?.title}</Text>
            <Text style={styles.modalDate}>{selectedNotice?.date}</Text>
            <View style={styles.modalDivider} />
            <Text style={styles.modalBody}>
              {selectedNotice?.content || '내용이 없습니다.'}
            </Text>
            <TouchableOpacity
              style={styles.modalClose}
              onPress={() => setSelectedNotice(null)}
            >
              <Text style={styles.modalCloseText}>닫기</Text>
            </TouchableOpacity>
          </View>
        </View>
      </Modal>

      {/* 작성 모달 */}
      <Modal visible={modalVisible} transparent animationType="slide">
        <View style={styles.modalOverlay}>
          <View style={styles.modalContent}>
            <Text style={styles.modalTitle}>공지사항 작성</Text>
            <TextInput
              style={styles.input}
              placeholder="제목"
              placeholderTextColor={COLORS.textPlaceholder}
              value={newTitle}
              onChangeText={setNewTitle}
            />
            <TextInput
              style={[styles.input, styles.inputMultiline]}
              placeholder="내용"
              placeholderTextColor={COLORS.textPlaceholder}
              value={newContent}
              onChangeText={setNewContent}
              multiline
              numberOfLines={5}
              textAlignVertical="top"
            />
            <View style={styles.modalActions}>
              <TouchableOpacity
                style={styles.cancelBtn}
                onPress={() => {
                  setModalVisible(false);
                  setNewTitle('');
                  setNewContent('');
                }}
              >
                <Text style={styles.cancelBtnText}>취소</Text>
              </TouchableOpacity>
              <TouchableOpacity style={styles.submitBtn} onPress={handleAdd}>
                <Text style={styles.submitBtnText}>등록</Text>
              </TouchableOpacity>
            </View>
          </View>
        </View>
      </Modal>
    </SafeAreaView>
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
  list: { paddingHorizontal: SPACING.xxl, paddingTop: SPACING.md },
  card: {
    backgroundColor: COLORS.card,
    padding: SPACING.xl,
    borderRadius: RADIUS.md,
    borderWidth: 1,
    borderColor: COLORS.cardBorder,
    marginBottom: SPACING.md,
  },
  cardTitle: { fontSize: FONT_SIZE.md, fontWeight: '700', color: COLORS.text },
  cardMeta: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: SPACING.xs,
    marginTop: SPACING.sm,
  },
  cardDate: { fontSize: FONT_SIZE.xs, color: COLORS.textMuted },
  emptyText: {
    textAlign: 'center',
    color: COLORS.textMuted,
    marginTop: 60,
    fontSize: FONT_SIZE.md,
  },

  // 모달 공통
  modalOverlay: {
    flex: 1,
    backgroundColor: COLORS.overlay,
    justifyContent: 'center',
    paddingHorizontal: SPACING.xxl,
  },
  modalContent: {
    backgroundColor: COLORS.surface,
    borderRadius: RADIUS.lg,
    padding: SPACING.xxl,
  },
  modalTitle: {
    fontSize: FONT_SIZE.lg,
    fontWeight: '800',
    color: COLORS.text,
  },
  modalDate: {
    fontSize: FONT_SIZE.xs,
    color: COLORS.textMuted,
    marginTop: SPACING.xs,
  },
  modalDivider: {
    height: 1,
    backgroundColor: COLORS.border,
    marginVertical: SPACING.lg,
  },
  modalBody: {
    fontSize: FONT_SIZE.md,
    color: COLORS.textSecondary,
    lineHeight: 22,
  },
  modalClose: {
    alignSelf: 'flex-end',
    marginTop: SPACING.xl,
    paddingVertical: SPACING.sm,
    paddingHorizontal: SPACING.lg,
  },
  modalCloseText: { fontSize: FONT_SIZE.md, color: COLORS.primary, fontWeight: '600' },

  // 작성 모달
  input: {
    backgroundColor: COLORS.card,
    borderRadius: RADIUS.sm,
    borderWidth: 1,
    borderColor: COLORS.cardBorder,
    paddingHorizontal: SPACING.lg,
    paddingVertical: SPACING.md,
    fontSize: FONT_SIZE.md,
    color: COLORS.text,
    marginTop: SPACING.lg,
  },
  inputMultiline: {
    minHeight: 120,
  },
  modalActions: {
    flexDirection: 'row',
    justifyContent: 'flex-end',
    gap: SPACING.md,
    marginTop: SPACING.xl,
  },
  cancelBtn: {
    paddingVertical: SPACING.md,
    paddingHorizontal: SPACING.xl,
    borderRadius: RADIUS.sm,
    borderWidth: 1,
    borderColor: COLORS.border,
  },
  cancelBtnText: { fontSize: FONT_SIZE.md, color: COLORS.textSecondary },
  submitBtn: {
    paddingVertical: SPACING.md,
    paddingHorizontal: SPACING.xl,
    borderRadius: RADIUS.sm,
    backgroundColor: COLORS.primary,
  },
  submitBtnText: {
    fontSize: FONT_SIZE.md,
    color: COLORS.primaryText,
    fontWeight: '700',
  },
});
