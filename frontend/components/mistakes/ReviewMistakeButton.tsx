"use client";

import { useState } from "react";
import Link from "next/link";
import { BookPlus, CheckCircle2 } from "lucide-react";
import { useToast } from "@/components/providers/ToastProvider";
import { Button } from "@/components/ui/Button";
import { errorMessage } from "@/lib/api";
import { api } from "@/lib/endpoints";
import type { MistakeFlashcard, MistakeStatus } from "@/types";

/**
 * "Review this mistake": asks the backend for an explanatory flashcard (model-written when
 * configured, otherwise built from the stored answer) and shows where it went. Once a card
 * exists the button becomes a link to study it.
 */
export function ReviewMistakeButton({
  mistakeId,
  status,
  cardId,
  onConverted,
  size = "sm",
}: {
  mistakeId: number;
  status: MistakeStatus | null;
  cardId: number | null;
  onConverted?: (result: MistakeFlashcard) => void;
  size?: "sm" | "md";
}) {
  const toast = useToast();
  const [busy, setBusy] = useState(false);
  const [created, setCreated] = useState<MistakeFlashcard | null>(null);

  const converted = created !== null || (status === "CONVERTED" && cardId !== null);
  if (converted) {
    return (
      <span className="inline-flex items-center gap-2 text-xs text-muted">
        <CheckCircle2 className="h-4 w-4 text-success" aria-hidden />
        Flashcard added{created ? ` to ${created.card.deckId ? "your deck" : "your cards"}, due today` : ""}
        <Link href="/study/all" className="font-medium text-primary hover:underline">
          Study now
        </Link>
      </span>
    );
  }

  const convert = async () => {
    setBusy(true);
    try {
      const result = await api.mistakes.toFlashcard(mistakeId);
      setCreated(result);
      toast.success(
        result.aiGenerated
          ? "An explanatory flashcard was generated and scheduled for today"
          : "A flashcard was built from the correct answer and scheduled for today",
      );
      onConverted?.(result);
    } catch (error) {
      toast.error(errorMessage(error));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Button size={size} variant="secondary" onClick={convert} loading={busy}>
      <BookPlus className="h-4 w-4" />
      Review this mistake
    </Button>
  );
}
