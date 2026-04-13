import { MainNavigation } from '@/features/navigation/MainNavigation';

export default function ProtectedLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <div className="flex min-h-screen flex-col overflow-hidden bg-neutral-100 text-neutral-900 font-sans selection:bg-neutral-300 selection:text-black md:h-screen md:flex-row">
      <MainNavigation />
      <main className="relative flex min-h-0 flex-1 flex-col overflow-hidden">
        {children}
      </main>
    </div>
  );
}
