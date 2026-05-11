# Java 소켓 채팅방 (발표용)

순수 Java 만으로 만든 TCP 소켓 채팅 서버/클라이언트 
외부 라이브러리 없이 `java.net.Socket` 만 사용

---

## 파일 구조 및 역할

| 파일 | 역할 |
|------|------|
| `ChatServer.java` | 채팅 서버. Railway(또는 로컬)에서 실행. 여러 클라이언트를 동시에 처리 |
| `ChatClientCLI.java` | 채팅 클라이언트. 팀원들이 터미널에서 실행 |
| `Dockerfile` | Railway 배포용. ChatServer를 컨테이너로 실행 |
| `README.md` | 이 문서 |

---

## 발표용 한 줄 설명 멘트

> "이 프로젝트는 순수 Java 소켓으로 구현한 실시간 채팅 서버입니다.  
> 서버는 Railway에 배포되어 있고, 팀원 누구나 터미널 하나로 접속해서 대화할 수 있습니다.  


---

## 로컬 테스트 방법

### 1. 컴파일

```bash
javac -encoding UTF-8 ChatServer.java ChatClientCLI.java
```

### 2. 서버 실행 (터미널 1)

```bash
java ChatServer
```

서버가 포트 8080 으로 대기합니다.

### 3. 클라이언트 실행 (터미널 2, 3, ...)

```bash
java ChatClientCLI
```

여러 터미널에서 동시에 실행하면 여러 명이 대화하는 것처럼 테스트할 수 있습니다.

---

## 채팅 명령어

| 명령어 | 설명 |
|--------|------|
| `/users` | 현재 접속자 목록 출력 |
| `/quit` | 채팅방 나가기 |

---

## Railway 배포 방법

### 사전 준비

```bash
# Railway CLI 설치 (npm 필요)
npm install -g @railway/cli

# 로그인
railway login
```

### 배포

```bash
# 프로젝트 초기화 (처음 한 번만)
railway init

# 배포
railway up
```

Railway 가 `Dockerfile` 을 자동으로 감지하여 빌드 & 배포합니다.

---

## Railway TCP Proxy 설정 방법

Railway 는 기본적으로 HTTP 서버를 가정합니다.  
TCP 소켓 서버를 외부에 노출하려면 **TCP Proxy** 를 설정해야 합니다.

1. Railway 대시보드 → 프로젝트 선택
2. 서비스 클릭 → **Settings** 탭
3. **Networking** 섹션 → **Add TCP Proxy** 클릭
4. **Internal Port = 8080** 입력 후 저장
5. 생성된 **Public Hostname** 과 **Port** 를 메모  
   예) `shuttle.proxy.rlwy.net : 15140`

> Internal Port 는 반드시 **8080** 이어야 합니다 (Dockerfile EXPOSE 값과 동일).

---

## 팀원이 클라이언트만 실행하는 방법

팀원은 서버를 실행할 필요가 없습니다.  
`ChatClientCLI.java` 만 컴파일해서 실행하면 됩니다.

```bash
# 컴파일
javac -encoding UTF-8 ChatClientCLI.java

# 실행
java ChatClientCLI
```

> **중요**: `ChatServer.java` 는 발표자가 Railway 에 미리 배포해둔 것을 사용합니다.  
> 팀원들은 절대 `java ChatServer` 를 실행하면 안 됩니다.

---

## 서버 주소를 클라이언트 코드에 넣는 방법

Railway TCP Proxy 주소를 받은 뒤 `ChatClientCLI.java` 상단의 상수를 수정합니다.

```java
// ChatClientCLI.java 상단 (수정 전 - 로컬 테스트용)
private static final String SERVER_HOST = "localhost";
private static final int    SERVER_PORT = 8080;

// 수정 후 (Railway 배포 서버용)
private static final String SERVER_HOST = "shuttle.proxy.rlwy.net";
private static final int    SERVER_PORT = 15140;
```

> SERVER_HOST 에 `https://` 또는 `http://` 를 붙이면 절대 안 됩니다.  
> TCP 소켓은 순수 호스트명(또는 IP)만 사용합니다.

수정 후 다시 컴파일하고 실행하세요.

```bash
javac -encoding UTF-8 ChatClientCLI.java
java ChatClientCLI
```

---

## 자주 나는 오류

### `Connection refused`

```
[오류] 서버에 접속하지 못했습니다.
```

- 서버가 실행 중인지 확인
- `SERVER_HOST` / `SERVER_PORT` 가 올바른지 확인
- Railway TCP Proxy 설정이 완료됐는지 확인
- `SERVER_HOST` 에 `https://` 가 붙어 있으면 제거

---

### 한글이 깨지는 경우

Windows 터미널에서 인코딩이 맞지 않을 때 발생합니다.

```bash
# Windows CMD 에서 UTF-8 로 전환
chcp 65001
java ChatClientCLI
```

---

### `Address already in use`

8080 포트를 다른 프로그램이 사용 중입니다.

```bash
# 사용 중인 프로세스 확인 (Windows)
netstat -ano | findstr :8080

# 사용 중인 프로세스 확인 (Mac/Linux)
lsof -i :8080
```

---

### Railway 배포 후 클라이언트가 연결 안 될 때

- Railway 대시보드에서 TCP Proxy 가 활성화됐는지 확인
- Public Port 번호가 `SERVER_PORT` 와 일치하는지 확인
- 서비스 로그에서 서버가 정상 시작됐는지 확인

```bash
railway logs
```

---

## 전체 명령어 요약

```bash
# 컴파일
javac -encoding UTF-8 ChatServer.java ChatClientCLI.java

# 로컬 서버 실행
java ChatServer

# 로컬 클라이언트 실행
java ChatClientCLI

# Railway 배포
railway login
railway init
railway up

# Railway 로그 확인
railway logs
```

**Railway TCP Proxy 설정:**  
Internal Port = 8080

**클라이언트 Railway 주소 설정 예시:**

```java
private static final String SERVER_HOST = "shuttle.proxy.rlwy.net";
private static final int    SERVER_PORT = 15140;
```

---

> 팀원들은 `ChatServer.java` 를 실행하면 안 되고 `ChatClientCLI.java` 만 실행해야 합니다.  
> 서버는 발표자가 미리 Railway 에 배포해둡니다.
