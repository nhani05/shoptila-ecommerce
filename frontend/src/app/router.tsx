import { createBrowserRouter, Navigate } from 'react-router-dom';

import { LoginPage } from '../features/auth/pages/LoginPage';
import { RegisterPage } from '../features/auth/pages/RegisterPage';

export const router = createBrowserRouter([
  {
    path: '/',
    element: <Navigate to="/register" replace />,
  },
  {
    path: '/register',
    element: <RegisterPage />,
  },
  {
    path: '/login',
    element: <LoginPage />,
  },
]);
