import axios from 'axios';

import { axiosClient } from '../../../shared/api/axiosClient';
import { isProblemDetail, type ProblemDetail } from '../../../shared/api/problemDetails';
import type { RegisterRequest, RegisterResponse } from '../types';

export interface RegisterFailure {
  ok: false;
  status: number | null;
  problem?: ProblemDetail;
  networkError: boolean;
}

export type RegisterResult = { ok: true; data: RegisterResponse } | RegisterFailure;

/**
 * Gọi `POST /api/v1/auth/register` và chuẩn hóa mọi lỗi thành {@link RegisterResult}
 * để hook không phải xử lý chi tiết Axios.
 */
export async function registerUser(payload: RegisterRequest): Promise<RegisterResult> {
  try {
    const response = await axiosClient.post<RegisterResponse>('/auth/register', payload);
    return { ok: true, data: response.data };
  } catch (error) {
    if (axios.isAxiosError(error)) {
      const response = error.response;
      if (response) {
        return {
          ok: false,
          status: response.status,
          problem: isProblemDetail(response.data) ? response.data : undefined,
          networkError: false,
        };
      }
    }
    return { ok: false, status: null, networkError: true };
  }
}
