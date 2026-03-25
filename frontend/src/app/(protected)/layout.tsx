import { MainNavigation } from '@/features/navigation/MainNavigation';

export default function ProtectedLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <div className="flex h-screen bg-[#FDFDFD] text-slate-900 overflow-hidden font-sans selection:bg-indigo-100 selection:text-indigo-900">
      <MainNavigation />
      <main className="flex-1 flex flex-col relative overflow-hidden">
        {children}
      </main>
    </div>
  );
}
