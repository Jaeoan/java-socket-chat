import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.nio.charset.Charset;
import javax.swing.*;
import javax.swing.text.*;

/**
 * ChatClientGUI.java
 * ------------------
 * Java Swing 으로 만든 채팅 클라이언트 (외부 라이브러리 없음).
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

    private JTextPane chatArea;
    private JTextField inputField;
    private JButton sendButton;
    private JLabel statusLabel;

    private PrintWriter serverOut;
    private Socket socket;
    private String nickname;

    // 메시지 색상
    private static final Color COLOR_SERVER  = new Color(100, 180, 100);  // 초록
    private static final Color COLOR_MY_MSG  = new Color(80,  130, 220);  // 파랑
    private static final Color COLOR_OTHER   = new Color(220, 220, 220);  // 흰회색
    private static final Color COLOR_SYSTEM  = new Color(180, 180, 100);  // 노랑
    private static final Color BG_COLOR      = new Color(30,  30,  40);   // 다크
    private static final Color INPUT_BG      = new Color(45,  45,  60);
    private static final Color SEND_BTN      = new Color(80,  130, 220);

    public ChatClientGUI(String nickname) {
        this.nickname = nickname;
        buildUI();
        connectToServer();
    }

    // ── UI 구성 ────────────────────────────────────────────────────────────
    private void buildUI() {
        setTitle("채팅방 - " + nickname);
        setSize(620, 500);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // 배경
        getContentPane().setBackground(BG_COLOR);
        setLayout(new BorderLayout(0, 0));

        // ── 상단 상태바 ──────────────────────────────────────────────────
        statusLabel = new JLabel("  접속 중...");
        statusLabel.setForeground(COLOR_SYSTEM);
        statusLabel.setBackground(new Color(20, 20, 30));
        statusLabel.setOpaque(true);
        statusLabel.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
        statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));
        add(statusLabel, BorderLayout.NORTH);

        // ── 채팅 출력 영역 ───────────────────────────────────────────────
        chatArea = new JTextPane();
        chatArea.setEditable(false);
        chatArea.setBackground(BG_COLOR);
        chatArea.setFont(new Font("맑은 고딕", Font.PLAIN, 14));
        chatArea.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JScrollPane scrollPane = new JScrollPane(chatArea);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getVerticalScrollBar().setBackground(BG_COLOR);
        add(scrollPane, BorderLayout.CENTER);

        // ── 하단 입력 영역 ───────────────────────────────────────────────
        JPanel inputPanel = new JPanel(new BorderLayout(6, 0));
        inputPanel.setBackground(new Color(20, 20, 30));
        inputPanel.setBorder(BorderFactory.createEmptyBorder(8, 10, 10, 10));

        inputField = new JTextField();
        inputField.setBackground(INPUT_BG);
        inputField.setForeground(Color.WHITE);
        inputField.setCaretColor(Color.WHITE);
        inputField.setFont(new Font("맑은 고딕", Font.PLAIN, 14));
        inputField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(80, 80, 100)),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)));
        inputField.addActionListener(e -> sendMessage());

        sendButton = new JButton("전송");
        sendButton.setBackground(SEND_BTN);
        sendButton.setForeground(Color.WHITE);
        sendButton.setFont(new Font("맑은 고딕", Font.BOLD, 13));
        sendButton.setFocusPainted(false);
        sendButton.setBorder(BorderFactory.createEmptyBorder(6, 18, 6, 18));
        sendButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        sendButton.addActionListener(e -> sendMessage());

        // 게임 버튼 패널
        JPanel buttonPanel = new JPanel(new GridLayout(1, 2, 4, 0));
        buttonPanel.setBackground(new Color(20, 20, 30));

        JButton startButton = new JButton("게임 시작");
        startButton.setBackground(new Color(60, 160, 80));
        startButton.setForeground(Color.WHITE);
        startButton.setFont(new Font("맑은 고딕", Font.BOLD, 12));
        startButton.setFocusPainted(false);
        startButton.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        startButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        startButton.addActionListener(e -> {
            if (serverOut != null) serverOut.println("/시작");
        });

        JButton voteButton = new JButton("AI 투표");
        voteButton.setBackground(new Color(160, 80, 60));
        voteButton.setForeground(Color.WHITE);
        voteButton.setFont(new Font("맑은 고딕", Font.BOLD, 12));
        voteButton.setFocusPainted(false);
        voteButton.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        voteButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        voteButton.addActionListener(e -> {
            String num = JOptionPane.showInputDialog(this, "AI라고 생각하는 번호를 입력하세요:", "투표", JOptionPane.PLAIN_MESSAGE);
            if (num != null && !num.trim().isEmpty()) {
                if (serverOut != null) serverOut.println("/투표 " + num.trim());
            }
        });

        buttonPanel.add(startButton);
        buttonPanel.add(voteButton);

        inputPanel.add(inputField, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);

        JPanel southPanel = new JPanel(new BorderLayout(0, 4));
        southPanel.setBackground(new Color(20, 20, 30));
        southPanel.setBorder(BorderFactory.createEmptyBorder(8, 10, 10, 10));
        southPanel.add(buttonPanel, BorderLayout.NORTH);
        southPanel.add(inputPanel, BorderLayout.CENTER);
        inputPanel.setBorder(null);

        add(southPanel, BorderLayout.SOUTH);

        // 창 닫을 때 /quit 전송
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                disconnect();
            }
        });

        setVisible(true);
        inputField.requestFocus();
    }

    // ── 서버 연결 ──────────────────────────────────────────────────────────
    private void connectToServer() {
        new Thread(() -> {
            try {
                socket = new Socket(SERVER_HOST, SERVER_PORT);

                serverOut = new PrintWriter(
                        new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);
                BufferedReader serverIn = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), "UTF-8"));

                // 닉네임 전송 (서버가 첫 줄로 닉네임을 요청함)
                serverOut.println(nickname);

                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("  " + SERVER_HOST + ":" + SERVER_PORT
                            + "  |  닉네임: " + nickname);
                    statusLabel.setForeground(COLOR_SERVER);
                    inputField.setEnabled(true);
                    sendButton.setEnabled(true);
                });

                // 서버 메시지 수신 루프
                String line;
                while ((line = serverIn.readLine()) != null) {
                    appendMessage(line);
                }

            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> {
                    appendColored("[오류] 서버에 접속하지 못했습니다: " + SERVER_HOST + ":" + SERVER_PORT,
                            new Color(220, 80, 80));
                    statusLabel.setText("  연결 실패");
                    statusLabel.setForeground(new Color(220, 80, 80));
                    inputField.setEnabled(false);
                    sendButton.setEnabled(false);
                });
            }
        }).start();

        // 연결 전까지 입력 비활성화
        inputField.setEnabled(false);
        sendButton.setEnabled(false);
    }

    // ── 메시지 전송 ────────────────────────────────────────────────────────
    private void sendMessage() {
        String text = inputField.getText().trim();
        if (text.isEmpty() || serverOut == null) return;

        inputField.setText("");

        if (text.equals("/quit")) {
            disconnect();
            return;
        }

        serverOut.println(text);
    }

    // ── 메시지 출력 (색상 구분) ────────────────────────────────────────────
    private void appendMessage(String message) {
        SwingUtilities.invokeLater(() -> {
            Color color;
            if (message.startsWith("[서버]") || message.startsWith("[입장]") || message.startsWith("[퇴장]")) {
                color = COLOR_SERVER;
            } else if (message.startsWith("[" + nickname + "]")) {
                color = COLOR_MY_MSG;
            } else if (message.startsWith("[연결")) {
                color = COLOR_SYSTEM;
            } else {
                color = COLOR_OTHER;
            }
            appendColored(message, color);
        });
    }

    private void appendColored(String text, Color color) {
        StyledDocument doc = chatArea.getStyledDocument();
        Style style = chatArea.addStyle("style", null);
        StyleConstants.setForeground(style, color);
        StyleConstants.setFontFamily(style, "맑은 고딕");
        StyleConstants.setFontSize(style, 14);
        try {
            doc.insertString(doc.getLength(), text + "\n", style);
            // 자동 스크롤
            chatArea.setCaretPosition(doc.getLength());
        } catch (BadLocationException ignored) {}
    }

    // ── 연결 종료 ──────────────────────────────────────────────────────────
    private void disconnect() {
        try {
            if (serverOut != null) serverOut.println("/quit");
            if (socket != null)   socket.close();
        } catch (IOException ignored) {}
        System.exit(0);
    }

    // ── 진입점 ─────────────────────────────────────────────────────────────
    public static void main(String[] args) {
        // 시스템 Look & Feel 적용
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        // 닉네임 입력 팝업
        JTextField nicknameField = new JTextField(15);
        nicknameField.setFont(new Font("맑은 고딕", Font.PLAIN, 14));

        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.add(new JLabel("닉네임을 입력하세요 (최대 20자):"), BorderLayout.NORTH);
        panel.add(nicknameField, BorderLayout.CENTER);

        int result = JOptionPane.showConfirmDialog(
                null, panel, "Java 채팅방 입장",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result != JOptionPane.OK_OPTION) return;

        String nickname = nicknameField.getText().trim();
        if (nickname.isEmpty()) nickname = "익명";
        if (nickname.length() > 20) nickname = nickname.substring(0, 20);

        final String finalNickname = nickname;
        SwingUtilities.invokeLater(() -> new ChatClientGUI(finalNickname));
    }
}
