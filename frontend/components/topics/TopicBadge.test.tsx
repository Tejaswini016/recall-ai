import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { TopicBadge, topicCategoryLabel } from "./TopicBadge";

describe("TopicBadge", () => {
  it("labels every category for a human", () => {
    expect(topicCategoryLabel("CRITICAL")).toBe("Critical");
    expect(topicCategoryLabel("WEAK")).toBe("Weak");
    expect(topicCategoryLabel("GOOD")).toBe("Good");
    expect(topicCategoryLabel("STRONG")).toBe("Strong");
    expect(topicCategoryLabel("UNRATED")).toBe("Not rated yet");
  });

  it("renders the label", () => {
    render(<TopicBadge category="WEAK" />);
    expect(screen.getByText("Weak")).toBeInTheDocument();
  });
});
