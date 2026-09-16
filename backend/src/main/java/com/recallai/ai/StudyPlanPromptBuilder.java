package com.recallai.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

/**
 * Asks the model for a short plan summary and one piece of advice per topic. The dated schedule
 * is computed deterministically elsewhere; the model only explains and motivates it, working
 * from the aggregate numbers it is given. {@link #VERSION} is part of the cache key.
 */
@Component
public class StudyPlanPromptBuilder {

    public static final String VERSION = "v1";
    public static final int MAX_SUMMARY_CHARS = 700;
    public static final int MAX_ADVICE_CHARS = 350;

    private static final String SYSTEM = """
            You are the study coach inside RecallAI, a spaced-repetition study app. The app has \
            already built a dated study plan for an exam. You receive the exam, the time available \
            and the student's measured standing on each topic, and you write the plan's summary and \
            one tip per topic.
            Rules:
            - Base everything on the numbers supplied. Do not invent facts about the subject matter, \
            the student, or the exam format.
            - "summary": three to five sentences. Say what the plan prioritises and why, name the \
            topics that need the most work, and mention the weekly mock exams and daily card reviews \
            the plan schedules. Be concrete and encouraging, never vague.
            - "topicAdvice": exactly one entry per topic listed, using the topic name exactly as \
            given. "advice" is one or two sentences of practical study strategy for that topic given \
            its standing (for example, critical topics need relearning before quizzing; strong topics \
            need light maintenance). No generic filler.
            - Plain text only. No markdown, no lists inside strings.
            Respond with a single JSON object that matches the required schema. No prose, no \
            markdown, no code fences, nothing before or after the JSON.
            """;

    private final JsonNode schema;

    public StudyPlanPromptBuilder(ObjectMapper objectMapper) {
        this.schema = buildSchema(objectMapper);
    }

    public AiPrompt build(StudyPlanContext context) {
        StringBuilder user = new StringBuilder("Write the summary and topic advice for this study plan.\n\n<plan>\n");
        user.append("Exam: ").append(context.examName().strip()).append('\n');
        user.append("Days until exam: ").append(context.daysUntilExam()).append('\n');
        user.append("Study days scheduled: ").append(context.studyDays()).append('\n');
        user.append("Minutes per study day: ").append(context.minutesPerDay()).append('\n');
        user.append("Self-assessed level: ").append(context.knowledgeLevel()).append('\n');
        user.append("Open mistakes waiting for review: ").append(context.openMistakes()).append('\n');
        user.append("Topics:\n");
        for (StudyPlanContext.TopicStanding topic : context.topics()) {
            user.append("- ").append(topic.topic().strip())
                    .append(" | standing: ").append(topic.category())
                    .append(topic.attempts() > 0 ? " | accuracy: " + topic.accuracy() + "% over " + topic.attempts() + " attempts"
                            : " | no attempts yet")
                    .append(" | planned minutes: ").append(topic.plannedMinutes())
                    .append('\n');
        }
        user.append("</plan>");
        return AiPrompt.of(AiOperation.STUDY_PLAN, VERSION, SYSTEM, user.toString(), schema);
    }

    private static JsonNode buildSchema(ObjectMapper objectMapper) {
        ObjectNode advice = objectMapper.createObjectNode();
        advice.put("type", "object");
        advice.put("additionalProperties", false);
        ObjectNode adviceProps = advice.putObject("properties");
        adviceProps.putObject("topic").put("type", "string").put("maxLength", 150);
        adviceProps.putObject("advice").put("type", "string").put("maxLength", MAX_ADVICE_CHARS);
        advice.putArray("required").add("topic").add("advice");

        ObjectNode root = objectMapper.createObjectNode();
        root.put("type", "object");
        root.put("additionalProperties", false);
        ObjectNode props = root.putObject("properties");
        props.putObject("summary").put("type", "string").put("maxLength", MAX_SUMMARY_CHARS);
        ObjectNode list = props.putObject("topicAdvice");
        list.put("type", "array");
        list.set("items", advice);
        root.putArray("required").add("summary").add("topicAdvice");
        return root;
    }
}
