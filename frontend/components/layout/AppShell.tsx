"use client";

import { useState } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { AlertTriangle, BarChart3, BookOpen, CalendarDays, ClipboardCheck, LayoutDashboard, LogOut, Menu, Settings, X } from "lucide-react";
import { useAuth } from "@/components/providers/AuthProvider";
import { LoadingState } from "@/components/ui/States";
import { cn } from "@/lib/cn";
import { GlobalSearch } from "@/components/layout/GlobalSearch";

const NAV = [
  { href: "/dashboard", label: "Dashboard", icon: LayoutDashboard },
  { href: "/decks", label: "Decks", icon: BookOpen },
  { href: "/plan", label: "Study plan", icon: CalendarDays },
  { href: "/exams", label: "Mock exams", icon: ClipboardCheck },
  { href: "/mistakes", label: "Mistakes", icon: AlertTriangle },
  { href: "/analytics", label: "Analytics", icon: BarChart3 },
  { href: "/settings", label: "Settings", icon: Settings },
];

export function AppShell({ children }: { children: React.ReactNode }) {
  const { user, loading, signOut } = useAuth();
  const pathname = usePathname();
  const [open, setOpen] = useState(false);

  const nav = (
    <nav className="flex flex-col gap-1" aria-label="Main">
      {NAV.map(({ href, label, icon: Icon }) => {
        const active = pathname === href || pathname.startsWith(`${href}/`);
        return (
          <Link
            key={href}
            href={href}
            onClick={() => setOpen(false)}
            className={cn(
              "flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition",
              active ? "bg-primary/10 text-primary" : "text-muted hover:bg-black/5 hover:text-foreground dark:hover:bg-white/10",
            )}
            aria-current={active ? "page" : undefined}
          >
            <Icon className="h-4 w-4" />
            {label}
          </Link>
        );
      })}
    </nav>
  );

  return (
    <div className="flex min-h-screen">
      <aside className="hidden w-60 shrink-0 flex-col border-r border-border bg-card p-4 lg:flex">
        <Link href="/dashboard" className="mb-6 px-3 text-lg font-bold tracking-tight text-primary">
          RecallAI
        </Link>
        {nav}
        <div className="mt-auto border-t border-border pt-4">
          {user && (
            <div className="px-3 pb-3">
              <p className="truncate text-sm font-medium">{user.name}</p>
              <p className="truncate text-xs text-muted">{user.email}</p>
            </div>
          )}
          <button
            type="button"
            onClick={signOut}
            className="flex w-full items-center gap-3 rounded-lg px-3 py-2 text-sm text-muted hover:bg-black/5 hover:text-foreground dark:hover:bg-white/10"
          >
            <LogOut className="h-4 w-4" />
            Sign out
          </button>
        </div>
      </aside>

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="sticky top-0 z-30 flex h-14 items-center gap-3 border-b border-border bg-card/90 px-4 backdrop-blur sm:px-6">
          <button
            type="button"
            className="rounded-lg p-2 hover:bg-black/5 lg:hidden dark:hover:bg-white/10"
            onClick={() => setOpen((v) => !v)}
            aria-label={open ? "Close menu" : "Open menu"}
            aria-expanded={open}
          >
            {open ? <X className="h-5 w-5" /> : <Menu className="h-5 w-5" />}
          </button>
          <Link href="/dashboard" className="font-bold text-primary lg:hidden">
            RecallAI
          </Link>
          <div className="ml-auto w-full max-w-md">
            <GlobalSearch />
          </div>
        </header>

        {open && (
          <div className="border-b border-border bg-card p-4 lg:hidden">
            {nav}
            <button
              type="button"
              onClick={signOut}
              className="mt-2 flex w-full items-center gap-3 rounded-lg px-3 py-2 text-sm text-muted hover:bg-black/5"
            >
              <LogOut className="h-4 w-4" />
              Sign out
            </button>
          </div>
        )}

        <main className="mx-auto w-full max-w-6xl flex-1 px-4 py-6 sm:px-6 sm:py-8">
          {loading ? <LoadingState label="Loading your workspace…" /> : children}
        </main>
      </div>
    </div>
  );
}
