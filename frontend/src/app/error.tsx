'use client';

export default function Error({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  return (
    <div className="flex min-h-screen items-center justify-center bg-[#EBEBEB] px-6">
      <div className="w-full max-w-[420px] rounded-2xl bg-white p-7 text-center shadow-[0_2px_16px_rgba(0,0,0,.07)]">
        <h1 className="text-xl font-bold tracking-[-.01em] text-[#111111]">
          Something went wrong
        </h1>
        <p className="mt-3 text-sm leading-[1.6] text-[#777777]">
          {error.message || 'The page could not be rendered. Try again or return later.'}
        </p>
        <button
          type="button"
          onClick={reset}
          className="mt-6 inline-flex h-10 items-center justify-center rounded-lg bg-[#111111] px-5 text-sm font-semibold text-white hover:bg-[#333333]"
        >
          Try again
        </button>
      </div>
    </div>
  );
}
