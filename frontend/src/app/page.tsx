'use client';

// This is the root page of the application.
export default function RootPage() {
  return (
    <div className="flex h-screen items-center justify-center bg-linear-to-br from-neutral-950 via-neutral-900 to-neutral-800">
      <div className="h-16 w-16 animate-spin rounded-full border-8 border-white/20 border-t-white"></div>
    </div>
  );
}
