import { AppSidebar } from '@/features/shell/AppSidebar';
import { MobileNav } from '@/features/shell/MobileNav';

export default function WorkspaceLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex min-h-screen bg-[#EBEBEB]">
      <div className="hidden md:flex">
        <AppSidebar />
      </div>
      <main className="flex-1 min-w-0 flex flex-col pb-17.5 md:pb-0">{children}</main>
      <MobileNav />
    </div>
  );
}
