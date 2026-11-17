import { describe, expect, it } from 'vitest';

import type { RegisterFormValues } from '../types';
import { validateRegisterForm } from '../validation';

function validValues(overrides: Partial<RegisterFormValues> = {}): RegisterFormValues {
  return {
    fullName: 'Nguyễn Văn A',
    email: 'khachhang@example.com',
    phone: '0987654321',
    password: 'MatKhau123',
    confirmPassword: 'MatKhau123',
    terms: true,
    ...overrides,
  };
}

describe('validateRegisterForm', () => {
  it('dữ liệu hợp lệ không có lỗi', () => {
    expect(validateRegisterForm(validValues())).toEqual({});
  });

  it('email rỗng báo lỗi bắt buộc', () => {
    expect(validateRegisterForm(validValues({ email: '   ' })).email).toMatch(/không được để trống/i);
  });

  it('email sai định dạng bị từ chối', () => {
    expect(validateRegisterForm(validValues({ email: 'khong-hop-le' })).email)
      .toMatch(/định dạng email không hợp lệ/i);
  });

  it('mật khẩu ngắn hơn 8 ký tự bị từ chối', () => {
    expect(validateRegisterForm(validValues({ password: 'Mat1', confirmPassword: 'Mat1' })).password)
      .toMatch(/8 ký tự/i);
  });

  it('mật khẩu thiếu chữ hoa bị từ chối', () => {
    expect(
      validateRegisterForm(validValues({ password: 'matkhau123', confirmPassword: 'matkhau123' })).password,
    ).toMatch(/chữ hoa/i);
  });

  it('mật khẩu thiếu chữ thường bị từ chối', () => {
    expect(
      validateRegisterForm(validValues({ password: 'MATKHAU123', confirmPassword: 'MATKHAU123' })).password,
    ).toMatch(/chữ thường/i);
  });

  it('mật khẩu thiếu chữ số bị từ chối', () => {
    expect(
      validateRegisterForm(validValues({ password: 'MatKhauXYZ', confirmPassword: 'MatKhauXYZ' })).password,
    ).toMatch(/chữ số/i);
  });

  it('mật khẩu vượt 72 byte UTF-8 bị từ chối', () => {
    const password = 'Aa1' + '😀'.repeat(20);
    const errors = validateRegisterForm(validValues({ password, confirmPassword: password }));
    expect(errors.password).toMatch(/72 byte/i);
  });

  it('họ tên ngắn hơn 2 ký tự bị từ chối', () => {
    expect(validateRegisterForm(validValues({ fullName: 'A' })).fullName).toMatch(/ít nhất 2 ký tự/i);
  });

  it('họ tên dài hơn 100 ký tự bị từ chối', () => {
    expect(validateRegisterForm(validValues({ fullName: 'A'.repeat(101) })).fullName)
      .toMatch(/vượt quá 100 ký tự/i);
  });

  it('số điện thoại sai định dạng bị từ chối', () => {
    expect(validateRegisterForm(validValues({ phone: '0987 654 321' })).phone)
      .toMatch(/số điện thoại không hợp lệ/i);
  });

  it('số điện thoại rỗng được chấp nhận (tùy chọn)', () => {
    expect(validateRegisterForm(validValues({ phone: '' })).phone).toBeUndefined();
  });

  it('xác nhận mật khẩu không khớp bị từ chối', () => {
    expect(validateRegisterForm(validValues({ confirmPassword: 'MatKhau124' })).confirmPassword)
      .toMatch(/không khớp/i);
  });

  it('chưa chọn điều khoản bị từ chối', () => {
    expect(validateRegisterForm(validValues({ terms: false })).terms).toMatch(/điều khoản/i);
  });
});
