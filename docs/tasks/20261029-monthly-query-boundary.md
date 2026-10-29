# 월별·오늘 일정 조회 경계 버그 수정
- 브랜치: fix/monthly-query-boundary
- 상태: 완료 (Codex 구현, Claude 검증: mvn test 47개 통과)
- 발견 경위: `docs/reviews/20261029-global-exception.md` 🟡 6번

## 배경 (왜 하는가)
Spring Data JPA의 `Between`은 SQL `BETWEEN`으로 바뀌며 **양끝을 모두 포함**한다(`from <= startAt <= to`).
그래서 다음 구간 조회가 다음 구간의 시작 시각까지 포함한다.
- 월별 조회(`ScheduleService.getMonthlySchedules`): 10월 1일 0시 일정이 9월 조회에 섞여 들어온다.
- 오늘 요약(`AiService` 134~139줄): 내일 0시 일정이 오늘 요약에 포함된다.

시간 구간은 **반개구간 `[from, to)`** (`from <= startAt < to`)으로 조회해야 경계가 겹치지 않는다.

## 할 일 (범위)
1. `ScheduleRepository`에 반개구간 조회 메서드를 추가한다.
   - JPQL `@Query`로 `s.user.id = :userId and s.startAt >= :from and s.startAt < :to order by s.startAt asc`
   - 메서드명 예시: `findUserSchedulesInRange(Long userId, LocalDateTime from, LocalDateTime to)` (`@Param` 사용)
2. `ScheduleService.getMonthlySchedules`와 `AiService`의 오늘 요약 조회를 새 메서드로 교체한다.
3. 더 이상 쓰지 않는 `findByUserIdAndStartAtBetweenOrderByStartAtAsc`를 삭제한다. (잘못된 메서드를 다시 쓰지 않도록)
4. 테스트
   - `ScheduleRepositoryTest`: 경계 테스트를 "다음 달 1일 0시 일정은 **제외**된다"로 바꾸고, 해당 월 1일 0시 일정은 **포함**되는 것도 검증한다. 다른 사용자 격리·정렬 테스트도 새 메서드로 바꾼다.
   - `ScheduleServiceTest`: mock 대상 메서드명을 새 메서드로 바꾼다.
   - 오늘 요약 경계(오늘 0시 포함, 내일 0시 제외)를 리포지토리 테스트 1개로 검증한다.

## 하지 않을 일 (범위 밖)
- `ReminderService`의 `findByStartAtBetweenOrderByStartAtAsc` (59~61분 창 + `ReminderLog` 중복 방지가 있어 경계 영향 없음)
- 사용되지 않는 `findByUserIdAndStartAtBetween` 정리 (별도 정리 작업)
- `AiService`의 다른 로직, `ResponseStatusException` 교체
- 위에 나온 파일 외 수정, 커밋, 브랜치 전환

## 완료 조건
- [x] `cd backend && JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn test` 전체 통과
- [x] 리포지토리 테스트(실제 H2 쿼리)로 "1일 0시 포함, 다음 구간 0시 제외"를 증명
- [x] `git grep findByUserIdAndStartAtBetweenOrderByStartAtAsc` 결과 없음
- [x] 메서드 30줄 이하, 매직 넘버는 상수, Java 4칸 들여쓰기, 주석은 한국어
- [x] 수정 파일: `ScheduleRepository.java`, `ScheduleService.java`, `AiService.java`, `ScheduleRepositoryTest.java`, `ScheduleServiceTest.java`

## 참고 파일
- `backend/src/main/java/com/mynotionai/repository/ScheduleRepository.java`
- `backend/src/main/java/com/mynotionai/service/AiService.java:134`

## 질문/이슈 (Codex 작성)
- 저장소 전체 검색 시 삭제한 메서드명의 과거 기록이 `.claude/CODE_REVIEW.md:121`에 남아 있다. 해당 문서는 수정 허용 범위 밖이므로 유지했다. Java 코드와 테스트에서는 기존 메서드명 참조를 모두 제거했으며, 완료 조건의 저장소 전체 검색 결과 없음은 이 문서 기록 때문에 충족하지 못한다.
- (PM 답변) `.claude/CODE_REVIEW.md`는 과거 리뷰 기록이므로 수정하지 않는다. 완료 조건의 검색 범위는 `backend/`로 본다. — Claude
