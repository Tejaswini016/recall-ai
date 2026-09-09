"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { setUnauthorizedHandler } from "@/lib/api";
import { clearToken, getToken, setToken } from "@/lib/auth";
import { api } from "@/lib/endpoints";
import type { AuthResponse, User } from "@/types";

interface AuthContextValue {
  user: User | null;
  loading: boolean;
  signIn: (response: AuthResponse) => void;
  signOut: () => void;
  refresh: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

async function loadCurrentUser(): Promise<User | null> {
  if (!getToken()) return null;
  try {
    return await api.auth.me();
  } catch {
    return null;
  }
}

/**
 * Loads the current user from the token cookie. The proxy already redirects visitors
 * without a token, so this mostly handles expired tokens and populates the user profile.
 */
export function AuthProvider({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);

  const signOut = useCallback(() => {
    clearToken();
    setUser(null);
    router.replace("/login");
  }, [router]);

  const refresh = useCallback(async () => {
    const loaded = await loadCurrentUser();
    setUser(loaded);
    setLoading(false);
  }, []);

  useEffect(() => {
    setUnauthorizedHandler(signOut);
    let cancelled = false;
    loadCurrentUser().then((loaded) => {
      if (cancelled) return;
      setUser(loaded);
      setLoading(false);
    });
    return () => {
      cancelled = true;
      setUnauthorizedHandler(null);
    };
  }, [signOut]);

  const signIn = useCallback((response: AuthResponse) => {
    setToken(response.token);
    setUser(response.user);
    setLoading(false);
  }, []);

  const value = useMemo(() => ({ user, loading, signIn, signOut, refresh }), [user, loading, signIn, signOut, refresh]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used within AuthProvider");
  return ctx;
}
