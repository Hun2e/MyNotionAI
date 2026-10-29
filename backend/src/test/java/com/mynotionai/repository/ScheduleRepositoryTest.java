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
            .endAt(startAt.plusHours(1))
            .createdBy(Schedule.CreatedBy.USER)
            .build());
    }

    @Test
    @DisplayName("findByIdAndUserId는 소유자가 아니면 빈 결과를 준다")
    void findByIdAndUserIdIsolatesUsers() {
        Schedule aliceSchedule = saveSchedule(alice, "앨리스 일정",
            LocalDateTime.of(2026, 9, 17, 10, 0));

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
        LocalDateTime september = LocalDateTime.of(2026, 9, 17, 10, 0);
        saveSchedule(alice, "앨리스 일정", september);
        saveSchedule(bob, "밥 일정", september);

        List<Schedule> aliceSchedules =
            scheduleRepository.findByUserIdAndStartAtBetweenOrderByStartAtAsc(
                alice.getId(),
                LocalDateTime.of(2026, 9, 1, 0, 0),
                LocalDateTime.of(2026, 10, 1, 0, 0));

        assertThat(aliceSchedules)
            .hasSize(1)
            .extracting(Schedule::getTitle)
            .containsExactly("앨리스 일정");
    }

    @Test
    @DisplayName("조회 구간 밖의 일정은 제외된다")
    void excludesSchedulesOutsideRange() {
        saveSchedule(alice, "8월 일정", LocalDateTime.of(2026, 8, 31, 23, 0));
        saveSchedule(alice, "9월 일정", LocalDateTime.of(2026, 9, 1, 0, 0));
        saveSchedule(alice, "10월 일정", LocalDateTime.of(2026, 10, 1, 0, 0));

        List<Schedule> result =
            scheduleRepository.findByUserIdAndStartAtBetweenOrderByStartAtAsc(
                alice.getId(),
                LocalDateTime.of(2026, 9, 1, 0, 0),
                LocalDateTime.of(2026, 10, 1, 0, 0));

        // Between은 양끝을 포함하므로 10월 1일 0시 일정도 들어온다.
        // 이 테스트는 그 경계 동작을 기록해 둔다.
        assertThat(result)
            .extracting(Schedule::getTitle)
            .containsExactly("9월 일정", "10월 일정");
    }

    @Test
    @DisplayName("월별 조회 결과는 시작 시간 오름차순으로 정렬된다")
    void ordersByStartAtAscending() {
        saveSchedule(alice, "오후", LocalDateTime.of(2026, 9, 17, 15, 0));
        saveSchedule(alice, "오전", LocalDateTime.of(2026, 9, 17, 9, 0));
        saveSchedule(alice, "정오", LocalDateTime.of(2026, 9, 17, 12, 0));

        List<Schedule> result =
            scheduleRepository.findByUserIdAndStartAtBetweenOrderByStartAtAsc(
                alice.getId(),
                LocalDateTime.of(2026, 9, 1, 0, 0),
                LocalDateTime.of(2026, 10, 1, 0, 0));

        assertThat(result)
            .extracting(Schedule::getTitle)
            .containsExactly("오전", "정오", "오후");
    }

    @Test
    @DisplayName("저장 시 createdAt·updatedAt이 자동으로 채워진다")
    void populatesTimestampsOnPersist() {
        Schedule saved = saveSchedule(alice, "일정",
            LocalDateTime.of(2026, 9, 17, 10, 0));

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getCreatedBy()).isEqualTo(Schedule.CreatedBy.USER);
    }
}
