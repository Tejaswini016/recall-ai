import { Badge } from "@/components/ui/Badge";
import type { DifficultyTier } from "@/types";

const TIERS: Record<DifficultyTier, { label: string; tone: "success" | "primary" | "warning" | "danger" }> = {
  EASY: { label: "Easy", tone: "success" },
  MEDIUM: { label: "Medium", tone: "primary" },
  HARD: { label: "Hard", tone: "warning" },
  EXPERT: { label: "Expert", tone: "danger" },
};

export function difficultyLabel(tier: DifficultyTier): string {
  return TIERS[tier].label;
}

/** The adaptive difficulty tier of a card, colour-coded from easy (green) to expert (red). */
export function DifficultyBadge({ tier }: { tier: DifficultyTier }) {
  const { label, tone } = TIERS[tier];
  return <Badge tone={tone}>{label}</Badge>;
}
