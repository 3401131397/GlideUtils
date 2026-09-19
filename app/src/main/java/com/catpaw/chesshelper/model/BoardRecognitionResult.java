package com.catpaw.chesshelper.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 棋盘识别结果
 */
public class BoardRecognitionResult {

    private int[][] board; // 10行 x 9列，0表示空，其他值对应棋子
    private List<ChessPiece> pieces;
    private boolean redTurn;
    private float confidence;
    private long timestamp;

    public BoardRecognitionResult() {
        this.board = new int[10][9];
        this.pieces = new ArrayList<>();
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * 从棋子列表构建棋盘
     */
    public void buildBoardFromPieces() {
        for (ChessPiece piece : pieces) {
            if (piece.getRow() >= 0 && piece.getRow() < 10
                    && piece.getCol() >= 0 && piece.getCol() < 9) {
                board[piece.getRow()][piece.getCol()] = piece.getInternalCode();
            }
        }
    }

    public int[][] getBoard() { return board; }
    public void setBoard(int[][] board) { this.board = board; }

    public List<ChessPiece> getPieces() { return pieces; }
    public void setPieces(List<ChessPiece> pieces) { this.pieces = pieces; }

    public boolean isRedTurn() { return redTurn; }
    public void setRedTurn(boolean redTurn) { this.redTurn = redTurn; }

    public float getConfidence() { return confidence; }
    public void setConfidence(float confidence) { this.confidence = confidence; }

    public long getTimestamp() { return timestamp; }

    public void addPiece(ChessPiece piece) {
        pieces.add(piece);
    }

    /**
     * 获取90维棋盘数组（用于引擎）
     */
    public int[] getBoard1D() {
        int[] result = new int[90];
        for (int row = 0; row < 10; row++) {
            for (int col = 0; col < 9; col++) {
                result[row * 9 + col] = board[row][col];
            }
        }
        return result;
    }
}
