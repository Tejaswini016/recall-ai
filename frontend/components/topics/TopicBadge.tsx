import { Badge } from "@/components/ui/Badge";
import type { TopicCategory } from "@/types";

const LABELS: Record<TopicCategory, { label: string; tone: "danger" | "warning" | "primary" | "success" | "neutral" }> = {
  CRITICAL: { label: "Critical", tone: "danger" },
  WEAK: { label: "Weak", tone: "warning" },
  GOOD: { label: "Good", tone: "primary" },
  STRONG: { label: "Strong", tone: "success" },
  UNRATED: { label: "Not rated yet", tone: "neutral" },
};

export function topicCategoryLabel(category: TopicCategory): string {
  return LABELS[category].label;
}

export function TopicBadge({ category }: { category: TopicCategory }) {
  const { label, tone } = LABELS[category];
  return <Badge tone={tone}>{label}</Badge>;
}
