import { describe, expect, it } from "vitest";
import { DIFFICULTY_LABELS, TYPE_LABELS, formatClock } from "./examLabels";

describe("exam labels", () => {
  it("formats a countdown as m:ss and never goes negative", () => {
    expect(formatClock(0)).toBe("0:00");
    expect(formatClock(65)).toBe("1:05");
    expect(formatClock(3599)).toBe("59:59");
    expect(formatClock(-4)).toBe("0:00");
  });

  it("labels every difficulty and type", () => {
    expect(Object.keys(DIFFICULTY_LABELS)).toEqual(["EASY", "MEDIUM", "HARD", "EXPERT"]);
    expect(TYPE_LABELS.SHORT_ANSWER).toBe("Short answer");
  });
});
