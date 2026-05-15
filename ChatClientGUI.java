import java.awt.*;
import java.awt.event.*;
import java.awt.font.TextAttribute;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.text.*;

/**
 * ChatClientGUI.java
 * ------------------
 * Java Swing AI 라이어 게임 클라이언트 (외부 라이브러리 없음).
 *
 * 디자인 시스템: Stripe-inspired (design.md 기반)
 *   - Indigo(#533AFD) 단일 CTA + Deep Navy(#0D253D) 본문 텍스트
 *   - Canvas(#FFFFFF) / Canvas Soft(#F6F9FC) / Canvas Cream(#F5E9D4) 표면
 *   - 그라데이션 메시 (cream → orange → lavender → indigo → ruby) 히어로
 *   - Pill 버튼 (9999px) · Hairline 보더 (1px #E3E8EE)
 *   - Sohne thin 시뮬레이션: 디스플레이는 PLAIN + 음수 트래킹
 *
 * 실행:
 *   javac -encoding UTF-8 ChatClientGUI.java
 *   java -cp . ChatClientGUI
 */
public class ChatClientGUI extends JFrame {

    // ── 서버 접속 정보 ─────────────────────────────────────────────────────
    private static final String SERVER_HOST = "tramway.proxy.rlwy.net";
    private static final int    SERVER_PORT = 17502;

    // 로컬 테스트용
    // private static final String SERVER_HOST = "localhost";
    // private static final int    SERVER_PORT = 8080;
    // ──────────────────────────────────────────────────────────────────────

    // ── 색상 (design.md 토큰) ─────────────────────────────────────────────
    // Brand
    private static final Color PRIMARY         = new Color(0x533AFD);
    private static final Color PRIMARY_DEEP    = new Color(0x4434D4);
    private static final Color PRIMARY_PRESS   = new Color(0x2E2B8C);
    private static final Color PRIMARY_SOFT    = new Color(0x665EFD);
    private static final Color PRIMARY_SUBDUED = new Color(0xB9B9F9);
    private static final Color BRAND_DARK_900  = new Color(0x1C1E54);
    private static final Color RUBY            = new Color(0xEA2261);
    private static final Color MAGENTA         = new Color(0xF96BEE);
    private static final Color LEMON           = new Color(0xC78A4E);

    // Surface
    private static final Color CANVAS          = new Color(0xFFFFFF);
    private static final Color CANVAS_SOFT     = new Color(0xF6F9FC);
    private static final Color CANVAS_CREAM    = new Color(0xF5E9D4);
    private static final Color HAIRLINE        = new Color(0xE3E8EE);
    private static final Color HAIRLINE_INPUT  = new Color(0xA8C3DE);

    // Text
    private static final Color INK             = new Color(0x0D253D);
    private static final Color INK_SECONDARY   = new Color(0x273951);
    private static final Color INK_MUTE        = new Color(0x64748D);
    private static final Color INK_MUTE_2      = new Color(0x61718A);
    private static final Color ON_PRIMARY      = new Color(0xFFFFFF);

    // ── 폰트 (Sohne 부재 → 맑은 고딕 PLAIN + 음수 트래킹) ─────────────────
    private static final String FONT_FAMILY = "맑은 고딕";

    private static final Font DISPLAY_XL   = tightFont(28, Font.PLAIN, -0.030f);
    private static final Font DISPLAY_LG   = tightFont(22, Font.PLAIN, -0.025f);
    private static final Font DISPLAY_MD   = tightFont(18, Font.PLAIN, -0.020f);
    private static final Font HEADING_LG   = tightFont(16, Font.PLAIN, -0.018f);
    private static final Font HEADING_MD   = tightFont(15, Font.PLAIN, -0.012f);
    private static final Font BODY_LG      = new Font(FONT_FAMILY, Font.PLAIN, 14);
    private static final Font BODY_MD      = new Font(FONT_FAMILY, Font.PLAIN, 13);
    private static final Font BODY_TABULAR = tightFont(13, Font.PLAIN, -0.030f);
    private static final Font BUTTON_MD    = new Font(FONT_FAMILY, Font.BOLD,  13);
    private static final Font BUTTON_SM    = new Font(FONT_FAMILY, Font.BOLD,  12);
    private static final Font CAPTION      = tightFont(12, Font.PLAIN, -0.026f);
    private static final Font MICRO        = new Font(FONT_FAMILY, Font.PLAIN, 11);
    private static final Font MICRO_CAP    = new Font(FONT_FAMILY, Font.BOLD,  10);

    private static Font tightFont(int size, int style, float tracking) {
        Font base = new Font(FONT_FAMILY, style, size);
        Map<TextAttribute, Object> attrs = new HashMap<>();
        attrs.put(TextAttribute.TRACKING, tracking);
        return base.deriveFont(attrs);
    }

    // ── 게임 상태 ──────────────────────────────────────────────────────────
    private enum Phase {
        WAITING("대기 중",     INK_MUTE),
        DESCRIBING("설명 중",  PRIMARY),
        VOTING("투표 중",      RUBY);
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

    private JLabel phaseValue;
    private JLabel wordValue;
    private JLabel turnValue;
    private JLabel myNumberValue;

    private DefaultListModel<String> playerListModel;
    private JList<String> playerListView;
    private JLabel playerCountLabel;

    private JButton startButton;
    private JButton voteButton;

    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm");

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
        setSize(980, 660);
        setMinimumSize(new Dimension(860, 580));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        getContentPane().setBackground(CANVAS_SOFT);
        setLayout(new BorderLayout());

        // Header + 1px gradient stripe + center
        JPanel topGroup = new JPanel(new BorderLayout());
        topGroup.setOpaque(false);
        topGroup.add(buildHeader(), BorderLayout.CENTER);
        topGroup.add(new GradientStripe(3), BorderLayout.SOUTH);
        add(topGroup, BorderLayout.NORTH);

        add(buildCenterArea(), BorderLayout.CENTER);

        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { disconnect(); }
        });

        setVisible(true);
        inputField.requestFocus();
    }

    // ── 상단 헤더 (Stripe nav-bar 스타일) ──────────────────────────────────
    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(CANVAS);
        header.setBorder(BorderFactory.createEmptyBorder(14, 24, 14, 24));

        // Left: wordmark
        JLabel wordmark = new JLabel("AI 라이어 게임");
        wordmark.setForeground(INK);
        wordmark.setFont(DISPLAY_MD);

        // Right: status pill + nickname pill
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        right.setOpaque(false);

        // Status chip
        JPanel statusChip = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        statusChip.setOpaque(false);
        statusChip.setBorder(BorderFactory.createCompoundBorder(
                new RoundedLineBorder(HAIRLINE, 1, 999),
                BorderFactory.createEmptyBorder(4, 12, 4, 14)));

        connectionDot = new JLabel("●");
        connectionDot.setForeground(LEMON);
        connectionDot.setFont(new Font(FONT_FAMILY, Font.BOLD, 11));

        connectionText = new JLabel("연결 중");
        connectionText.setForeground(INK_MUTE);
        connectionText.setFont(CAPTION);

        statusChip.add(connectionDot);
        statusChip.add(connectionText);

        // Nickname chip (cream-band feel)
        JPanel nickChip = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        nickChip.setOpaque(false);
        nickChip.setBorder(BorderFactory.createCompoundBorder(
                new RoundedFilledBorder(CANVAS_CREAM, 999),
                BorderFactory.createEmptyBorder(4, 12, 4, 12)));

        JLabel nickEyebrow = new JLabel("닉네임  ");
        nickEyebrow.setForeground(LEMON);
        nickEyebrow.setFont(MICRO_CAP);

        JLabel nickName = new JLabel(nickname);
        nickName.setForeground(INK);
        nickName.setFont(BUTTON_SM);

        nickChip.add(nickEyebrow);
        nickChip.add(nickName);

        right.add(statusChip);
        right.add(nickChip);

        header.add(wordmark, BorderLayout.WEST);
        header.add(right, BorderLayout.EAST);
        return header;
    }

    // ── 중앙 영역 ─────────────────────────────────────────────────────────
    private JPanel buildCenterArea() {
        JPanel center = new JPanel(new BorderLayout(16, 16));
        center.setBackground(CANVAS_SOFT);
        center.setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));

        center.add(buildGameInfoBar(), BorderLayout.NORTH);
        center.add(buildChatCard(),    BorderLayout.CENTER);
        center.add(buildSidebar(),     BorderLayout.EAST);
        center.add(buildSouthPanel(),  BorderLayout.SOUTH);

        return center;
    }

    // ── 게임 정보 카드 4개 (마지막 하나는 cream 강조) ─────────────────────
    private JPanel buildGameInfoBar() {
        JPanel bar = new JPanel(new GridLayout(1, 4, 12, 0));
        bar.setOpaque(false);

        phaseValue    = new JLabel(phase.label);
        wordValue     = new JLabel("—");
        turnValue     = new JLabel("—");
        myNumberValue = new JLabel("—");

        styleInfoValue(phaseValue,    phase.color, false);
        styleInfoValue(wordValue,     INK_MUTE,    false);
        styleInfoValue(turnValue,     INK_MUTE,    false);
        styleInfoValue(myNumberValue, INK_MUTE,    true); // cream emphasis

        bar.add(buildInfoCard("게임 단계",  phaseValue,    false));
        bar.add(buildInfoCard("제시어",     wordValue,     false));
        bar.add(buildInfoCard("현재 차례",  turnValue,     false));
        bar.add(buildInfoCard("내 번호",    myNumberValue, true));
        return bar;
    }

    private void styleInfoValue(JLabel l, Color c, boolean isMonetaryStyle) {
        l.setForeground(c);
        l.setFont(isMonetaryStyle ? DISPLAY_LG : DISPLAY_LG);
    }

    private JPanel buildInfoCard(String label, JLabel value, boolean cream) {
        JPanel card = new JPanel(new BorderLayout(0, 6));
        card.setBackground(cream ? CANVAS_CREAM : CANVAS);
        card.setBorder(BorderFactory.createCompoundBorder(
                new RoundedLineBorder(cream ? CANVAS_CREAM : HAIRLINE, 1, 12),
                BorderFactory.createEmptyBorder(14, 18, 14, 18)));

        JLabel eyebrow = new JLabel(label.toUpperCase());
        eyebrow.setForeground(cream ? LEMON : INK_MUTE);
        eyebrow.setFont(MICRO_CAP);
        Map<TextAttribute, Object> ts = new HashMap<>();
        ts.put(TextAttribute.TRACKING, 0.08);
        eyebrow.setFont(eyebrow.getFont().deriveFont(ts));

        card.add(eyebrow, BorderLayout.NORTH);
        card.add(value,   BorderLayout.CENTER);
        return card;
    }

    // ── 채팅 카드 ─────────────────────────────────────────────────────────
    private JComponent buildChatCard() {
        chatArea = new JTextPane();
        chatArea.setEditable(false);
        chatArea.setBackground(CANVAS);
        chatArea.setFont(BODY_MD);
        chatArea.setForeground(INK);
        chatArea.setMargin(new Insets(16, 20, 16, 20));

        JScrollPane scroll = new JScrollPane(chatArea,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(new RoundedLineBorder(HAIRLINE, 1, 12));
        scroll.getViewport().setBackground(CANVAS);
        scroll.getVerticalScrollBar().setUI(new ThinScrollBarUI());
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }

    // ── 우측 사이드바 (접속자) ─────────────────────────────────────────────
    private JComponent buildSidebar() {
        JPanel side = new JPanel(new BorderLayout(0, 0));
        side.setPreferredSize(new Dimension(240, 0));
        side.setBackground(CANVAS);
        side.setBorder(new RoundedLineBorder(HAIRLINE, 1, 12));

        // Header
        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.setBorder(BorderFactory.createEmptyBorder(16, 18, 12, 18));

        JLabel title = new JLabel("접속자");
        title.setForeground(INK);
        title.setFont(HEADING_LG);

        playerCountLabel = new JLabel("0");
        playerCountLabel.setForeground(PRIMARY);
        playerCountLabel.setFont(BODY_TABULAR);
        playerCountLabel.setBorder(BorderFactory.createCompoundBorder(
                new RoundedFilledBorder(PRIMARY_SUBDUED, 999),
                BorderFactory.createEmptyBorder(2, 10, 2, 10)));

        head.add(title, BorderLayout.WEST);
        head.add(playerCountLabel, BorderLayout.EAST);

        // List
        playerListModel = new DefaultListModel<>();
        playerListView = new JList<>(playerListModel);
        playerListView.setBackground(CANVAS);
        playerListView.setForeground(INK);
        playerListView.setFont(BODY_MD);
        playerListView.setSelectionBackground(CANVAS_SOFT);
        playerListView.setSelectionForeground(INK);
        playerListView.setFixedCellHeight(32);
        playerListView.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
        playerListView.setCellRenderer(new PlayerCellRenderer());

        JScrollPane scroll = new JScrollPane(playerListView,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(CANVAS);
        scroll.getVerticalScrollBar().setUI(new ThinScrollBarUI());

        // Footer hint
        JLabel hint = new JLabel("<html><div style='color:#64748d; font-size:11px; line-height:1.4;'>"
                + "AI 한 명이 사람들 사이에<br/>"
                + "숨어 있습니다. 설명을 듣고<br/>"
                + "찾아보세요.</div></html>");
        hint.setBorder(BorderFactory.createEmptyBorder(10, 18, 16, 18));

        side.add(head,   BorderLayout.NORTH);
        side.add(scroll, BorderLayout.CENTER);
        side.add(hint,   BorderLayout.SOUTH);
        return side;
    }

    // ── 하단 액션 + 입력 ──────────────────────────────────────────────────
    private JPanel buildSouthPanel() {
        JPanel south = new JPanel(new BorderLayout(0, 14));
        south.setOpaque(false);

        // ── 액션 버튼 ──────────────────────────────────────────────────────
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.setOpaque(false);

        startButton = makePillButton("게임 시작", ButtonStyle.PRIMARY);
        startButton.addActionListener(e -> sendRaw("/시작"));

        voteButton = makePillButton("AI 투표", ButtonStyle.SECONDARY);
        voteButton.addActionListener(e -> openVoteDialog());

        JButton usersButton = makePillButton("접속자 새로고침", ButtonStyle.GHOST);
        usersButton.addActionListener(e -> sendRaw("/users"));

        JButton helpButton = makePillButton("도움말", ButtonStyle.GHOST);
        helpButton.addActionListener(e -> openHelpDialog());

        actions.add(startButton);
        actions.add(voteButton);
        actions.add(usersButton);
        actions.add(helpButton);

        // ── 입력 ───────────────────────────────────────────────────────────
        JPanel inputRow = new JPanel(new BorderLayout(10, 0));
        inputRow.setOpaque(false);

        inputField = new JTextField();
        inputField.setBackground(CANVAS);
        inputField.setForeground(INK);
        inputField.setCaretColor(INK);
        inputField.setFont(BODY_MD);
        inputField.setBorder(BorderFactory.createCompoundBorder(
                new RoundedLineBorder(HAIRLINE_INPUT, 1, 6),
                BorderFactory.createEmptyBorder(10, 14, 10, 14)));
        inputField.addActionListener(e -> sendMessage());
        inputField.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) {
                inputField.setBorder(BorderFactory.createCompoundBorder(
                        new RoundedLineBorder(PRIMARY, 2, 6),
                        BorderFactory.createEmptyBorder(9, 13, 9, 13)));
            }
            @Override public void focusLost(FocusEvent e) {
                inputField.setBorder(BorderFactory.createCompoundBorder(
                        new RoundedLineBorder(HAIRLINE_INPUT, 1, 6),
                        BorderFactory.createEmptyBorder(10, 14, 10, 14)));
            }
        });

        sendButton = makePillButton("전송", ButtonStyle.PRIMARY);
        sendButton.setPreferredSize(new Dimension(88, 40));
        sendButton.addActionListener(e -> sendMessage());

        inputRow.add(inputField, BorderLayout.CENTER);
        inputRow.add(sendButton, BorderLayout.EAST);

        south.add(actions,  BorderLayout.NORTH);
        south.add(inputRow, BorderLayout.SOUTH);
        return south;
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Pill 버튼 (Primary 인디고 / Secondary 아웃라인 / Ghost 텍스트)
    // ──────────────────────────────────────────────────────────────────────
    private enum ButtonStyle { PRIMARY, SECONDARY, GHOST }

    private JButton makePillButton(String text, ButtonStyle style) {
        JButton b = new JButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                    RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth(), h = getHeight();
                boolean rollover = getModel().isRollover();
                boolean pressed  = getModel().isPressed();
                boolean enabled  = isEnabled();

                switch (style) {
                    case PRIMARY: {
                        Color bg = pressed  ? PRIMARY_PRESS
                                 : rollover ? PRIMARY_DEEP
                                 : PRIMARY;
                        if (!enabled) bg = new Color(0xCBD2DD);
                        g2.setColor(bg);
                        g2.fillRoundRect(0, 0, w, h, h, h);
                        break;
                    }
                    case SECONDARY: {
                        Color bg = pressed ? PRIMARY_SUBDUED
                                 : rollover ? new Color(0xF1F0FE)
                                 : CANVAS;
                        g2.setColor(bg);
                        g2.fillRoundRect(0, 0, w, h, h, h);
                        g2.setColor(enabled ? PRIMARY : new Color(0xCBD2DD));
                        g2.setStroke(new BasicStroke(1.4f));
                        g2.drawRoundRect(0, 0, w - 1, h - 1, h, h);
                        break;
                    }
                    case GHOST: {
                        if (rollover) {
                            g2.setColor(CANVAS_SOFT);
                            g2.fillRoundRect(0, 0, w, h, h, h);
                        }
                        break;
                    }
                }
                g2.dispose();
                super.paintComponent(g);
            }
            @Override public boolean isContentAreaFilled() { return false; }
        };
        switch (style) {
            case PRIMARY:
                b.setForeground(ON_PRIMARY);
                break;
            case SECONDARY:
                b.setForeground(PRIMARY);
                break;
            case GHOST:
                b.setForeground(INK_MUTE);
                break;
        }
        b.setFont(BUTTON_MD);
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setOpaque(false);
        b.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
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

                serverOut.println(nickname);

                SwingUtilities.invokeLater(() -> {
                    connectionDot.setForeground(PRIMARY);
                    connectionText.setText("연결됨 · " + SERVER_HOST + ":" + SERVER_PORT);
                    setInputEnabled(true);
                    appendSystem("서버에 연결되었습니다.", PRIMARY);
                });

                serverOut.println("/users");

                String line;
                while ((line = serverIn.readLine()) != null) {
                    handleServerLine(line);
                }

            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> {
                    appendSystem("서버에 접속하지 못했습니다 · "
                            + SERVER_HOST + ":" + SERVER_PORT, RUBY);
                    connectionDot.setForeground(RUBY);
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
    //  서버 메시지 파싱 & 렌더링
    // ──────────────────────────────────────────────────────────────────────
    private void handleServerLine(String line) {
        SwingUtilities.invokeLater(() -> {
            parseGameState(line);
            renderLine(line);
        });
    }

    private void parseGameState(String line) {
        if (line.contains("게임 시작!")) {
            setPhase(Phase.DESCRIBING);
        } else if (line.startsWith("[게임] 단어: 【")) {
            int s = line.indexOf('【'); int e = line.indexOf('】');
            if (s >= 0 && e > s) {
                currentWord = line.substring(s + 1, e);
                wordValue.setText(currentWord);
                wordValue.setForeground(PRIMARY);
            }
        } else if (line.contains("당신은") && line.contains("번 플레이어입니다")) {
            int idx = line.indexOf("당신은 ") + 4;
            int end = line.indexOf("번", idx);
            try {
                myNumber = Integer.parseInt(line.substring(idx, end).trim());
                myNumberValue.setText(myNumber + "번");
                myNumberValue.setForeground(BRAND_DARK_900);
            } catch (NumberFormatException ignored) {}
        } else if (line.startsWith("[게임] 총 ") && line.contains("명이 참가")) {
            int s = "[게임] 총 ".length();
            int e = line.indexOf("명", s);
            try { totalPlayers = Integer.parseInt(line.substring(s, e).trim()); }
            catch (NumberFormatException ignored) {}
        } else if (line.matches("\\[게임\\] \\d+번 플레이어의 차례.*")) {
            int s = "[게임] ".length();
            int e = line.indexOf("번", s);
            try {
                currentTurnNum = Integer.parseInt(line.substring(s, e).trim());
                boolean mine = (currentTurnNum == myNumber);
                turnValue.setText(currentTurnNum + "번" + (mine ? "  · 내 차례" : ""));
                turnValue.setForeground(mine ? RUBY : PRIMARY);
            } catch (NumberFormatException ignored) {}
        } else if (line.contains("투표 시작!")) {
            setPhase(Phase.VOTING);
            turnValue.setText("—");
            turnValue.setForeground(INK_MUTE);
        } else if (line.contains("다시 하려면") || line.contains("게임이 취소")) {
            resetGameUI();
        } else if (line.startsWith("[입장] ")) {
            String name = extractName(line, "[입장] ", "님 입장");
            if (name != null) addPlayer(name);
        } else if (line.startsWith("[퇴장] ")) {
            String name = extractName(line, "[퇴장] ", "님 퇴장");
            if (name != null) removePlayer(name);
        } else if (line.startsWith("[서버] 접속자 (")) {
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
        phaseValue.setText(p.label);
        phaseValue.setForeground(p.color);
        if (p == Phase.WAITING) {
            wordValue.setText("—");      wordValue.setForeground(INK_MUTE);
            turnValue.setText("—");      turnValue.setForeground(INK_MUTE);
            myNumberValue.setText("—");  myNumberValue.setForeground(INK_MUTE);
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
        playerCountLabel.setText(String.valueOf(playerListModel.size()));
    }

    // ──────────────────────────────────────────────────────────────────────
    //  메시지 렌더링
    // ──────────────────────────────────────────────────────────────────────
    private void renderLine(String line) {
        if (line.startsWith("[게임]")) {
            appendSystem(line.substring("[게임]".length()).trim(), PRIMARY);
            return;
        }
        if (line.startsWith("[서버]")) {
            appendSystem(line.substring("[서버]".length()).trim(), INK_MUTE);
            return;
        }
        if (line.startsWith("[입장]") || line.startsWith("[퇴장]")) {
            appendSystem(line, INK_MUTE);
            return;
        }
        if (line.startsWith("[투표]")) {
            appendSystem(line.substring("[투표]".length()).trim(), RUBY);
            return;
        }
        if (line.matches("\\[\\d+번\\].*")) {
            int close = line.indexOf(']');
            String tag = line.substring(1, close);
            String body = line.substring(close + 1).trim();
            boolean mine = false;
            try {
                int num = Integer.parseInt(tag.replace("번", "").trim());
                mine = (num == myNumber);
            } catch (NumberFormatException ignored) {}
            appendBubble(tag, body, mine);
            return;
        }
        if (line.startsWith("[") && line.contains("] ")) {
            int close = line.indexOf(']');
            String sender = line.substring(1, close);
            String body = line.substring(close + 1).trim();
            boolean mine = sender.equals(nickname);
            appendBubble(sender, body, mine);
            return;
        }
        if (line.trim().isEmpty()) {
            appendRaw("\n", INK_MUTE);
        } else {
            appendRaw(line + "\n", INK_MUTE);
        }
    }

    private void appendBubble(String sender, String body, boolean mine) {
        StyledDocument doc = chatArea.getStyledDocument();
        try {
            SimpleAttributeSet senderAttr = new SimpleAttributeSet();
            StyleConstants.setForeground(senderAttr, mine ? PRIMARY : INK_SECONDARY);
            StyleConstants.setFontFamily(senderAttr, FONT_FAMILY);
            StyleConstants.setFontSize(senderAttr, 12);
            StyleConstants.setBold(senderAttr, true);
            StyleConstants.setSpaceAbove(senderAttr, 10f);
            StyleConstants.setAlignment(senderAttr,
                    mine ? StyleConstants.ALIGN_RIGHT : StyleConstants.ALIGN_LEFT);

            SimpleAttributeSet bodyAttr = new SimpleAttributeSet();
            StyleConstants.setForeground(bodyAttr, INK);
            StyleConstants.setFontFamily(bodyAttr, FONT_FAMILY);
            StyleConstants.setFontSize(bodyAttr, 14);
            StyleConstants.setSpaceBelow(bodyAttr, 2f);
            StyleConstants.setAlignment(bodyAttr,
                    mine ? StyleConstants.ALIGN_RIGHT : StyleConstants.ALIGN_LEFT);

            int sStart = doc.getLength();
            String senderLine = (mine ? "나 · " + sender : sender)
                    + "    " + timeFormat.format(new Date()) + "\n";
            doc.insertString(doc.getLength(), senderLine, senderAttr);
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
            StyleConstants.setAlignment(attr, StyleConstants.ALIGN_CENTER);
            StyleConstants.setSpaceAbove(attr, 6f);
            StyleConstants.setSpaceBelow(attr, 6f);

            int start = doc.getLength();
            doc.insertString(doc.getLength(), text + "\n", attr);
            doc.setParagraphAttributes(start, doc.getLength() - start, attr, true);
            chatArea.setCaretPosition(doc.getLength());
        } catch (BadLocationException ignored) {}
    }

    private void appendRaw(String text, Color color) {
        StyledDocument doc = chatArea.getStyledDocument();
        try {
            SimpleAttributeSet attr = new SimpleAttributeSet();
            StyleConstants.setForeground(attr, color);
            StyleConstants.setFontFamily(attr, FONT_FAMILY);
            StyleConstants.setFontSize(attr, 12);
            doc.insertString(doc.getLength(), text, attr);
            chatArea.setCaretPosition(doc.getLength());
        } catch (BadLocationException ignored) {}
    }

    // ──────────────────────────────────────────────────────────────────────
    //  투표 다이얼로그
    // ──────────────────────────────────────────────────────────────────────
    private void openVoteDialog() {
        int playerCount = totalPlayers > 0 ? totalPlayers
                : Math.max(2, playerListModel.size() + 1);

        JDialog dialog = new JDialog(this, "AI 투표", true);
        dialog.getContentPane().setBackground(CANVAS);
        dialog.setLayout(new BorderLayout());

        // Hero with gradient stripe
        JPanel hero = new JPanel(new BorderLayout());
        hero.setBackground(CANVAS);
        hero.setBorder(BorderFactory.createEmptyBorder(20, 24, 4, 24));

        JLabel title = new JLabel("누가 AI라고 생각하시나요?");
        title.setForeground(INK);
        title.setFont(DISPLAY_MD);

        JLabel sub = new JLabel("플레이어 번호를 선택하면 바로 투표됩니다.");
        sub.setForeground(INK_MUTE);
        sub.setFont(BODY_MD);
        sub.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));

        JPanel titleBox = new JPanel();
        titleBox.setLayout(new BoxLayout(titleBox, BoxLayout.Y_AXIS));
        titleBox.setOpaque(false);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        sub.setAlignmentX(Component.LEFT_ALIGNMENT);
        titleBox.add(title);
        titleBox.add(sub);
        hero.add(titleBox, BorderLayout.CENTER);

        // Number grid
        int cols = Math.min(5, playerCount);
        int rows = (int) Math.ceil(playerCount / (double) cols);
        JPanel grid = new JPanel(new GridLayout(rows, cols, 10, 10));
        grid.setOpaque(false);
        grid.setBorder(BorderFactory.createEmptyBorder(16, 24, 8, 24));

        for (int i = 1; i <= playerCount; i++) {
            final int num = i;
            JButton b;
            if (num == myNumber) {
                b = makePillButton(num + "번 (나)", ButtonStyle.GHOST);
                b.setEnabled(false);
                b.setForeground(INK_MUTE);
            } else {
                b = makePillButton(num + "번", ButtonStyle.SECONDARY);
            }
            b.setPreferredSize(new Dimension(72, 52));
            b.setFont(new Font(FONT_FAMILY, Font.BOLD, 15));
            b.addActionListener(e -> {
                sendRaw("/투표 " + num);
                dialog.dispose();
            });
            grid.add(b);
        }

        // Footer
        JPanel foot = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        foot.setOpaque(false);
        foot.setBorder(BorderFactory.createEmptyBorder(4, 18, 18, 18));
        JButton cancel = makePillButton("취소", ButtonStyle.GHOST);
        cancel.addActionListener(e -> dialog.dispose());
        foot.add(cancel);

        JPanel body = new JPanel(new BorderLayout());
        body.setBackground(CANVAS);
        body.add(hero, BorderLayout.NORTH);
        body.add(grid, BorderLayout.CENTER);
        body.add(foot, BorderLayout.SOUTH);

        // Top gradient stripe accent
        dialog.add(new GradientStripe(4), BorderLayout.NORTH);
        dialog.add(body, BorderLayout.CENTER);
        dialog.pack();
        dialog.setMinimumSize(new Dimension(440, 0));
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    // ──────────────────────────────────────────────────────────────────────
    //  도움말 다이얼로그
    // ──────────────────────────────────────────────────────────────────────
    private void openHelpDialog() {
        JDialog dialog = new JDialog(this, "도움말", true);
        dialog.getContentPane().setBackground(CANVAS);
        dialog.setLayout(new BorderLayout());

        // Top gradient stripe
        dialog.add(new GradientStripe(4), BorderLayout.NORTH);

        JPanel head = new JPanel(new BorderLayout());
        head.setBackground(CANVAS);
        head.setBorder(BorderFactory.createEmptyBorder(20, 24, 4, 24));
        JLabel title = new JLabel("도움말");
        title.setForeground(INK);
        title.setFont(DISPLAY_MD);
        head.add(title, BorderLayout.CENTER);

        String html = "<html><div style='font-family:맑은 고딕; color:#0D253D; padding:8px 24px 24px 24px; width:520px; font-size:13px; line-height:1.55;'>"
                + "<p style='color:#64748D; font-size:11px; letter-spacing:0.08em; margin:14px 0 4px 0;'>게임 규칙</p>"
                + "<ol style='padding-left:18px; margin-top:4px;'>"
                + "<li>2명 이상 모이면 <b>게임 시작</b>을 눌러 시작합니다.</li>"
                + "<li>서버가 단어를 공개합니다. 참가자 중에 <b>AI 한 명이 섞여 있습니다.</b></li>"
                + "<li>번호 순서대로 단어를 <b>1~2문장으로 설명</b>합니다.</li>"
                + "<li>모두 설명한 후 <b>AI 투표</b>로 AI라고 생각하는 번호를 선택합니다.</li>"
                + "<li>가장 많은 표를 받은 번호가 AI면 인간 승리, 아니면 AI 승리.</li>"
                + "</ol>"
                + "<p style='color:#64748D; font-size:11px; letter-spacing:0.08em; margin:18px 0 4px 0;'>채팅 명령어</p>"
                + "<table cellpadding='4' style='margin-top:4px;'>"
                + "<tr><td style='color:#533AFD; font-weight:bold;'>/시작</td><td>게임을 시작합니다</td></tr>"
                + "<tr><td style='color:#533AFD; font-weight:bold;'>/투표 [번호]</td><td>AI를 지목합니다</td></tr>"
                + "<tr><td style='color:#533AFD; font-weight:bold;'>/users</td><td>접속자 목록을 갱신합니다</td></tr>"
                + "<tr><td style='color:#533AFD; font-weight:bold;'>/quit</td><td>채팅방에서 나갑니다</td></tr>"
                + "</table>"
                + "<p style='color:#64748D; font-size:11px; letter-spacing:0.08em; margin:18px 0 4px 0;'>색상 가이드</p>"
                + "<div style='color:#533AFD;'>● 내 메시지 / 게임 진행</div>"
                + "<div style='color:#273951;'>● 다른 사람 메시지</div>"
                + "<div style='color:#EA2261;'>● 투표 현황</div>"
                + "<div style='color:#64748D;'>● 서버/입장/퇴장 알림</div>"
                + "</div></html>";
        JLabel content = new JLabel(html);
        content.setVerticalAlignment(SwingConstants.TOP);

        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(CANVAS);
        scroll.getVerticalScrollBar().setUI(new ThinScrollBarUI());

        JPanel foot = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        foot.setOpaque(false);
        foot.setBorder(BorderFactory.createEmptyBorder(0, 18, 18, 18));
        JButton close = makePillButton("닫기", ButtonStyle.PRIMARY);
        close.addActionListener(e -> dialog.dispose());
        foot.add(close);

        JPanel body = new JPanel(new BorderLayout());
        body.setBackground(CANVAS);
        body.add(head,   BorderLayout.NORTH);
        body.add(scroll, BorderLayout.CENTER);
        body.add(foot,   BorderLayout.SOUTH);

        dialog.add(body, BorderLayout.CENTER);
        dialog.setSize(600, 580);
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
    //  접속자 셀 렌더러
    // ──────────────────────────────────────────────────────────────────────
    private class PlayerCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                int index, boolean isSelected, boolean cellHasFocus) {
            JLabel l = (JLabel) super.getListCellRendererComponent(
                    list, value, index, isSelected, cellHasFocus);
            String name = String.valueOf(value);
            boolean me = name.equals(nickname);
            l.setText("  " + (me ? "● " : "○ ") + name + (me ? "  · 나" : ""));
            l.setBackground(isSelected ? CANVAS_SOFT : CANVAS);
            l.setForeground(me ? PRIMARY : INK);
            l.setFont(me ? new Font(FONT_FAMILY, Font.BOLD, 13) : BODY_MD);
            l.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
            return l;
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Stripe-style 그라데이션 메시 스트라이프
    // ──────────────────────────────────────────────────────────────────────
    private static class GradientStripe extends JComponent {
        private final int thickness;
        GradientStripe(int thickness) {
            this.thickness = thickness;
            setPreferredSize(new Dimension(0, thickness));
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                RenderingHints.VALUE_ANTIALIAS_ON);
            float w = getWidth();
            float[] stops  = {0f, 0.25f, 0.5f, 0.75f, 1f};
            Color[] colors = {
                new Color(0xFDE2C4), // cream
                new Color(0xFFB594), // sherbet orange
                new Color(0xD4BBF5), // lavender
                new Color(0x533AFD), // indigo
                new Color(0xEA2261)  // ruby
            };
            g2.setPaint(new java.awt.LinearGradientPaint(0, 0, w, 0, stops, colors));
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.dispose();
        }
    }

    // ── 풀-사이즈 그라데이션 메시 (welcome hero용) ────────────────────────
    private static class GradientMeshPanel extends JComponent {
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            // 베이스: 좌→우 5 stop 그라데이션
            float[] stops  = {0f, 0.22f, 0.50f, 0.75f, 1f};
            Color[] colors = {
                new Color(0xFDE2C4),
                new Color(0xFFB594),
                new Color(0xD4BBF5),
                new Color(0x6E5BFE),
                new Color(0xEA2261)
            };
            g2.setPaint(new java.awt.LinearGradientPaint(0, 0, w, 0, stops, colors));
            g2.fillRect(0, 0, w, h);

            // 상단 부드러운 빛 오버레이 (어둠 → 투명)
            g2.setPaint(new java.awt.GradientPaint(
                    0, 0, new Color(255, 255, 255, 40),
                    0, h, new Color(0, 0, 0, 40)));
            g2.fillRect(0, 0, w, h);
            g2.dispose();
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Borders (라운드 보더 / 라운드 채움 보더)
    // ──────────────────────────────────────────────────────────────────────
    private static class RoundedLineBorder extends AbstractBorder {
        private final Color color; private final int thickness; private final int radius;
        RoundedLineBorder(Color color, int thickness, int radius) {
            this.color = color; this.thickness = thickness; this.radius = radius;
        }
        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.setStroke(new BasicStroke(thickness));
            g2.drawRoundRect(x, y, w - 1, h - 1, radius, radius);
            g2.dispose();
        }
        @Override public Insets getBorderInsets(Component c) {
            return new Insets(thickness, thickness, thickness, thickness);
        }
    }

    private static class RoundedFilledBorder extends AbstractBorder {
        private final Color fill; private final int radius;
        RoundedFilledBorder(Color fill, int radius) {
            this.fill = fill; this.radius = radius;
        }
        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(fill);
            g2.fillRoundRect(x, y, w, h, radius, radius);
            g2.dispose();
        }
        @Override public Insets getBorderInsets(Component c) {
            return new Insets(0, 0, 0, 0);
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    //  얇은 스크롤바
    // ──────────────────────────────────────────────────────────────────────
    private static class ThinScrollBarUI extends javax.swing.plaf.basic.BasicScrollBarUI {
        @Override protected void configureScrollBarColors() {
            this.thumbColor = new Color(0xCBD5DF);
            this.trackColor = CANVAS;
        }
        @Override protected JButton createDecreaseButton(int o) { return zeroButton(); }
        @Override protected JButton createIncreaseButton(int o) { return zeroButton(); }
        private JButton zeroButton() {
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
    //  진입점
    // ──────────────────────────────────────────────────────────────────────
    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
        catch (Exception ignored) {}

        SwingUtilities.invokeLater(WelcomeDialog::showAndConnect);
    }

    // ──────────────────────────────────────────────────────────────────────
    //  시작 화면 (그라데이션 메시 히어로 + 닉네임 폼)
    // ──────────────────────────────────────────────────────────────────────
    private static class WelcomeDialog {
        static void showAndConnect() {
            JFrame dummy = new JFrame();
            dummy.setUndecorated(true);
            dummy.setLocationRelativeTo(null);

            JDialog dialog = new JDialog(dummy, "AI 라이어 게임", true);
            dialog.getContentPane().setBackground(CANVAS);
            dialog.setLayout(new BorderLayout());
            dialog.setSize(480, 480);
            dialog.setLocationRelativeTo(null);
            dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

            // ── 그라데이션 히어로 ─────────────────────────────────────────
            GradientMeshPanel mesh = new GradientMeshPanel();
            mesh.setLayout(new BorderLayout());
            mesh.setPreferredSize(new Dimension(480, 180));

            JPanel heroContent = new JPanel();
            heroContent.setLayout(new BoxLayout(heroContent, BoxLayout.Y_AXIS));
            heroContent.setOpaque(false);
            heroContent.setBorder(BorderFactory.createEmptyBorder(36, 28, 36, 28));

            // Eyebrow
            JLabel eyebrow = new JLabel("AI LIAR GAME");
            eyebrow.setForeground(new Color(255, 255, 255, 220));
            Map<TextAttribute, Object> ts = new HashMap<>();
            ts.put(TextAttribute.TRACKING, 0.18);
            eyebrow.setFont(new Font(FONT_FAMILY, Font.BOLD, 10).deriveFont(ts));
            eyebrow.setAlignmentX(Component.LEFT_ALIGNMENT);

            JLabel title = new JLabel("AI 라이어 게임");
            title.setForeground(Color.WHITE);
            title.setFont(tightFont(32, Font.PLAIN, -0.030f));
            title.setAlignmentX(Component.LEFT_ALIGNMENT);

            JLabel subtitle = new JLabel("<html><div style='color:rgba(255,255,255,0.85); font-size:13px; line-height:1.45;'>"
                    + "AI 한 명이 사람들 사이에 숨어 있습니다.<br/>설명을 듣고 누가 AI인지 맞춰보세요.</div></html>");
            subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);

            heroContent.add(eyebrow);
            heroContent.add(Box.createVerticalStrut(10));
            heroContent.add(title);
            heroContent.add(Box.createVerticalStrut(8));
            heroContent.add(subtitle);
            mesh.add(heroContent, BorderLayout.CENTER);

            // ── 폼 영역 ───────────────────────────────────────────────────
            JPanel form = new JPanel();
            form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
            form.setBackground(CANVAS);
            form.setBorder(BorderFactory.createEmptyBorder(24, 28, 14, 28));

            JLabel label = new JLabel("닉네임");
            label.setForeground(INK_MUTE);
            label.setFont(MICRO_CAP);
            label.setAlignmentX(Component.LEFT_ALIGNMENT);
            Map<TextAttribute, Object> tsLabel = new HashMap<>();
            tsLabel.put(TextAttribute.TRACKING, 0.10);
            label.setFont(label.getFont().deriveFont(tsLabel));

            JTextField field = new JTextField();
            field.setBackground(CANVAS);
            field.setForeground(INK);
            field.setCaretColor(INK);
            field.setFont(new Font(FONT_FAMILY, Font.PLAIN, 15));
            field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
            field.setAlignmentX(Component.LEFT_ALIGNMENT);
            field.setBorder(BorderFactory.createCompoundBorder(
                    new RoundedLineBorder(HAIRLINE_INPUT, 1, 6),
                    BorderFactory.createEmptyBorder(10, 14, 10, 14)));
            field.addFocusListener(new FocusAdapter() {
                @Override public void focusGained(FocusEvent e) {
                    field.setBorder(BorderFactory.createCompoundBorder(
                            new RoundedLineBorder(PRIMARY, 2, 6),
                            BorderFactory.createEmptyBorder(9, 13, 9, 13)));
                }
                @Override public void focusLost(FocusEvent e) {
                    field.setBorder(BorderFactory.createCompoundBorder(
                            new RoundedLineBorder(HAIRLINE_INPUT, 1, 6),
                            BorderFactory.createEmptyBorder(10, 14, 10, 14)));
                }
            });

            JLabel hint = new JLabel("최대 20자 · 다른 사용자와 겹치지 않게");
            hint.setForeground(INK_MUTE);
            hint.setFont(MICRO);
            hint.setAlignmentX(Component.LEFT_ALIGNMENT);

            JLabel serverInfo = new JLabel("서버  " + SERVER_HOST + ":" + SERVER_PORT);
            serverInfo.setForeground(INK_MUTE);
            serverInfo.setFont(BODY_TABULAR);
            serverInfo.setAlignmentX(Component.LEFT_ALIGNMENT);

            form.add(label);
            form.add(Box.createVerticalStrut(8));
            form.add(field);
            form.add(Box.createVerticalStrut(8));
            form.add(hint);
            form.add(Box.createVerticalStrut(18));
            form.add(serverInfo);

            // ── 버튼 ──────────────────────────────────────────────────────
            JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            actions.setBackground(CANVAS);
            actions.setBorder(BorderFactory.createEmptyBorder(0, 20, 20, 20));

            JButton enter = staticPillButton("입장하기", ButtonStyle.PRIMARY);
            JButton cancel = staticPillButton("취소", ButtonStyle.GHOST);

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

            dialog.add(mesh,    BorderLayout.NORTH);
            dialog.add(form,    BorderLayout.CENTER);
            dialog.add(actions, BorderLayout.SOUTH);

            SwingUtilities.invokeLater(field::requestFocus);
            dialog.setVisible(true);
        }

        // welcome 다이얼로그에서 사용할 static pill 버튼 (인스턴스 메서드 의존성 회피)
        private static JButton staticPillButton(String text, ButtonStyle style) {
            JButton b = new JButton(text) {
                @Override protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                        RenderingHints.VALUE_ANTIALIAS_ON);
                    int w = getWidth(), h = getHeight();
                    boolean rollover = getModel().isRollover();
                    boolean pressed  = getModel().isPressed();
                    if (style == ButtonStyle.PRIMARY) {
                        Color bg = pressed  ? PRIMARY_PRESS
                                 : rollover ? PRIMARY_DEEP : PRIMARY;
                        g2.setColor(bg);
                        g2.fillRoundRect(0, 0, w, h, h, h);
                    } else if (style == ButtonStyle.GHOST) {
                        if (rollover) {
                            g2.setColor(CANVAS_SOFT);
                            g2.fillRoundRect(0, 0, w, h, h, h);
                        }
                    } else { // SECONDARY
                        Color bg = pressed ? PRIMARY_SUBDUED
                                : rollover ? new Color(0xF1F0FE) : CANVAS;
                        g2.setColor(bg);
                        g2.fillRoundRect(0, 0, w, h, h, h);
                        g2.setColor(PRIMARY);
                        g2.setStroke(new BasicStroke(1.4f));
                        g2.drawRoundRect(0, 0, w - 1, h - 1, h, h);
                    }
                    g2.dispose();
                    super.paintComponent(g);
                }
                @Override public boolean isContentAreaFilled() { return false; }
            };
            b.setForeground(style == ButtonStyle.PRIMARY ? ON_PRIMARY
                          : style == ButtonStyle.SECONDARY ? PRIMARY : INK_MUTE);
            b.setFont(BUTTON_MD);
            b.setFocusPainted(false);
            b.setBorderPainted(false);
            b.setOpaque(false);
            b.setBorder(BorderFactory.createEmptyBorder(10, 22, 10, 22));
            b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            return b;
        }
    }
}
