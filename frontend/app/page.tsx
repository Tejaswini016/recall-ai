import Link from "next/link";

const features = [
  {
    title: "AI-generated flashcards",
    body: "Paste notes or upload a PDF and get concise, validated question and answer cards.",
  },
  {
    title: "SM-2 spaced repetition",
    body: "Every review reschedules the card so you study only what is actually due today.",
  },
  {
    title: "Quizzes with explanations",
    body: "Multiple-choice quizzes built from your decks, with an explanation for every miss.",
  },
];

export default function HomePage() {
  return (
    <main className="flex flex-1 flex-col items-center justify-center px-6 py-16">
      <div className="max-w-2xl text-center">
        <p className="mb-3 text-sm font-semibold uppercase tracking-wide text-primary">
          RecallAI
        </p>
        <h1 className="text-4xl font-bold tracking-tight sm:text-5xl">
          Study less. Remember more.
        </h1>
        <p className="mt-4 text-lg text-muted">
          Turn your study material into flashcards and quizzes, then let spaced
          repetition tell you exactly what to review each day.
        </p>
        <div className="mt-8 flex flex-wrap justify-center gap-3">
          <Link
            href="/register"
            className="rounded-lg bg-primary px-5 py-2.5 text-sm font-semibold text-primary-foreground shadow-sm transition hover:opacity-90 focus:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2"
          >
            Get started
          </Link>
          <Link
            href="/login"
            className="rounded-lg border border-border bg-card px-5 py-2.5 text-sm font-semibold transition hover:bg-background focus:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2"
          >
            Log in
          </Link>
        </div>
      </div>

      <section className="mt-16 grid w-full max-w-4xl gap-4 sm:grid-cols-3">
        {features.map((feature) => (
          <div
            key={feature.title}
            className="rounded-xl border border-border bg-card p-5 text-left shadow-sm"
          >
            <h2 className="font-semibold">{feature.title}</h2>
            <p className="mt-2 text-sm text-muted">{feature.body}</p>
          </div>
        ))}
      </section>
    </main>
  );
}
