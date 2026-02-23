package main;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.ArrayList;

public class DotsAndBoxes extends JFrame {

	private static final long serialVersionUID = 1L;

	private final int ROWS = 5, COLS = 5, SIZE = 60, OFFSET = 50;

    private boolean player1Turn = true;
    private ArrayList<Line> lines = new ArrayList<>();
    private int[][] boxes = new int[ROWS - 1][COLS - 1];

    private int score1 = 0, score2 = 0;
    private JLabel statusLabel;

    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private boolean isServer;
    private boolean myTurn;

    // CHAT
    private JTextArea chatArea;
    private JTextField chatField;

    // RESTART
    @SuppressWarnings("unused")
	private boolean restartRequested = false;

    public DotsAndBoxes() {
        if (!setupNetwork()) System.exit(0);

        setTitle("Dots and Boxes Online - " + (isServer ? "Servidor (Vermelho)" : "Cliente (Azul)"));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        JPanel topPanel = new JPanel(new BorderLayout());
        statusLabel = new JLabel("", SwingConstants.CENTER);
        statusLabel.setFont(new Font("Arial", Font.BOLD, 16));
        updateStatus();
        topPanel.add(statusLabel, BorderLayout.CENTER);
        add(topPanel, BorderLayout.NORTH);

        JPanel gamePanel = new JPanel() {
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                drawGame((Graphics2D) g);
            }
        };

        gamePanel.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                if (myTurn) {
                    handleMouseClick(e.getX(), e.getY());
                    repaint();
                }
            }
        });

        gamePanel.setPreferredSize(new Dimension(COLS * SIZE + OFFSET * 2,
                ROWS * SIZE + OFFSET * 2));

        add(gamePanel, BorderLayout.CENTER);

        // ================= CHAT =================
        JPanel chatPanel = new JPanel(new BorderLayout());

        chatArea = new JTextArea(10, 20);
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);

        JScrollPane scroll = new JScrollPane(chatArea);

        chatField = new JTextField();
        chatField.addActionListener(e -> sendChatMessage());

        JButton sendButton = new JButton("Enviar");
        sendButton.addActionListener(e -> sendChatMessage());

        JPanel bottomChat = new JPanel(new BorderLayout());
        bottomChat.add(chatField, BorderLayout.CENTER);
        bottomChat.add(sendButton, BorderLayout.EAST);

        chatPanel.add(scroll, BorderLayout.CENTER);
        chatPanel.add(bottomChat, BorderLayout.SOUTH);

        add(chatPanel, BorderLayout.EAST);
        // ========================================

        pack();
        setLocationRelativeTo(null);
        setVisible(true);

        new Thread(this::listenForOpponent).start();
    }

    private boolean setupNetwork() {
        String[] options = {"Criar Sala (Host)", "Entrar em Sala (Cliente)"};
        int choice = JOptionPane.showOptionDialog(null, "Selecione o modo:", "Conexão",
                0, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);

        try {
            if (choice == 0) {
                isServer = true;
                myTurn = true;
                @SuppressWarnings("resource")
				ServerSocket serverSocket = new ServerSocket(5000);
                JOptionPane.showMessageDialog(null, "Aguardando na porta 5000...");
                socket = serverSocket.accept();
            } else {
                isServer = false;
                myTurn = false;
                String ip = JOptionPane.showInputDialog("IP do Host:", "localhost");
                socket = new Socket(ip, 5000);
            }

            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());
            return true;

        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "Erro: " + e.getMessage());
            return false;
        }
    }

    private void listenForOpponent() {
        try {
            while (true) {
                NetworkMessage msg = (NetworkMessage) in.readObject();

                switch (msg.type) {

                    case "MOVE":
                        Line incoming = (Line) msg.data;
                        processMove(incoming, false);
                        break;

                    case "CHAT":
                        chatArea.append("Oponente: " + msg.data + "\n");
                        break;

                    case "RESTART_REQUEST":
                        int option = JOptionPane.showConfirmDialog(
                                this,
                                "O oponente quer jogar novamente.\nAceitar?",
                                "Novo Jogo",
                                JOptionPane.YES_NO_OPTION);

                        if (option == JOptionPane.YES_OPTION) {
                            out.writeObject(new NetworkMessage("RESTART_ACCEPT", null));
                            out.flush();
                            resetGame();
                        }
                        break;

                    case "RESTART_ACCEPT":
                        resetGame();
                        break;
                }
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Oponente desconectou!");
        }
    }

    private void sendChatMessage() {
        String text = chatField.getText().trim();
        if (!text.isEmpty()) {
            try {
                out.writeObject(new NetworkMessage("CHAT", text));
                out.flush();
                chatArea.append("Você: " + text + "\n");
                chatField.setText("");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void handleMouseClick(int x, int y) {
        for (int i = 0; i < ROWS; i++) {
            for (int j = 0; j < COLS; j++) {

                int px = OFFSET + j * SIZE;
                int py = OFFSET + i * SIZE;

                Line line = null;

                if (x > px && x < px + SIZE && Math.abs(y - py) < 10 && j < COLS - 1)
                    line = new Line(px, py, px + SIZE, py,
                            isServer ? Color.RED : Color.BLUE);

                else if (y > py && y < py + SIZE && Math.abs(x - px) < 10 && i < ROWS - 1)
                    line = new Line(px, py, px, py + SIZE,
                            isServer ? Color.RED : Color.BLUE);

                if (line != null && !lines.contains(line)) {
                    try {
                        out.writeObject(new NetworkMessage("MOVE", line));
                        out.flush();
                        processMove(line, true);
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                    return;
                }
            }
        }
    }

    private synchronized void processMove(Line line, boolean isMyMove) {
        lines.add(line);
        boolean boxClosed = checkForBox();

        if (!boxClosed) {
            myTurn = !isMyMove;
            player1Turn = !player1Turn;
        } else {
            myTurn = isMyMove;
        }

        updateStatus();
        checkGameOver();
        repaint();
    }

    private void drawGame(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

        for (int r = 0; r < ROWS - 1; r++)
            for (int c = 0; c < COLS - 1; c++)
                if (boxes[r][c] != 0) {
                    g.setColor(boxes[r][c] == 1 ?
                            new Color(255, 100, 100, 150) :
                            new Color(100, 100, 255, 150));
                    g.fillRect(OFFSET + c * SIZE,
                            OFFSET + r * SIZE, SIZE, SIZE);
                }

        g.setStroke(new BasicStroke(4));
        for (Line l : lines) {
            g.setColor(l.color);
            g.drawLine(l.x1, l.y1, l.x2, l.y2);
        }

        g.setColor(Color.DARK_GRAY);
        for (int i = 0; i < ROWS; i++)
            for (int j = 0; j < COLS; j++)
                g.fillOval(OFFSET + j * SIZE - 5,
                        OFFSET + i * SIZE - 5, 10, 10);
    }

    private boolean checkForBox() {
        boolean closed = false;

        for (int r = 0; r < ROWS - 1; r++)
            for (int c = 0; c < COLS - 1; c++)
                if (boxes[r][c] == 0 && isSquareComplete(r, c)) {
                    boxes[r][c] = player1Turn ? 1 : 2;
                    if (player1Turn) score1++; else score2++;
                    closed = true;
                }

        return closed;
    }

    private boolean isSquareComplete(int r, int c) {
        int x = OFFSET + c * SIZE;
        int y = OFFSET + r * SIZE;

        return hasLine(x, y, x + SIZE, y) &&
                hasLine(x, y + SIZE, x + SIZE, y + SIZE) &&
                hasLine(x, y, x, y + SIZE) &&
                hasLine(x + SIZE, y, x + SIZE, y + SIZE);
    }

    private boolean hasLine(int x1, int y1, int x2, int y2) {
        for (Line l : lines)
            if (l.x1 == x1 && l.y1 == y1 &&
                    l.x2 == x2 && l.y2 == y2)
                return true;
        return false;
    }

    private void updateStatus() {
        statusLabel.setText((myTurn ? "SUA VEZ!" : "Aguardando...")
                + " | Placar: " + score1 + " - " + score2);
        statusLabel.setForeground(myTurn ?
                new Color(0, 150, 0) : Color.BLACK);
    }

    private void checkGameOver() {
        if (score1 + score2 == (ROWS - 1) * (COLS - 1)) {

            String winner = (score1 > score2) ? "Vermelho venceu!" :
                    (score2 > score1) ? "Azul venceu!" : "Empate!";

            int option = JOptionPane.showConfirmDialog(
                    this,
                    "Fim de Jogo!\n" + winner +
                            "\n\nDeseja jogar novamente?",
                    "Jogar Novamente",
                    JOptionPane.YES_NO_OPTION);

            if (option == JOptionPane.YES_OPTION) {
                try {
                    out.writeObject(new NetworkMessage("RESTART_REQUEST", null));
                    out.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void resetGame() {
        lines.clear();
        boxes = new int[ROWS - 1][COLS - 1];
        score1 = 0;
        score2 = 0;
        player1Turn = true;
        myTurn = isServer;
        updateStatus();
        repaint();
    }

    static class NetworkMessage implements Serializable {
		private static final long serialVersionUID = 1L;
		
		String type;
        Object data;

        NetworkMessage(String t, Object d) {
            type = t;
            data = d;
        }
    }

    static class Line implements Serializable {
		private static final long serialVersionUID = 1L;
		
		int x1, y1, x2, y2;
        Color color;

        Line(int x1, int y1, int x2, int y2, Color c) {
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
            color = c;
        }

        public boolean equals(Object o) {
            if (!(o instanceof Line)) return false;
            Line l = (Line) o;
            return x1 == l.x1 && y1 == l.y1 &&
                    x2 == l.x2 && y2 == l.y2;
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(DotsAndBoxes::new);
    }
}