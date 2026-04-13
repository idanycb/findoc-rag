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
    <div className="flex min-h-screen items-center justify-center bg-linear-to-br from-neutral-900 via-neutral-800 to-neutral-700 p-4 sm:p-6">
      <Card className="w-full max-w-md border-white/15 bg-white/95 p-7 shadow-3xl backdrop-blur-xl sm:p-10">
        <div className="mb-8 flex flex-col items-center sm:mb-10">
          <div className="mb-5 rounded-2xl bg-neutral-900 p-4 text-white shadow-2xl shadow-neutral-500/30 sm:mb-6">
            <ShieldCheck size={32} />
          </div>
          <h1 className="text-center text-3xl font-black tracking-tight text-neutral-900">
            RAG Workspace
          </h1>
        </div>

        <form onSubmit={handleLogin} className="space-y-6">
          <div>
            <label className="mb-2 block text-sm font-bold text-neutral-800">
              Username
            </label>
            <input
              type="text"
              name="username"
              required
              className="w-full rounded-2xl border border-neutral-300 bg-neutral-100 px-4 py-3 text-black transition-all focus:border-transparent focus:outline-none focus:ring-2 focus:ring-neutral-500"
            />
          </div>

          <div>
            <label className="mb-2 block text-sm font-bold text-neutral-800">
              Password
            </label>
            <input
              type="password"
              name="password"
              required
              className="w-full rounded-2xl border border-neutral-300 bg-neutral-100 px-4 py-3 text-black transition-all focus:border-transparent focus:outline-none focus:ring-2 focus:ring-neutral-500"
            />
          </div>

          {error && (
            <p className="rounded-xl border border-neutral-300 bg-neutral-100 px-3 py-2 text-sm font-semibold text-neutral-700">
              {error}
            </p>
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
