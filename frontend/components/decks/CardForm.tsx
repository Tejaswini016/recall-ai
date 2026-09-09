"use client";

import { useState } from "react";
import { Button } from "@/components/ui/Button";
import { Input, Textarea } from "@/components/ui/Field";
import { Modal } from "@/components/ui/Modal";
import { TagInput } from "@/components/ui/TagInput";
import { useToast } from "@/components/providers/ToastProvider";
import { ApiRequestError, errorMessage } from "@/lib/api";
import { api } from "@/lib/endpoints";
import type { Card } from "@/types";

export function CardFormModal({
  open,
  onClose,
  deckId,
  card,
  onSaved,
}: {
  open: boolean;
  onClose: () => void;
  deckId: number;
  card?: Card | null;
  onSaved: (card: Card) => void;
}) {
  return (
    <Modal open={open} onClose={onClose} title={card ? "Edit card" : "Add card"} size="lg">
      {open && <CardForm deckId={deckId} card={card} onClose={onClose} onSaved={onSaved} />}
    </Modal>
  );
}

function CardForm({
  deckId,
  card,
  onClose,
  onSaved,
}: {
  deckId: number;
  card?: Card | null;
  onClose: () => void;
  onSaved: (card: Card) => void;
}) {
  const toast = useToast();
  const [question, setQuestion] = useState(card?.question ?? "");
  const [answer, setAnswer] = useState(card?.answer ?? "");
  const [explanation, setExplanation] = useState(card?.explanation ?? "");
  const [topic, setTopic] = useState(card?.topic ?? "");
  const [tags, setTags] = useState<string[]>(card?.tags ?? []);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [saving, setSaving] = useState(false);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaving(true);
    setErrors({});
    try {
      const body = { question, answer, explanation, topic, tags };
      const saved = card ? await api.cards.update(card.id, body) : await api.decks.addCard(deckId, body);
      toast.success(card ? "Card updated" : "Card added");
      onSaved(saved);
      onClose();
    } catch (error) {
      if (error instanceof ApiRequestError && Object.keys(error.fieldErrors).length) {
        setErrors(error.fieldErrors);
      } else {
        toast.error(errorMessage(error));
      }
    } finally {
      setSaving(false);
    }
  };

  return (
    <form onSubmit={submit} className="space-y-4">
      <Textarea label="Question" value={question} onChange={(e) => setQuestion(e.target.value)} maxLength={2000} required error={errors.question} />
      <Textarea label="Answer" value={answer} onChange={(e) => setAnswer(e.target.value)} maxLength={5000} required error={errors.answer} />
      <Textarea
        label="Explanation"
        value={explanation}
        onChange={(e) => setExplanation(e.target.value)}
        maxLength={5000}
        hint="Shown after you reveal the answer."
        error={errors.explanation}
      />
      <div className="grid gap-4 sm:grid-cols-2">
        <Input label="Topic" value={topic} onChange={(e) => setTopic(e.target.value)} maxLength={150} placeholder="e.g. Cell energy" error={errors.topic} />
        <TagInput label="Tags" value={tags} onChange={setTags} />
      </div>
      <div className="flex justify-end gap-2 pt-2">
        <Button type="button" variant="secondary" onClick={onClose} disabled={saving}>
          Cancel
        </Button>
        <Button type="submit" loading={saving}>
          {card ? "Save changes" : "Add card"}
        </Button>
      </div>
    </form>
  );
}
