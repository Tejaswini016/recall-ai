"use client";

import { useState } from "react";
import { Button } from "@/components/ui/Button";
import { Input, Textarea } from "@/components/ui/Field";
import { Modal } from "@/components/ui/Modal";
import { TagInput } from "@/components/ui/TagInput";
import { useToast } from "@/components/providers/ToastProvider";
import { ApiRequestError, errorMessage } from "@/lib/api";
import { api } from "@/lib/endpoints";
import type { Deck } from "@/types";

export function DeckFormModal({
  open,
  onClose,
  deck,
  onSaved,
}: {
  open: boolean;
  onClose: () => void;
  deck?: Deck | null;
  onSaved: (deck: Deck) => void;
}) {
  return (
    <Modal open={open} onClose={onClose} title={deck ? "Edit deck" : "New deck"}>
      {open && <DeckForm deck={deck} onClose={onClose} onSaved={onSaved} />}
    </Modal>
  );
}

function DeckForm({ deck, onClose, onSaved }: { deck?: Deck | null; onClose: () => void; onSaved: (deck: Deck) => void }) {
  const toast = useToast();
  const [name, setName] = useState(deck?.name ?? "");
  const [subject, setSubject] = useState(deck?.subject ?? "");
  const [description, setDescription] = useState(deck?.description ?? "");
  const [tags, setTags] = useState<string[]>(deck?.tags ?? []);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [saving, setSaving] = useState(false);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaving(true);
    setErrors({});
    try {
      const body = { name, subject, description, tags };
      const saved = deck ? await api.decks.update(deck.id, body) : await api.decks.create(body);
      toast.success(deck ? "Deck updated" : "Deck created");
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
      <Input label="Name" value={name} onChange={(e) => setName(e.target.value)} maxLength={150} required error={errors.name} />
      <Input
        label="Subject"
        value={subject}
        onChange={(e) => setSubject(e.target.value)}
        maxLength={100}
        placeholder="e.g. Biology"
        error={errors.subject}
      />
      <Textarea
        label="Description"
        value={description}
        onChange={(e) => setDescription(e.target.value)}
        maxLength={2000}
        error={errors.description}
      />
      <TagInput label="Tags" value={tags} onChange={setTags} />
      <div className="flex justify-end gap-2 pt-2">
        <Button type="button" variant="secondary" onClick={onClose} disabled={saving}>
          Cancel
        </Button>
        <Button type="submit" loading={saving}>
          {deck ? "Save changes" : "Create deck"}
        </Button>
      </div>
    </form>
  );
}
