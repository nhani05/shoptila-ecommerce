/**
 * Kiểu và tiện ích cho lỗi RFC 7807 `ProblemDetail` do backend trả về.
 */

export interface FieldError {
  field: string;
  message: string;
}

export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  errors?: FieldError[];
  [key: string]: unknown;
}

export function isProblemDetail(value: unknown): value is ProblemDetail {
  if (typeof value !== 'object' || value === null) {
    return false;
  }
  const candidate = value as Record<string, unknown>;
  return (
    typeof candidate.status === 'number' ||
    typeof candidate.title === 'string' ||
    typeof candidate.detail === 'string' ||
    Array.isArray(candidate.errors)
  );
}

/**
 * Ánh xạ mảng `errors[]` thành bản đồ `field -> message`.
 * Nếu một trường có nhiều lỗi, giữ lỗi đầu tiên.
 */
export function extractFieldErrors(problem: ProblemDetail | undefined): Record<string, string> {
  const result: Record<string, string> = {};
  if (!problem?.errors) {
    return result;
  }
  for (const error of problem.errors) {
    if (error?.field && typeof error.message === 'string' && !(error.field in result)) {
      result[error.field] = error.message;
    }
  }
  return result;
}

export function extractDetail(problem: ProblemDetail | undefined): string | undefined {
  return problem?.detail?.trim() || problem?.title?.trim() || undefined;
}
