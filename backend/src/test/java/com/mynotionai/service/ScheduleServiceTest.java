package com.mynotionai.service;

import com.mynotionai.dto.ScheduleRequest;
import com.mynotionai.dto.ScheduleResponse;
import com.mynotionai.entity.Schedule;
import com.mynotionai.entity.User;
import com.mynotionai.exception.BusinessException;
import com.mynotionai.repository.ScheduleRepository;
import com.mynotionai.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * ScheduleService 단위 테스트.
 *
 * 정상 흐름보다 예외 경로 검증에 집중한다.
 * - 권한 경계: 다른 사용자의 일정에 접근할 수 없는가
 * - 입력 검증: 잘못된 시간 범위, 범위를 벗어난 월
 * - 존재하지 않는 리소스 처리
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ScheduleService 단위 테스트")
class ScheduleServiceTest {

    private static final Long OWNER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long SCHEDULE_ID = 100L;

    @Mock
    private ScheduleRepository scheduleRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ScheduleService scheduleService;

    private User owner() {
        return User.builder()
            .id(OWNER_ID)
            .email("owner@example.com")
            .nickname("소유자")
            .provider(User.Provider.LOCAL)
            .build();
    }

    private Schedule schedule(User user) {
        return Schedule.builder()
            .id(SCHEDULE_ID)
            .user(user)
            .title("팀 회의")
            .startAt(LocalDateTime.of(2026, 9, 17, 10, 0))
            .endAt(LocalDateTime.of(2026, 9, 17, 11, 0))
            .memo("주간 회의")
            .createdBy(Schedule.CreatedBy.USER)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
    }

    private ScheduleRequest request(LocalDateTime startAt, LocalDateTime endAt) {
        return ScheduleRequest.builder()
            .title("팀 회의")
            .startAt(startAt)
            .endAt(endAt)
            .memo("주간 회의")
            .build();
    }

    private ScheduleRequest validRequest() {
        return request(
            LocalDateTime.of(2026, 9, 17, 10, 0),
            LocalDateTime.of(2026, 9, 17, 11, 0)
        );
    }

    @Nested
    @DisplayName("권한 경계")
    class AuthorizationBoundary {

        @Test
        @DisplayName("다른 사용자의 일정은 수정할 수 없다")
        void cannotUpdateOthersSchedule() {
            // 조회 자체가 (scheduleId, userId) 쌍으로 이뤄지므로
            // 소유자가 아니면 빈 결과가 돌아온다.
            given(scheduleRepository.findByIdAndUserId(SCHEDULE_ID, OTHER_USER_ID))
                .willReturn(Optional.empty());

            assertThatThrownBy(() ->
                scheduleService.updateSchedule(OTHER_USER_ID, SCHEDULE_ID, validRequest()))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode().getStatus())
                    .isEqualTo(HttpStatus.NOT_FOUND));

            verify(scheduleRepository, never()).save(any());
        }

        @Test
        @DisplayName("다른 사용자의 일정은 삭제할 수 없다")
        void cannotDeleteOthersSchedule() {
            given(scheduleRepository.findByIdAndUserId(SCHEDULE_ID, OTHER_USER_ID))
                .willReturn(Optional.empty());

            assertThatThrownBy(() ->
                scheduleService.deleteSchedule(OTHER_USER_ID, SCHEDULE_ID))
                .isInstanceOf(BusinessException.class);

            // 삭제가 호출되지 않아야 한다
            verify(scheduleRepository, never()).delete(any());
        }

        @Test
        @DisplayName("소유자는 자신의 일정을 삭제할 수 있다")
        void ownerCanDeleteOwnSchedule() {
            Schedule target = schedule(owner());
            given(scheduleRepository.findByIdAndUserId(SCHEDULE_ID, OWNER_ID))
                .willReturn(Optional.of(target));

            scheduleService.deleteSchedule(OWNER_ID, SCHEDULE_ID);

            verify(scheduleRepository).delete(target);
        }
    }

    @Nested
    @DisplayName("입력 검증")
    class InputValidation {

        @Test
        @DisplayName("종료 시간이 시작 시간보다 빠르면 생성할 수 없다")
        void rejectsReversedTimeRange() {
            ScheduleRequest reversed = request(
                LocalDateTime.of(2026, 9, 17, 11, 0),
                LocalDateTime.of(2026, 9, 17, 10, 0)
            );

            assertThatThrownBy(() -> scheduleService.createSchedule(OWNER_ID, reversed))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("startAt must be before endAt");

            // 검증 실패 시 사용자 조회조차 하지 않아야 한다
            verify(userRepository, never()).findById(anyLong());
            verify(scheduleRepository, never()).save(any());
        }

        @Test
        @DisplayName("시작과 종료가 같은 시각이면 생성할 수 없다")
        void rejectsZeroDuration() {
            LocalDateTime sameMoment = LocalDateTime.of(2026, 9, 17, 10, 0);
            ScheduleRequest zeroDuration = request(sameMoment, sameMoment);

            assertThatThrownBy(() -> scheduleService.createSchedule(OWNER_ID, zeroDuration))
                .isInstanceOf(BusinessException.class);

            verify(scheduleRepository, never()).save(any());
        }

        @Test
        @DisplayName("수정 시에도 시간 범위를 검증한다")
        void validatesTimeRangeOnUpdate() {
            ScheduleRequest reversed = request(
                LocalDateTime.of(2026, 9, 17, 11, 0),
                LocalDateTime.of(2026, 9, 17, 10, 0)
            );

            assertThatThrownBy(() ->
                scheduleService.updateSchedule(OWNER_ID, SCHEDULE_ID, reversed))
                .isInstanceOf(BusinessException.class);

            // 시간 검증이 조회보다 먼저 일어나므로 조회가 호출되지 않는다
            verify(scheduleRepository, never()).findByIdAndUserId(anyLong(), anyLong());
        }

        @Test
        @DisplayName("13월처럼 범위를 벗어난 월은 조회할 수 없다")
        void rejectsMonthAboveRange() {
            assertThatThrownBy(() -> scheduleService.getMonthlySchedules(OWNER_ID, 2026, 13))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("month must be between 1 and 12");
        }

        @Test
        @DisplayName("0월도 조회할 수 없다")
        void rejectsMonthBelowRange() {
            assertThatThrownBy(() -> scheduleService.getMonthlySchedules(OWNER_ID, 2026, 0))
                .isInstanceOf(BusinessException.class);
        }
    }

    @Nested
    @DisplayName("존재하지 않는 리소스")
    class MissingResource {

        @Test
        @DisplayName("없는 사용자로는 일정을 만들 수 없다")
        void rejectsUnknownUser() {
            given(userRepository.findById(OWNER_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> scheduleService.createSchedule(OWNER_ID, validRequest()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("User not found");

            verify(scheduleRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("월별 조회 인자와 mock 결과 변환")
    class MonthlyQueryBoundary {

        @Test
        @DisplayName("해당 월과 다음 달 1일 0시로 조회하고 mock이 반환한 일정을 변환한다")
        void queriesExactMonthRange() {
            // 실제 쿼리의 경계 포함 여부가 아니라 조회 인자와 mock 결과 변환을 검증한다.
            given(scheduleRepository.findByUserIdAndStartAtBetweenOrderByStartAtAsc(
                OWNER_ID,
                LocalDateTime.of(2026, 9, 1, 0, 0),
                LocalDateTime.of(2026, 10, 1, 0, 0)
            )).willReturn(List.of(schedule(owner())));

            List<ScheduleResponse> result =
                scheduleService.getMonthlySchedules(OWNER_ID, 2026, 9);

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("평년 2월은 3월 1일 0시로 조회하고 mock의 빈 결과를 반환한다")
        void handlesFebruaryBoundary() {
            // 평년의 조회 인자를 검증하며 윤년이나 실제 DB의 날짜 필터링은 검증하지 않는다.
            given(scheduleRepository.findByUserIdAndStartAtBetweenOrderByStartAtAsc(
                OWNER_ID,
                LocalDateTime.of(2026, 2, 1, 0, 0),
                LocalDateTime.of(2026, 3, 1, 0, 0)
            )).willReturn(List.of());

            List<ScheduleResponse> result =
                scheduleService.getMonthlySchedules(OWNER_ID, 2026, 2);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("12월은 다음 해 1월 1일 0시로 조회하고 mock의 빈 결과를 반환한다")
        void handlesYearBoundary() {
            given(scheduleRepository.findByUserIdAndStartAtBetweenOrderByStartAtAsc(
                OWNER_ID,
                LocalDateTime.of(2026, 12, 1, 0, 0),
                LocalDateTime.of(2027, 1, 1, 0, 0)
            )).willReturn(List.of());

            List<ScheduleResponse> result =
                scheduleService.getMonthlySchedules(OWNER_ID, 2026, 12);

            assertThat(result).isEmpty();
        }
    }
}
