package main;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.ArrayList;

// Classe principal que herda JFrame
// Controla: Interface gráfica, Lógica do jogo, Comunicação em rede e Estado da partida
public class DotsAndBoxes extends JFrame {
	
	// Variáveis globais (configuração do tabuleiro)
	
	// ROWS: quantidade de linhas de pontos
	// COLS: quantidade de colunas de pontos
	// SIZE: tamanho de cada célula
	// OFFSET: margem da tela
	// Como são 5x5 pontos → teremos 4x4 caixas
    private final int ROWS = 5, COLS = 5, SIZE = 60, OFFSET = 50;
    
    // Controle de turno
    // player1Turn: controla alternância lógica (vermelho/azul)
    private boolean player1Turn = true; // No online, define quem começa (Sempre o Host)
    
    // lines: Armazena todas as linhas desenhadas
    // boxes: Matriz que representa as caixas fechadas:
    // 0 → não fechada | 1 → jogador vermelho | 2 → jogador azul
    private ArrayList<Line> lines = new ArrayList<>();
    private int[][] boxes = new int[ROWS-1][COLS-1];
    
    private int score1 = 0, score2 = 0;
    private JLabel statusLabel;

    // Variáveis de Rede
    // Socket: conexão cliente-servidor
    // ObjectOutputStream: envia objetos
    // ObjectInputStream: recebe objetos
    // isServer: define se é host
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private boolean isServer; 
    
    // myTurn: controla se o jogador local pode jogar
    private boolean myTurn;

    // Construtor
    // Fluxo: configura rede, configura janela, cria painel de jogo, adiciona listener de mouse
    // e inicia thread para ouvir oponente
    public DotsAndBoxes() {
        if (!setupNetwork()) System.exit(0);

        setTitle("Dots and Boxes Online - " + (isServer ? "Servidor (Vermelho)" : "Cliente (Azul)"));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        JPanel topPanel = new JPanel(new BorderLayout());
        statusLabel = new JLabel("", SwingConstants.CENTER);
        statusLabel.setFont(new Font("Arial", Font.BOLD, 16));
        updateStatus();
        
        // Reset no online geralmente é desabilitado ou sincronizado 
        // Para simplificar, deixaremos apenas o status no topo
        topPanel.add(statusLabel, BorderLayout.CENTER);
        add(topPanel, BorderLayout.NORTH);

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
                if (myTurn) {
                    handleMouseClick(e.getX(), e.getY());
                    gamePanel.repaint();
                }
            }
        });

        gamePanel.setPreferredSize(new Dimension(COLS * SIZE + OFFSET * 2, ROWS * SIZE + OFFSET * 2));
        add(gamePanel, BorderLayout.CENTER);

        pack();
        setLocationRelativeTo(null);
        setVisible(true);

        // Inicia a escuta de jogadas
        new Thread(this::listenForOpponent).start();
    }

    // Define se o jogador será: servidor (cria sala) e cliente (entra na sala)
    private boolean setupNetwork() {
        String[] options = {"Criar Sala (Host)", "Entrar em Sala (Cliente)"};
        int choice = JOptionPane.showOptionDialog(null, "Selecione o modo de jogo:", "Conexão",
                0, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);

        try {
            if (choice == 0) {
            	// Modo servidor abre porta 5000 e espera cliente conectar
                isServer = true;
                myTurn = true; // Host começa
                ServerSocket serverSocket = new ServerSocket(5000);
                JOptionPane.showMessageDialog(null, "Aguardando oponente na porta 5000...");
                socket = serverSocket.accept();
            } else {
            	// Modo cliente conecta o IP informado
                isServer = false;
                myTurn = false; // Cliente aguarda
                String ip = JOptionPane.showInputDialog("Digite o IP do Host:", "localhost");
                socket = new Socket(ip, 5000);
            }
            
            // Criação dos streams (permite enviar objetos Line pela rede)
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());
            return true;
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "Erro de conexão: " + e.getMessage());
            return false;
        }
    }

    // Função que fica em loop infinito aguardando jogadas do adversário
    // Executa em uma Thread separada para não travar a interface
    private void listenForOpponent() {
        try {
            while (true) {
                Line incomingLine = (Line) in.readObject();
                // Quando recebe uma linha, processa ela como jogada do oponente
                processMove(incomingLine, false);
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Oponente desconectou!");
        }
    }

    // Função que detecta se o clique foi: em uma linha horizontal ou em uma linha vertical
    // Cria objeto: 
    // Se a linha ainda não existe: envia para o oponente e processa localmente
    private void handleMouseClick(int x, int y) {
        for (int i = 0; i < ROWS; i++) {
            for (int j = 0; j < COLS; j++) {
                int px = OFFSET + j * SIZE;
                int py = OFFSET + i * SIZE;

                Line lineToAdd = null;
                if (x > px && x < px + SIZE && Math.abs(y - py) < 10 && j < COLS - 1) {
                    lineToAdd = new Line(px, py, px + SIZE, py, isServer ? Color.RED : Color.BLUE);
                } else if (y > py && y < py + SIZE && Math.abs(x - px) < 10 && i < ROWS - 1) {
                    lineToAdd = new Line(px, py, px, py + SIZE, isServer ? Color.RED : Color.BLUE);
                }

                if (lineToAdd != null && !lines.contains(lineToAdd)) {
                    try {
                        out.writeObject(lineToAdd); // Envia para o outro
                        out.flush();
                        processMove(lineToAdd, true);
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                    return;
                }
            }
        }
    }

    // Função Central do Sistema: adiciona linha à lista, Verifica se fechou caixa,
    // atualiza turno, atualiza placar e repaint
    private synchronized void processMove(Line line, boolean isMyMove) {
        lines.add(line);
        boolean boxClosed = checkForBox();
        
        // Regra: se fechou caixa, continua sendo a vez de quem jogou
        if (!boxClosed) {
            myTurn = !isMyMove;
            player1Turn = !player1Turn;
        } else {
            myTurn = isMyMove;
            // player1Turn não muda para manter a cor correta de quem fechou
        }
        
        updateStatus();
        checkGameOver();
        repaint();
    }

    // Responsável por desenhar: caixas preenchidas, linhas jogadas e pontos do tabuleiro
    private void drawGame(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        for (int r = 0; r < ROWS - 1; r++) {
            for (int c = 0; c < COLS - 1; c++) {
                if (boxes[r][c] != 0) {
                    g.setColor(boxes[r][c] == 1 ? new Color(255, 100, 100, 150) : new Color(100, 100, 255, 150));
                    g.fillRect(OFFSET + c * SIZE, OFFSET + r * SIZE, SIZE, SIZE);
                }
            }
        }
        g.setStroke(new BasicStroke(4));
        for (Line l : lines) {
            g.setColor(l.color);
            g.drawLine(l.x1, l.y1, l.x2, l.y2);
        }
        g.setColor(Color.DARK_GRAY);
        for (int i = 0; i < ROWS; i++) {
            for (int j = 0; j < COLS; j++) {
                g.fillOval(OFFSET + j * SIZE - 5, OFFSET + i * SIZE - 5, 10, 10);
            }
        }
    }

    // Percorre todas as possíveis caixas
    // Se fechou: marca dono da caixa, incrementa pontuação e retorna true
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

    // Verifica se existem as 4 linhas
    private boolean isSquareComplete(int r, int c) {
        int x = OFFSET + c * SIZE, y = OFFSET + r * SIZE;
        return hasLine(x, y, x + SIZE, y) && hasLine(x, y + SIZE, x + SIZE, y + SIZE) &&
               hasLine(x, y, x, y + SIZE) && hasLine(x + SIZE, y, x + SIZE, y + SIZE);
    }

    // Percorre a lista de linhas e verifica se existe exatamente aquela coordenada
    private boolean hasLine(int x1, int y1, int x2, int y2) {
        for (Line l : lines) {
            if (l.x1 == x1 && l.y1 == y1 && l.x2 == x2 && l.y2 == y2) return true;
        }
        return false;
    }

    // Atualiza: texto do turno, cor da mensagem e placar
    private void updateStatus() {
        String turnText = myTurn ? "SUA VEZ!" : "Aguardando oponente...";
        statusLabel.setText(turnText + " | Placar: " + score1 + " - " + score2);
        statusLabel.setForeground(myTurn ? new Color(0, 150, 0) : Color.BLACK);
    }

    // Condição de fim se todas as caixas foram preenchidas → exibe vencedor
    private void checkGameOver() {
        if (score1 + score2 == (ROWS - 1) * (COLS - 1)) {
            String winner = (score1 > score2) ? "Vermelho venceu!" : (score2 > score1) ? "Azul venceu!" : "Empate!";
            JOptionPane.showMessageDialog(this, "Fim de Jogo!\n" + winner);
            // No online, o reset deve ser manual fechando e abrindo para evitar bugs de dessincronização
        }
    }

    // Método mantido para compatibilidade, mas no online o ideal é reiniciar a conexão
    private void resetGame() {
        lines.clear();
        boxes = new int[ROWS-1][COLS-1];
        score1 = 0; score2 = 0;
        player1Turn = true;
        myTurn = isServer;
        updateStatus();
        repaint();
    }

    // Line precisa ser Serializable para viajar pela rede!
    // Por que Serializable? 
    // Porque objetos são enviados via ObjectOutputStream
    static class Line implements Serializable {
        private static final long serialVersionUID = 1L;
        int x1, y1, x2, y2;
        Color color;
        Line(int x1, int y1, int x2, int y2, Color c) {
            this.x1 = x1; this.y1 = y1; this.x2 = x2; this.y2 = y2; this.color = c;
        }
        
        // Evita duplicação
        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof Line)) return false;
            Line o = (Line) obj;
            return (x1 == o.x1 && y1 == o.y1 && x2 == o.x2 && y2 == o.y2);
        }
    }

    public static void main(String[] args) {
    	// Garante que a interface seja criada na Event Dispatch Thread (EDT)
        SwingUtilities.invokeLater(() -> new DotsAndBoxes());
    }
}