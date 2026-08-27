import Link from 'next/link';

export default function NotFound() {
  return (
    <div className="flex min-h-screen items-center justify-center bg-[#EBEBEB] px-6">
      <div className="w-full max-w-105 rounded-2xl bg-white p-7 text-center shadow-[0_2px_16px_rgba(0,0,0,.07)]">
        <p className="text-[11px] font-bold uppercase tracking-[.12em] text-[#AAAAAA]">
          404
        </p>
        <h1 className="mt-2 text-xl font-bold tracking-[-.01em] text-[#111111]">
          Page not found
        </h1>
        <p className="mt-3 text-sm leading-[1.6] text-[#777777]">
          This route does not exist in FinDoc Analyzer.
        </p>
        <Link
          href="/"
          className="mt-6 inline-flex h-10 items-center justify-center rounded-lg bg-[#111111] px-5 text-sm font-semibold text-white hover:bg-[#333333]"
        >
          Go home
        </Link>
      </div>
    </div>
  );
}
