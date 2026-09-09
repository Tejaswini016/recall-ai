import { forwardRef, useId } from "react";
import { cn } from "@/lib/cn";

interface FieldProps {
  label: string;
  error?: string;
  hint?: string;
  className?: string;
}

const inputClass =
  "w-full rounded-lg border border-border bg-card px-3 py-2 text-sm text-foreground placeholder:text-muted focus:outline-none focus-visible:ring-2 focus-visible:ring-primary disabled:opacity-60";

export const Input = forwardRef<HTMLInputElement, FieldProps & React.InputHTMLAttributes<HTMLInputElement>>(
  function Input({ label, error, hint, className, id, ...props }, ref) {
    const generated = useId();
    const inputId = id ?? generated;
    return (
      <div className={cn("space-y-1", className)}>
        <label htmlFor={inputId} className="block text-sm font-medium">
          {label}
        </label>
        <input
          ref={ref}
          id={inputId}
          aria-invalid={error ? true : undefined}
          aria-describedby={error ? `${inputId}-error` : undefined}
          className={cn(inputClass, error && "border-danger")}
          {...props}
        />
        {error ? (
          <p id={`${inputId}-error`} className="text-xs text-danger">
            {error}
          </p>
        ) : hint ? (
          <p className="text-xs text-muted">{hint}</p>
        ) : null}
      </div>
    );
  },
);

export const Textarea = forwardRef<
  HTMLTextAreaElement,
  FieldProps & React.TextareaHTMLAttributes<HTMLTextAreaElement>
>(function Textarea({ label, error, hint, className, id, ...props }, ref) {
  const generated = useId();
  const inputId = id ?? generated;
  return (
    <div className={cn("space-y-1", className)}>
      <label htmlFor={inputId} className="block text-sm font-medium">
        {label}
      </label>
      <textarea
        ref={ref}
        id={inputId}
        aria-invalid={error ? true : undefined}
        className={cn(inputClass, "min-h-24 resize-y", error && "border-danger")}
        {...props}
      />
      {error ? <p className="text-xs text-danger">{error}</p> : hint ? <p className="text-xs text-muted">{hint}</p> : null}
    </div>
  );
});

export const Select = forwardRef<HTMLSelectElement, FieldProps & React.SelectHTMLAttributes<HTMLSelectElement>>(
  function Select({ label, error, hint, className, id, children, ...props }, ref) {
    const generated = useId();
    const inputId = id ?? generated;
    return (
      <div className={cn("space-y-1", className)}>
        <label htmlFor={inputId} className="block text-sm font-medium">
          {label}
        </label>
        <select ref={ref} id={inputId} className={cn(inputClass, error && "border-danger")} {...props}>
          {children}
        </select>
        {error ? <p className="text-xs text-danger">{error}</p> : hint ? <p className="text-xs text-muted">{hint}</p> : null}
      </div>
    );
  },
);
