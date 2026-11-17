import { useCallback, useRef, useState } from 'react';

import { registerUser } from '../api/registerUser';
import { extractDetail, extractFieldErrors } from '../../../shared/api/problemDetails';
import type { FieldErrors, RegisterFormValues, RegisterRequest, RegisterStatus } from '../types';

export interface UseRegisterResult {
  status: RegisterStatus;
  fieldErrors: FieldErrors;
  generalError: string | null;
  submit: (values: RegisterFormValues) => Promise<void>;
}

/**
 * Quản lý trạng thái gửi đăng ký và ánh xạ lỗi server theo trường.
 * Bỏ qua lần gửi trùng khi đang xử lý.
 */
export function useRegister(): UseRegisterResult {
  const [status, setStatus] = useState<RegisterStatus>('idle');
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [generalError, setGeneralError] = useState<string | null>(null);
  const inFlight = useRef(false);

  const submit = useCallback(async (values: RegisterFormValues) => {
    if (inFlight.current) {
      return;
    }
    inFlight.current = true;
    setStatus('loading');
    setFieldErrors({});
    setGeneralError(null);

    const phone = values.phone.trim();
    const payload: RegisterRequest = {
      email: values.email.trim().toLowerCase(),
      password: values.password,
      fullName: values.fullName.trim(),
      ...(phone ? { phone } : {}),
    };

    const result = await registerUser(payload);

    if (result.ok) {
      setStatus('success');
      inFlight.current = false;
      return;
    }

    if (result.networkError) {
      setStatus('error');
      setGeneralError('Không thể kết nối tới máy chủ. Vui lòng kiểm tra mạng và thử lại.');
      inFlight.current = false;
      return;
    }

    if (result.status === 409) {
      setStatus('error');
      setFieldErrors({
        email:
          extractDetail(result.problem)
          ?? 'Email đã được sử dụng. Vui lòng đăng nhập hoặc dùng email khác.',
      });
      inFlight.current = false;
      return;
    }

    if (result.status === 400) {
      const mapped = extractFieldErrors(result.problem);
      setStatus('error');
      if (Object.keys(mapped).length > 0) {
        setFieldErrors(mapped);
      } else {
        setGeneralError(extractDetail(result.problem) ?? 'Dữ liệu không hợp lệ. Vui lòng kiểm tra lại.');
      }
      inFlight.current = false;
      return;
    }

    setStatus('error');
    setGeneralError('Đã xảy ra lỗi, vui lòng thử lại sau.');
    inFlight.current = false;
  }, []);

  return { status, fieldErrors, generalError, submit };
}
