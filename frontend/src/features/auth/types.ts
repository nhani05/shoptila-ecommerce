export interface RegisterRequest {
  email: string;
  password: string;
  fullName: string;
  phone?: string;
}

export interface RegisterResponse {
  id: number;
  email: string;
  fullName: string;
  phone: string | null;
  role: 'ROLE_CUSTOMER' | 'ROLE_ADMIN';
  status: 'ACTIVE' | 'INACTIVE';
  createdAt: string;
}

export interface RegisterFormValues {
  fullName: string;
  email: string;
  phone: string;
  password: string;
  confirmPassword: string;
  terms: boolean;
}

export type FieldErrors = Record<string, string>;

export type RegisterStatus = 'idle' | 'loading' | 'success' | 'error';
