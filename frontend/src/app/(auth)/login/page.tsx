import { LoginForm } from '@/features/auth/LoginForm';

export const metadata = { title: 'Sign in · FinDoc Analyzer' };

export default function LoginPage() {
  return (
    <div className="min-h-screen bg-[#EBEBEB] flex items-center justify-center px-6 py-12">
      <div className="w-full max-w-[900px] overflow-hidden rounded-[20px] bg-white shadow-[0_2px_24px_rgba(0,0,0,.08)] md:flex">
        <div className="bg-[#111111] px-7 py-9 md:w-[420px] md:flex-none md:p-12 md:flex md:flex-col md:justify-between">
          <div>
            <div className="flex items-center gap-[11px]">
              <div className="flex h-[38px] w-[38px] items-center justify-center rounded-[11px] bg-white">
                <svg xmlns="http://www.w3.org/2000/svg" width={20} height={20} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2.2} strokeLinecap="round" strokeLinejoin="round" className="text-[#111111]"><path d="M15 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7Z"/><path d="M14 2v4a2 2 0 0 0 2 2h4"/><path d="M10 13H8"/><path d="M16 17H8"/><path d="M16 13h-2"/></svg>
              </div>
              <div>
                <div className="text-[15px] font-bold text-white">FinDoc</div>
                <div className="text-[10px] uppercase tracking-[.1em] text-[#777777]">
                  Analyzer
                </div>
              </div>
            </div>
            <h2 className="mt-10 text-[28px] font-bold leading-[1.25] tracking-[-.02em] text-white md:block hidden">
              Financial documents,
              <br />
              analyzed and
              <br />
              reasoned over.
            </h2>
            <h1 className="mt-7 text-2xl font-bold tracking-[-.01em] text-white md:hidden">
              Sign in
            </h1>
            <p className="mt-[6px] text-sm text-[#888888] md:hidden">
              Access your team&apos;s document vault.
            </p>
            <p className="mt-[18px] hidden max-w-[300px] text-sm leading-[1.65] text-[#888888] md:block">
              Upload PDFs and documents. Get grounded answers through RAG-powered chat.
            </p>
          </div>
          <div className="mt-8 hidden flex-wrap gap-2 md:flex">
            <span className="rounded-md border border-[#333333] bg-[#1E1E1E] px-[11px] py-[5px] text-[11px] font-semibold tracking-[.04em] text-[#555555]">
              RAG grounded
            </span>
            <span className="rounded-md border border-[#333333] bg-[#1E1E1E] px-[11px] py-[5px] text-[11px] font-semibold tracking-[.04em] text-[#555555]">
              Team-scoped vault
            </span>
          </div>
        </div>

        <div className="flex flex-1 flex-col justify-center px-7 py-8 md:px-12 md:py-12">
          <LoginForm />
        </div>
      </div>
    </div>
  );
}
