import { useState } from 'react';
import {
  View,
  TextInput,
  Text,
  TouchableOpacity,
  StyleSheet,
  type TextInputProps,
  type ViewStyle,
} from 'react-native';
import { Eye, EyeOff, type LucideIcon } from 'lucide-react-native';
import { COLORS, FONT_SIZE, RADIUS, SPACING } from '@/constants/Colors';

interface InputProps extends TextInputProps {
  label?: string;
  /** Lucide 아이콘 컴포넌트. 예: <Input icon={Mail} ... /> */
  icon?: LucideIcon;
  error?: string;
  isPassword?: boolean;
  containerStyle?: ViewStyle;
}

export default function Input({
  label,
  icon: Icon,
  error,
  isPassword = false,
  containerStyle,
  ...props
}: InputProps) {
  const [showPassword, setShowPassword] = useState(false);
  const [isFocused, setIsFocused] = useState(false);

  return (
    <View style={containerStyle}>
      {label && <Text style={styles.label}>{label}</Text>}
      <View
        style={[
          styles.inputContainer,
          isFocused && styles.inputFocused,
          error && styles.inputError,
        ]}
      >
        {Icon && (
          <View style={styles.icon}>
            <Icon size={18} color={COLORS.textMuted} strokeWidth={2} />
          </View>
        )}
        <TextInput
          style={styles.input}
          placeholderTextColor={COLORS.textPlaceholder}
          selectionColor={COLORS.primary}
          secureTextEntry={isPassword && !showPassword}
          onFocus={() => setIsFocused(true)}
          onBlur={() => setIsFocused(false)}
          {...props}
        />
        {isPassword && (
          <TouchableOpacity
            onPress={() => setShowPassword(!showPassword)}
            hitSlop={{ top: 10, bottom: 10, left: 10, right: 10 }}
          >
            {showPassword ? (
              <EyeOff size={18} color={COLORS.textMuted} strokeWidth={2} />
            ) : (
              <Eye size={18} color={COLORS.textMuted} strokeWidth={2} />
            )}
          </TouchableOpacity>
        )}
      </View>
      {error && <Text style={styles.errorText}>{error}</Text>}
    </View>
  );
}

const styles = StyleSheet.create({
  label: {
    color: COLORS.text,
    fontSize: FONT_SIZE.sm,
    fontWeight: '600',
    marginBottom: SPACING.sm,
  },
  inputContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: COLORS.surface,
    borderRadius: RADIUS.md,
    borderWidth: 1,
    borderColor: COLORS.cardBorder,
    paddingHorizontal: SPACING.lg,
    height: 52,
  },
  inputFocused: {
    borderColor: COLORS.primary,
  },
  inputError: {
    borderColor: COLORS.error,
  },
  icon: {
    marginRight: SPACING.md,
  },
  input: {
    flex: 1,
    color: COLORS.text,
    fontSize: FONT_SIZE.md,
  },
  errorText: {
    color: COLORS.error,
    fontSize: FONT_SIZE.xs,
    marginTop: SPACING.xs,
  },
});
