import { describe, expect, it } from "vitest";
import { QUALITY_LABELS, formatDuration, greeting, pluralize, relativeDay } from "./format";

describe("format helpers", () => {
  it("describes due dates relative to today", () => {
    const today = new Date(2026, 8, 9);
    expect(relativeDay("2026-09-09", today)).toBe("today");
    expect(relativeDay("2026-09-10", today)).toBe("tomorrow");
    expect(relativeDay("2026-09-08", today)).toBe("yesterday");
    expect(relativeDay("2026-09-05", today)).toBe("4 days ago");
    expect(relativeDay("2026-09-20", today)).toBe("in 11 days");
  });

  it("formats durations", () => {
    expect(formatDuration(42)).toBe("42s");
    expect(formatDuration(125)).toBe("2m 5s");
    expect(formatDuration(null)).toBe("—");
  });

  it("greets by time of day", () => {
    expect(greeting(new Date(2026, 0, 1, 8))).toBe("Good morning");
    expect(greeting(new Date(2026, 0, 1, 14))).toBe("Good afternoon");
    expect(greeting(new Date(2026, 0, 1, 20))).toBe("Good evening");
  });

  it("pluralizes counts", () => {
    expect(pluralize(1, "card")).toBe("1 card");
    expect(pluralize(3, "card")).toBe("3 cards");
  });

  it("maps every SM-2 quality 0-5 to a single-key shortcut", () => {
    expect(QUALITY_LABELS.map((q) => q.quality)).toEqual([0, 1, 2, 3, 4, 5]);
    expect(QUALITY_LABELS.map((q) => q.key)).toEqual(["0", "1", "2", "3", "4", "5"]);
    expect(QUALITY_LABELS.find((q) => q.quality === 1)?.label).toBe("Again");
    expect(QUALITY_LABELS.find((q) => q.quality === 5)?.label).toBe("Excellent");
  });
});
