import { Link } from 'react-router-dom';

/**
 * Placeholder cho US-02 (đăng nhập). Chỉ đóng vai trò điểm đến sau khi đăng ký thành công.
 */
export function LoginPage() {
  return (
    <main className="auth-page">
      <section className="auth-card" aria-labelledby="login-title">
        <h1 id="login-title">Đăng nhập</h1>
        <p className="auth-subtitle">Chức năng đăng nhập sẽ được triển khai ở US-02.</p>
        <p className="form-footer">
          Chưa có tài khoản? <Link to="/register">Đăng ký ngay</Link>
        </p>
      </section>
    </main>
  );
}
