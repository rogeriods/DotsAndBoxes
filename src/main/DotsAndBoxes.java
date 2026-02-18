package main;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;

public class DotsAndBoxes extends JFrame {
    private final int ROWS = 5, COLS = 5; 
    private final int SIZE = 60; 
    private final int OFFSET = 50; 
    
    private boolean player1Turn = true;
    private ArrayList<Line> lines = new ArrayList<>();
    private int[][] boxes = new int[ROWS-1][COLS-1]; // 0: vazio, 1: P1, 2: P2
    
    // Novas variáveis para Placar
    private int score1 = 0;
    private int score2 = 0;
    private JLabel statusLabel;

    public DotsAndBoxes() {
        setTitle("Dots and Boxes - Placar & Reset");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // Painel de Status (Topo)
        JPanel topPanel = new JPanel(new BorderLayout());
        statusLabel = new JLabel("Vez do Vermelho | Placar: 0 - 0", SwingConstants.CENTER);
        statusLabel.setFont(new Font("Arial", Font.BOLD, 16));
        
        JButton resetBtn = new JButton("Reiniciar Jogo");
        resetBtn.addActionListener(e -> resetGame());
        
        topPanel.add(statusLabel, BorderLayout.CENTER);
        topPanel.add(resetBtn, BorderLayout.EAST);
        add(topPanel, BorderLayout.NORTH);

        // Painel do Jogo (Centro)
        JPanel gamePanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                drawGame((Graphics2D) g);
            }
        };

        gamePanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                handleMouseClick(e.getX(), e.getY());
                gamePanel.repaint();
            }
        });

        gamePanel.setPreferredSize(new Dimension(COLS * SIZE + OFFSET * 2, ROWS * SIZE + OFFSET * 2));
        add(gamePanel, BorderLayout.CENTER);

        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    private void drawGame(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Desenhar caixas preenchidas
        for (int r = 0; r < ROWS - 1; r++) {
            for (int c = 0; c < COLS - 1; c++) {
                if (boxes[r][c] != 0) {
                    g.setColor(boxes[r][c] == 1 ? new Color(255, 100, 100, 150) : new Color(100, 100, 255, 150));
                    g.fillRect(OFFSET + c * SIZE, OFFSET + r * SIZE, SIZE, SIZE);
                }
            }
        }

        // Desenhar linhas
        g.setStroke(new BasicStroke(4));
        for (Line l : lines) {
            g.setColor(l.color);
            g.drawLine(l.x1, l.y1, l.x2, l.y2);
        }

        // Desenhar pontos
        g.setColor(Color.DARK_GRAY);
        for (int i = 0; i < ROWS; i++) {
            for (int j = 0; j < COLS; j++) {
                g.fillOval(OFFSET + j * SIZE - 5, OFFSET + i * SIZE - 5, 10, 10);
            }
        }
    }

    private void handleMouseClick(int x, int y) {
        for (int i = 0; i < ROWS; i++) {
            for (int j = 0; j < COLS; j++) {
                int px = OFFSET + j * SIZE;
                int py = OFFSET + i * SIZE;

                if (x > px && x < px + SIZE && Math.abs(y - py) < 10 && j < COLS - 1) {
                    addLine(px, py, px + SIZE, py);
                    return;
                }
                if (y > py && y < py + SIZE && Math.abs(x - px) < 10 && i < ROWS - 1) {
                    addLine(px, py, px, py + SIZE);
                    return;
                }
            }
        }
    }

    private void addLine(int x1, int y1, int x2, int y2) {
        Line newLine = new Line(x1, y1, x2, y2, player1Turn ? Color.RED : Color.BLUE);
        if (!lines.contains(newLine)) {
            lines.add(newLine);
            boolean boxClosed = checkForBox();
            
            if (!boxClosed) {
                player1Turn = !player1Turn;
            }
            updateStatus();
            checkGameOver();
        }
    }

    private boolean checkForBox() {
        boolean closedAny = false;
        for (int r = 0; r < ROWS - 1; r++) {
            for (int c = 0; c < COLS - 1; c++) {
                if (boxes[r][c] == 0 && isSquareComplete(r, c)) {
                    boxes[r][c] = player1Turn ? 1 : 2;
                    if (player1Turn) score1++; else score2++;
                    closedAny = true;
                }
            }
        }
        return closedAny;
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
        for (Line l : lines) {
            if (l.x1 == x1 && l.y1 == y1 && l.x2 == x2 && l.y2 == y2) return true;
        }
        return false;
    }

    private void updateStatus() {
        String turn = player1Turn ? "Vez do Vermelho" : "Vez do Azul";
        statusLabel.setText(turn + " | Placar: " + score1 + " - " + score2);
    }

    private void checkGameOver() {
        if (score1 + score2 == (ROWS - 1) * (COLS - 1)) {
            String winner;
            if (score1 > score2) winner = "Vermelho venceu!";
            else if (score2 > score1) winner = "Azul venceu!";
            else winner = "Empate!";
            
            JOptionPane.showMessageDialog(this, "Fim de Jogo!\n" + winner + "\nPlacar final: " + score1 + " - " + score2);
            resetGame();
        }
    }

    private void resetGame() {
        lines.clear();
        boxes = new int[ROWS-1][COLS-1];
        score1 = 0;
        score2 = 0;
        player1Turn = true;
        updateStatus();
        repaint();
    }

    static class Line {
        int x1, y1, x2, y2;
        Color color;
        Line(int x1, int y1, int x2, int y2, Color c) {
            this.x1 = x1; this.y1 = y1; this.x2 = x2; this.y2 = y2; this.color = c;
        }
        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof Line)) return false;
            Line other = (Line) obj;
            return (x1 == other.x1 && y1 == other.y1 && x2 == other.x2 && y2 == other.y2);
        }
    }

    public static void main(String[] args) {
        // Garante que o Swing rode na thread correta
        SwingUtilities.invokeLater(() -> new DotsAndBoxes());
    }
}