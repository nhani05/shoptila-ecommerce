import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { RegisterForm } from '../components/RegisterForm';
import { registerUser } from '../api/registerUser';
import type { RegisterResponse } from '../types';

vi.mock('../api/registerUser', () => ({
  registerUser: vi.fn(),
}));

const mockedRegisterUser = vi.mocked(registerUser);
const navigateMock = vi.fn();

vi.mock('react-router-dom', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-router-dom')>();
  return {
    ...actual,
    useNavigate: () => navigateMock,
  };
});

const successResponse: RegisterResponse = {
  id: 42,
  email: 'khachhang@example.com',
  fullName: 'Nguyễn Văn A',
  phone: '0987654321',
  role: 'ROLE_CUSTOMER',
  status: 'ACTIVE',
  createdAt: '2026-11-16T10:15:30Z',
};

function renderForm() {
  return render(
    <MemoryRouter initialEntries={['/register']}>
      <RegisterForm />
    </MemoryRouter>,
  );
}

async function fillValidForm() {
  const user = userEvent.setup();
  await user.type(screen.getByLabelText('Họ và tên'), 'Nguyễn Văn A');
  await user.type(screen.getByLabelText('Email'), 'khachhang@example.com');
  await user.type(screen.getByLabelText('Số điện thoại (tùy chọn)'), '0987654321');
  await user.type(screen.getByLabelText('Mật khẩu'), 'MatKhau123');
  await user.type(screen.getByLabelText('Xác nhận mật khẩu'), 'MatKhau123');
  await user.click(screen.getByRole('checkbox', { name: /điều khoản/i }));
  return user;
}

describe('RegisterForm — luồng thành công', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockedRegisterUser.mockResolvedValue({ ok: true, data: successResponse });
  });

  it('gửi form hợp lệ, khóa input khi loading, hiển thị thành công và chuyển /login sau 1,5 giây', async () => {
    let resolveRequest: (value: Awaited<ReturnType<typeof registerUser>>) => void = () => {};
    const pending = new Promise<Awaited<ReturnType<typeof registerUser>>>((resolve) => {
      resolveRequest = resolve;
    });
    mockedRegisterUser.mockReturnValueOnce(pending);

    renderForm();
    const user = await fillValidForm();
    await user.click(screen.getByRole('button', { name: 'Đăng ký' }));

    expect(mockedRegisterUser).toHaveBeenCalledTimes(1);
    expect(screen.getByLabelText('Email')).toBeDisabled();
    expect(screen.getByLabelText('Mật khẩu')).toBeDisabled();
    expect(screen.getByRole('button', { name: /đang xử lý/i })).toBeDisabled();

    resolveRequest({ ok: true, data: successResponse });

    expect(await screen.findByRole('status')).toHaveTextContent(/đăng ký thành công/i);

    await waitFor(() => expect(navigateMock).toHaveBeenCalledWith('/login'), { timeout: 3000 });
  });
});

describe('RegisterForm — trùng email', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('ánh xạ 409 vào ô email, giữ dữ liệu đã nhập và không điều hướng', async () => {
    mockedRegisterUser.mockResolvedValue({
      ok: false,
      status: 409,
      networkError: false,
      problem: {
        type: 'https://api.shoptila.com/errors/email-already-exists',
        title: 'Email đã tồn tại',
        status: 409,
        detail: "Email 'khachhang@example.com' đã được sử dụng. Vui lòng đăng nhập hoặc dùng email khác.",
        instance: '/api/v1/auth/register',
      },
    });

    renderForm();
    const user = await fillValidForm();
    await user.click(screen.getByRole('button', { name: 'Đăng ký' }));

    const emailInput = screen.getByLabelText('Email');
    expect(await screen.findByText(/đã được sử dụng/i)).toBeInTheDocument();
    expect(emailInput).toHaveAttribute('aria-invalid', 'true');
    expect(emailInput).toHaveValue('khachhang@example.com');
    expect(screen.getByLabelText('Họ và tên')).toHaveValue('Nguyễn Văn A');
    expect(screen.getByLabelText('Mật khẩu')).toHaveValue('MatKhau123');
    expect(navigateMock).not.toHaveBeenCalled();
  });
});

describe('RegisterForm — kiểm tra dữ liệu phía client', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('không gọi API khi dữ liệu sai, hiển thị lỗi từng trường và focus trường lỗi đầu tiên', async () => {
    renderForm();
    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: 'Đăng ký' }));

    expect(mockedRegisterUser).not.toHaveBeenCalled();
    expect(screen.getByText('Họ và tên không được để trống')).toBeInTheDocument();
    expect(screen.getByText('Email không được để trống')).toBeInTheDocument();
    expect(screen.getByText('Mật khẩu không được để trống')).toBeInTheDocument();
    expect(screen.getByText('Bạn phải đồng ý với điều khoản sử dụng')).toBeInTheDocument();
    expect(screen.getByLabelText('Họ và tên')).toHaveFocus();
    expect(screen.getByLabelText('Họ và tên')).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByLabelText('Email')).toHaveAttribute('aria-invalid', 'true');
  });

  it('ánh xạ errors[] của 400 vào đúng trường và focus trường lỗi đầu tiên', async () => {
    mockedRegisterUser.mockResolvedValue({
      ok: false,
      status: 400,
      networkError: false,
      problem: {
        type: 'https://api.shoptila.com/errors/validation-failed',
        title: 'Dữ liệu không hợp lệ',
        status: 400,
        detail: 'Một hoặc nhiều trường không hợp lệ.',
        instance: '/api/v1/auth/register',
        errors: [
          { field: 'email', message: 'Email không hợp lệ từ server' },
          { field: 'fullName', message: 'Họ tên không hợp lệ từ server' },
        ],
      },
    });

    renderForm();
    const user = await fillValidForm();
    await user.click(screen.getByRole('button', { name: 'Đăng ký' }));

    expect(await screen.findByText('Email không hợp lệ từ server')).toBeInTheDocument();
    expect(screen.getByText('Họ tên không hợp lệ từ server')).toBeInTheDocument();
    expect(screen.getByLabelText('Email')).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByLabelText('Họ và tên')).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByLabelText('Họ và tên')).toHaveFocus();
    expect(navigateMock).not.toHaveBeenCalled();
  });
});
