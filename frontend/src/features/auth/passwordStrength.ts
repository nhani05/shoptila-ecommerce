export type PasswordStrength = 'weak' | 'medium' | 'strong';

export const MAX_PASSWORD_BYTES = 72;

export function utf8ByteLength(value: string): number {
  return new TextEncoder().encode(value).length;
}

export function isPasswordWithinByteLimit(value: string, maxBytes = MAX_PASSWORD_BYTES): boolean {
  return utf8ByteLength(value) <= maxBytes;
}

/**
 * Đo độ mạnh mật khẩu theo quy tắc của backend:
 * - strong: ≥8 ký tự, có chữ hoa, chữ thường và chữ số.
 * - medium: ≥8 ký tự, có chữ số nhưng thiếu chữ hoa hoặc chữ thường.
 * - weak: còn lại.
 */
export function getPasswordStrength(value: string): PasswordStrength {
  const hasMinLength = value.length >= 8;
  const hasLower = /[a-z]/.test(value);
  const hasUpper = /[A-Z]/.test(value);
  const hasDigit = /\d/.test(value);

  if (hasMinLength && hasLower && hasUpper && hasDigit) {
    return 'strong';
  }
  if (hasMinLength && hasDigit && (!hasUpper || !hasLower)) {
    return 'medium';
  }
  return 'weak';
}
