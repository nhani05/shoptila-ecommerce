import { RegisterForm } from '../components/RegisterForm';

export function RegisterPage() {
  return (
    <main className="auth-page">
      <section className="auth-card" aria-labelledby="register-title">
        <h1 id="register-title">Đăng ký tài khoản</h1>
        <p className="auth-subtitle">Tạo tài khoản khách hàng để mua sắm tại Shoptila.</p>
        <RegisterForm />
      </section>
    </main>
  );
}
