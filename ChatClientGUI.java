import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.text.*;

/**
 * ChatClientGUI.java
 * ------------------
 * Java Swing 으로 만든 AI 라이어 게임 클라이언트 (외부 라이브러리 없음).
 *
 * 직관적인 UI/UX 개선 사항:
 *   - 게임 상태 카드 (단계 / 단어 / 현재 차례 / 내 번호) 상단 고정
 *   - 우측 접속자 사이드바 (실시간 입퇴장 반영)
 *   - 메시지 종류별 시각적 구분 (내 메시지 / 타인 / 시스템 / 게임 / AI)
 *   - 투표 다이얼로그를 번호 버튼으로 개선 (입력 불필요)
 *   - 도움말 / 접속자 새로고침 / 게임 시작 / AI 투표 액션 버튼
 *   - 서버 메시지 자동 파싱 → UI 상태 동기화
 *
 * 실행:
 *   javac -encoding UTF-8 ChatClientGUI.java
 *   java ChatClientGUI
 */
public class ChatClientGUI extends JFrame {

    // ── 서버 접속 정보 ─────────────────────────────────────────────────────
    // SERVER_HOST 에 https:// 를 붙이면 안 됩니다. 순수 호스트명만 입력하세요.
    private static final String SERVER_HOST = "tramway.proxy.rlwy.net";
    private static final int    SERVER_PORT = 17502;

    // 로컬 테스트용
    // private static final String SERVER_HOST = "localhost";
    // private static final int    SERVER_PORT = 8080;
    // ──────────────────────────────────────────────────────────────────────

    // ── 색상 팔레트 (Tokyo Night 기반 다크 테마) ──────────────────────────
    private static final Color BG_MAIN     = new Color(0x1E1F29);
    private static final Color BG_HEADER   = new Color(0x16171F);
    private static final Color BG_CARD     = new Color(0x2A2C3A);
    private static final Color BG_CARD_ALT = new Color(0x32354A);
    private static final Color BG_INPUT    = new Color(0x2D2F3D);
    private static final Color BORDER_SOFT = new Color(0x3B3E52);

    private static final Color TEXT_MAIN   = new Color(0xE8E9F0);
    private static final Color TEXT_MUTED  = new Color(0x9B9DAB);
    private static final Color TEXT_DIM    = new Color(0x6B6E80);

    private static final Color ACCENT_BLUE   = new Color(0x6C8EFF);
    private static final Color ACCENT_GREEN  = new Color(0x56C596);
    private static final Color ACCENT_YELLOW = new Color(0xFFB454);
    private static final Color ACCENT_RED    = new Color(0xFF6B7A);
    private static final Color ACCENT_PURPLE = new Color(0xB084FF);
    private static final Color ACCENT_PINK   = new Color(0xFF9EC4);

    // ── 폰트 ──────────────────────────────────────────────────────────────
    private static final String FONT_FAMILY = "맑은 고딕";
    private static final Font FONT_TITLE  = new Font(FONT_FAMILY, Font.BOLD,  16);
    private static final Font FONT_LABEL  = new Font(FONT_FAMILY, Font.BOLD,  11);
    private static final Font FONT_VALUE  = new Font(FONT_FAMILY, Font.BOLD,  15);
    private static final Font FONT_BODY   = new Font(FONT_FAMILY, Font.PLAIN, 13);
    private static final Font FONT_SMALL  = new Font(FONT_FAMILY, Font.PLAIN, 11);
    private static final Font FONT_BUTTON = new Font(FONT_FAMILY, Font.BOLD,  12);

    // ── 게임 상태 (서버 메시지에서 추적) ──────────────────────────────────
    private enum Phase {
        WAITING("대기 중",   ACCENT_YELLOW),
        DESCRIBING("설명 중", ACCENT_GREEN),
        VOTING("투표 중",     ACCENT_PURPLE);
        final String label;
        final Color color;
        Phase(String l, Color c) { this.label = l; this.color = c; }
    }
    private Phase phase = Phase.WAITING;
    private String currentWord = "—";
    private int currentTurnNum = 0;
    private int myNumber = -1;
    private int totalPlayers = 0;

    // ── 네트워크 ──────────────────────────────────────────────────────────
    private PrintWriter serverOut;
    private Socket socket;
    private final String nickname;

    // ── UI 컴포넌트 ───────────────────────────────────────────────────────
    private JTextPane chatArea;
    private JTextField inputField;
    private JButton sendButton;

    private JLabel connectionDot;
    private JLabel connectionText;

    private JLabel phaseBadge;
    private JLabel wordValue;
    private JLabel turnValue;
    private JLabel myNumberValue;

    private DefaultListModel<String> playerListModel;
    private JList<String> playerListView;
    private JLabel playerCountLabel;

    private JButton startButton;
    private JButton voteButton;

    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm");

    // ── 생성자 ────────────────────────────────────────────────────────────
    public ChatClientGUI(String nickname) {
        this.nickname = nickname;
        buildUI();
        connectToServer();
    }

    // ──────────────────────────────────────────────────────────────────────
    //  UI 구성
    // ──────────────────────────────────────────────────────────────────────
    private void buildUI() {
        setTitle("AI 라이어 게임 — " + nickname);
        setSize(960, 640);
        setMinimumSize(new Dimension(820, 560));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        getContentPane().setBackground(BG_MAIN);
        setLayout(new BorderLayout());

        add(buildHeader(),     BorderLayout.NORTH);
        add(buildCenterArea(), BorderLayout.CENTER);

        // 창 닫을 때 /quit 전송
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { disconnect(); }
        });

        setVisible(true);
        inputField.requestFocus();
    }

    // ── 상단 헤더 ─────────────────────────────────────────────────────────
    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(BG_HEADER);
        header.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(0, 0, 1, 0, BORDER_SOFT),
                BorderFactory.createEmptyBorder(10, 16, 10, 16)));

        JLabel title = new JLabel("🎯  AI 라이어 게임");
        title.setForeground(TEXT_MAIN);
        title.setFont(FONT_TITLE);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        right.setOpaque(false);

        connectionDot = new JLabel("●");
        connectionDot.setForeground(ACCENT_YELLOW);
        connectionDot.setFont(new Font(FONT_FAMILY, Font.BOLD, 14));

        connectionText = new JLabel("접속 중…");
        connectionText.setForeground(TEXT_MUTED);
        connectionText.setFont(FONT_SMALL);

        JLabel nick = new JLabel("닉네임  " + nickname);
        nick.setForeground(TEXT_MAIN);
        nick.setFont(FONT_LABEL);
        nick.setBorder(BorderFactory.createCompoundBorder(
                new RoundedBorder(BG_CARD_ALT, 12),
                BorderFactory.createEmptyBorder(4, 12, 4, 12)));
        nick.setOpaque(false);

        right.add(connectionDot);
        right.add(connectionText);
        right.add(Box.createHorizontalStrut(8));
        right.add(nick);

        header.add(title, BorderLayout.WEST);
        header.add(right, BorderLayout.EAST);
        return header;
    }

    // ── 중앙 영역 (게임 정보 + 채팅 + 사이드바 + 입력) ────────────────────
    private JPanel buildCenterArea() {
        JPanel center = new JPanel(new BorderLayout(12, 12));
        center.setBackground(BG_MAIN);
        center.setBorder(BorderFactory.createEmptyBorder(12, 16, 16, 16));

        center.add(buildGameInfoBar(), BorderLayout.NORTH);
        center.add(buildChatArea(),    BorderLayout.CENTER);
        center.add(buildSidebar(),     BorderLayout.EAST);
        center.add(buildSouthPanel(),  BorderLayout.SOUTH);

        return center;
    }

    // ── 게임 정보 바 (단계 / 단어 / 현재 차례 / 내 번호) ──────────────────
    private JPanel buildGameInfoBar() {
        JPanel bar = new JPanel(new GridLayout(1, 4, 10, 0));
        bar.setOpaque(false);

        phaseBadge   = makeInfoCard("게임 단계", phase.label, phase.color, true);
        wordValue    = makeInfoCard("제시어",   currentWord, ACCENT_BLUE,  false);
        turnValue    = makeInfoCard("현재 차례", "—",        TEXT_MUTED,   false);
        myNumberValue= makeInfoCard("내 번호",  "—",        TEXT_MUTED,   false);

        bar.add(phaseBadge.getParent());
        bar.add(wordValue.getParent());
        bar.add(turnValue.getParent());
        bar.add(myNumberValue.getParent());
        return bar;
    }

    private JLabel makeInfoCard(String labelText, String valueText, Color valueColor, boolean isBadge) {
        JPanel card = new JPanel(new BorderLayout(0, 4));
        card.setBackground(BG_CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
                new RoundedBorder(BORDER_SOFT, 10),
                BorderFactory.createEmptyBorder(10, 14, 10, 14)));

        JLabel label = new JLabel(labelText);
        label.setForeground(TEXT_MUTED);
        label.setFont(FONT_LABEL);

        JLabel value = new JLabel(valueText);
        value.setForeground(valueColor);
        value.setFont(FONT_VALUE);
        if (isBadge) {
            value.setOpaque(false);
            value.setHorizontalAlignment(SwingConstants.LEFT);
        }

        card.add(label, BorderLayout.NORTH);
        card.add(value, BorderLayout.CENTER);
        return value;
    }

    // ── 채팅 영역 ─────────────────────────────────────────────────────────
    private JComponent buildChatArea() {
        chatArea = new JTextPane();
        chatArea.setEditable(false);
        chatArea.setBackground(BG_CARD);
        chatArea.setFont(FONT_BODY);
        chatArea.setMargin(new Insets(12, 14, 12, 14));

        // 줄바꿈 자동 처리
        chatArea.setEditorKit(new StyledEditorKit());

        JScrollPane scroll = new JScrollPane(chatArea,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(new RoundedBorder(BORDER_SOFT, 12));
        scroll.getViewport().setBackground(BG_CARD);
        scroll.getVerticalScrollBar().setUI(new ThinScrollBarUI());
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }

    // ── 우측 사이드바 (접속자 목록) ───────────────────────────────────────
    private JComponent buildSidebar() {
        JPanel side = new JPanel(new BorderLayout(0, 0));
        side.setPreferredSize(new Dimension(220, 0));
        side.setBackground(BG_CARD);
        side.setBorder(new RoundedBorder(BORDER_SOFT, 12));

        // 헤더
        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.setBorder(BorderFactory.createEmptyBorder(12, 14, 8, 14));

        JLabel title = new JLabel("접속자");
        title.setForeground(TEXT_MAIN);
        title.setFont(FONT_TITLE);

        playerCountLabel = new JLabel("0명");
        playerCountLabel.setForeground(TEXT_MUTED);
        playerCountLabel.setFont(FONT_SMALL);

        head.add(title, BorderLayout.WEST);
        head.add(playerCountLabel, BorderLayout.EAST);

        // 목록
        playerListModel = new DefaultListModel<>();
        playerListView = new JList<>(playerListModel);
        playerListView.setBackground(BG_CARD);
        playerListView.setForeground(TEXT_MAIN);
        playerListView.setFont(FONT_BODY);
        playerListView.setSelectionBackground(BG_CARD_ALT);
        playerListView.setSelectionForeground(TEXT_MAIN);
        playerListView.setFixedCellHeight(28);
        playerListView.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
        playerListView.setCellRenderer(new PlayerCellRenderer());

        JScrollPane scroll = new JScrollPane(playerListView,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(BG_CARD);
        scroll.getVerticalScrollBar().setUI(new ThinScrollBarUI());

        // 하단 안내
        JLabel hint = new JLabel("<html><div style='color:#9B9DAB; font-size:10px;'>"
                + "💡 게임 중에는 차례인 사람이<br/>"
                + "강조됩니다.</div></html>");
        hint.setBorder(BorderFactory.createEmptyBorder(8, 14, 12, 14));

        side.add(head,   BorderLayout.NORTH);
        side.add(scroll, BorderLayout.CENTER);
        side.add(hint,   BorderLayout.SOUTH);
        return side;
    }

    // ── 하단: 액션 버튼 + 입력 ────────────────────────────────────────────
    private JPanel buildSouthPanel() {
        JPanel south = new JPanel(new BorderLayout(0, 10));
        south.setOpaque(false);

        // 액션 버튼들
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.setOpaque(false);

        startButton = makeActionButton("🎮  게임 시작", ACCENT_GREEN);
        startButton.addActionListener(e -> sendRaw("/시작"));

        voteButton = makeActionButton("🗳  AI 투표", ACCENT_PURPLE);
        voteButton.addActionListener(e -> openVoteDialog());

        JButton usersButton = makeActionButton("👥  접속자 새로고침", BG_CARD_ALT);
        usersButton.addActionListener(e -> sendRaw("/users"));

        JButton helpButton = makeActionButton("❓  도움말", BG_CARD_ALT);
        helpButton.addActionListener(e -> openHelpDialog());

        actions.add(startButton);
        actions.add(voteButton);
        actions.add(usersButton);
        actions.add(helpButton);

        // 입력 영역
        JPanel inputRow = new JPanel(new BorderLayout(8, 0));
        inputRow.setOpaque(false);

        inputField = new JTextField();
        inputField.setBackground(BG_INPUT);
        inputField.setForeground(TEXT_MAIN);
        inputField.setCaretColor(TEXT_MAIN);
        inputField.setFont(FONT_BODY);
        inputField.setBorder(BorderFactory.createCompoundBorder(
                new RoundedBorder(BORDER_SOFT, 10),
                BorderFactory.createEmptyBorder(10, 14, 10, 14)));
        inputField.addActionListener(e -> sendMessage());

        sendButton = makeActionButton("전송", ACCENT_BLUE);
        sendButton.setPreferredSize(new Dimension(86, 38));
        sendButton.addActionListener(e -> sendMessage());

        inputRow.add(inputField, BorderLayout.CENTER);
        inputRow.add(sendButton, BorderLayout.EAST);

        south.add(actions,  BorderLayout.NORTH);
        south.add(inputRow, BorderLayout.SOUTH);
        return south;
    }

    // ──────────────────────────────────────────────────────────────────────
    //  스타일 헬퍼
    // ──────────────────────────────────────────────────────────────────────
    private JButton makeActionButton(String text, Color bg) {
        JButton b = new JButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                    RenderingHints.VALUE_ANTIALIAS_ON);
                Color base = getModel().isRollover()
                        ? brighten(getBackground(), 0.15f)
                        : getBackground();
                if (!isEnabled()) base = new Color(0x444656);
                g2.setColor(base);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.dispose();
                super.paintComponent(g);
            }
            @Override public boolean isContentAreaFilled() { return false; }
        };
        b.setBackground(bg);
        b.setForeground(bg.equals(BG_CARD_ALT) ? TEXT_MAIN : Color.WHITE);
        b.setFont(FONT_BUTTON);
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setOpaque(false);
        b.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    private static Color brighten(Color c, float amount) {
        int r = Math.min(255, (int) (c.getRed()   + (255 - c.getRed())   * amount));
        int g = Math.min(255, (int) (c.getGreen() + (255 - c.getGreen()) * amount));
        int b = Math.min(255, (int) (c.getBlue()  + (255 - c.getBlue())  * amount));
        return new Color(r, g, b);
    }

    // ──────────────────────────────────────────────────────────────────────
    //  서버 연결
    // ──────────────────────────────────────────────────────────────────────
    private void connectToServer() {
        setInputEnabled(false);
        new Thread(() -> {
            try {
                socket = new Socket(SERVER_HOST, SERVER_PORT);

                serverOut = new PrintWriter(
                        new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);
                BufferedReader serverIn = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), "UTF-8"));

                // 닉네임 전송
                serverOut.println(nickname);

                SwingUtilities.invokeLater(() -> {
                    connectionDot.setForeground(ACCENT_GREEN);
                    connectionText.setText(SERVER_HOST + ":" + SERVER_PORT);
                    setInputEnabled(true);
                    appendSystem("서버에 연결되었습니다.", ACCENT_GREEN);
                });

                // 접속자 목록 자동 요청
                serverOut.println("/users");

                // 서버 메시지 수신
                String line;
                while ((line = serverIn.readLine()) != null) {
                    handleServerLine(line);
                }

            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> {
                    appendSystem("[오류] 서버에 접속하지 못했습니다: "
                            + SERVER_HOST + ":" + SERVER_PORT, ACCENT_RED);
                    connectionDot.setForeground(ACCENT_RED);
                    connectionText.setText("연결 실패");
                    setInputEnabled(false);
                });
            }
        }, "server-reader").start();
    }

    private void setInputEnabled(boolean enabled) {
        inputField.setEnabled(enabled);
        sendButton.setEnabled(enabled);
        startButton.setEnabled(enabled);
        voteButton.setEnabled(enabled);
    }

    // ──────────────────────────────────────────────────────────────────────
    //  메시지 전송
    // ──────────────────────────────────────────────────────────────────────
    private void sendMessage() {
        String text = inputField.getText().trim();
        if (text.isEmpty() || serverOut == null) return;
        inputField.setText("");

        if (text.equals("/quit")) { disconnect(); return; }
        serverOut.println(text);
    }

    private void sendRaw(String command) {
        if (serverOut == null) return;
        serverOut.println(command);
    }

    // ──────────────────────────────────────────────────────────────────────
    //  서버 메시지 파싱 & 출력
    // ──────────────────────────────────────────────────────────────────────
    private void handleServerLine(String line) {
        SwingUtilities.invokeLater(() -> {
            parseGameState(line);
            renderLine(line);
        });
    }

    /** 서버 메시지에서 게임 상태를 추출해 UI 업데이트 */
    private void parseGameState(String line) {
        // 게임 시작
        if (line.contains("게임 시작!")) {
            setPhase(Phase.DESCRIBING);
        }
        // 단어 공개
        else if (line.startsWith("[게임] 단어: 【")) {
            int s = line.indexOf('【'); int e = line.indexOf('】');
            if (s >= 0 && e > s) {
                currentWord = line.substring(s + 1, e);
                wordValue.setText(currentWord);
                wordValue.setForeground(ACCENT_BLUE);
            }
        }
        // 내 번호
        else if (line.contains("당신은") && line.contains("번 플레이어입니다")) {
            int idx = line.indexOf("당신은 ") + 4;
            int end = line.indexOf("번", idx);
            try {
                myNumber = Integer.parseInt(line.substring(idx, end).trim());
                myNumberValue.setText(myNumber + "번");
                myNumberValue.setForeground(ACCENT_PINK);
            } catch (NumberFormatException ignored) {}
        }
        // 총 인원
        else if (line.startsWith("[게임] 총 ") && line.contains("명이 참가")) {
            int s = "[게임] 총 ".length();
            int e = line.indexOf("명", s);
            try { totalPlayers = Integer.parseInt(line.substring(s, e).trim()); }
            catch (NumberFormatException ignored) {}
        }
        // 차례 안내
        else if (line.matches("\\[게임\\] \\d+번 플레이어의 차례.*")) {
            int s = "[게임] ".length();
            int e = line.indexOf("번", s);
            try {
                currentTurnNum = Integer.parseInt(line.substring(s, e).trim());
                boolean mine = (currentTurnNum == myNumber);
                turnValue.setText(currentTurnNum + "번" + (mine ? "  (내 차례!)" : ""));
                turnValue.setForeground(mine ? ACCENT_PINK : ACCENT_GREEN);
            } catch (NumberFormatException ignored) {}
        }
        // 투표 시작
        else if (line.contains("투표 시작!")) {
            setPhase(Phase.VOTING);
            turnValue.setText("—");
            turnValue.setForeground(TEXT_MUTED);
        }
        // 결과 발표 → 다시 대기
        else if (line.contains("결과 발표!")
              || line.contains("게임이 취소되었습니다")
              || line.contains("다시 하려면")) {
            // 결과 라인이 여러 줄에 걸쳐 오므로, "다시 하려면" 이후에 phase 리셋
            if (line.contains("다시 하려면") || line.contains("게임이 취소")) {
                resetGameUI();
            }
        }
        // 입장
        else if (line.startsWith("[입장] ")) {
            String name = extractName(line, "[입장] ", "님 입장");
            if (name != null) addPlayer(name);
        }
        // 퇴장
        else if (line.startsWith("[퇴장] ")) {
            String name = extractName(line, "[퇴장] ", "님 퇴장");
            if (name != null) removePlayer(name);
        }
        // /users 응답
        else if (line.startsWith("[서버] 접속자 (")) {
            int colon = line.indexOf("): ");
            if (colon > 0) {
                String csv = line.substring(colon + 3);
                playerListModel.clear();
                for (String n : csv.split(",")) {
                    String t = n.trim();
                    if (!t.isEmpty()) playerListModel.addElement(t);
                }
                updatePlayerCount();
            }
        }
    }

    private void setPhase(Phase p) {
        this.phase = p;
        phaseBadge.setText(p.label);
        phaseBadge.setForeground(p.color);
        if (p == Phase.WAITING) {
            wordValue.setText("—");
            wordValue.setForeground(TEXT_MUTED);
            turnValue.setText("—");
            turnValue.setForeground(TEXT_MUTED);
            myNumberValue.setText("—");
            myNumberValue.setForeground(TEXT_MUTED);
        }
    }

    private void resetGameUI() {
        setPhase(Phase.WAITING);
        currentWord = "—";
        currentTurnNum = 0;
        myNumber = -1;
    }

    private String extractName(String line, String prefix, String suffix) {
        if (!line.startsWith(prefix)) return null;
        int s = prefix.length();
        int e = line.indexOf(suffix, s);
        if (e < 0) return null;
        return line.substring(s, e).trim();
    }

    private void addPlayer(String name) {
        if (name == null || name.isEmpty()) return;
        if (!playerListModel.contains(name)) playerListModel.addElement(name);
        updatePlayerCount();
    }

    private void removePlayer(String name) {
        if (name == null) return;
        playerListModel.removeElement(name);
        updatePlayerCount();
    }

    private void updatePlayerCount() {
        playerCountLabel.setText(playerListModel.size() + "명");
    }

    // ──────────────────────────────────────────────────────────────────────
    //  메시지 렌더링
    // ──────────────────────────────────────────────────────────────────────
    private void renderLine(String line) {
        // 게임/서버/시스템 메시지
        if (line.startsWith("[게임]")) {
            appendSystem(line.substring("[게임]".length()).trim(), ACCENT_PURPLE);
            return;
        }
        if (line.startsWith("[서버]")) {
            appendSystem(line.substring("[서버]".length()).trim(), ACCENT_GREEN);
            return;
        }
        if (line.startsWith("[입장]") || line.startsWith("[퇴장]")) {
            appendSystem(line, TEXT_MUTED);
            return;
        }
        if (line.startsWith("[투표]")) {
            appendSystem(line.substring("[투표]".length()).trim(), ACCENT_YELLOW);
            return;
        }
        // 게임 중 N번 플레이어 설명
        if (line.matches("\\[\\d+번\\].*")) {
            int close = line.indexOf(']');
            String tag = line.substring(1, close);     // "1번"
            String body = line.substring(close + 1).trim();
            boolean mine = false;
            try {
                int num = Integer.parseInt(tag.replace("번", "").trim());
                mine = (num == myNumber);
            } catch (NumberFormatException ignored) {}
            appendBubble(tag, body, mine);
            return;
        }
        // 일반 채팅 [닉네임] 메시지
        if (line.startsWith("[") && line.contains("] ")) {
            int close = line.indexOf(']');
            String sender = line.substring(1, close);
            String body = line.substring(close + 1).trim();
            boolean mine = sender.equals(nickname);
            appendBubble(sender, body, mine);
            return;
        }
        // 그 외 빈 줄 / 기타
        if (line.trim().isEmpty()) {
            appendRaw("\n", TEXT_DIM, false);
        } else {
            appendRaw(line + "\n", TEXT_MUTED, false);
        }
    }

    private void appendBubble(String sender, String body, boolean mine) {
        StyledDocument doc = chatArea.getStyledDocument();
        try {
            // 송신자 라벨
            SimpleAttributeSet senderAttr = new SimpleAttributeSet();
            StyleConstants.setForeground(senderAttr,
                    mine ? ACCENT_BLUE : ACCENT_PINK);
            StyleConstants.setFontFamily(senderAttr, FONT_FAMILY);
            StyleConstants.setFontSize(senderAttr, 12);
            StyleConstants.setBold(senderAttr, true);
            StyleConstants.setSpaceAbove(senderAttr, 6f);
            StyleConstants.setAlignment(senderAttr,
                    mine ? StyleConstants.ALIGN_RIGHT : StyleConstants.ALIGN_LEFT);

            // 메시지 본문
            SimpleAttributeSet bodyAttr = new SimpleAttributeSet();
            StyleConstants.setForeground(bodyAttr, TEXT_MAIN);
            StyleConstants.setFontFamily(bodyAttr, FONT_FAMILY);
            StyleConstants.setFontSize(bodyAttr, 14);
            StyleConstants.setSpaceBelow(bodyAttr, 4f);
            StyleConstants.setAlignment(bodyAttr,
                    mine ? StyleConstants.ALIGN_RIGHT : StyleConstants.ALIGN_LEFT);

            int sStart = doc.getLength();
            doc.insertString(doc.getLength(),
                    (mine ? "나 (" + sender + ")  " : sender + "  ")
                            + timeFormat.format(new Date()) + "\n",
                    senderAttr);
            doc.setParagraphAttributes(sStart, doc.getLength() - sStart, senderAttr, true);

            int bStart = doc.getLength();
            doc.insertString(doc.getLength(), body + "\n", bodyAttr);
            doc.setParagraphAttributes(bStart, doc.getLength() - bStart, bodyAttr, true);

            chatArea.setCaretPosition(doc.getLength());
        } catch (BadLocationException ignored) {}
    }

    private void appendSystem(String text, Color color) {
        StyledDocument doc = chatArea.getStyledDocument();
        try {
            SimpleAttributeSet attr = new SimpleAttributeSet();
            StyleConstants.setForeground(attr, color);
            StyleConstants.setFontFamily(attr, FONT_FAMILY);
            StyleConstants.setFontSize(attr, 12);
            StyleConstants.setItalic(attr, true);
            StyleConstants.setAlignment(attr, StyleConstants.ALIGN_CENTER);
            StyleConstants.setSpaceAbove(attr, 4f);
            StyleConstants.setSpaceBelow(attr, 4f);

            int start = doc.getLength();
            doc.insertString(doc.getLength(), "— " + text + " —\n", attr);
            doc.setParagraphAttributes(start, doc.getLength() - start, attr, true);
            chatArea.setCaretPosition(doc.getLength());
        } catch (BadLocationException ignored) {}
    }

    private void appendRaw(String text, Color color, boolean bold) {
        StyledDocument doc = chatArea.getStyledDocument();
        try {
            SimpleAttributeSet attr = new SimpleAttributeSet();
            StyleConstants.setForeground(attr, color);
            StyleConstants.setFontFamily(attr, FONT_FAMILY);
            StyleConstants.setFontSize(attr, 13);
            StyleConstants.setBold(attr, bold);
            doc.insertString(doc.getLength(), text, attr);
            chatArea.setCaretPosition(doc.getLength());
        } catch (BadLocationException ignored) {}
    }

    // ──────────────────────────────────────────────────────────────────────
    //  투표 다이얼로그 (번호 버튼 방식)
    // ──────────────────────────────────────────────────────────────────────
    private void openVoteDialog() {
        int playerCount = totalPlayers > 0 ? totalPlayers
                : Math.max(2, playerListModel.size() + 1); // 게임 정보가 없을 때 fallback

        JDialog dialog = new JDialog(this, "AI 투표", true);
        dialog.setUndecorated(false);
        dialog.getContentPane().setBackground(BG_MAIN);
        dialog.setLayout(new BorderLayout(0, 0));

        // 헤더
        JPanel head = new JPanel(new BorderLayout());
        head.setBackground(BG_HEADER);
        head.setBorder(BorderFactory.createEmptyBorder(14, 18, 14, 18));
        JLabel title = new JLabel("🗳  누가 AI라고 생각하시나요?");
        title.setForeground(TEXT_MAIN);
        title.setFont(FONT_TITLE);
        head.add(title, BorderLayout.CENTER);

        // 안내
        JLabel hint = new JLabel("<html><div style='color:#9B9DAB;'>"
                + "AI라고 생각하는 플레이어 번호를 선택하세요.</div></html>");
        hint.setBorder(BorderFactory.createEmptyBorder(14, 18, 6, 18));

        // 번호 버튼 그리드
        int cols = Math.min(5, playerCount);
        int rows = (int) Math.ceil(playerCount / (double) cols);
        JPanel grid = new JPanel(new GridLayout(rows, cols, 10, 10));
        grid.setOpaque(false);
        grid.setBorder(BorderFactory.createEmptyBorder(8, 18, 18, 18));

        for (int i = 1; i <= playerCount; i++) {
            final int num = i;
            JButton b = makeActionButton(String.valueOf(i) + "번", BG_CARD_ALT);
            if (num == myNumber) {
                b.setText(num + "번 (나)");
                b.setBackground(new Color(0x444656));
                b.setEnabled(false);
            } else {
                b.setBackground(ACCENT_PURPLE);
            }
            b.setPreferredSize(new Dimension(72, 48));
            b.setFont(new Font(FONT_FAMILY, Font.BOLD, 14));
            b.addActionListener(e -> {
                sendRaw("/투표 " + num);
                dialog.dispose();
            });
            grid.add(b);
        }

        // 취소 버튼
        JPanel foot = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        foot.setOpaque(false);
        foot.setBorder(BorderFactory.createEmptyBorder(0, 18, 14, 18));
        JButton cancel = makeActionButton("취소", BG_CARD_ALT);
        cancel.addActionListener(e -> dialog.dispose());
        foot.add(cancel);

        JPanel body = new JPanel(new BorderLayout());
        body.setBackground(BG_MAIN);
        body.add(hint, BorderLayout.NORTH);
        body.add(grid, BorderLayout.CENTER);
        body.add(foot, BorderLayout.SOUTH);

        dialog.add(head, BorderLayout.NORTH);
        dialog.add(body, BorderLayout.CENTER);
        dialog.pack();
        dialog.setMinimumSize(new Dimension(420, 0));
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    // ──────────────────────────────────────────────────────────────────────
    //  도움말 다이얼로그
    // ──────────────────────────────────────────────────────────────────────
    private void openHelpDialog() {
        JDialog dialog = new JDialog(this, "도움말", true);
        dialog.getContentPane().setBackground(BG_MAIN);
        dialog.setLayout(new BorderLayout());

        JPanel head = new JPanel(new BorderLayout());
        head.setBackground(BG_HEADER);
        head.setBorder(BorderFactory.createEmptyBorder(14, 18, 14, 18));
        JLabel title = new JLabel("❓  도움말");
        title.setForeground(TEXT_MAIN);
        title.setFont(FONT_TITLE);
        head.add(title, BorderLayout.CENTER);

        String html = "<html><div style='font-family:맑은 고딕; color:#E8E9F0; padding:18px; width:480px;'>"
                + "<h2 style='color:#6C8EFF;'>🎯 AI 라이어 게임 규칙</h2>"
                + "<ol>"
                + "<li>2명 이상 모이면 <b>게임 시작</b> 버튼을 눌러 시작합니다.</li>"
                + "<li>서버가 단어를 공개합니다. 단, 참가자들 중에 <b>AI 한 명이 섞여 있습니다.</b></li>"
                + "<li>번호 순서대로 단어를 <b>1~2문장으로 설명</b>합니다.</li>"
                + "<li>모두 설명한 후 <b>AI 투표</b> 버튼을 눌러 AI라고 생각하는 번호를 선택합니다.</li>"
                + "<li>가장 많은 표를 받은 번호가 AI면 인간 승리! 아니면 AI 승리!</li>"
                + "</ol>"
                + "<h3 style='color:#B084FF;'>💬 채팅 명령어</h3>"
                + "<table cellpadding='4'>"
                + "<tr><td style='color:#FFB454;'>/시작</td><td>게임을 시작합니다 (버튼으로도 가능)</td></tr>"
                + "<tr><td style='color:#FFB454;'>/투표 [번호]</td><td>AI를 지목합니다 (버튼으로도 가능)</td></tr>"
                + "<tr><td style='color:#FFB454;'>/users</td><td>접속자 목록을 갱신합니다</td></tr>"
                + "<tr><td style='color:#FFB454;'>/quit</td><td>채팅방에서 나갑니다</td></tr>"
                + "</table>"
                + "<h3 style='color:#56C596;'>🎨 색상 가이드</h3>"
                + "<div style='color:#6C8EFF;'>● 내가 보낸 메시지</div>"
                + "<div style='color:#FF9EC4;'>● 다른 사람 메시지</div>"
                + "<div style='color:#B084FF;'>● 게임 진행 안내</div>"
                + "<div style='color:#56C596;'>● 서버 알림</div>"
                + "<div style='color:#FFB454;'>● 투표 현황</div>"
                + "</div></html>";
        JLabel content = new JLabel(html);
        content.setVerticalAlignment(SwingConstants.TOP);

        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(BG_MAIN);
        scroll.getVerticalScrollBar().setUI(new ThinScrollBarUI());

        JPanel foot = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        foot.setOpaque(false);
        foot.setBorder(BorderFactory.createEmptyBorder(8, 18, 14, 18));
        JButton close = makeActionButton("닫기", ACCENT_BLUE);
        close.addActionListener(e -> dialog.dispose());
        foot.add(close);

        dialog.add(head,   BorderLayout.NORTH);
        dialog.add(scroll, BorderLayout.CENTER);
        dialog.add(foot,   BorderLayout.SOUTH);

        dialog.setSize(560, 540);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    // ──────────────────────────────────────────────────────────────────────
    //  연결 종료
    // ──────────────────────────────────────────────────────────────────────
    private void disconnect() {
        try {
            if (serverOut != null) serverOut.println("/quit");
            if (socket != null)    socket.close();
        } catch (IOException ignored) {}
        System.exit(0);
    }

    // ──────────────────────────────────────────────────────────────────────
    //  접속자 리스트 셀 렌더러 (현재 차례 강조)
    // ──────────────────────────────────────────────────────────────────────
    private class PlayerCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                int index, boolean isSelected, boolean cellHasFocus) {
            JLabel l = (JLabel) super.getListCellRendererComponent(
                    list, value, index, isSelected, cellHasFocus);
            String name = String.valueOf(value);
            boolean me = name.equals(nickname);
            l.setText("  " + (me ? "● " : "○ ") + name + (me ? "  (나)" : ""));
            l.setBackground(isSelected ? BG_CARD_ALT : BG_CARD);
            l.setForeground(me ? ACCENT_BLUE : TEXT_MAIN);
            l.setFont(me ? new Font(FONT_FAMILY, Font.BOLD, 13) : FONT_BODY);
            l.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
            return l;
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    //  유틸: 라운드 보더 / 스크롤바 UI
    // ──────────────────────────────────────────────────────────────────────
    private static class RoundedBorder extends AbstractBorder {
        private final Color color;
        private final int radius;
        RoundedBorder(Color color, int radius) {
            this.color = color; this.radius = radius;
        }
        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.drawRoundRect(x, y, w - 1, h - 1, radius, radius);
            g2.dispose();
        }
        @Override public Insets getBorderInsets(Component c) {
            return new Insets(1, 1, 1, 1);
        }
    }

    private static class ThinScrollBarUI extends javax.swing.plaf.basic.BasicScrollBarUI {
        @Override protected void configureScrollBarColors() {
            this.thumbColor = new Color(0x4A4D63);
            this.trackColor = BG_CARD;
        }
        @Override protected JButton createDecreaseButton(int o) { return invisibleButton(); }
        @Override protected JButton createIncreaseButton(int o) { return invisibleButton(); }
        private JButton invisibleButton() {
            JButton b = new JButton();
            b.setPreferredSize(new Dimension(0, 0));
            b.setMinimumSize(new Dimension(0, 0));
            b.setMaximumSize(new Dimension(0, 0));
            return b;
        }
        @Override protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(thumbColor);
            g2.fillRoundRect(r.x + 3, r.y + 2, r.width - 6, r.height - 4, 8, 8);
            g2.dispose();
        }
        @Override protected void paintTrack(Graphics g, JComponent c, Rectangle r) {
            g.setColor(trackColor);
            g.fillRect(r.x, r.y, r.width, r.height);
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    //  진입점: 시작 화면 (닉네임 입력)
    // ──────────────────────────────────────────────────────────────────────
    public static void main(String[] args) {
        // 시스템 Look & Feel
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
        catch (Exception ignored) {}

        SwingUtilities.invokeLater(WelcomeDialog::showAndConnect);
    }

    // ──────────────────────────────────────────────────────────────────────
    //  시작 화면 (브랜딩 + 닉네임 입력)
    // ──────────────────────────────────────────────────────────────────────
    private static class WelcomeDialog {
        static void showAndConnect() {
            JFrame dummy = new JFrame();
            dummy.setUndecorated(true);
            dummy.setLocationRelativeTo(null);

            JDialog dialog = new JDialog(dummy, "AI 라이어 게임 — 입장", true);
            dialog.getContentPane().setBackground(BG_MAIN);
            dialog.setLayout(new BorderLayout());
            dialog.setSize(440, 380);
            dialog.setLocationRelativeTo(null);
            dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

            // 상단 배너
            JPanel banner = new JPanel(new BorderLayout());
            banner.setBackground(BG_HEADER);
            banner.setBorder(BorderFactory.createEmptyBorder(28, 24, 28, 24));

            JLabel titleLabel = new JLabel("🎯  AI 라이어 게임");
            titleLabel.setForeground(TEXT_MAIN);
            titleLabel.setFont(new Font(FONT_FAMILY, Font.BOLD, 24));

            JLabel subtitle = new JLabel("<html><div style='color:#9B9DAB; font-size:12px;'>"
                    + "AI 한 명이 사람들 사이에 숨어 있습니다.<br/>"
                    + "설명을 듣고 누가 AI인지 맞춰보세요.</div></html>");

            JPanel titleBox = new JPanel();
            titleBox.setLayout(new BoxLayout(titleBox, BoxLayout.Y_AXIS));
            titleBox.setOpaque(false);
            titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
            titleBox.add(titleLabel);
            titleBox.add(Box.createVerticalStrut(8));
            titleBox.add(subtitle);
            banner.add(titleBox, BorderLayout.CENTER);

            // 입력 영역
            JPanel form = new JPanel();
            form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
            form.setBackground(BG_MAIN);
            form.setBorder(BorderFactory.createEmptyBorder(20, 24, 12, 24));

            JLabel label = new JLabel("닉네임");
            label.setForeground(TEXT_MUTED);
            label.setFont(FONT_LABEL);
            label.setAlignmentX(Component.LEFT_ALIGNMENT);

            JTextField field = new JTextField();
            field.setBackground(BG_INPUT);
            field.setForeground(TEXT_MAIN);
            field.setCaretColor(TEXT_MAIN);
            field.setFont(new Font(FONT_FAMILY, Font.PLAIN, 15));
            field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
            field.setAlignmentX(Component.LEFT_ALIGNMENT);
            field.setBorder(BorderFactory.createCompoundBorder(
                    new RoundedBorder(BORDER_SOFT, 10),
                    BorderFactory.createEmptyBorder(8, 12, 8, 12)));

            JLabel hint = new JLabel("최대 20자 · 다른 사용자와 겹치지 않게 해주세요");
            hint.setForeground(TEXT_DIM);
            hint.setFont(FONT_SMALL);
            hint.setAlignmentX(Component.LEFT_ALIGNMENT);

            JLabel serverInfo = new JLabel("서버  " + SERVER_HOST + ":" + SERVER_PORT);
            serverInfo.setForeground(TEXT_DIM);
            serverInfo.setFont(FONT_SMALL);
            serverInfo.setAlignmentX(Component.LEFT_ALIGNMENT);

            form.add(label);
            form.add(Box.createVerticalStrut(6));
            form.add(field);
            form.add(Box.createVerticalStrut(6));
            form.add(hint);
            form.add(Box.createVerticalStrut(14));
            form.add(serverInfo);

            // 버튼
            JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            actions.setBackground(BG_MAIN);
            actions.setBorder(BorderFactory.createEmptyBorder(0, 16, 16, 16));

            JButton enter = makeStaticButton("입장하기", ACCENT_BLUE, Color.WHITE);
            JButton cancel = makeStaticButton("취소",     BG_CARD_ALT, TEXT_MAIN);

            Runnable submit = () -> {
                String nick = field.getText().trim();
                if (nick.isEmpty()) nick = "익명";
                if (nick.length() > 20) nick = nick.substring(0, 20);
                dialog.dispose();
                dummy.dispose();
                final String finalNick = nick;
                SwingUtilities.invokeLater(() -> new ChatClientGUI(finalNick));
            };
            enter.addActionListener(e -> submit.run());
            field.addActionListener(e -> submit.run());
            cancel.addActionListener(e -> { dialog.dispose(); dummy.dispose(); System.exit(0); });

            actions.add(cancel);
            actions.add(enter);

            dialog.add(banner, BorderLayout.NORTH);
            dialog.add(form,   BorderLayout.CENTER);
            dialog.add(actions, BorderLayout.SOUTH);

            SwingUtilities.invokeLater(field::requestFocus);
            dialog.setVisible(true);
        }

        private static JButton makeStaticButton(String text, Color bg, Color fg) {
            JButton b = new JButton(text) {
                @Override protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                        RenderingHints.VALUE_ANTIALIAS_ON);
                    Color base = getModel().isRollover() ? brighten(getBackground(), 0.15f)
                                                         : getBackground();
                    g2.setColor(base);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                    g2.dispose();
                    super.paintComponent(g);
                }
                @Override public boolean isContentAreaFilled() { return false; }
            };
            b.setBackground(bg);
            b.setForeground(fg);
            b.setFont(FONT_BUTTON);
            b.setFocusPainted(false);
            b.setBorderPainted(false);
            b.setOpaque(false);
            b.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
            b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            return b;
        }
    }
}
