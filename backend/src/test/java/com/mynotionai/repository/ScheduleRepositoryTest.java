package com.mynotionai.repository;

import com.mynotionai.entity.Schedule;
import com.mynotionai.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ScheduleRepository 통합 테스트 (H2 인메모리).
 *
 * 쿼리 메서드가 실제로 의도한 SQL을 만들어내는지 확인한다.
 * 특히 사용자별 데이터 격리는 Mock으로는 검증할 수 없어 실제 DB가 필요하다.
 */
@DataJpaTest
@ActiveProfiles("test")
@DisplayName("ScheduleRepository 통합 테스트")
class ScheduleRepositoryTest {

    private static final long SCHEDULE_DURATION_HOURS = 1;
    private static final long BOUNDARY_OFFSET_SECONDS = 1;
    private static final LocalDateTime MONTH_START = LocalDateTime.parse("2026-09-01T00:00:00");
    private static final LocalDateTime NEXT_MONTH_START = LocalDateTime.parse("2026-10-01T00:00:00");
    private static final LocalDateTime DAY_START = LocalDateTime.parse("2026-09-17T00:00:00");
    private static final LocalDateTime NEXT_DAY_START = LocalDateTime.parse("2026-09-18T00:00:00");
    private static final LocalDateTime SAMPLE_START = LocalDateTime.parse("2026-09-17T10:00:00");
    private static final LocalDateTime MORNING_START = LocalDateTime.parse("2026-09-17T09:00:00");
    private static final LocalDateTime NOON_START = LocalDateTime.parse("2026-09-17T12:00:00");
    private static final LocalDateTime AFTERNOON_START = LocalDateTime.parse("2026-09-17T15:00:00");

    @Autowired
    private ScheduleRepository scheduleRepository;

    @Autowired
    private UserRepository userRepository;

    private User alice;
    private User bob;

    @BeforeEach
    void setUp() {
        alice = userRepository.save(User.builder()
            .email("alice@example.com")
            .passwordHash("hash")
            .nickname("앨리스")
            .provider(User.Provider.LOCAL)
            .build());

        bob = userRepository.save(User.builder()
            .email("bob@example.com")
            .passwordHash("hash")
            .nickname("밥")
            .provider(User.Provider.LOCAL)
            .build());
    }

    private Schedule saveSchedule(User user, String title, LocalDateTime startAt) {
        return scheduleRepository.save(Schedule.builder()
            .user(user)
            .title(title)
            .startAt(startAt)
            .endAt(startAt.plusHours(SCHEDULE_DURATION_HOURS))
            .createdBy(Schedule.CreatedBy.USER)
            .build());
    }

    @Test
    @DisplayName("findByIdAndUserId는 소유자가 아니면 빈 결과를 준다")
    void findByIdAndUserIdIsolatesUsers() {
        Schedule aliceSchedule = saveSchedule(alice, "앨리스 일정",
            SAMPLE_START);

        Optional<Schedule> asOwner =
            scheduleRepository.findByIdAndUserId(aliceSchedule.getId(), alice.getId());
        Optional<Schedule> asOther =
            scheduleRepository.findByIdAndUserId(aliceSchedule.getId(), bob.getId());

        assertThat(asOwner).isPresent();
        assertThat(asOther).isEmpty();
    }

    @Test
    @DisplayName("월별 조회는 다른 사용자의 일정을 포함하지 않는다")
    void monthlyQueryExcludesOtherUsers() {
        LocalDateTime september = SAMPLE_START;
        saveSchedule(alice, "앨리스 일정", september);
        saveSchedule(bob, "밥 일정", september);

        List<Schedule> aliceSchedules =
            scheduleRepository.findUserSchedulesInRange(
                alice.getId(),
                MONTH_START,
                NEXT_MONTH_START);

        assertThat(aliceSchedules)
            .extracting(Schedule::getTitle)
            .containsExactly("앨리스 일정");
    }

    @Test
    @DisplayName("해당 월 1일 0시는 포함하고 다음 달 1일 0시는 제외한다")
    void monthlyQueryIncludesStartAndExcludesEnd() {
        saveSchedule(alice, "8월 일정", MONTH_START.minusSeconds(BOUNDARY_OFFSET_SECONDS));
        saveSchedule(alice, "9월 일정", MONTH_START);
        saveSchedule(alice, "9월 마지막 일정", NEXT_MONTH_START.minusSeconds(BOUNDARY_OFFSET_SECONDS));
        saveSchedule(alice, "10월 일정", NEXT_MONTH_START);

        List<Schedule> result =
            scheduleRepository.findUserSchedulesInRange(
                alice.getId(),
                MONTH_START,
                NEXT_MONTH_START);

        assertThat(result)
            .extracting(Schedule::getTitle)
            .containsExactly("9월 일정", "9월 마지막 일정");
    }

    @Test
    @DisplayName("오늘 요약 조회는 오늘 0시를 포함하고 내일 0시를 제외한다")
    void dailyQueryIncludesStartAndExcludesEnd() {
        saveSchedule(alice, "어제 마지막 일정", DAY_START.minusSeconds(BOUNDARY_OFFSET_SECONDS));
        saveSchedule(alice, "오늘 0시 일정", DAY_START);
        saveSchedule(alice, "오늘 마지막 일정", NEXT_DAY_START.minusSeconds(BOUNDARY_OFFSET_SECONDS));
        saveSchedule(alice, "내일 0시 일정", NEXT_DAY_START);

        List<Schedule> result = scheduleRepository.findUserSchedulesInRange(
            alice.getId(), DAY_START, NEXT_DAY_START);

        assertThat(result)
            .extracting(Schedule::getTitle)
            .containsExactly("오늘 0시 일정", "오늘 마지막 일정");
    }

    @Test
    @DisplayName("월별 조회 결과는 시작 시간 오름차순으로 정렬된다")
    void ordersByStartAtAscending() {
        saveSchedule(alice, "오후", AFTERNOON_START);
        saveSchedule(alice, "오전", MORNING_START);
        saveSchedule(alice, "정오", NOON_START);

        List<Schedule> result =
            scheduleRepository.findUserSchedulesInRange(
                alice.getId(),
                MONTH_START,
                NEXT_MONTH_START);

        assertThat(result)
            .extracting(Schedule::getTitle)
            .containsExactly("오전", "정오", "오후");
    }

    @Test
    @DisplayName("저장 시 createdAt·updatedAt이 자동으로 채워진다")
    void populatesTimestampsOnPersist() {
        Schedule saved = saveSchedule(alice, "일정",
            SAMPLE_START);

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getCreatedBy()).isEqualTo(Schedule.CreatedBy.USER);
    }
}
