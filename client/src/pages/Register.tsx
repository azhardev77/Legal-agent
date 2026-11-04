import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api, setToken, type AuthResponse } from '../api/client';

export default function Register() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [companyName, setCompanyName] = useState('');
  const register = useMutation({
    mutationFn: () =>
      api<AuthResponse>('/api/v1/auth/register', {
        method: 'POST',
        body: JSON.stringify({ email, password, companyName }),
      }),
    onSuccess: (data) => {
      setToken(data.accessToken);
      queryClient.setQueryData(['me'], data.user);
      navigate('/');
    },
  });

  return (
    <main>
      <h1>TenderOps AI — Register</h1>
      <form
        onSubmit={(e) => {
          e.preventDefault();
          register.mutate();
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
          Password (min 8 chars)
          <input
            name="password"
            type="password"
            autoComplete="new-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
            minLength={8}
          />
        </label>
        <label>
          Company name
          <input
            name="companyName"
            value={companyName}
            onChange={(e) => setCompanyName(e.target.value)}
            required
          />
        </label>
        <button type="submit" disabled={register.isPending}>
          {register.isPending ? 'Registering…' : 'Register'}
        </button>
      </form>
      {register.isError && <p role="alert">{(register.error as Error).message}</p>}
      <p>
        Have an account? <Link to="/login">Login</Link>
      </p>
    </main>
  );
}
