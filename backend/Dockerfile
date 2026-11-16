FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /build

# 소스 변경 시에도 의존성 다운로드 레이어를 재사용한다.
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src/ src/
# 테스트는 별도로 실행하고 이미지 빌드에서는 패키징만 수행한다.
RUN mvn -B -DskipTests package

FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

# 상태 검사 도구를 설치하고 앱 실행 전용 사용자를 만든다.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system spring \
    && useradd --system --gid spring --no-create-home spring

COPY --from=build --chown=spring:spring /build/target/*.jar /app/app.jar

USER spring

EXPOSE 8080 8081

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
