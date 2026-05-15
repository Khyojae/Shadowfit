import { Tabs } from 'expo-router';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Home, BarChart3, CirclePlay, User, type LucideIcon } from 'lucide-react-native';
import { COLORS } from '@/constants/Colors';

function TabIcon({ Icon, color }: { Icon: LucideIcon; color: string }) {
  return <Icon size={22} color={color} strokeWidth={2} />;
}

export default function TabLayout() {
  const insets = useSafeAreaInsets();

  return (
    <Tabs
      screenOptions={{
        headerShown: false,
        tabBarActiveTintColor: COLORS.primary,
        tabBarInactiveTintColor: COLORS.textMuted,
        tabBarStyle: {
          backgroundColor: COLORS.surface,
          borderTopColor: COLORS.border,
          height: 60 + insets.bottom,
          paddingBottom: 8 + insets.bottom,
          paddingTop: 4,
        },
        tabBarLabelStyle: {
          fontSize: 11,
          fontWeight: '600',
        },
      }}
    >
      <Tabs.Screen
        name="index"
        options={{
          title: '홈',
          tabBarIcon: ({ color }) => <TabIcon Icon={Home} color={color} />,
        }}
      />
      <Tabs.Screen
        name="activity"
        options={{
          title: '활동',
          tabBarIcon: ({ color }) => <TabIcon Icon={BarChart3} color={color} />,
        }}
      />
      <Tabs.Screen
        name="exercise"
        options={{
          title: '운동',
          tabBarIcon: ({ color }) => <TabIcon Icon={CirclePlay} color={color} />,
        }}
      />
      <Tabs.Screen
        name="mypage"
        options={{
          title: '마이',
          tabBarIcon: ({ color }) => <TabIcon Icon={User} color={color} />,
        }}
      />
    </Tabs>
  );
}
