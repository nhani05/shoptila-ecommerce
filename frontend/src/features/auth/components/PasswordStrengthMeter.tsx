import { getPasswordStrength, isPasswordWithinByteLimit } from '../passwordStrength';

const LABELS: Record<ReturnType<typeof getPasswordStrength>, string> = {
  weak: 'Yếu',
  medium: 'Trung bình',
  strong: 'Mạnh',
};

interface PasswordStrengthMeterProps {
  password: string;
}

/**
 * Hiển thị độ mạnh mật khẩu bằng chữ (không chỉ màu) và thông báo qua aria-live.
 */
export function PasswordStrengthMeter({ password }: PasswordStrengthMeterProps) {
  if (!password) {
    return null;
  }

  const tooLong = !isPasswordWithinByteLimit(password);
  const strength = getPasswordStrength(password);
  const label = tooLong ? 'Quá dài (vượt 72 byte)' : LABELS[strength];
  const filledBars = tooLong ? 3 : { weak: 1, medium: 2, strong: 3 }[strength];

  return (
    <div className="password-strength" aria-live="polite" data-strength={tooLong ? 'too-long' : strength}>
      <div className="password-strength__bars" aria-hidden="true">
        {[1, 2, 3].map((bar) => (
          <span
            key={bar}
            className={`password-strength__bar${bar <= filledBars ? ' password-strength__bar--filled' : ''}`}
          />
        ))}
      </div>
      <span className="password-strength__label">
        Độ mạnh mật khẩu: <strong>{label}</strong>
      </span>
    </div>
  );
}
