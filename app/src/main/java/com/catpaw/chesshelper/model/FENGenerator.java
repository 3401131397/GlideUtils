package com.catpaw.chesshelper.model;

/**
 * FEN字符串生成器
 *
 * FEN格式示例（中国象棋）：
 * rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 1
 *
 * 说明：
 * - K/k: 帅/将
 * - A/a: 仕/士
 * - B/b: 相/象 (也可用E/e)
 * - N/n: 马 (也可用H/h)
 * - R/r: 车
 * - C/c: 炮
 * - P/p: 兵/卒
 * - 数字: 连续空格数
 * - 小写: 黑方（上方）
 * - 大写: 红方（下方）
 * - w: 红走, b: 黑走
 */
public class FENGenerator {

    /**
     * 从90维棋盘数组生成FEN字符串
     * @param board 90维数组，索引0=左上角(row0,col0)，索引89=右下角(row9,col8)
     * @param redTurn true为红方走棋
     * @return FEN字符串
     */
    public static String generateFEN(int[] board, boolean redTurn) {
        if (board == null || board.length != 90) return null;

        StringBuilder fen = new StringBuilder();

        for (int row = 0; row < 10; row++) {
            int emptyCount = 0;
            for (int col = 0; col < 9; col++) {
                int piece = board[row * 9 + col];
                if (piece == 0) {
                    emptyCount++;
                } else {
                    if (emptyCount > 0) {
                        fen.append(emptyCount);
                        emptyCount = 0;
                    }
                    fen.append(mapPieceToChar(piece));
                }
            }
            if (emptyCount > 0) {
                fen.append(emptyCount);
            }
            if (row < 9) fen.append('/');
        }

        fen.append(redTurn ? " w" : " b");
        fen.append(" - - 0 1");

        return fen.toString();
    }

    /**
     * 从二维棋盘数组生成FEN字符串
     * @param board 10x9二维数组
     * @return FEN字符串
     */
    public static String generateFEN(int[][] board) {
        if (board == null || board.length != 10) return null;
        boolean redTurn = true; // 默认红方

        StringBuilder fen = new StringBuilder();
        for (int row = 0; row < 10; row++) {
            int emptyCount = 0;
            for (int col = 0; col < 9; col++) {
                int piece = board[row][col];
                if (piece == 0) {
                    emptyCount++;
                } else {
                    if (emptyCount > 0) {
                        fen.append(emptyCount);
                        emptyCount = 0;
                    }
                    fen.append(mapPieceToChar(piece));
                }
            }
            if (emptyCount > 0) fen.append(emptyCount);
            if (row < 9) fen.append('/');
        }

        fen.append(redTurn ? " w" : " b");
        fen.append(" - - 0 1");

        return fen.toString();
    }

    /**
     * 从BoardRecognitionResult生成FEN
     */
    public static String generateFEN(BoardRecognitionResult result) {
        if (result == null) return null;
        int[] board1D = result.getBoard1D();
        return generateFEN(board1D, result.isRedTurn());
    }

    /**
     * 将内部棋子编码映射为FEN字符
     */
    private static char mapPieceToChar(int piece) {
        switch (piece) {
            case 1:  return 'K'; // 红帅
            case 2:  return 'A'; // 红仕
            case 3:  return 'B'; // 红相
            case 4:  return 'N'; // 红马
            case 5:  return 'R'; // 红车
            case 6:  return 'C'; // 红炮
            case 7:  return 'P'; // 红兵
            case 8:  return 'k'; // 黑将
            case 9:  return 'a'; // 黑士
            case 10: return 'b'; // 黑象
            case 11: return 'n'; // 黑马
            case 12: return 'r'; // 黑车
            case 13: return 'c'; // 黑炮
            case 14: return 'p'; // 黑卒
            default: return '.';
        }
    }

    /**
     * 校验FEN字符串格式是否合法
     */
    public static boolean isValidFEN(String fen) {
        if (fen == null || fen.isEmpty()) return false;

        fen = fen.replace("%20", " ");
        String[] parts = fen.split(" ");
        if (parts.length < 2) return false;

        String boardPart = parts[0];
        String[] rows = boardPart.split("/");
        if (rows.length != 10) return false;

        // 每行总列数必须为9
        for (String row : rows) {
            int colCount = 0;
            for (int i = 0; i < row.length(); i++) {
                char c = row.charAt(i);
                if (c >= '1' && c <= '9') {
                    colCount += (c - '0');
                } else if ("KABNRCPkabnrcp".indexOf(c) >= 0) {
                    colCount++;
                }
            }
            if (colCount != 9) return false;
        }

        // 检查行棋方
        if (!parts[1].equals("w") && !parts[1].equals("b")) return false;

        return true;
    }

    /**
     * 将FEN解析为90维棋盘数组
     */
    public static int[] parseFEN(String fen) {
        if (!isValidFEN(fen)) return null;

        fen = fen.replace("%20", " ");
        String[] parts = fen.split(" ");
        String boardPart = parts[0];

        int[] board = new int[90];
        String[] rows = boardPart.split("/");

        for (int row = 0; row < 10; row++) {
            int col = 0;
            for (int i = 0; i < rows[row].length(); i++) {
                char c = rows[row].charAt(i);
                if (c >= '1' && c <= '9') {
                    col += (c - '0');
                } else {
                    int piece = mapCharToPiece(c);
                    board[row * 9 + col] = piece;
                    col++;
                }
            }
        }

        return board;
    }

    private static int mapCharToPiece(char c) {
        switch (c) {
            case 'K': return 1;
            case 'A': return 2;
            case 'B': case 'E': return 3;
            case 'N': case 'H': return 4;
            case 'R': return 5;
            case 'C': return 6;
            case 'P': return 7;
            case 'k': return 8;
            case 'a': return 9;
            case 'b': case 'e': return 10;
            case 'n': case 'h': return 11;
            case 'r': return 12;
            case 'c': return 13;
            case 'p': return 14;
            default: return 0;
        }
    }
}
