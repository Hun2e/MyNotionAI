# 코드 리뷰: 월별·오늘 일정 조회 경계 버그 수정 (PR #3)

- PR: https://github.com/Hun2e/MyNotionAI/pull/3
- 브랜치: `fix/monthly-query-boundary` (HEAD `7f89d76`)
- 작업 지시서: `docs/tasks/20261029-monthly-query-boundary.md`
- 리뷰어: Codex / 검증: Claude(PM)
- 결과: 🔴 필수 수정 0건, 🟡 권장 0건

## 🔴 필수 수정
없음.

## 🟡 권장
없음.

## 🟢 참고 (문제 없음)
- **반개구간 쿼리**: `ScheduleRepository.java:17`의 `startAt >= :from and startAt < :to`가 정확하다. 사용자 조건과 시작 시각 오름차순 정렬도 유지된다.
- **월별 범위**: `ScheduleService.java:32`는 `YearMonth`로 이번 달 1일과 다음 달 1일을 계산한다. 월마다 다른 일수, 윤년, 12월→1월 연도 전환을 `YearMonth`가 처리한다.
- **오늘 범위**: `AiService.java:134`는 `today`를 한 번만 구하고 오늘·내일 0시를 계산한다. 계산 도중 자정이 지나도 범위가 어긋나지 않는다.
- **범위 밖 판단**: `ReminderService`는 매분 59~61분 창을 조회하고 `ReminderLog`로 중복을 막는다. 월·일 구간이 겹치는 문제와 성격이 달라 이번 범위에서 제외한 판단이 타당하다. (단, 서버 여러 대의 동시 실행 안전성은 별개 문제이며 4단계 ShedLock에서 다룬다.)
- **테스트**: `ScheduleRepositoryTest.java:111`, `:130`은 `@DataJpaTest`로 실제 H2 SQL을 실행한다. 시작 직전·시작 정각·끝 직전·끝 정각 4개를 저장하고 `containsExactly`로 비교하므로 "시작 포함, 끝 제외"를 직접 증명한다. 경계값이 고정 날짜라 실행 날짜에 따라 결과가 바뀌지 않는다.
- **규칙 준수**: 변경된 메서드는 30줄 이하이고, 테스트 값은 상수로 정의했고, 4칸 들여쓰기와 한국어 주석을 지켰다.

## 남는 고려사항 (이번 PR 범위 밖)
- `LocalDateTime`에는 시간대 정보가 없다. "오늘"은 JVM 기본 시간대 기준이므로, 운영 서버 시간대가 사용자 기준(한국)과 다르면 오늘 요약이 어긋날 수 있다. 5단계 배포(Docker) 때 `TZ=Asia/Seoul` 같은 설정으로 맞춘다.
- 사용하지 않는 `findByUserIdAndStartAtBetween` 선언이 남아 있다. 별도 정리 작업으로 처리한다.

## 학습 포인트
1. **구간은 `[시작, 다음 시작)`으로 자른다.** 인접한 구간이 겹치지 않고, "23:59:59.999999" 같은 끝 시각의 정밀도를 신경 쓸 필요가 없다.
2. **Mock 테스트와 리포지토리 테스트는 검증하는 대상이 다르다.** 서비스 Mock 테스트는 "어떤 범위로 조회하는가", 리포지토리 테스트는 "SQL이 그 범위를 정말 그렇게 거르는가"를 검증한다.
3. **반개구간이 정확해도 "오늘"의 기준 시간대는 따로 맞춰야 한다.**

## 검증 (Claude)
- Codex 세션은 읽기 전용 샌드박스라 `mvn test`를 실행하지 못했다.
- Claude가 로컬에서 `cd backend && JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn test`를 실행했다: **47개 통과, BUILD SUCCESS**.
