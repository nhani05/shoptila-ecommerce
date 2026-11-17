import type { FieldErrors, RegisterFormValues } from './types';
import { isPasswordWithinByteLimit } from './passwordStrength';

export const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
export const PHONE_PATTERN = /^(0|\+84)[35789][0-9]{8}$/;
export const PASSWORD_LETTER_DIGIT_PATTERN = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{8,}$/;

/**
 * Kiểm tra hợp lệ phía client (UX). Backend vẫn là nguồn xác thực cuối cùng.
 */
export function validateRegisterForm(values: RegisterFormValues): FieldErrors {
  const errors: FieldErrors = {};

  const email = values.email.trim();
  if (!email) {
    errors.email = 'Email không được để trống';
  } else if (email.length > 150) {
    errors.email = 'Email không được vượt quá 150 ký tự';
  } else if (!EMAIL_PATTERN.test(email)) {
    errors.email = 'Định dạng email không hợp lệ';
  }

  const password = values.password;
  if (!password) {
    errors.password = 'Mật khẩu không được để trống';
  } else if (password.length < 8) {
    errors.password = 'Mật khẩu phải có ít nhất 8 ký tự';
  } else if (!/[a-z]/.test(password)) {
    errors.password = 'Mật khẩu phải có ít nhất 1 chữ thường';
  } else if (!/[A-Z]/.test(password)) {
    errors.password = 'Mật khẩu phải có ít nhất 1 chữ hoa';
  } else if (!/\d/.test(password)) {
    errors.password = 'Mật khẩu phải có ít nhất 1 chữ số';
  } else if (!isPasswordWithinByteLimit(password)) {
    errors.password = 'Mật khẩu không được vượt quá 72 byte';
  }

  if (!values.confirmPassword) {
    errors.confirmPassword = 'Vui lòng xác nhận mật khẩu';
  } else if (values.confirmPassword !== password) {
    errors.confirmPassword = 'Mật khẩu xác nhận không khớp';
  }

  const fullName = values.fullName.trim();
  if (!fullName) {
    errors.fullName = 'Họ và tên không được để trống';
  } else if (fullName.length < 2) {
    errors.fullName = 'Họ và tên phải có ít nhất 2 ký tự';
  } else if (fullName.length > 100) {
    errors.fullName = 'Họ và tên không được vượt quá 100 ký tự';
  }

  const phone = values.phone.trim();
  if (phone.length > 20) {
    errors.phone = 'Số điện thoại không được vượt quá 20 ký tự';
  } else if (phone && !PHONE_PATTERN.test(phone)) {
    errors.phone = 'Số điện thoại không hợp lệ';
  }

  if (!values.terms) {
    errors.terms = 'Bạn phải đồng ý với điều khoản sử dụng';
  }

  return errors;
}
