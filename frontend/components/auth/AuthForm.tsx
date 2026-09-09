"use client";

import { useState } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Field";
import { useToast } from "@/components/providers/ToastProvider";
import { ApiRequestError, errorMessage } from "@/lib/api";
import { setToken } from "@/lib/auth";
import { api } from "@/lib/endpoints";

const SAFE_NEXT = /^\/(?!\/)[\w\-/?=&.]*$/;

export function AuthForm({ mode }: { mode: "login" | "register" }) {
  const router = useRouter();
  const params = useSearchParams();
  const toast = useToast();
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const onSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    setLoading(true);
    setFieldErrors({});
    setFormError(null);
    try {
      const response =
        mode === "login"
          ? await api.auth.login({ email, password })
          : await api.auth.register({ name, email, password });
      setToken(response.token);
      toast.success(mode === "login" ? `Welcome back, ${response.user.name}` : "Account created. Welcome!");
      const next = params.get("next");
      router.replace(next && SAFE_NEXT.test(next) ? next : "/dashboard");
    } catch (error) {
      if (error instanceof ApiRequestError && Object.keys(error.fieldErrors).length) {
        setFieldErrors(error.fieldErrors);
      } else {
        setFormError(errorMessage(error));
      }
      setLoading(false);
    }
  };

  return (
    <form onSubmit={onSubmit} className="space-y-4" noValidate>
      <div>
        <h1 className="text-xl font-semibold">{mode === "login" ? "Log in" : "Create your account"}</h1>
        <p className="mt-1 text-sm text-muted">
          {mode === "login" ? "Pick up where you left off." : "Start turning notes into lasting memory."}
        </p>
      </div>
      {mode === "register" && (
        <Input
          label="Name"
          value={name}
          onChange={(e) => setName(e.target.value)}
          autoComplete="name"
          maxLength={100}
          required
          error={fieldErrors.name}
        />
      )}
      <Input
        label="Email"
        type="email"
        value={email}
        onChange={(e) => setEmail(e.target.value)}
        autoComplete="email"
        required
        error={fieldErrors.email}
      />
      <Input
        label="Password"
        type="password"
        value={password}
        onChange={(e) => setPassword(e.target.value)}
        autoComplete={mode === "login" ? "current-password" : "new-password"}
        minLength={8}
        required
        error={fieldErrors.password}
        hint={mode === "register" ? "At least 8 characters." : undefined}
      />
      {formError && (
        <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-800 dark:bg-red-950/40 dark:text-red-200">
          {formError}
        </p>
      )}
      <Button type="submit" className="w-full" loading={loading}>
        {mode === "login" ? "Log in" : "Create account"}
      </Button>
      <p className="text-center text-sm text-muted">
        {mode === "login" ? (
          <>
            New here?{" "}
            <Link href="/register" className="font-medium text-primary hover:underline">
              Create an account
            </Link>
          </>
        ) : (
          <>
            Already have an account?{" "}
            <Link href="/login" className="font-medium text-primary hover:underline">
              Log in
            </Link>
          </>
        )}
      </p>
    </form>
  );
}
