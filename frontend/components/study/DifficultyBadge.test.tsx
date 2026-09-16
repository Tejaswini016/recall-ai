import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { DifficultyBadge, difficultyLabel } from "./DifficultyBadge";

describe("DifficultyBadge", () => {
  it("labels every tier", () => {
    expect(difficultyLabel("EASY")).toBe("Easy");
    expect(difficultyLabel("MEDIUM")).toBe("Medium");
    expect(difficultyLabel("HARD")).toBe("Hard");
    expect(difficultyLabel("EXPERT")).toBe("Expert");
  });

  it("renders the tier", () => {
    render(<DifficultyBadge tier="EXPERT" />);
    expect(screen.getByText("Expert")).toBeInTheDocument();
  });
});
