'use client';

import { SubmitEventHandler, useState } from 'react';
import { useRouter } from 'next/navigation';
import { ShieldCheck } from 'lucide-react';
import Button from '@/components/ui/Button';
import Card from '@/components/ui/Card';
import { setCookie } from '@/lib/api';

export default function LoginPage() {
  const router = useRouter();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleLogin: SubmitEventHandler<HTMLFormElement> = async (e) => {
    e.preventDefault();
    setLoading(true);
    setError(null);

    try {
      const formData = new FormData(e.currentTarget as HTMLFormElement);
      const username = formData.get('username') as string;
      const password = formData.get('password') as string;

      const response = await fetch('/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, password }),
      });

      if (response.status === 401) {
        setError('Invalid credentials');
        return;
      }

      if (!response.ok) {
        throw new Error('Authentication failed');
      }

      const { accessToken: token } = await response.json();
      setCookie('jwtToken', token);
      router.push('/dashboard');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Login failed');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-linear-to-br from-slate-900 via-indigo-900 to-slate-900 flex items-center justify-center p-6">
      <Card className="max-w-md w-full p-12 shadow-3xl border-white/10 bg-white/95 backdrop-blur-xl">
        <div className="flex flex-col items-center mb-10">
          <div className="bg-indigo-600 p-4 rounded-2xl text-white shadow-2xl shadow-indigo-200 mb-6">
            <ShieldCheck size={32} />
          </div>
          <h1 className="text-3xl font-black tracking-tight text-slate-800">
            FinDoc Analyzer
          </h1>
        </div>

        <form onSubmit={handleLogin} className="space-y-6">
          <div>
            <label className="block text-sm font-bold text-slate-700 mb-2">
              Username
            </label>
            <input
              type="text"
              name="username"
              required
              className="w-full px-4 py-3 text-black bg-slate-50 border border-slate-200 rounded-2xl focus:outline-none focus:ring-2 focus:ring-indigo-600 focus:border-transparent transition-all"
            />
          </div>

          <div>
            <label className="block text-sm font-bold text-slate-700 mb-2">
              Password
            </label>
            <input
              type="password"
              name="password"
              required
              className="w-full px-4 py-3 text-black bg-slate-50 border border-slate-200 rounded-2xl focus:outline-none focus:ring-2 focus:ring-indigo-600 focus:border-transparent transition-all"
            />
          </div>

          {error && (
            <p className="text-sm text-rose-600 font-semibold">{error}</p>
          )}

          <Button
            type="submit"
            className="w-full py-4 shadow-2xl"
            disabled={loading}
          >
            {loading ? 'Authenticating...' : 'Sign In'}
          </Button>
        </form>
      </Card>
    </div>
  );
}
