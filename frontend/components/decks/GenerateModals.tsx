"use client";

import { useRef, useState } from "react";
import { FileText, Upload } from "lucide-react";
import { Button } from "@/components/ui/Button";
import { Input, Textarea } from "@/components/ui/Field";
import { Modal } from "@/components/ui/Modal";
import { AiStatusNotice } from "@/components/decks/AiStatusNotice";
import { useToast } from "@/components/providers/ToastProvider";
import { errorMessage } from "@/lib/api";
import { api } from "@/lib/endpoints";
import { cn } from "@/lib/cn";
import type { GenerateCardsResponse, Quiz } from "@/types";

const MAX_UPLOAD_BYTES = 10 * 1024 * 1024;

export function GenerateCardsModal({
  open,
  onClose,
  deckId,
  onGenerated,
}: {
  open: boolean;
  onClose: () => void;
  deckId: number;
  onGenerated: (result: GenerateCardsResponse) => void;
}) {
  const toast = useToast();
  const [mode, setMode] = useState<"paste" | "upload">("paste");
  const [text, setText] = useState("");
  const [file, setFile] = useState<File | null>(null);
  const [count, setCount] = useState(15);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const fileInput = useRef<HTMLInputElement>(null);

  const reset = () => {
    setText("");
    setFile(null);
    setError(null);
  };

  const pickFile = (picked: File | null) => {
    setError(null);
    if (!picked) return setFile(null);
    if (picked.size > MAX_UPLOAD_BYTES) return setError("File is larger than 10 MB.");
    if (!/\.(txt|pdf|md)$/i.test(picked.name)) return setError("Upload a .txt or .pdf file.");
    setFile(picked);
  };

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const result =
        mode === "paste"
          ? await api.ai.flashcards({ deckId, text, count })
          : await api.ai.flashcardsUpload(deckId, file as File, count);
      toast.success(
        `${result.cardsCreated} cards added${result.cachedChunks > 0 ? " (served from cache)" : ""}`,
      );
      onGenerated(result);
      reset();
      onClose();
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  const canSubmit = mode === "paste" ? text.trim().length >= 20 : file !== null;

  return (
    <Modal
      open={open}
      onClose={() => !busy && onClose()}
      title="Generate cards with AI"
      description="Claude reads only your material and returns question–answer cards, which are validated before they are saved."
      size="lg"
    >
      <form onSubmit={submit} className="space-y-4">
        <AiStatusNotice />
        <div className="flex rounded-lg border border-border p-0.5" role="tablist">
          {(["paste", "upload"] as const).map((m) => (
            <button
              key={m}
              type="button"
              role="tab"
              aria-selected={mode === m}
              onClick={() => setMode(m)}
              className={cn(
                "flex flex-1 items-center justify-center gap-2 rounded-md px-3 py-1.5 text-sm font-medium",
                mode === m ? "bg-primary text-primary-foreground" : "text-muted hover:text-foreground",
              )}
            >
              {m === "paste" ? <FileText className="h-4 w-4" /> : <Upload className="h-4 w-4" />}
              {m === "paste" ? "Paste notes" : "Upload file"}
            </button>
          ))}
        </div>

        {mode === "paste" ? (
          <Textarea
            label="Study material"
            value={text}
            onChange={(e) => setText(e.target.value)}
            placeholder="Paste lecture notes, a chapter summary, or your own write-up…"
            className="[&_textarea]:min-h-48"
            hint={`${text.length.toLocaleString()} characters. Long material is split into chunks automatically.`}
          />
        ) : (
          <div
            className="flex cursor-pointer flex-col items-center gap-2 rounded-xl border-2 border-dashed border-border p-8 text-center hover:border-primary"
            onClick={() => fileInput.current?.click()}
            onDragOver={(e) => e.preventDefault()}
            onDrop={(e) => {
              e.preventDefault();
              pickFile(e.dataTransfer.files[0] ?? null);
            }}
            role="button"
            tabIndex={0}
            onKeyDown={(e) => (e.key === "Enter" || e.key === " ") && fileInput.current?.click()}
          >
            <Upload className="h-6 w-6 text-muted" />
            {file ? (
              <p className="text-sm font-medium">{file.name}</p>
            ) : (
              <p className="text-sm text-muted">Drop a .txt or .pdf here, or click to choose (max 10 MB)</p>
            )}
            <input
              ref={fileInput}
              type="file"
              accept=".txt,.pdf,.md,text/plain,application/pdf"
              className="hidden"
              onChange={(e) => pickFile(e.target.files?.[0] ?? null)}
            />
          </div>
        )}

        <Input
          label="How many cards?"
          type="number"
          min={1}
          max={60}
          value={count}
          onChange={(e) => setCount(Number(e.target.value))}
          className="sm:w-48"
          hint="Fewer may be returned if the material is thin."
        />

        {error && (
          <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-800 dark:bg-red-950/40 dark:text-red-200">
            {error}
          </p>
        )}

        <div className="flex justify-end gap-2">
          <Button type="button" variant="secondary" onClick={onClose} disabled={busy}>
            Cancel
          </Button>
          <Button type="submit" loading={busy} disabled={!canSubmit}>
            {busy ? "Generating…" : "Generate cards"}
          </Button>
        </div>
      </form>
    </Modal>
  );
}

export function GenerateQuizModal({
  open,
  onClose,
  deckId,
  deckName,
  hasCards,
  onGenerated,
}: {
  open: boolean;
  onClose: () => void;
  deckId: number;
  deckName: string;
  hasCards: boolean;
  onGenerated: (quiz: Quiz) => void;
}) {
  const toast = useToast();
  const [title, setTitle] = useState("");
  const [count, setCount] = useState(10);
  const [useText, setUseText] = useState(!hasCards);
  // The modal stays mounted while closed, so re-derive the default each time it opens
  // (cards may have been generated since the page first rendered). Adjusting state during
  // render is React's recommended way to reset derived state on a prop change.
  const [wasOpen, setWasOpen] = useState(open);
  if (open !== wasOpen) {
    setWasOpen(open);
    if (open) setUseText(!hasCards);
  }
  const [text, setText] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const quiz = await api.ai.quiz({ deckId, title: title || undefined, count, text: useText ? text : undefined });
      toast.success(`Quiz ready with ${quiz.questionCount} questions`);
      onGenerated(quiz);
      onClose();
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal open={open} onClose={() => !busy && onClose()} title="Generate a quiz" description="Multiple-choice questions with an explanation for every answer.">
      <form onSubmit={submit} className="space-y-4">
        <AiStatusNotice />
        <Input label="Title" value={title} onChange={(e) => setTitle(e.target.value)} placeholder={`${deckName} quiz`} maxLength={200} />
        <Input label="Questions" type="number" min={1} max={30} value={count} onChange={(e) => setCount(Number(e.target.value))} className="sm:w-48" />
        <label className="flex items-center gap-2 text-sm">
          <input type="checkbox" checked={useText} onChange={(e) => setUseText(e.target.checked)} disabled={!hasCards} />
          {hasCards ? "Use my own material instead of this deck's cards" : "This deck has no cards yet, so paste material to quiz on"}
        </label>
        {useText && (
          <Textarea label="Study material" value={text} onChange={(e) => setText(e.target.value)} className="[&_textarea]:min-h-40" />
        )}
        {error && (
          <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-800 dark:bg-red-950/40 dark:text-red-200">
            {error}
          </p>
        )}
        <div className="flex justify-end gap-2">
          <Button type="button" variant="secondary" onClick={onClose} disabled={busy}>
            Cancel
          </Button>
          <Button type="submit" loading={busy} disabled={useText && text.trim().length < 20}>
            {busy ? "Generating…" : "Generate quiz"}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
