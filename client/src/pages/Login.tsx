import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api, setToken, type AuthResponse } from '../api/client';

export default function Login() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const login = useMutation({
    mutationFn: () =>
      api<AuthResponse>('/api/v1/auth/login', {
        method: 'POST',
        body: JSON.stringify({ email, password }),
      }),
    onSuccess: (data) => {
      setToken(data.accessToken);
      queryClient.setQueryData(['me'], data.user);
      navigate('/');
    },
  });

  return (
    <main>
      <h1>TenderOps AI — Login</h1>
      <form
        onSubmit={(e) => {
          e.preventDefault();
          login.mutate();
        }}
      >
        <label>
          Email
          <input
            name="email"
            type="email"
            autoComplete="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
          />
        </label>
        <label>
          Password
          <input
            name="password"
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
          />
        </label>
        <button type="submit" disabled={login.isPending}>
          {login.isPending ? 'Logging in…' : 'Login'}
        </button>
      </form>
      {login.isError && <p role="alert">{(login.error as Error).message}</p>}
      <p>
        No account? <Link to="/register">Register</Link>
      </p>
    </main>
  );
}
