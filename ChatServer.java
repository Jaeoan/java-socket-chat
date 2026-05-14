import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class ChatServer {

    private static final ConcurrentHashMap<String, PrintWriter> clients = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Socket> clientSockets = new ConcurrentHashMap<>();

    // ── 게임 상태 ──────────────────────────────────────────────────────────────
    enum GamePhase { WAITING, DESCRIBING, VOTING }

    static volatile GamePhase gamePhase = GamePhase.WAITING;
    static final Object gameLock = new Object();

    static final String[] WORDS = {"사과", "자동차", "바다", "피자", "강아지"};

    static List<String> playerOrder = new ArrayList<>(); // 순서대로 번호 배정, "AI" 포함
    static int aiIndex = -1;          // playerOrder 에서 AI 위치 (0-based)
    static String currentWord = "";
    static volatile int currentTurn = 0;

    static final ConcurrentHashMap<String, Integer> votes = new ConcurrentHashMap<>();

    // ── 서버 진입점 ────────────────────────────────────────────────────────────
    public static void main(String[] args) throws IOException {
        int port = 8080;
        String envPort = System.getenv("PORT");
        if (envPort != null && !envPort.isEmpty()) {
            port = Integer.parseInt(envPort);
        }

        ServerSocket serverSocket = new ServerSocket(port, 50, InetAddress.getByName("0.0.0.0"));
        System.out.println("[서버] 시작 - 포트: " + port);

        try {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                Thread t = new Thread(new ClientHandler(clientSocket));
                t.setDaemon(true);
                t.start();
            }
        } finally {
            serverSocket.close();
        }
    }

    // ── 전송 유틸 ──────────────────────────────────────────────────────────────
    static void broadcast(String msg) {
        System.out.println("[broadcast] " + msg);
        for (PrintWriter w : clients.values()) w.println(msg);
    }

    static void sendTo(String nickname, String msg) {
        PrintWriter w = clients.get(nickname);
        if (w != null) w.println(msg);
    }

    static String getUserList() {
        Set<String> names = clients.keySet();
        if (names.isEmpty()) return "[서버] 접속자 없음";
        return "[서버] 접속자 (" + names.size() + "명): " + String.join(", ", names);
    }

    static void kickClient(String target, String by) {
        Socket s = clientSockets.get(target);
        if (s == null) {
            sendTo(by, "[서버] '" + target + "' 님을 찾을 수 없습니다.");
            return;
        }
        try {
            sendTo(target, "[서버] 강제 퇴장 처리되었습니다.");
            s.close();
            broadcast("[서버] " + by + "님이 " + target + "님을 강제 퇴장했습니다.");
        } catch (IOException ignored) {}
    }

    // ── 게임 시작 ──────────────────────────────────────────────────────────────
    static void startGame(String requester) {
        synchronized (gameLock) {
            if (gamePhase != GamePhase.WAITING) {
                sendTo(requester, "[서버] 이미 게임이 진행 중입니다.");
                return;
            }
            List<String> humans = new ArrayList<>(clients.keySet());
            if (humans.size() < 2) {
                sendTo(requester, "[서버] 2명 이상이어야 시작할 수 있습니다.");
                return;
            }

            Collections.shuffle(humans);
            currentWord = WORDS[new Random().nextInt(WORDS.length)];

            // AI를 랜덤 위치에 끼워 넣기
            aiIndex = new Random().nextInt(humans.size() + 1);
            playerOrder = new ArrayList<>(humans);
            playerOrder.add(aiIndex, "AI");

            currentTurn = 0;
            votes.clear();
            gamePhase = GamePhase.DESCRIBING;

            broadcast("[게임] ══════════ 게임 시작! ══════════");
            broadcast("[게임] 단어: 【" + currentWord + "】");
            broadcast("[게임] 이 단어를 설명하는 사람 중 AI가 섞여 있습니다!");
            broadcast("[게임] 설명이 끝나면 /투표 [번호] 로 AI를 지목하세요.");
            broadcast("");

            // 참가자에게 자신의 번호 비공개 전송
            for (int i = 0; i < playerOrder.size(); i++) {
                String p = playerOrder.get(i);
                if (!p.equals("AI")) {
                    sendTo(p, "[게임] ★ 당신은 " + (i + 1) + "번 플레이어입니다. 비밀로 하세요! ★");
                }
            }
            broadcast("[게임] 총 " + playerOrder.size() + "명이 참가합니다. (1번 ~ " + playerOrder.size() + "번)");
        }
        announceTurn();
    }

    // ── 차례 안내 ──────────────────────────────────────────────────────────────
    static void announceTurn() {
        int turn;
        String player;
        synchronized (gameLock) {
            if (gamePhase != GamePhase.DESCRIBING) return;
            if (currentTurn >= playerOrder.size()) {
                startVoting();
                return;
            }
            turn = currentTurn;
            player = playerOrder.get(turn);
        }

        int num = turn + 1;
        broadcast("[게임] " + num + "번 플레이어의 차례입니다. 단어를 설명해주세요!");
        if (player.equals("AI")) {
            new Thread(() -> {
                try { Thread.sleep(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                String desc = getAIDescription(currentWord);
                broadcast("[" + num + "번] " + desc);
                try { Thread.sleep(800); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                synchronized (gameLock) { currentTurn++; }
                announceTurn();
            }).start();
        }
    }

    // ── 설명 처리 ──────────────────────────────────────────────────────────────
    static void handleDescription(String nickname, String msg) {
        boolean advance = false;
        int num = -1;
        synchronized (gameLock) {
            if (gamePhase != GamePhase.DESCRIBING) return;
            if (currentTurn >= playerOrder.size()) return;

            String curPlayer = playerOrder.get(currentTurn);
            if (curPlayer.equals("AI")) {
                sendTo(nickname, "[서버] AI가 생각 중입니다. 잠시만 기다려주세요.");
                return;
            }
            if (!curPlayer.equals(nickname)) {
                sendTo(nickname, "[서버] 지금은 " + (currentTurn + 1) + "번 플레이어의 차례입니다.");
                return;
            }
            num = currentTurn + 1;
            currentTurn++;
            advance = true;
        }
        if (advance) {
            broadcast("[" + num + "번] " + msg);
            announceTurn();
        }
    }

    // ── 투표 시작 ──────────────────────────────────────────────────────────────
    static void startVoting() {
        synchronized (gameLock) {
            gamePhase = GamePhase.VOTING;
        }
        broadcast("");
        broadcast("[게임] ══════════ 투표 시작! ══════════");
        broadcast("[게임] AI라고 생각하는 번호를 입력하세요: /투표 [번호]");
        broadcast("[게임] 모두 투표하면 결과가 공개됩니다!");
    }

    // ── 투표 처리 ──────────────────────────────────────────────────────────────
    static void handleVote(String nickname, int voteNum) {
        synchronized (gameLock) {
            if (gamePhase != GamePhase.VOTING) {
                sendTo(nickname, "[서버] 지금은 투표 시간이 아닙니다.");
                return;
            }
            if (!playerOrder.contains(nickname)) {
                sendTo(nickname, "[서버] 게임 도중 입장하셨습니다. 이번 게임은 투표할 수 없습니다.");
                return;
            }
            if (voteNum < 1 || voteNum > playerOrder.size()) {
                sendTo(nickname, "[서버] 올바른 번호를 입력하세요. (1 ~ " + playerOrder.size() + ")");
                return;
            }
            if (votes.containsKey(nickname)) {
                sendTo(nickname, "[서버] 이미 투표했습니다.");
                return;
            }
            votes.put(nickname, voteNum);
            int humanCount = playerOrder.size() - 1;
            broadcast("[투표] 투표 완료 (" + votes.size() + "/" + humanCount + ")");

            if (votes.size() >= humanCount) {
                revealResult();
            }
        }
    }

    // ── 결과 공개 ──────────────────────────────────────────────────────────────
    static void revealResult() {
        // 가장 많이 투표받은 번호 집계
        Map<Integer, Integer> tally = new HashMap<>();
        for (int v : votes.values()) tally.merge(v, 1, Integer::sum);

        int maxVotes = 0, mostVoted = -1;
        for (Map.Entry<Integer, Integer> e : tally.entrySet()) {
            if (e.getValue() > maxVotes) {
                maxVotes = e.getValue();
                mostVoted = e.getKey();
            }
        }

        boolean correct = (mostVoted == aiIndex + 1);

        broadcast("");
        broadcast("[게임] ══════════ 결과 발표! ══════════");
        broadcast("[게임] 가장 많은 표: " + mostVoted + "번 (" + maxVotes + "표)");
        broadcast("[게임] AI는 " + (aiIndex + 1) + "번이었습니다!");
        broadcast("");
        if (correct) {
            broadcast("[게임] ★★★ 인간 승리! AI를 찾아냈습니다! ★★★");
        } else {
            broadcast("[게임] ☆☆☆ AI 승리! AI가 살아남았습니다! ☆☆☆");
        }
        broadcast("[게임] 다시 하려면 /시작 을 입력하세요.");

        // 게임 초기화
        gamePhase = GamePhase.WAITING;
        playerOrder.clear();
        votes.clear();
        aiIndex = -1;
    }

    // ── OpenAI API 호출 ────────────────────────────────────────────────────────
    static final String[] FALLBACKS = {
        "어 이거 나 진짜 좋아하는데 딱 뭐라 설명이 안 되네",
        "흔한 건데 어떻게 설명하지... 일상에서 많이 쓰는 거잖아",
        "이거 나 어릴 때부터 알던 건데 갑자기 설명하려니까 말이 안 나옴",
        "되게 친숙한 건데 막상 설명하려니까 어렵다",
        "이거 진짜 자주 보는 건데 설명하기가 생각보다 어렵네"
    };

    static String getAIDescription(String word) {
        String apiKey = System.getenv("GROQ_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            System.out.println("[AI 오류] GROQ_API_KEY 환경변수가 없음");
            return FALLBACKS[new Random().nextInt(FALLBACKS.length)];
        }
        try {
            URL url = new URL("https://api.groq.com/openai/v1/chat/completions");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);

            String prompt =
                "너는 지금 친구들이랑 라이어 게임 중이야. " +
                "주어진 단어를 딱 1~2단어로 특징만 말해. " +
                "절대 단어를 직접 말하면 안 되고, 문장 끝을 반드시 '~임', '~음', '~함' 으로 끝내. " +
                "예시: '멋있음 빠름', '달달함 맛있음', '무서움 깊음', '귀여움 작음' " +
                "단어: " + word;

            String body = "{\"model\":\"llama-3.3-70b-versatile\",\"messages\":[{\"role\":\"user\",\"content\":\""
                    + escapeJson(prompt) + "\"}],\"max_tokens\":150}";

            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes("UTF-8"));
            }

            int status = conn.getResponseCode();
            if (status != 200) {
                StringBuilder err = new StringBuilder();
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getErrorStream(), "UTF-8"))) {
                    String line;
                    while ((line = br.readLine()) != null) err.append(line);
                }
                System.out.println("[AI 오류] " + status + " - " + err);
                return FALLBACKS[new Random().nextInt(FALLBACKS.length)];
            }

            StringBuilder resp = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
                String line;
                while ((line = br.readLine()) != null) resp.append(line);
            }

            return extractContent(resp.toString());

        } catch (Exception e) {
            System.out.println("[AI 오류] " + e.getClass().getSimpleName() + ": " + e.getMessage());
            return FALLBACKS[new Random().nextInt(FALLBACKS.length)];
        }
    }

    static String extractContent(String json) {
        int idx = json.indexOf("\"content\":");
        if (idx < 0) return "...";
        int start = json.indexOf("\"", idx + 10) + 1;
        StringBuilder sb = new StringBuilder();
        int i = start;
        while (i < json.length()) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) {
                char next = json.charAt(i + 1);
                if (next == '"') sb.append('"');
                else if (next == 'n') sb.append(' ');
                else if (next == '\\') sb.append('\\');
                else sb.append(next);
                i += 2;
            } else if (c == '"') {
                break;
            } else {
                sb.append(c);
                i++;
            }
        }
        return sb.toString().trim();
    }

    static String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    // ── 클라이언트 핸들러 ──────────────────────────────────────────────────────
    static class ClientHandler implements Runnable {

        private final Socket socket;
        private String nickname;
        private PrintWriter out;

        ClientHandler(Socket socket) { this.socket = socket; }

        @Override
        public void run() {
            try {
                socket.setSoTimeout(180000); // 3분 무응답 시 자동 종료
                BufferedReader in = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), "UTF-8"));
                out = new PrintWriter(
                        new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);

                // 닉네임 등록
                out.println("[서버] 닉네임을 입력하세요 (최대 20자):");
                String raw = in.readLine();
                if (raw == null) return;

                nickname = raw.trim().replaceAll("\\s+", "_");
                if (nickname.isEmpty()) nickname = "익명";
                if (nickname.length() > 20) nickname = nickname.substring(0, 20);

                if (clients.containsKey(nickname)) {
                    out.println("[서버] 이미 사용 중인 닉네임입니다. 연결을 종료합니다.");
                    socket.close();
                    return;
                }

                clients.put(nickname, out);
                clientSockets.put(nickname, socket);
                out.println("[서버] 환영합니다, " + nickname + "님!");
                out.println("[서버] 명령어: /시작  /투표 [번호]  /강퇴 [닉네임]  /users  /quit");
                broadcast("[입장] " + nickname + "님 입장. (현재 " + clients.size() + "명)");
                if (gamePhase != GamePhase.WAITING) {
                    out.println("[서버] ⚠ 당신은 대기자입니다. 게임이 끝날 때까지 기다려주세요.");
                }

                // 메시지 루프
                String line;
                while ((line = in.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;

                    if (line.equals("/quit")) {
                        break;
                    } else if (line.equals("/users")) {
                        out.println(getUserList());
                    } else if (line.startsWith("/강퇴 ")) {
                        String target = line.substring(4).trim();
                        kickClient(target, nickname);
                    } else if (line.equals("/시작")) {
                        startGame(nickname);
                    } else if (line.startsWith("/투표 ")) {
                        try {
                            int num = Integer.parseInt(line.substring(4).trim());
                            handleVote(nickname, num);
                        } catch (NumberFormatException e) {
                            out.println("[서버] 사용법: /투표 [번호]");
                        }
                    } else if (gamePhase == GamePhase.DESCRIBING) {
                        if (!playerOrder.contains(nickname)) {
                            out.println("[서버] 게임 도중 입장하셨습니다. 게임이 끝난 후 참여할 수 있습니다.");
                            continue;
                        }
                        if (line.length() > 200) line = line.substring(0, 200) + "...";
                        handleDescription(nickname, line);
                    } else {
                        if (line.length() > 500) line = line.substring(0, 500) + "...";
                        broadcast("[" + nickname + "] " + line);
                    }
                }

            } catch (IOException e) {
                System.out.println("[서버] 오류 (" + nickname + "): " + e.getMessage());
            } finally {
                if (nickname != null) {
                    clients.remove(nickname);
                    clientSockets.remove(nickname);
                    broadcast("[퇴장] " + nickname + "님 퇴장. (현재 " + clients.size() + "명)");
                    synchronized (gameLock) {
                        if (gamePhase != GamePhase.WAITING) {
                            gamePhase = GamePhase.WAITING;
                            playerOrder.clear();
                            votes.clear();
                            aiIndex = -1;
                            currentTurn = 0;
                            broadcast("[게임] 참가자가 퇴장하여 게임이 취소되었습니다. 다시 /시작 을 입력하세요.");
                        }
                    }
                }
                try { socket.close(); } catch (IOException ignored) {}
            }
        }
    }
}
