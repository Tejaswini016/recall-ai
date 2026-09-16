// End-to-end walkthrough of the adaptive study companion against a running stack (Groq or any
// configured provider). Registers a fresh user, drives the real API for setup and the real UI
// (Microsoft Edge via playwright-core) for every flow, and refreshes the README screenshots.
// Usage: npm i playwright-core && node scripts/e2e-walkthrough.mjs   (stack on :3000 and :8080)
import { chromium } from "playwright-core";
import { fileURLToPath } from "node:url";

const API = "http://localhost:8080";
const APP = "http://localhost:3000";
const OUT = fileURLToPath(new URL("../docs/screenshots/", import.meta.url)).replace(/[\/]$/, "");
const stamp = Date.now();
const email = `adaptive-${stamp}@example.com`;
const report = [];
const log = (...a) => { console.log(...a); report.push(a.join(" ")); };

async function api(path, { method = "GET", body, token } = {}) {
  const res = await fetch(API + path, {
    method,
    headers: { "Content-Type": "application/json", ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await res.text();
  let json = null;
  try { json = text ? JSON.parse(text) : null; } catch { json = text; }
  if (!res.ok) throw new Error(`${method} ${path} -> ${res.status}: ${text.slice(0, 300)}`);
  return json;
}

// ---------- API setup: user, deck, Groq-generated cards ----------
const auth = await api("/api/auth/register", { method: "POST", body: { name: "Adaptive Tester", email, password: "password-123" } });
const token = auth.token;
const status = await api("/api/ai/status", { token });
log(`ai status: provider=${status.provider} model=${status.model} available=${status.available}`);

const deck = await api("/api/decks", { method: "POST", token, body: { name: "Cell Biology", subject: "Biology", tags: ["bio"] } });
const notes = `
Mitochondria are the organelles that produce most of a cell's ATP through cellular respiration.
The inner mitochondrial membrane is folded into cristae, which increase the surface area for the electron transport chain.
Ribosomes build proteins by translating messenger RNA into chains of amino acids.
The nucleus stores the cell's DNA and is surrounded by a double membrane called the nuclear envelope.
DNA polymerase copies DNA before cell division, adding nucleotides to the growing strand.
A codon is a sequence of three bases in mRNA that specifies one amino acid.
Meiosis produces four haploid gametes from one diploid cell, halving the chromosome number.
Crossing over during prophase I of meiosis exchanges segments between homologous chromosomes.
Photosynthesis in chloroplasts converts carbon dioxide and water into glucose and oxygen using light energy.
The Calvin cycle fixes carbon dioxide into sugar in the stroma of the chloroplast.
`;
const t0 = Date.now();
const gen = await api("/api/ai/flashcards", { method: "POST", token, body: { deckId: deck.id, text: notes, count: 10 } });
log(`flashcards: ${gen.cardsCreated} cards in ${((Date.now() - t0) / 1000).toFixed(1)}s (cached ${gen.cachedChunks}, retries ${gen.retries})`);
const topics = [...new Set(gen.cards.map((c) => c.topic).filter(Boolean))];
log(`topics: ${topics.join(" | ")}`);

// Reviews with response times: some cards easy, one topic consistently wrong.
const weakTopic = topics[0];
for (const card of gen.cards) {
  const wrong = card.topic === weakTopic;
  for (let i = 0; i < 3; i++) {
    const r = await api(`/api/reviews/${card.id}`, { method: "POST", token, body: { quality: wrong ? 1 : 5, responseMs: wrong ? 9000 : 2500 } });
    if (i === 2) log(`review ${card.id} (${card.topic}) -> tier ${r.previousDifficulty}->${r.difficulty} interval ${r.newInterval} (sm2 ${r.sm2Interval})`);
  }
}

// Quiz with topics, answered mostly wrong on the weak topic.
const t1 = Date.now();
const quiz = await api("/api/ai/quiz", { method: "POST", token, body: { deckId: deck.id, count: 6 } });
log(`quiz: ${quiz.questionCount} questions in ${((Date.now() - t1) / 1000).toFixed(1)}s; topics ${[...new Set(quiz.questions.map((q) => q.topic))].join(" | ")}`);

// ---------- UI flows ----------
const browser = await chromium.launch({ channel: "msedge", headless: true });
const context = await browser.newContext({ viewport: { width: 1440, height: 900 }, colorScheme: "light" });
await context.addCookies([{ name: "recallai_token", value: token, url: APP }]);
const page = await context.newPage();
const settle = async (ms = 1200) => { await page.waitForLoadState("networkidle"); await page.waitForTimeout(ms); };

// Quiz: answer everything with option B, so some are wrong; then review a mistake from the results page.
await page.goto(`${APP}/quiz/${quiz.id}`);
await page.getByRole("radiogroup", { name: "Options" }).waitFor();
for (let i = 0; i < 50; i++) {
  await page.getByRole("radio").nth(1).click();
  const finish = page.getByRole("button", { name: "Finish quiz" });
  if (await finish.count()) { await finish.click(); break; }
  await page.getByRole("button", { name: "Next" }).click();
  await page.waitForTimeout(200);
}
await page.getByRole("button", { name: /retry quiz/i }).waitFor();
await settle(600);
const reviewButtons = page.getByRole("button", { name: /review this mistake/i });
log(`quiz results: ${await reviewButtons.count()} wrong answers offer "Review this mistake"`);
if (await reviewButtons.count()) {
  const t2 = Date.now();
  await reviewButtons.first().click();
  await page.getByText(/flashcard added/i).first().waitFor({ timeout: 60000 });
  log(`mistake -> flashcard via UI in ${((Date.now() - t2) / 1000).toFixed(1)}s`);
}
await page.screenshot({ path: `${OUT}/quiz-results.png` });

// Study: practice the weak topic from the dashboard chooser.
await page.goto(`${APP}/dashboard`);
await page.getByText(/estimated exam readiness/i).waitFor();
await settle(1500);
await page.screenshot({ path: `${OUT}/dashboard.png`, fullPage: false });
const practice = page.getByRole("button", { name: "Practice" }).first();
if (await practice.count()) {
  await practice.click();
  await page.getByRole("dialog").waitFor();
  await page.screenshot({ path: `${OUT}/practice-topic.png` });
  await page.getByRole("button", { name: /flashcard session/i }).click();
  await page.getByRole("button", { name: "Show answer" }).waitFor();
  await settle(500);
  await page.screenshot({ path: `${OUT}/study.png` });
  await page.getByRole("button", { name: "Show answer" }).click();
  await page.getByRole("group", { name: "Rate your recall" }).waitFor();
  await page.getByRole("group", { name: "Rate your recall" }).getByRole("button").nth(4).click();
  await page.waitForTimeout(500);
  log("practice session: revealed and rated a card on the weak topic");
}

// Study plan: create via the form, then view.
await page.goto(`${APP}/plan/new`);
await page.getByLabel("Exam name").fill("Biology midterm");
const exam = new Date(Date.now() + 14 * 86400000).toISOString().slice(0, 10);
await page.getByLabel("Exam date").fill(exam);
for (const t of topics.slice(0, 3)) {
  await page.getByLabel("Topics").fill(t);
  await page.keyboard.press("Enter");
}
await page.getByLabel("Minutes per study day").fill("45");
const t3 = Date.now();
await page.getByRole("button", { name: /build my plan/i }).click();
await page.waitForURL(`${APP}/plan`, { timeout: 90000 });
await page.getByText(/estimated|summary/i).first().waitFor();
await settle(1500);
log(`study plan created via UI in ${((Date.now() - t3) / 1000).toFixed(1)}s`);
await page.screenshot({ path: `${OUT}/plan.png` });
const plans = await api("/api/study-plans", { token });
log(`plan: ${plans[0].progress.totalTasks} tasks over ${plans[0].progress.studyDaysTotal} study days, aiGenerated=${plans[0].aiGenerated}`);
// Mark the first task done from the UI.
const firstCheck = page.getByRole("button", { name: /mark as done/i }).first();
if (await firstCheck.count()) { await firstCheck.click(); await page.waitForTimeout(600); log("plan: first task marked done"); }

// Mock exam: create via the form, answer, submit.
await page.goto(`${APP}/exams`);
await page.getByRole("button", { name: /all my cards/i }).click();
await page.getByLabel("Questions").fill("6");
await page.getByLabel("Minutes").fill("10");
const t4 = Date.now();
await page.getByRole("button", { name: /generate and start/i }).click();
await page.waitForURL(/\/exams\/\d+$/, { timeout: 120000 });
await page.getByRole("timer").waitFor();
await settle(800);
log(`mock exam generated via UI in ${((Date.now() - t4) / 1000).toFixed(1)}s`);
await page.screenshot({ path: `${OUT}/exam.png` });
const examId = Number(page.url().split("/").pop());
const examData = await api(`/api/mock-exams/${examId}`, { token });
log(`exam: ${examData.questionCount} questions, types ${[...new Set(examData.questions.map((q) => q.type))].join("/")}`);
for (let i = 0; i < examData.questions.length; i++) {
  const q = examData.questions[i];
  if (q.type === "SHORT_ANSWER") {
    await page.getByLabel("Your answer").fill(i % 2 === 0 ? "mitochondrion" : "not sure");
  } else {
    await page.getByRole("radio").nth(i % 2 === 0 ? 0 : 1).click();
  }
  if (i < examData.questions.length - 1) await page.getByRole("button", { name: "Next" }).click();
}
await page.getByRole("button", { name: "Submit exam" }).click();
await page.getByRole("dialog").getByRole("button", { name: "Submit" }).click();
await page.waitForURL(/\/results$/, { timeout: 60000 });
await page.getByText(/accuracy/i).first().waitFor();
await settle(1000);
await page.screenshot({ path: `${OUT}/exam-results.png` });
const result = await api(`/api/mock-exams/${examId}/results`, { token });
log(`exam result: ${result.percent}% (${result.correctCount} right, ${result.incorrectCount} wrong, ${result.skippedCount} skipped) weak: ${result.weakTopics.join(", ")}`);

// Mistakes page and analytics.
await page.goto(`${APP}/mistakes`);
await page.getByRole("heading", { name: "Mistakes" }).waitFor();
await settle(1000);
await page.screenshot({ path: `${OUT}/mistakes.png` });
const summary = await api("/api/mistakes/summary", { token });
log(`mistakes: open ${summary.open}, converted ${summary.converted}`);

await page.goto(`${APP}/analytics`);
await page.locator(".recharts-surface").first().waitFor();
await settle(1500);
await page.screenshot({ path: `${OUT}/analytics.png` });

await page.goto(`${APP}/decks/${deck.id}`);
await page.getByRole("heading", { name: "Cell Biology" }).waitFor();
await settle();
await page.getByRole("button", { name: /generate cards/i }).first().click();
await page.getByRole("dialog").waitFor();
await page.waitForTimeout(400);
await page.screenshot({ path: `${OUT}/deck.png` });

await page.goto(`${APP}/dashboard`);
await page.getByText(/estimated exam readiness/i).waitFor();
await settle(1500);
await page.screenshot({ path: `${OUT}/dashboard.png` });
const readiness = await api("/api/analytics/readiness", { token });
log(`readiness: ${readiness.score} (${readiness.label}, confidence ${readiness.confidence}) -> ${readiness.recommendation}`);
const insights = await api("/api/analytics/topic-insights", { token });
log(`topic insights: ${insights.map((t) => `${t.topic}=${t.category}/${t.accuracyPercent}%`).join(" | ")}`);

await browser.close();
writeFileSync(new URL("./walkthrough-report.txt", import.meta.url), report.join("\n"));
console.log("DONE");
