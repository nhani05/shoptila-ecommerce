import { useEffect, useRef, useState, type ChangeEvent, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';

import { useRegister } from '../hooks/useRegister';
import type { FieldErrors, RegisterFormValues } from '../types';
import { validateRegisterForm } from '../validation';
import { PasswordStrengthMeter } from './PasswordStrengthMeter';

const EMPTY_VALUES: RegisterFormValues = {
  fullName: '',
  email: '',
  phone: '',
  password: '',
  confirmPassword: '',
  terms: false,
};

const FIELD_ORDER: Array<keyof RegisterFormValues> = [
  'fullName',
  'email',
  'phone',
  'password',
  'confirmPassword',
  'terms',
];

export function RegisterForm() {
  const navigate = useNavigate();
  const { status, fieldErrors, generalError, submit } = useRegister();

  const [values, setValues] = useState<RegisterFormValues>(EMPTY_VALUES);
  const [clientErrors, setClientErrors] = useState<FieldErrors>({});
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);

  const fullNameRef = useRef<HTMLInputElement>(null);
  const emailRef = useRef<HTMLInputElement>(null);
  const phoneRef = useRef<HTMLInputElement>(null);
  const passwordRef = useRef<HTMLInputElement>(null);
  const confirmPasswordRef = useRef<HTMLInputElement>(null);
  const termsRef = useRef<HTMLInputElement>(null);

  const isLoading = status === 'loading';
  const isSuccess = status === 'success';
  const isDisabled = isLoading || isSuccess;
  const errors: FieldErrors = { ...clientErrors, ...fieldErrors };

  function focusFirst(invalid: FieldErrors) {
    const first = FIELD_ORDER.find((field) => Boolean(invalid[field]));
    if (!first) {
      return;
    }
    const refs = {
      fullName: fullNameRef,
      email: emailRef,
      phone: phoneRef,
      password: passwordRef,
      confirmPassword: confirmPasswordRef,
      terms: termsRef,
    } as const;
    refs[first].current?.focus();
  }

  useEffect(() => {
    if (status === 'error' && Object.keys(fieldErrors).length > 0) {
      focusFirst(fieldErrors);
    }
  }, [status, fieldErrors]);

  useEffect(() => {
    if (!isSuccess) {
      return undefined;
    }
    const timer = window.setTimeout(() => navigate('/login'), 1500);
    return () => window.clearTimeout(timer);
  }, [isSuccess, navigate]);

  function clearFieldError(field: keyof RegisterFormValues) {
    setClientErrors((current) => {
      if (!current[field]) {
        return current;
      }
      const next = { ...current };
      delete next[field];
      return next;
    });
  }

  function setText(field: 'fullName' | 'email' | 'phone' | 'password' | 'confirmPassword') {
    return (event: ChangeEvent<HTMLInputElement>) => {
      const { value } = event.target;
      setValues((current) => ({ ...current, [field]: value }));
      clearFieldError(field);
    };
  }

  function handleTermsChange(event: ChangeEvent<HTMLInputElement>) {
    setValues((current) => ({ ...current, terms: event.target.checked }));
    clearFieldError('terms');
  }

  function validateOne(field: keyof RegisterFormValues) {
    const allErrors = validateRegisterForm(values);
    setClientErrors((current) => {
      const next = { ...current };
      const message = allErrors[field];
      if (message) {
        next[field] = message;
      } else {
        delete next[field];
      }
      return next;
    });
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (isDisabled) {
      return;
    }
    const validationErrors = validateRegisterForm(values);
    setClientErrors(validationErrors);
    if (Object.keys(validationErrors).length > 0) {
      focusFirst(validationErrors);
      return;
    }
    await submit(values);
  }

  return (
    <form className="register-form" onSubmit={handleSubmit} noValidate>
      <div className="form-field">
        <label htmlFor="fullName">Họ và tên</label>
        <input
          id="fullName"
          name="fullName"
          type="text"
          autoComplete="name"
          value={values.fullName}
          onChange={setText('fullName')}
          onBlur={() => validateOne('fullName')}
          disabled={isDisabled}
          aria-invalid={Boolean(errors.fullName)}
          aria-describedby={errors.fullName ? 'fullName-error' : undefined}
          ref={fullNameRef}
        />
        {errors.fullName && (
          <p id="fullName-error" className="field-error" role="alert">
            {errors.fullName}
          </p>
        )}
      </div>

      <div className="form-field">
        <label htmlFor="email">Email</label>
        <input
          id="email"
          name="email"
          type="email"
          autoComplete="email"
          value={values.email}
          onChange={setText('email')}
          onBlur={() => validateOne('email')}
          disabled={isDisabled}
          aria-invalid={Boolean(errors.email)}
          aria-describedby={errors.email ? 'email-error' : undefined}
          ref={emailRef}
        />
        {errors.email && (
          <p id="email-error" className="field-error" role="alert">
            {errors.email}
          </p>
        )}
      </div>

      <div className="form-field">
        <label htmlFor="phone">Số điện thoại (tùy chọn)</label>
        <input
          id="phone"
          name="phone"
          type="tel"
          autoComplete="tel"
          value={values.phone}
          onChange={setText('phone')}
          onBlur={() => validateOne('phone')}
          disabled={isDisabled}
          aria-invalid={Boolean(errors.phone)}
          aria-describedby={errors.phone ? 'phone-error' : undefined}
          ref={phoneRef}
        />
        {errors.phone && (
          <p id="phone-error" className="field-error" role="alert">
            {errors.phone}
          </p>
        )}
      </div>

      <div className="form-field">
        <label htmlFor="password">Mật khẩu</label>
        <div className="password-input">
          <input
            id="password"
            name="password"
            type={showPassword ? 'text' : 'password'}
            autoComplete="new-password"
            value={values.password}
            onChange={setText('password')}
            onBlur={() => validateOne('password')}
            disabled={isDisabled}
            aria-invalid={Boolean(errors.password)}
            aria-describedby={errors.password ? 'password-error' : undefined}
            ref={passwordRef}
          />
          <button
            type="button"
            className="password-toggle"
            aria-label={showPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
            onClick={() => setShowPassword((visible) => !visible)}
            disabled={isDisabled}
          >
            {showPassword ? 'Ẩn' : 'Hiện'}
          </button>
        </div>
        <PasswordStrengthMeter password={values.password} />
        {errors.password && (
          <p id="password-error" className="field-error" role="alert">
            {errors.password}
          </p>
        )}
      </div>

      <div className="form-field">
        <label htmlFor="confirmPassword">Xác nhận mật khẩu</label>
        <div className="password-input">
          <input
            id="confirmPassword"
            name="confirmPassword"
            type={showConfirmPassword ? 'text' : 'password'}
            autoComplete="new-password"
            value={values.confirmPassword}
            onChange={setText('confirmPassword')}
            onBlur={() => validateOne('confirmPassword')}
            disabled={isDisabled}
            aria-invalid={Boolean(errors.confirmPassword)}
            aria-describedby={errors.confirmPassword ? 'confirmPassword-error' : undefined}
            ref={confirmPasswordRef}
          />
          <button
            type="button"
            className="password-toggle"
            aria-label={showConfirmPassword ? 'Ẩn mật khẩu xác nhận' : 'Hiện mật khẩu xác nhận'}
            onClick={() => setShowConfirmPassword((visible) => !visible)}
            disabled={isDisabled}
          >
            {showConfirmPassword ? 'Ẩn' : 'Hiện'}
          </button>
        </div>
        {errors.confirmPassword && (
          <p id="confirmPassword-error" className="field-error" role="alert">
            {errors.confirmPassword}
          </p>
        )}
      </div>

      <div className="form-field form-field--checkbox">
        <input
          id="terms"
          name="terms"
          type="checkbox"
          checked={values.terms}
          onChange={handleTermsChange}
          disabled={isDisabled}
          aria-invalid={Boolean(errors.terms)}
          aria-describedby={errors.terms ? 'terms-error' : undefined}
          ref={termsRef}
        />
        <label htmlFor="terms">Tôi đồng ý với điều khoản sử dụng và chính sách bảo mật</label>
        {errors.terms && (
          <p id="terms-error" className="field-error" role="alert">
            {errors.terms}
          </p>
        )}
      </div>

      {generalError && (
        <p className="form-error" role="alert">
          {generalError}
        </p>
      )}

      <div aria-live="polite">
        {isSuccess && (
          <p className="form-success" role="status">
            Đăng ký thành công! Đang chuyển đến trang đăng nhập…
          </p>
        )}
      </div>

      <button type="submit" className="submit-button" disabled={isDisabled}>
        {isLoading ? 'Đang xử lý…' : 'Đăng ký'}
      </button>

      <p className="form-footer">
        Đã có tài khoản? <Link to="/login">Đăng nhập ngay</Link>
      </p>
    </form>
  );
}
