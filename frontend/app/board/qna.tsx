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
import { ChevronLeft, Plus, Clock, Lock, Unlock } from 'lucide-react-native';
import { COLORS, FONT_SIZE, SPACING, RADIUS } from '@/constants/Colors';

interface QnAItem {
  id: string;
  title: string;
  content: string;
  date: string;
  isSecret: boolean;
  answered: boolean;
  answer?: string;
}

// TODO: API 연동 후 실제 데이터로 교체
const INITIAL_QNA: QnAItem[] = [
  {
    id: '1',
    title: '싱크로율이 계속 낮게 나와요',
    content: '기준 영상대로 하고 있는데 싱크로율이 50% 이하로 나옵니다.',
    date: '2026.04.02',
    isSecret: false,
    answered: true,
    answer: '카메라 각도와 거리를 확인해주세요. 전신이 보이도록 1.5m 이상 거리를 두시는 것을 권장합니다.',
  },
  {
    id: '2',
    title: '기준 영상 변경이 안 돼요',
    content: '마이페이지에서 기준 영상을 변경하려고 하는데 반영이 안 됩니다.',
    date: '2026.04.01',
    isSecret: true,
    answered: false,
  },
];

export default function QnAScreen() {
  const router = useRouter();
  const [qnaList, setQnaList] = useState<QnAItem[]>(INITIAL_QNA);
  const [selectedItem, setSelectedItem] = useState<QnAItem | null>(null);
  const [modalVisible, setModalVisible] = useState(false);
  const [newTitle, setNewTitle] = useState('');
  const [newContent, setNewContent] = useState('');
  const [isSecret, setIsSecret] = useState(false);

  const handleAdd = () => {
    if (!newTitle.trim()) {
      Alert.alert('알림', '제목을 입력해주세요');
      return;
    }
    const item: QnAItem = {
      id: Date.now().toString(),
      title: newTitle.trim(),
      content: newContent.trim(),
      date: new Date().toISOString().slice(0, 10).replace(/-/g, '.'),
      isSecret,
      answered: false,
    };
    setQnaList([item, ...qnaList]);
    setNewTitle('');
    setNewContent('');
    setIsSecret(false);
    setModalVisible(false);
  };

  const renderItem = ({ item }: { item: QnAItem }) => (
    <TouchableOpacity
      style={styles.card}
      activeOpacity={0.7}
      onPress={() => setSelectedItem(item)}
    >
      <View style={styles.cardRow}>
        {item.isSecret && (
          <Lock
            size={14}
            color={item.answered ? COLORS.primary : COLORS.textMuted}
            strokeWidth={2}
          />
        )}
        <Text style={styles.cardTitle} numberOfLines={1}>
          {item.title}
        </Text>
      </View>
      <View style={styles.cardMeta}>
        <Clock size={12} color={COLORS.textMuted} strokeWidth={2} />
        <Text style={styles.cardDate}>{item.date}</Text>
        {item.answered && (
          <View style={styles.answeredBadge}>
            <Text style={styles.answeredText}>답변완료</Text>
          </View>
        )}
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
        <Text style={styles.title}>Q&A</Text>
        <TouchableOpacity onPress={() => setModalVisible(true)}>
          <Plus size={20} color={COLORS.primary} strokeWidth={2} />
        </TouchableOpacity>
      </View>

      <FlatList
        data={qnaList}
        renderItem={renderItem}
        keyExtractor={(item) => item.id}
        contentContainerStyle={styles.list}
        showsVerticalScrollIndicator={false}
        ListEmptyComponent={
          <Text style={styles.emptyText}>등록된 질문이 없습니다</Text>
        }
      />

      {/* 상세 보기 모달 */}
      <Modal visible={!!selectedItem} transparent animationType="fade">
        <View style={styles.modalOverlay}>
          <View style={styles.modalContent}>
            <View style={styles.cardRow}>
              {selectedItem?.isSecret && (
                <Lock size={14} color={COLORS.textMuted} strokeWidth={2} />
              )}
              <Text style={styles.modalTitle}>{selectedItem?.title}</Text>
            </View>
            <Text style={styles.modalDate}>{selectedItem?.date}</Text>
            <View style={styles.modalDivider} />

            <Text style={styles.sectionLabel}>질문</Text>
            <Text style={styles.modalBody}>
              {selectedItem?.content || '내용이 없습니다.'}
            </Text>

            {selectedItem?.answered && (
              <>
                <View style={styles.modalDivider} />
                <Text style={[styles.sectionLabel, { color: COLORS.primary }]}>
                  답변
                </Text>
                <Text style={styles.modalBody}>{selectedItem.answer}</Text>
              </>
            )}

            <TouchableOpacity
              style={styles.modalClose}
              onPress={() => setSelectedItem(null)}
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
            <Text style={styles.modalTitle}>질문 작성</Text>
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

            {/* 비밀글 토글 */}
            <TouchableOpacity
              style={styles.secretToggle}
              onPress={() => setIsSecret(!isSecret)}
              activeOpacity={0.7}
            >
              {isSecret ? (
                <Lock size={14} color={COLORS.primary} strokeWidth={2} />
              ) : (
                <Unlock size={14} color={COLORS.textMuted} strokeWidth={2} />
              )}
              <Text
                style={[
                  styles.secretText,
                  isSecret && { color: COLORS.primary },
                ]}
              >
                비밀글
              </Text>
            </TouchableOpacity>

            <View style={styles.modalActions}>
              <TouchableOpacity
                style={styles.cancelBtn}
                onPress={() => {
                  setModalVisible(false);
                  setNewTitle('');
                  setNewContent('');
                  setIsSecret(false);
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
  cardRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: SPACING.sm,
  },
  cardTitle: {
    fontSize: FONT_SIZE.md,
    fontWeight: '700',
    color: COLORS.text,
    flex: 1,
  },
  cardMeta: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: SPACING.xs,
    marginTop: SPACING.sm,
  },
  cardDate: { fontSize: FONT_SIZE.xs, color: COLORS.textMuted },
  answeredBadge: {
    backgroundColor: COLORS.primaryDim,
    paddingHorizontal: SPACING.sm,
    paddingVertical: 2,
    borderRadius: RADIUS.sm,
    marginLeft: SPACING.sm,
  },
  answeredText: {
    fontSize: FONT_SIZE.xs,
    color: COLORS.primary,
    fontWeight: '600',
  },
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
  sectionLabel: {
    fontSize: FONT_SIZE.sm,
    fontWeight: '700',
    color: COLORS.textSecondary,
    marginBottom: SPACING.sm,
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
  modalCloseText: {
    fontSize: FONT_SIZE.md,
    color: COLORS.primary,
    fontWeight: '600',
  },

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
  secretToggle: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: SPACING.sm,
    marginTop: SPACING.lg,
    paddingVertical: SPACING.sm,
  },
  secretText: {
    fontSize: FONT_SIZE.sm,
    color: COLORS.textMuted,
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
