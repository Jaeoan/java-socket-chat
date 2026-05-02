# ────────────────────────────────────────────────────────
# Dockerfile - ChatServer 빌드 & 실행
# ────────────────────────────────────────────────────────
# eclipse-temurin:21-jdk 이미지를 사용한다.
# ChatServer.java 만 컴파일하여 서버로 실행한다.
# Railway 환경변수 PORT 를 통해 포트를 동적으로 받는다.
# ────────────────────────────────────────────────────────

FROM eclipse-temurin:21-jdk

# 작업 디렉터리 설정
WORKDIR /app

# ChatServer.java 를 컨테이너 안으로 복사
COPY ChatServer.java .

# UTF-8 인코딩으로 컴파일
RUN javac -encoding UTF-8 ChatServer.java

# 기본 포트 (Railway가 환경변수 PORT 로 덮어씀)
ENV PORT=8080

# Railway TCP Proxy 가 내부 포트 8080 을 바라본다
EXPOSE 8080

# 서버 실행
CMD ["java", "ChatServer"]
