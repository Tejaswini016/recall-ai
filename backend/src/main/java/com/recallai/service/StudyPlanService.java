package com.recallai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recallai.ai.AiGenerationService;
import com.recallai.ai.AiInvalidResponseException;
import com.recallai.ai.AiProperties;
import com.recallai.ai.AiUnavailableException;
import com.recallai.ai.StudyPlanAdvice;
import com.recallai.ai.StudyPlanContext;
import com.recallai.dto.CreateStudyPlanRequest;
import com.recallai.dto.StudyPlanProgress;
import com.recallai.dto.StudyPlanResponse;
import com.recallai.dto.StudyPlanSummaryResponse;
import com.recallai.dto.StudyPlanTaskResponse;
import com.recallai.dto.TodayPlanResponse;
import com.recallai.dto.TopicCategory;
import com.recallai.dto.TopicInsightResponse;
import com.recallai.entity.MistakeStatus;
import com.recallai.entity.StudyPlan;
import com.recallai.entity.StudyPlanStatus;
import com.recallai.entity.StudyPlanTask;
import com.recallai.entity.StudyTaskStatus;
import com.recallai.entity.StudyTaskType;
import com.recallai.exception.ApiException;
import com.recallai.exception.ErrorCode;
import com.recallai.exception.ResourceNotFoundException;
import com.recallai.repository.CardRepository;
import com.recallai.repository.MistakeRepository;
import com.recallai.repository.StudyPlanRepository;
import com.recallai.repository.StudyPlanTaskRepository;
import com.recallai.repository.UserRepository;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Exam study plans. The schedule is computed by {@link StudyPlanAllocator} from the student's
 * topic insights, cards and open mistakes; the model only adds a validated summary and per-topic
 * advice (outside any transaction, with a deterministic fallback). Regenerating rebuilds the
 * pending future tasks from fresh insights, so the plan adapts as results come in.
 */
@Service
public class StudyPlanService {

    private static final Logger log = LoggerFactory.getLogger(StudyPlanService.class);
    private static final TypeReference<List<StudyPlanResponse.TopicAdvice>> ADVICE_LIST = new TypeReference<>() {
    };
    private static final int MAX_DAYS_AHEAD = 365;
    private static final int CARRY_OVER_DAYS = 7;

    private final StudyPlanRepository planRepository;
    private final StudyPlanTaskRepository taskRepository;
    private final UserRepository userRepository;
    private final CardRepository cardRepository;
    private final MistakeRepository mistakeRepository;
    private final TopicInsightService topicInsightService;
    private final AiGenerationService aiGenerationService;
    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    /** Create/regenerate run the model between two transactions; these delimit them explicitly. */
    private final TransactionTemplate writeTx;
    private final TransactionTemplate readTx;

    public StudyPlanService(StudyPlanRepository planRepository, StudyPlanTaskRepository taskRepository,
                            UserRepository userRepository, CardRepository cardRepository,
                            MistakeRepository mistakeRepository, TopicInsightService topicInsightService,
                            AiGenerationService aiGenerationService, AiProperties aiProperties,
                            ObjectMapper objectMapper, PlatformTransactionManager transactionManager, Clock clock) {
        this.writeTx = new TransactionTemplate(transactionManager);
        this.readTx = new TransactionTemplate(transactionManager);
        this.readTx.setReadOnly(true);
        this.planRepository = planRepository;
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
        this.cardRepository = cardRepository;
        this.mistakeRepository = mistakeRepository;
        this.topicInsightService = topicInsightService;
        this.aiGenerationService = aiGenerationService;
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    // ---- create / regenerate (model call outside the transaction) ----

    public StudyPlanResponse create(Long userId, CreateStudyPlanRequest request) {
        LocalDate today = LocalDate.now(clock);
        if (!request.examDate().isAfter(today)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "The exam date must be after today");
        }
        if (request.examDate().isAfter(today.plusDays(MAX_DAYS_AHEAD))) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Plans cover at most a year ahead");
        }
        List<String> topics = new ArrayList<>(new LinkedHashSet<>(
                request.topics().stream().map(String::strip).filter(t -> !t.isEmpty()).toList()));
        if (topics.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "At least one topic is required");
        }
        List<Integer> preferredDays = request.preferredDays() == null || request.preferredDays().isEmpty()
                ? List.of(1, 2, 3, 4, 5, 6, 7)
                : new ArrayList<>(new java.util.TreeSet<>(request.preferredDays()));

        Long planId = writeTx.execute(status -> persistNewPlan(userId, request, topics, preferredDays, today));
        Advice advice = adviseOutsideTransaction(userId, planId, today);
        return writeTx.execute(status -> applyAdvice(userId, planId, advice));
    }

    public StudyPlanResponse regenerate(Long userId, Long planId) {
        LocalDate today = LocalDate.now(clock);
        writeTx.executeWithoutResult(status -> rebuildPendingTasks(userId, planId, today));
        Advice advice = adviseOutsideTransaction(userId, planId, today);
        return writeTx.execute(status -> applyAdvice(userId, planId, advice));
    }

    private Long persistNewPlan(Long userId, CreateStudyPlanRequest request, List<String> topics,
                                  List<Integer> preferredDays, LocalDate today) {
        StudyPlan plan = new StudyPlan(userRepository.getReferenceById(userId), request.examName().strip(),
                request.examDate(), topics, request.knowledgeLevel(), request.minutesPerDay(), preferredDays,
                clock.instant());
        for (StudyPlanAllocator.PlannedTask task : schedule(userId, plan, today)) {
            plan.addTask(task.date(), task.order(), task.type(), task.topic(), task.title(), task.description(),
                    task.minutes());
        }
        plan = planRepository.save(plan);
        log.info("User {} created study plan {} for '{}' on {} with {} tasks", userId, plan.getId(),
                plan.getExamName(), plan.getExamDate(), plan.getTasks().size());
        return plan.getId();
    }

    /** Drops pending tasks from today on and reschedules them from fresh insights; done work stays. */
    private void rebuildPendingTasks(Long userId, Long planId, LocalDate today) {
        StudyPlan plan = getOwnedWithTasks(userId, planId);
        if (plan.getStatus() != StudyPlanStatus.ACTIVE) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Only an active plan can be regenerated");
        }
        if (!plan.getExamDate().isAfter(today)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "The exam date has passed; mark the plan completed");
        }
        Map<LocalDate, Integer> nextOrder = new LinkedHashMap<>();
        for (StudyPlanTask task : plan.getTasks()) {
            boolean future = !task.getScheduledDate().isBefore(today);
            if (future && task.getStatus() == StudyTaskStatus.PENDING) {
                plan.removeTask(task);
            } else if (future) {
                nextOrder.merge(task.getScheduledDate(), task.getSortOrder() + 1, Math::max);
            }
        }
        for (StudyPlanAllocator.PlannedTask task : schedule(userId, plan, today)) {
            int order = nextOrder.getOrDefault(task.date(), 0);
            nextOrder.put(task.date(), order + 1);
            plan.addTask(task.date(), order, task.type(), task.topic(), task.title(), task.description(),
                    task.minutes());
        }
        log.info("User {} regenerated study plan {}", userId, planId);
    }

    private List<StudyPlanAllocator.PlannedTask> schedule(Long userId, StudyPlan plan, LocalDate from) {
        Map<String, TopicInsightResponse> insights = topicInsightService.insights(userId, null).stream()
                .collect(Collectors.toMap(t -> TopicInsightService.key(t.topic()), Function.identity(), (a, b) -> a));
        Set<DayOfWeek> days = plan.getPreferredDays().stream().map(DayOfWeek::of)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(DayOfWeek.class)));
        StudyPlanAllocator.Input input = new StudyPlanAllocator.Input(plan.getExamName(), plan.getExamDate(),
                plan.getTopics(), plan.getKnowledgeLevel(), plan.getMinutesPerDay(), days, insights,
                cardRepository.countByDeckUserId(userId),
                mistakeRepository.countByUserIdAndStatus(userId, MistakeStatus.OPEN));
        return StudyPlanAllocator.allocate(input, from);
    }

    private record Advice(String summary, List<StudyPlanResponse.TopicAdvice> topicAdvice, boolean ai) {
    }

    private Advice adviseOutsideTransaction(Long userId, Long planId, LocalDate today) {
        StudyPlanContext context = readTx.execute(status -> contextFor(userId, planId, today));
        if (aiProperties.generationAvailable()) {
            try {
                StudyPlanAdvice advice = aiGenerationService.generateStudyPlanAdvice(context).items().get(0);
                return new Advice(advice.summary(), advice.topicAdvice().stream()
                        .map(a -> new StudyPlanResponse.TopicAdvice(a.topic(), a.advice())).toList(), true);
            } catch (AiUnavailableException | AiInvalidResponseException e) {
                log.warn("Study plan {}: model advice unavailable ({}); using the deterministic summary",
                        planId, e.getMessage());
            }
        }
        return new Advice(fallbackSummary(context), List.of(), false);
    }

    private StudyPlanContext contextFor(Long userId, Long planId, LocalDate today) {
        StudyPlan plan = getOwnedWithTasks(userId, planId);
        Map<String, TopicInsightResponse> insights = topicInsightService.insights(userId, null).stream()
                .collect(Collectors.toMap(t -> TopicInsightService.key(t.topic()), Function.identity(), (a, b) -> a));
        Map<String, Integer> plannedMinutes = new LinkedHashMap<>();
        for (StudyPlanTask task : plan.getTasks()) {
            if (task.getTopic() != null && task.getStatus() == StudyTaskStatus.PENDING) {
                plannedMinutes.merge(TopicInsightService.key(task.getTopic()), task.getMinutes(), Integer::sum);
            }
        }
        List<StudyPlanContext.TopicStanding> standings = plan.getTopics().stream().map(topic -> {
            TopicInsightResponse insight = insights.get(TopicInsightService.key(topic));
            return new StudyPlanContext.TopicStanding(topic,
                    insight == null ? TopicCategory.UNRATED.name() : insight.category().name(),
                    insight == null ? 0 : insight.accuracyPercent(),
                    insight == null ? 0 : insight.attempts(),
                    plannedMinutes.getOrDefault(TopicInsightService.key(topic), 0));
        }).toList();
        long studyDays = plan.getTasks().stream().filter(t -> !t.getScheduledDate().isBefore(today))
                .map(StudyPlanTask::getScheduledDate).distinct().count();
        return new StudyPlanContext(plan.getExamName(), ChronoUnit.DAYS.between(today, plan.getExamDate()), studyDays,
                plan.getMinutesPerDay(), plan.getKnowledgeLevel().name().toLowerCase(Locale.ROOT), standings,
                mistakeRepository.countByUserIdAndStatus(userId, MistakeStatus.OPEN));
    }

    static String fallbackSummary(StudyPlanContext context) {
        List<String> priority = context.topics().stream()
                .filter(t -> "CRITICAL".equals(t.category()) || "WEAK".equals(t.category()))
                .map(StudyPlanContext.TopicStanding::topic).toList();
        List<String> unknown = context.topics().stream()
                .filter(t -> "UNRATED".equals(t.category()))
                .map(StudyPlanContext.TopicStanding::topic).toList();
        long hours = Math.round(context.studyDays() * context.minutesPerDay() / 60.0);
        StringBuilder text = new StringBuilder();
        text.append(context.studyDays()).append(" study days until ").append(context.examName())
                .append(", about ").append(hours).append(" hours in total. ");
        if (!priority.isEmpty()) {
            text.append("Most time goes to ").append(String.join(", ", priority))
                    .append(", where your accuracy is lowest. ");
        }
        if (!unknown.isEmpty()) {
            text.append(String.join(", ", unknown)).append(unknown.size() == 1 ? " has" : " have")
                    .append(" no results yet, so the plan starts them with learning blocks. ");
        }
        text.append("Every study day begins with your due cards");
        if (context.openMistakes() > 0) {
            text.append(" and, every other day, the ").append(context.openMistakes()).append(" open mistakes");
        }
        text.append("; weekly mock exams show what still needs work. Regenerate the plan after new results to rebalance it.");
        return text.toString();
    }

    private StudyPlanResponse applyAdvice(Long userId, Long planId, Advice advice) {
        StudyPlan plan = getOwnedWithTasks(userId, planId);
        try {
            plan.setAdvice(advice.summary(), objectMapper.writeValueAsString(advice.topicAdvice()), advice.ai(),
                    clock.instant());
        } catch (JsonProcessingException e) {
            plan.setAdvice(advice.summary(), "[]", advice.ai(), clock.instant());
        }
        return toResponse(plan, LocalDate.now(clock));
    }

    // ---- reads ----

    @Transactional(readOnly = true)
    public List<StudyPlanSummaryResponse> list(Long userId) {
        LocalDate today = LocalDate.now(clock);
        return planRepository.findByUserIdOrderByCreatedAtDescIdDesc(userId).stream()
                .map(plan -> toSummary(plan, today)).toList();
    }

    @Transactional(readOnly = true)
    public StudyPlanResponse get(Long userId, Long planId) {
        return toResponse(getOwnedWithTasks(userId, planId), LocalDate.now(clock));
    }

    @Transactional(readOnly = true)
    public TodayPlanResponse today(Long userId) {
        LocalDate today = LocalDate.now(clock);
        List<StudyPlanTask> tasks = taskRepository.findForDay(userId, StudyPlanStatus.ACTIVE, today);
        List<StudyPlanTask> carried = taskRepository.findOverdue(userId, StudyPlanStatus.ACTIVE, StudyTaskStatus.PENDING,
                today, today.minusDays(CARRY_OVER_DAYS), PageRequest.of(0, 10));
        int total = tasks.stream().mapToInt(StudyPlanTask::getMinutes).sum();
        int done = tasks.stream().filter(t -> t.getStatus() == StudyTaskStatus.DONE).mapToInt(StudyPlanTask::getMinutes).sum();
        int activePlans = planRepository.findByUserIdAndStatusOrderByExamDateAscIdDesc(userId, StudyPlanStatus.ACTIVE).size();
        return new TodayPlanResponse(today, activePlans,
                tasks.stream().map(StudyPlanTaskResponse::from).toList(),
                carried.stream().map(StudyPlanTaskResponse::from).toList(), total, done);
    }

    // ---- updates ----

    @Transactional
    public StudyPlanTaskResponse updateTask(Long userId, Long planId, Long taskId, StudyTaskStatus status) {
        StudyPlanTask task = taskRepository.findOwned(taskId, planId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));
        task.setStatus(status, clock.instant());
        StudyPlan plan = task.getPlan();
        if (plan.getStatus() == StudyPlanStatus.ACTIVE
                && taskRepository.countByPlanIdAndStatus(planId, StudyTaskStatus.PENDING) == 0) {
            plan.setStatus(StudyPlanStatus.COMPLETED);
            log.info("Study plan {} completed: every task is done or skipped", planId);
        } else if (plan.getStatus() == StudyPlanStatus.COMPLETED && status == StudyTaskStatus.PENDING) {
            plan.setStatus(StudyPlanStatus.ACTIVE);
        }
        return StudyPlanTaskResponse.from(task);
    }

    @Transactional
    public StudyPlanSummaryResponse updateStatus(Long userId, Long planId, StudyPlanStatus status) {
        StudyPlan plan = getOwnedWithTasks(userId, planId);
        plan.setStatus(status);
        return toSummary(plan, LocalDate.now(clock));
    }

    @Transactional
    public void delete(Long userId, Long planId) {
        planRepository.delete(getOwnedWithTasks(userId, planId));
        log.info("User {} deleted study plan {}", userId, planId);
    }

    // ---- mapping ----

    private StudyPlan getOwnedWithTasks(Long userId, Long planId) {
        return planRepository.findWithTasks(planId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Study plan", planId));
    }

    private StudyPlanResponse toResponse(StudyPlan plan, LocalDate today) {
        List<StudyPlanResponse.TopicAdvice> advice;
        try {
            advice = objectMapper.readValue(plan.getTopicAdvice(), ADVICE_LIST);
        } catch (JsonProcessingException e) {
            advice = List.of();
        }
        return new StudyPlanResponse(plan.getId(), plan.getExamName(), plan.getExamDate(), plan.getTopics(),
                plan.getKnowledgeLevel(), plan.getMinutesPerDay(), plan.getPreferredDays(), plan.getStatus(),
                plan.getSummary(), advice, plan.isAiGenerated(), plan.getGeneratedAt(), plan.getCreatedAt(),
                progress(plan, today), plan.getTasks().stream().map(StudyPlanTaskResponse::from).toList());
    }

    private StudyPlanSummaryResponse toSummary(StudyPlan plan, LocalDate today) {
        return new StudyPlanSummaryResponse(plan.getId(), plan.getExamName(), plan.getExamDate(), plan.getTopics(),
                plan.getKnowledgeLevel(), plan.getMinutesPerDay(), plan.getStatus(), plan.isAiGenerated(),
                plan.getCreatedAt(), progress(plan, today));
    }

    static StudyPlanProgress progress(StudyPlan plan, LocalDate today) {
        List<StudyPlanTask> tasks = plan.getTasks();
        long done = tasks.stream().filter(t -> t.getStatus() == StudyTaskStatus.DONE).count();
        long skipped = tasks.stream().filter(t -> t.getStatus() == StudyTaskStatus.SKIPPED).count();
        long pending = tasks.size() - done - skipped;
        int totalMinutes = tasks.stream().mapToInt(StudyPlanTask::getMinutes).sum();
        int doneMinutes = tasks.stream().filter(t -> t.getStatus() == StudyTaskStatus.DONE)
                .mapToInt(StudyPlanTask::getMinutes).sum();
        List<LocalDate> studyDays = tasks.stream().map(StudyPlanTask::getScheduledDate).distinct().sorted().toList();
        long studyDaysLeft = studyDays.stream().filter(d -> !d.isBefore(today)).count();
        long elapsedDays = studyDays.size() - studyDaysLeft;
        double expectedShare = studyDays.isEmpty() ? 0 : (double) elapsedDays / studyDays.size();
        double doneShare = tasks.isEmpty() ? 0 : (double) (done + skipped) / tasks.size();
        int dueToday = tasks.stream()
                .filter(t -> t.getScheduledDate().equals(today) && t.getStatus() == StudyTaskStatus.PENDING)
                .mapToInt(StudyPlanTask::getMinutes).sum();
        return new StudyPlanProgress(tasks.size(), done, skipped, pending,
                DeckService.progressPercent(done + skipped, tasks.size()), totalMinutes, doneMinutes,
                Math.max(0, ChronoUnit.DAYS.between(today, plan.getExamDate())), studyDaysLeft, studyDays.size(),
                doneShare + 0.05 >= expectedShare, dueToday);
    }

    /** Task types that have a natural destination in the app; used by the frontend, exposed for tests. */
    static boolean isTopicTask(StudyTaskType type) {
        return type == StudyTaskType.LEARN_TOPIC || type == StudyTaskType.PRACTICE_QUIZ;
    }
}
