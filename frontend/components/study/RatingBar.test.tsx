import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { RatingBar } from "./RatingBar";

describe("RatingBar", () => {
  it("renders all six SM-2 quality buttons with their shortcut keys", () => {
    render(<RatingBar onRate={() => {}} />);

    const group = screen.getByRole("group", { name: /rate your recall/i });
    const buttons = group.querySelectorAll("button");
    expect(buttons).toHaveLength(6);
    expect(screen.getByRole("button", { name: /again/i })).toHaveTextContent("1");
    expect(screen.getByRole("button", { name: /excellent/i })).toHaveTextContent("5");
  });

  it("reports the numeric quality for the clicked button", async () => {
    const onRate = vi.fn();
    render(<RatingBar onRate={onRate} />);

    await userEvent.click(screen.getByRole("button", { name: /good/i }));
    await userEvent.click(screen.getByRole("button", { name: /blank/i }));

    expect(onRate).toHaveBeenNthCalledWith(1, 3);
    expect(onRate).toHaveBeenNthCalledWith(2, 0);
  });

  it("disables every button while a rating is in flight", () => {
    render(<RatingBar onRate={() => {}} disabled />);

    screen.getAllByRole("button").forEach((button) => expect(button).toBeDisabled());
  });
});
