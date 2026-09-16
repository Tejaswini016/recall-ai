"use client";

import { useId, useState } from "react";
import { X } from "lucide-react";

/**
 * Comma- or Enter-separated tag editor. By default tags are lower-cased to match the API's tag
 * normalization; topic lists keep their case with {@code preserveCase}.
 */
export function TagInput({
  label,
  value,
  onChange,
  max = 10,
  maxLength = 30,
  preserveCase = false,
  placeholder,
  hint,
}: {
  label: string;
  value: string[];
  onChange: (tags: string[]) => void;
  max?: number;
  maxLength?: number;
  preserveCase?: boolean;
  placeholder?: string;
  hint?: string;
}) {
  const [draft, setDraft] = useState("");
  const id = useId();

  const commit = () => {
    const tag = preserveCase ? draft.trim() : draft.trim().toLowerCase();
    setDraft("");
    if (!tag || value.includes(tag) || value.length >= max) return;
    onChange([...value, tag]);
  };

  return (
    <div className="space-y-1">
      <label htmlFor={id} className="block text-sm font-medium">
        {label}
      </label>
      <div className="flex flex-wrap items-center gap-1.5 rounded-lg border border-border bg-card px-2 py-1.5 focus-within:ring-2 focus-within:ring-primary">
        {value.map((tag) => (
          <span key={tag} className="inline-flex items-center gap-1 rounded-full bg-primary/10 px-2 py-0.5 text-xs font-medium text-primary">
            {tag}
            <button type="button" onClick={() => onChange(value.filter((t) => t !== tag))} aria-label={`Remove ${tag}`}>
              <X className="h-3 w-3" />
            </button>
          </span>
        ))}
        <input
          id={id}
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Enter" || e.key === ",") {
              e.preventDefault();
              commit();
            } else if (e.key === "Backspace" && !draft && value.length) {
              onChange(value.slice(0, -1));
            }
          }}
          onBlur={commit}
          placeholder={value.length ? "" : (placeholder ?? "Add a tag and press Enter")}
          maxLength={maxLength}
          className="min-w-32 flex-1 bg-transparent px-1 py-0.5 text-sm outline-none placeholder:text-muted"
        />
      </div>
      <p className="text-xs text-muted">{hint ?? `Up to ${max} tags. Press Enter or comma to add.`}</p>
    </div>
  );
}
