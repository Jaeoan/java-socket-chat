import java.io.*;
import java.net.*;
import java.util.Scanner;

/**
 * ChatClientCLI.java
 * ------------------
 * 터미널에서 실행하는 채팅 클라이언트.
 *
 * 로컬 테스트:
 *   java ChatClientCLI
 *
 * Railway 서버에 접속하려면:
 *   SERVER_HOST 와 SERVER_PORT 를 Railway TCP Proxy 값으로 바꾸세요.
 *   예) SERVER_HOST = "shuttle.proxy.rlwy.net"
 *       SERVER_PORT = 15140
 *
 * 주의:
 *   SERVER_HOST 에는 "https://" 또는 "http://" 를 붙이면 안 됩니다.
 *   TCP 소켓은 순수 호스트명(또는 IP)만 사용합니다.
 */
public class ChatClientCLI {

    // ── 서버 접속 정보 ─────────────────────────────────────────────────────
    // 로컬 테스트용 기본값
    private static final String SERVER_HOST = "tramway.proxy.rlwy.net";
    private static final int    SERVER_PORT = 17502;

    // Railway TCP Proxy 주소 예시 (배포 후 위 상수를 아래 값으로 교체하세요)
    // private static final String SERVER_HOST = "shuttle.proxy.rlwy.net";
    // private static final int    SERVER_PORT = 15140;
    // ──────────────────────────────────────────────────────────────────────

    public static void main(String[] args) {
        System.out.println("==============================================");
        System.out.println("     Java 소켓 채팅방 - 클라이언트");
        System.out.println("  접속 중: " + SERVER_HOST + ":" + SERVER_PORT);
        System.out.println("==============================================");

        Socket socket = null;
        try {
            socket = new Socket(SERVER_HOST, SERVER_PORT);
        } catch (IOException e) {
            System.out.println("[오류] 서버에 접속하지 못했습니다.");
            System.out.println("  → SERVER_HOST / SERVER_PORT 를 확인하세요.");
            System.out.println("  → 서버가 실행 중인지 확인하세요.");
            System.out.println("  → SERVER_HOST 에 https:// 를 붙이면 안 됩니다.");
            return;
        }

        System.out.println("[접속 성공] 서버에 연결되었습니다.");

        try {
            // 소켓: UTF-8 (서버와 통신)
            // 터미널 출력: 시스템 기본 인코딩 (Windows CP949 자동 대응)
            BufferedReader serverIn = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), "UTF-8"));
            PrintWriter serverOut = new PrintWriter(
                    new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);

            PrintStream consoleOut = new PrintStream(System.out, true,
                    java.nio.charset.Charset.defaultCharset().name());

            // ── 서버 메시지 수신 스레드 ────────────────────────────────────
            final Socket finalSocket = socket;
            Thread receiver = new Thread(() -> {
                try {
                    String line;
                    while ((line = serverIn.readLine()) != null) {
                        consoleOut.println(line);
                    }
                } catch (IOException e) {
                    // 소켓이 닫히면 루프 종료
                }
                consoleOut.println("[연결 끊김] 서버와의 연결이 종료되었습니다.");
                // 메인 스레드가 Scanner.nextLine() 에서 블로킹 중일 수 있으므로 강제 종료
                System.exit(0);
            });
            receiver.setDaemon(true);
            receiver.start();

            // ── 사용자 입력 루프 ───────────────────────────────────────────
            // 서버가 먼저 닉네임 입력을 요청하는 메시지를 보내므로
            // 사용자가 텍스트를 입력하면 그대로 서버로 전송한다.
            Scanner scanner = new Scanner(System.in); // 터미널 기본 인코딩(CP949/UTF-8) 자동 사용
            while (scanner.hasNextLine()) {
                String input = scanner.nextLine();

                if (input.trim().equals("/quit")) {
                    serverOut.println("/quit");
                    break;
                }

                serverOut.println(input);
            }

        } catch (IOException e) {
            System.out.println("[오류] 통신 중 문제가 발생했습니다: " + e.getMessage());
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
            System.out.println("[종료] 채팅방을 나갔습니다.");
        }
    }
}
