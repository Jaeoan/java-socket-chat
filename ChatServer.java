import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * ChatServer.java
 * ---------------
 * 여러 클라이언트가 동시에 접속할 수 있는 TCP 소켓 채팅 서버.
 * Railway 환경변수 PORT 를 읽어서 0.0.0.0 에 바인딩한다.
 *
 * 실행:
 *   javac -encoding UTF-8 ChatServer.java
 *   java ChatServer
 */
public class ChatServer {

    // 접속 중인 클라이언트를 thread-safe 하게 보관
    private static final ConcurrentHashMap<String, PrintWriter> clients = new ConcurrentHashMap<>();

    public static void main(String[] args) throws IOException {
        // Railway 환경변수 PORT 를 읽는다. 없으면 기본값 8080
        int port = 8080;
        String envPort = System.getenv("PORT");
        if (envPort != null && !envPort.isEmpty()) {
            port = Integer.parseInt(envPort);
        }

        // 0.0.0.0 으로 바인딩 (Railway 필수)
        ServerSocket serverSocket = new ServerSocket(port, 50, InetAddress.getByName("0.0.0.0"));
        System.out.println("[서버] 채팅 서버 시작 - 포트: " + port);

        try {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("[서버] 새 연결 시도: " + clientSocket.getRemoteSocketAddress());
                // 클라이언트마다 별도 스레드로 처리
                Thread t = new Thread(new ClientHandler(clientSocket));
                t.setDaemon(true);
                t.start();
            }
        } finally {
            serverSocket.close();
        }
    }

    // 모든 클라이언트에게 메시지를 전송 (broadcast)
    static void broadcast(String message) {
        System.out.println("[broadcast] " + message);
        for (PrintWriter writer : clients.values()) {
            writer.println(message);
        }
    }

    // 특정 닉네임을 제외하고 broadcast
    static void broadcastExcept(String excludeNickname, String message) {
        System.out.println("[broadcast] " + message);
        clients.forEach((nickname, writer) -> {
            if (!nickname.equals(excludeNickname)) {
                writer.println(message);
            }
        });
    }

    // 접속자 목록을 문자열로 반환
    static String getUserList() {
        Set<String> names = clients.keySet();
        if (names.isEmpty()) {
            return "[서버] 현재 접속자가 없습니다.";
        }
        return "[서버] 현재 접속자 (" + names.size() + "명): " + String.join(", ", names);
    }

    // -------------------------------------------------------------------------
    // 클라이언트 1명을 담당하는 스레드
    // -------------------------------------------------------------------------
    static class ClientHandler implements Runnable {

        private final Socket socket;
        private String nickname;
        private PrintWriter out;

        ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                // UTF-8 스트림 설정
                BufferedReader in = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), "UTF-8"));
                out = new PrintWriter(
                        new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);

                // ── 1단계: 닉네임 받기 ──────────────────────────────────────
                out.println("[서버] 닉네임을 입력하세요 (최대 20자):");
                String raw = in.readLine();
                if (raw == null) return; // 연결 끊김

                // 닉네임 정리 및 길이 제한
                nickname = raw.trim().replaceAll("\\s+", "_");
                if (nickname.isEmpty()) nickname = "익명";
                if (nickname.length() > 20) nickname = nickname.substring(0, 20);

                // 중복 닉네임 처리
                if (clients.containsKey(nickname)) {
                    out.println("[서버] 이미 사용 중인 닉네임입니다. 연결을 종료합니다.");
                    socket.close();
                    return;
                }

                // 클라이언트 등록
                clients.put(nickname, out);
                System.out.println("[서버] 입장: " + nickname + " / 현재 " + clients.size() + "명");

                out.println("[서버] 환영합니다, " + nickname + "님! 채팅방에 입장했습니다.");
                out.println("[서버] 명령어: /users (접속자 목록)  /quit (나가기)");
                broadcast("[입장] " + nickname + "님이 채팅방에 들어왔습니다.");

                // ── 2단계: 메시지 수신 루프 ─────────────────────────────────
                String line;
                while ((line = in.readLine()) != null) {
                    line = line.trim();

                    if (line.isEmpty()) continue;

                    if (line.equals("/quit")) {
                        // 정상 퇴장
                        break;
                    } else if (line.equals("/users")) {
                        // 접속자 목록은 요청한 클라이언트에게만 전송
                        out.println(getUserList());
                    } else {
                        // 일반 메시지: 500자 제한
                        if (line.length() > 500) {
                            line = line.substring(0, 500) + "...(생략)";
                        }
                        broadcast("[" + nickname + "] " + line);
                    }
                }

            } catch (IOException e) {
                System.out.println("[서버] 연결 오류 (" + nickname + "): " + e.getMessage());
            } finally {
                // ── 3단계: 정리 ─────────────────────────────────────────────
                if (nickname != null) {
                    clients.remove(nickname);
                    System.out.println("[서버] 퇴장: " + nickname + " / 현재 " + clients.size() + "명");
                    broadcast("[퇴장] " + nickname + "님이 채팅방에서 나갔습니다.");
                }
                try { socket.close(); } catch (IOException ignored) {}
            }
        }
    }
}
