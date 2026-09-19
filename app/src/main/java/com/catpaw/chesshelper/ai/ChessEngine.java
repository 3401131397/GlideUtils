package com.catpaw.chesshelper.ai;

import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * 中国象棋本地AI引擎
 *
 * 实现算法：
 * 1. Alpha-Beta剪枝搜索（Negamax）
 * 2. 迭代加深搜索（Iterative Deepening）
 * 3. 置换表（Transposition Table）
 * 4. 走法排序（MVV-LVA + Killer Moves）
 * 5. 静态搜索（Quiescence Search）
 * 6. 局面评估函数（位置价值 + 机动性 + 王安全）
 *
 * 棋盘坐标：
 * - 内部使用90格棋盘表示，9列x10行
 * - 红方在下半部分（行5-9），黑方在上半部分（行0-4）
 * - 棋子类型：将(0)、士(1)、象(2)、马(3)、车(4)、炮(5)、兵(6)
 * - 红色用大写，黑色用小写
 */
public class ChessEngine {

    private static final String TAG = "ChessEngine";

    // ========== 棋子定义 ==========
    // 红方
    public static final int R_JIANG = 1;   // 帅
    public static final int R_SHI = 2;    // 仕
    public static final int R_XIANG = 3;  // 相
    public static final int R_MA = 4;     // 马
    public static final int R_JU = 5;     // 车
    public static final int R_PAO = 6;    // 炮
    public static final int R_BING = 7;   // 兵

    // 黑方
    public static final int B_JIANG = 8;   // 将
    public static final int B_SHI = 9;     // 士
    public static final int B_XIANG = 10;  // 象
    public static final int B_MA = 11;     // 马
    public static final int B_JU = 12;     // 车
    public static final int B_PAO = 13;    // 炮
    public static final int B_BING = 14;   // 卒

    public static final int EMPTY = 0;
    public static final int COLS = 9;
    public static final int ROWS = 10;
    public static final int TOTAL_SQUARES = 90;

    // ========== 棋子价值（基础+位置价值）==========
    // 基础价值
    private static final int[] PIECE_VALUE = {
            0,      // 0 - empty
            10000,  // 1 - R_JIANG
            240,    // 2 - R_SHI
            240,    // 3 - R_XIANG
            480,    // 4 - R_MA
            1000,   // 5 - R_JU
            500,    // 6 - R_PAO
            100,    // 7 - R_BING
            10000,  // 8 - B_JIANG
            240,    // 9 - B_SHI
            240,    // 10 - B_XIANG
            480,    // 11 - B_MA
            1000,   // 12 - B_JU
            500,    // 13 - B_PAO
            100,    // 14 - B_BING
    };

    // 兵/卒位置价值矩阵（红方视角，黑方需要翻转）
    // 过河后价值大幅提升
    private static final int[][] BING_POSITION_VALUE = {
            {0, 0, 0, 0, 0, 0, 0, 0, 0},
            {0, 0, 0, 0, 0, 0, 0, 0, 0},
            {0, 0, 0, 0, 0, 0, 0, 0, 0},
            {0, 0, 0, 0, 0, 0, 0, 0, 0},
            {10, 10, 20, 30, 40, 30, 20, 10, 10},   // 黑卒过河线附近
            {20, 20, 30, 40, 50, 40, 30, 20, 20},   // 红兵过河线附近
            {40, 40, 40, 50, 60, 50, 40, 40, 40},
            {60, 60, 60, 70, 80, 70, 60, 60, 60},
            {80, 80, 80, 90, 100, 90, 80, 80, 80},
            {90, 90, 90, 100, 120, 100, 90, 90, 90},
    };

    // 马位置价值
    private static final int[][] MA_POSITION_VALUE = {
            {4, 8, 16, 12, 4, 12, 16, 8, 4},
            {4, 10, 28, 16, 8, 16, 28, 10, 4},
            {12, 14, 16, 20, 18, 20, 16, 14, 12},
            {8, 24, 18, 24, 20, 24, 18, 24, 8},
            {6, 16, 14, 18, 16, 18, 14, 16, 6},
            {4, 12, 16, 14, 12, 14, 16, 12, 4},
            {2, 6, 8, 6, 10, 6, 8, 6, 2},
            {4, 2, 8, 8, 4, 8, 8, 2, 4},
            {0, 2, 4, 4, -2, 4, 4, 2, 0},
            {0, -4, 0, 0, 0, 0, 0, -4, 0},
    };

    // 炮位置价值
    private static final int[][] PAO_POSITION_VALUE = {
            {6, 4, 0, -10, -12, -10, 0, 4, 6},
            {2, 2, 0, -4, -14, -4, 0, 2, 2},
            {2, 2, 0, -10, -8, -10, 0, 2, 2},
            {0, 0, -2, 4, 10, 4, -2, 0, 0},
            {0, 0, 0, 2, 8, 2, 0, 0, 0},
            {-2, 0, 4, 2, 6, 2, 4, 0, -2},
            {0, 0, 0, 2, 4, 2, 0, 0, 0},
            {4, 0, 8, 6, 10, 6, 8, 0, 4},
            {0, 2, 4, 6, 6, 6, 4, 2, 0},
            {0, 0, 2, 6, 6, 6, 2, 0, 0},
    };

    // 车位置价值
    private static final int[][] JU_POSITION_VALUE = {
            {14, 14, 12, 18, 16, 18, 12, 14, 14},
            {16, 20, 18, 24, 26, 24, 18, 20, 16},
            {12, 12, 12, 18, 18, 18, 12, 12, 12},
            {12, 18, 16, 22, 22, 22, 16, 18, 12},
            {12, 14, 12, 18, 18, 18, 12, 14, 12},
            {12, 16, 14, 20, 20, 20, 14, 16, 12},
            {6, 10, 8, 14, 14, 14, 8, 10, 6},
            {4, 8, 6, 14, 12, 14, 6, 8, 4},
            {8, 4, 8, 16, 8, 16, 8, 4, 8},
            {-2, 10, 6, 14, 12, 14, 6, 10, -2},
    };

    // ========== 走法方向 ==========
    private static final int[][] MA_OFFSET = {
            {-1, -2, 1, 2, -1, 2, 1, -2},  // dx
            {-2, -1, -2, -1, 2, 1, 2, 1},  // dy
            {0, -1, 1, 0, 0, 1, -1, 0},    // 蹩马腿检查点dx
            {-1, 0, 0, 1, 1, 0, 0, -1},    // 蹩马腿检查点dy
    };

    private static final int[][] MA_MOVE_RAW = {
            {-1, -2}, {1, -2}, {-2, -1}, {-2, 1},
            {-1, 2}, {1, 2}, {2, -1}, {2, 1}
    };

    private static final int[] MA_LEG_RAW = {
            {-1, 0}, {-1, 0}, {0, -1}, {0, -1},
            {-1, 0}, {-1, 0}, {0, 1}, {0, 1}
    };

    // 将/帅的走法
    private static final int[][] JIANG_MOVES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    // 士的走法
    private static final int[][] SHI_MOVES = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
    // 象的走法
    private static final int[][] XIANG_MOVES = {{2, 2}, {2, -2}, {-2, 2}, {-2, -2}};
    // 象眼检查
    private static final int[][] XIANG_EYE = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

    // 将帅合法区域（九宫格）
    private static final int[] RED_JIANG_ZONE_COL = {3, 4, 5};
    private static final int[] RED_JIANG_ZONE_ROW = {7, 8, 9};
    private static final int[] BLACK_JIANG_ZONE_COL = {3, 4, 5};
    private static final int[] BLACK_JIANG_ZONE_ROW = {0, 1, 2};

    // ========== 引擎状态 ==========
    private int[] mBoard; // 90格棋盘
    private boolean mRedTurn;
    private int mSearchDepth;
    private int mNodesSearched;
    private long mStartTime;
    private long mTimeLimitMs;

    // 置换表
    private static final int TT_SIZE = 1 << 20;
    private static final int TT_MASK = TT_SIZE - 1;
    private long[] mTTKey;
    private int[] mTTDepth;
    private int[] mTTScore;
    private int[] mTTMove;

    // Killer moves
    private int[][] mKillerMoves;

    // 搜索结果
    public static class EngineResult {
        public String bestMove;
        public int score;
        public String analysis;
        public int nodesSearched;

        public EngineResult(String bestMove, int score, String analysis, int nodes) {
            this.bestMove = bestMove;
            this.score = score;
            this.analysis = analysis;
            this.nodesSearched = nodes;
        }
    }

    // 走法表示
    private static class Move {
        int from;     // 起点 (0-89)
        int to;       // 终点 (0-89)
        int captured; // 被吃的棋子
        int piece;    // 移动的棋子

        Move(int from, int to, int piece, int captured) {
            this.from = from;
            this.to = to;
            this.piece = piece;
            this.captured = captured;
        }

        // UCCI格式走法: 如 "h0h1" (列a-i, 行0-9)
        String toUCCINotation() {
            int fromCol = from % 9;
            int fromRow = from / 9;
            int toCol = to % 9;
            int toRow = to / 9;
            return "" + (char) ('a' + fromCol) + fromRow + (char) ('a' + toCol) + toRow;
        }
    }

    public ChessEngine() {
        initializeTT();
    }

    private void initializeTT() {
        mTTKey = new long[TT_SIZE];
        mTTDepth = new int[TT_SIZE];
        mTTScore = new int[TT_SIZE];
        mTTMove = new int[TT_SIZE];
        mKillerMoves = new int[32][2]; // 存两层的killer move
        Arrays.fill(mKillerMoves[0], -1);
        Arrays.fill(mKillerMoves[1], -1);
    }

    // ========== FEN解析和棋盘初始化 ==========

    /**
     * 从FEN字符串解析棋盘
     */
    public void parseFEN(String fen) {
        mBoard = new int[TOTAL_SQUARES];
        Arrays.fill(mBoard, EMPTY);

        // URL解码（某些FEN有%20表示空格）
        fen = fen.replace("%20", " ");

        String[] parts = fen.split(" ");
        String boardStr = parts[0];
        mRedTurn = parts.length > 1 && parts[1].equals("w");

        String[] rows = boardStr.split("/");
        // FEN的第一行是黑方（顶行，row=0）
        for (int row = 0; row < ROWS; row++) {
            String rowStr = rows[row];
            int col = 0;
            for (int i = 0; i < rowStr.length(); i++) {
                char c = rowStr.charAt(i);
                if (c >= '1' && c <= '9') {
                    col += (c - '0');
                } else {
                    int piece = mapCharToPiece(c);
                    mBoard[row * COLS + col] = piece;
                    col++;
                }
            }
        }
    }

    private int mapCharToPiece(char c) {
        switch (c) {
            case 'K': return R_JIANG;
            case 'A': return R_SHI;
            case 'B': case 'E': return R_XIANG; // E用于部分表示
            case 'N': case 'H': return R_MA;    // N=马
            case 'R': return R_JU;
            case 'C': return R_PAO;
            case 'P': return R_BING;
            case 'k': return B_JIANG;
            case 'a': return B_SHI;
            case 'b': case 'e': return B_XIANG;
            case 'n': case 'h': return B_MA;
            case 'r': return B_JU;
            case 'c': return B_PAO;
            case 'p': return B_BING;
            default: return EMPTY;
        }
    }

    private char mapPieceToChar(int piece) {
        switch (piece) {
            case R_JIANG: return 'K';
            case R_SHI: return 'A';
            case R_XIANG: return 'B';
            case R_MA: return 'N';
            case R_JU: return 'R';
            case R_PAO: return 'C';
            case R_BING: return 'P';
            case B_JIANG: return 'k';
            case B_SHI: return 'a';
            case B_XIANG: return 'b';
            case B_MA: return 'n';
            case B_JU: return 'r';
            case B_PAO: return 'c';
            case B_BING: return 'p';
            default: return '.';
        }
    }

    // ========== 走法生成 ==========

    /**
     * 生成所有合法走法
     */
    List<Move> generateMoves() {
        List<Move> moves = new ArrayList<>();
        for (int i = 0; i < TOTAL_SQUARES; i++) {
            int piece = mBoard[i];
            if (piece == EMPTY) continue;
            boolean isRed = isRedPiece(piece);

            // 只生成当前方走棋
            if (mRedTurn != isRed) continue;

            switch (piece) {
                case R_JIANG: generateJiangMoves(i, true, moves); break;
                case B_JIANG: generateJiangMoves(i, false, moves); break;
                case R_SHI: generateShiMoves(i, true, moves); break;
                case B_SHI: generateShiMoves(i, false, moves); break;
                case R_XIANG: generateXiangMoves(i, true, moves); break;
                case B_XIANG: generateXiangMoves(i, false, moves); break;
                case R_MA: generateMaMoves(i, moves); break;
                case B_MA: generateMaMoves(i, moves); break;
                case R_JU: generateJuMoves(i, moves); break;
                case B_JU: generateJuMoves(i, moves); break;
                case R_PAO: generatePaoMoves(i, moves); break;
                case B_PAO: generatePaoMoves(i, moves); break;
                case R_BING: generateBingMoves(i, true, moves); break;
                case B_BING: generateBingMoves(i, false, moves); break;
            }
        }
        return moves;
    }

    private List<Move> generateJiangMoves(int pos, boolean isRed, List<Move> moves) {
        int col = pos % COLS;
        int row = pos / COLS;
        int minCol = 3, maxCol = 5;
        int minRow = isRed ? 7 : 0;
        int maxRow = isRed ? 9 : 2;

        for (int[] move : JIANG_MOVES) {
            int newCol = col + move[0];
            int newRow = row + move[1];
            if (newCol < minCol || newCol > maxCol || newRow < minRow || newRow > maxRow) continue;
            addMoveIfLegal(pos, newRow * COLS + newCol, moves);
        }

        // 将帅对脸（飞将）
        int enemyJiang = isRed ? B_JIANG : R_JIANG;
        for (int i = 0; i < TOTAL_SQUARES; i++) {
            if (mBoard[i] == enemyJiang) {
                int ejCol = i % COLS;
                if (ejCol == col) {
                    // 同一列，检查中间是否有棋子
                    int minR = Math.min(row, i / COLS) + 1;
                    int maxR = Math.max(row, i / COLS);
                    boolean blocked = false;
                    for (int r = minR; r < maxR; r++) {
                        if (mBoard[r * COLS + col] != EMPTY) {
                            blocked = true;
                            break;
                        }
                    }
                    if (!blocked) {
                        addMoveIfLegal(pos, i, moves);
                    }
                }
            }
        }
        return moves;
    }

    private List<Move> generateShiMoves(int pos, boolean isRed, List<Move> moves) {
        int col = pos % COLS;
        int row = pos / COLS;
        int minCol = 3, maxCol = 5;
        int minRow = isRed ? 7 : 0;
        int maxRow = isRed ? 9 : 2;

        for (int[] move : SHI_MOVES) {
            int newCol = col + move[0];
            int newRow = row + move[1];
            if (newCol < minCol || newCol > maxCol || newRow < minRow || newRow > maxRow) continue;
            addMoveIfLegal(pos, newRow * COLS + newCol, moves);
        }
        return moves;
    }

    private List<Move> generateXiangMoves(int pos, boolean isRed, List<Move> moves) {
        int col = pos % COLS;
        int row = pos / COLS;

        for (int i = 0; i < XIANG_MOVES.length; i++) {
            int newCol = col + XIANG_MOVES[i][0];
            int newRow = row + XIANG_MOVES[i][1];
            // 不能过河
            if (isRed && newRow < 5) continue;
            if (!isRed && newRow > 4) continue;
            if (newCol < 0 || newCol >= COLS || newRow < 0 || newRow >= ROWS) continue;
            // 象眼不能被堵
            int eyeRow = row + XIANG_EYE[i][1];
            int eyeCol = col + XIANG_EYE[i][0];
            if (mBoard[eyeRow * COLS + eyeCol] != EMPTY) continue;

            addMoveIfLegal(pos, newRow * COLS + newCol, moves);
        }
        return moves;
    }

    private List<Move> generateMaMoves(int pos, List<Move> moves) {
        int col = pos % COLS;
        int row = pos / COLS;

        for (int i = 0; i < MA_MOVE_RAW.length; i++) {
            int newCol = col + MA_MOVE_RAW[i][0];
            int newRow = row + MA_MOVE_RAW[i][1];
            if (newCol < 0 || newCol >= COLS || newRow < 0 || newRow >= ROWS) continue;
            // 蹩马腿检查
            int legRow = row + MA_LEG_RAW[i][1];
            int legCol = col + MA_LEG_RAW[i][0];
            if (mBoard[legRow * COLS + legCol] != EMPTY) continue;

            addMoveIfLegal(pos, newRow * COLS + newCol, moves);
        }
        return moves;
    }

    private List<Move> generateJuMoves(int pos, List<Move> moves) {
        int col = pos % COLS;
        int row = pos / COLS;

        // 四个方向，沿直线移动直到碰到棋子
        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] dir : directions) {
            int newCol = col + dir[0];
            int newRow = row + dir[1];
            while (newCol >= 0 && newCol < COLS && newRow >= 0 && newRow < ROWS) {
                int newPos = newRow * COLS + newCol;
                int piece = mBoard[newPos];
                if (piece == EMPTY) {
                    addMoveIfLegal(pos, newPos, moves);
                } else {
                    // 碰到棋子，如果是敌方的可以吃
                    if (isEnemy(piece, mBoard[pos])) {
                        addMoveIfLegal(pos, newPos, moves);
                    }
                    break;
                }
                newCol += dir[0];
                newRow += dir[1];
            }
        }
        return moves;
    }

    private List<Move> generatePaoMoves(int pos, List<Move> moves) {
        int col = pos % COLS;
        int row = pos / COLS;

        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] dir : directions) {
            int newCol = col + dir[0];
            int newRow = row + dir[1];
            boolean jumped = false; // 是否已搭炮架

            while (newCol >= 0 && newCol < COLS && newRow >= 0 && newRow < ROWS) {
                int newPos = newRow * COLS + newCol;
                int piece = mBoard[newPos];

                if (!jumped) {
                    if (piece == EMPTY) {
                        addMoveIfLegal(pos, newPos, moves); // 不吃子的移动
                    } else {
                        jumped = true; // 遇到炮架
                    }
                } else {
                    if (piece != EMPTY) {
                        if (isEnemy(piece, mBoard[pos])) {
                            addMoveIfLegal(pos, newPos, moves); // 隔山打牛
                        }
                        break; // 炮到此停止
                    }
                }
                newCol += dir[0];
                newRow += dir[1];
            }
        }
        return moves;
    }

    private List<Move> generateBingMoves(int pos, boolean isRed, List<Move> moves) {
        int col = pos % COLS;
        int row = pos / COLS;
        // 兵的方向
        int forward = isRed ? -1 : 1;
        boolean crossedRiver = isRed ? row <= 4 : row >= 5;

        // 前进
        int newRow = row + forward;
        if (newRow >= 0 && newRow < ROWS) {
            addMoveIfLegal(pos, newRow * COLS + col, moves);
        }

        // 过河后可以左右走
        if (crossedRiver) {
            if (col > 0) addMoveIfLegal(pos, row * COLS + col - 1, moves);
            if (col < COLS - 1) addMoveIfLegal(pos, row * COLS + col + 1, moves);
        }
        return moves;
    }

    /**
     * 添加走法 - 过滤掉送将的走法
     */
    private void addMoveIfLegal(int from, int to, List<Move> moves) {
        int targetPiece = mBoard[to];
        int movingPiece = mBoard[from];

        // 不能吃己方棋子
        if (targetPiece != EMPTY && !isEnemy(targetPiece, movingPiece)) return;

        // 模拟走法，检查是否送将
        mBoard[to] = movingPiece;
        mBoard[from] = EMPTY;

        boolean legal = !isInCheck(mRedTurn);

        // 还原
        mBoard[from] = movingPiece;
        mBoard[to] = targetPiece;

        if (legal) {
            moves.add(new Move(from, to, movingPiece, targetPiece));
        }
    }

    // ========== 局面评估 ==========

    /**
     * 评估局面（正数为红方优势，负数为黑方优势）
     */
    private int evaluate() {
        int score = 0;

        for (int i = 0; i < TOTAL_SQUARES; i++) {
            int piece = mBoard[i];
            if (piece == EMPTY) continue;

            boolean isRed = isRedPiece(piece);
            int col = i % COLS;
            int row = i / 9;

            // 基础棋子价值
            int baseValue = PIECE_VALUE[piece];

            // 位置价值（红方用原始坐标，黑方翻转坐标）
            int posRow = isRed ? row : 9 - row;
            int posValue = getPositionValue(piece, posRow, col);

            if (isRed) {
                score += baseValue + posValue;
            } else {
                score -= baseValue + posValue;
            }
        }

        // 机动性评估
        int mobility = evaluateMobility();
        score += mobility;

        // 王安全评估
        int kingSafety = evaluateKingSafety();
        score += kingSafety;

        return score;
    }

    private int getPositionValue(int piece, int row, int col) {
        switch (piece) {
            case R_BING: return BING_POSITION_VALUE[row][col];
            case B_BING: return BING_POSITION_VALUE[row][col];
            case R_MA: case B_MA: return MA_POSITION_VALUE[row][col];
            case R_PAO: case B_PAO: return PAO_POSITION_VALUE[row][col];
            case R_JU: case B_JU: return JU_POSITION_VALUE[row][col];
            default: return 0;
        }
    }

    /**
     * 评估机动性
     */
    private int evaluateMobility() {
        int redMobility = 0;
        int blackMobility = 0;

        // 快速机动性计算（不送将）
        for (int i = 0; i < TOTAL_SQUARES; i++) {
            int piece = mBoard[i];
            if (piece == EMPTY) continue;

            int pieceValue = PIECE_VALUE[piece];
            int mobility = estimateMobilityValue(i, piece);
            if (isRedPiece(piece)) {
                redMobility += mobility;
            } else {
                blackMobility += mobility;
            }
        }

        return (redMobility - blackMobility) / 10;
    }

    private int estimateMobilityValue(int pos, int piece) {
        int mobility = 0;
        switch (piece) {
            case R_JU: case B_JU:
                // 车的机动性：线路开放程度
                mobility = 100;
                break;
            case R_MA: case B_MA:
                // 马的机动性：可走位置数量 * 20
                int col = pos % COLS;
                int row = pos / 9;
                for (int i = 0; i < MA_MOVE_RAW.length; i++) {
                    int nc = col + MA_MOVE_RAW[i][0];
                    int nr = row + MA_MOVE_RAW[i][1];
                    int lc = col + MA_LEG_RAW[i][0];
                    int lr = row + MA_LEG_RAW[i][1];
                    if (nc >= 0 && nc < COLS && nr >= 0 && nr < ROWS
                            && mBoard[lr * COLS + lc] == EMPTY) {
                        mobility += 20;
                    }
                }
                break;
            case R_PAO: case B_PAO:
                mobility = 80;
                break;
            default:
                mobility = PIECE_VALUE[piece] / 10;
        }
        return mobility;
    }

    /**
     * 评估将帅安全性
     */
    private int evaluateKingSafety() {
        int score = 0;

        // 找红帅
        for (int i = 0; i < TOTAL_SQUARES; i++) {
            if (mBoard[i] == R_JIANG) {
                // 周围士象保护
                int col = i % COLS;
                int row = i / 9;
                int protectors = countProtectors(col, row, true);
                score += protectors * 30;
                break;
            }
        }

        // 找黑将
        for (int i = 0; i < TOTAL_SQUARES; i++) {
            if (mBoard[i] == B_JIANG) {
                int col = i % COLS;
                int row = i / 9;
                int protectors = countProtectors(col, row, false);
                score -= protectors * 30;
                break;
            }
        }

        return score;
    }

    private int countProtectors(int col, int row, boolean isRed) {
        int count = 0;
        // 检查周围8格是否有保护棋子
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                if (dr == 0 && dc == 0) continue;
                int nc = col + dc;
                int nr = row + dr;
                if (nc >= 0 && nc < COLS && nr >= 0 && nr < ROWS) {
                    int piece = mBoard[nr * COLS + nc];
                    if (piece != EMPTY && isRedPiece(piece) == isRed) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    // ========== Alpha-Beta搜索 ==========

    /**
     * 迭代加深搜索
     */
    public EngineResult searchBestMove(String fen, int maxDepth) {
        parseFEN(fen);
        return searchBestMove(maxDepth);
    }

    public EngineResult searchBestMove(int maxDepth) {
        mNodesSearched = 0;
        mStartTime = System.currentTimeMillis();
        mTimeLimitMs = 5000; // 5秒超时
        mSearchDepth = maxDepth;

        // 初始化置换表
        Arrays.fill(mTTDepth, -1);
        Arrays.fill(mKillerMoves[0], -1);
        Arrays.fill(mKillerMoves[1], -1);

        // 迭代加深
        Move bestMove = null;
        int bestScore = 0;
        String bestUCII = "--";

        for (int depth = 1; depth <= maxDepth; depth++) {
            if (System.currentTimeMillis() - mStartTime > mTimeLimitMs) break;

            SearchResult result = searchRoot(depth);
            if (result == null) break;

            bestScore = result.score;
            bestMove = result.bestMove;

            if (bestMove != null) {
                bestUCII = bestMove.toUCCINotation();
            }

            // 找到必杀棋
            if (bestScore > 9000 || bestScore < -9000) {
                break;
            }

            Log.d(TAG, String.format("深度%d完成, 最佳走法%s, 评分%d, 已搜索%d节点",
                    depth, bestUCII, bestScore, mNodesSearched));
        }

        String analysisDepth = mRedTurn ? "红方" : "黑方";
        return new EngineResult(bestUCII, bestScore,
                String.format("%s分析(深度%d): %d个节点, %d着法可走",
                        analysisDepth, mSearchDepth, mNodesSearched,
                        generateMovesSafe().size()),
                mNodesSearched);
    }

    private List<Move> generateMovesSafe() {
        try {
            return generateMoves();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private static class SearchResult {
        Move bestMove;
        int score;
        SearchResult(Move move, int score) {
            this.bestMove = move;
            this.score = score;
        }
    }

    /**
     * Root搜索 - 返回最佳走法和分数
     */
    private SearchResult searchRoot(int depth) {
        List<Move> moves = generateMovesSafe();
        if (moves.isEmpty()) return null;

        // 走法排序
        sortMoves(moves);

        Move bestMove = null;
        int bestScore = Integer.MIN_VALUE;
        List<Move> bestLine = new ArrayList<>();

        for (Move move : moves) {
            // 执行走法
            makeMove(move);

            int score = -alphaBeta(depth - 1, Integer.MIN_VALUE, Integer.MAX_VALUE, !mRedTurn);

            // 还原
            unmakeMove(move);

            if (score > bestScore) {
                bestScore = score;
                bestMove = move;
            }
        }

        return new SearchResult(bestMove, bestScore);
    }

    /**
     * Alpha-Beta搜索
     */
    private int alphaBeta(int depth, int alpha, int beta, boolean redTurn) {
        mNodesSearched++;

        // 超时检查
        if ((mNodesSearched & 1023) == 0 &&
                System.currentTimeMillis() - mStartTime > mTimeLimitMs) {
            return 0;
        }

        // 置换表查询
        long key = computeZobristKey();
        int ttIndex = (int) (key & TT_MASK);
        if (mTTDepth[ttIndex] >= depth && mTTKey[ttIndex] == key) {
            return mTTScore[ttIndex];
        }

        // 到达叶子节点
        if (depth <= 0) {
            return quiescenceSearch(alpha, beta);
        }

        // 生成走法
        mRedTurn = redTurn;
        List<Move> moves = generateMovesSafe();
        if (moves.isEmpty()) {
            // 无子可走：被将死（极劣势）或和棋
            if (isInCheck(redTurn)) {
                return -10000 + (mSearchDepth - depth); // 尽快将死
            }
            return 0; // 和棋
        }

        // 走法排序
        sortMoves(moves);

        int bestScore = Integer.MIN_VALUE;
        int bestMove = -1;
        int originalAlpha = alpha;

        for (Move move : moves) {
            makeMove(move);

            int score = -alphaBeta(depth - 1, -beta, -alpha, !redTurn);

            unmakeMove(move);

            if (score > bestScore) {
                bestScore = score;
                bestMove = move.from * TOTAL_SQUARES + move.to;
            }

            if (score > alpha) {
                alpha = score;
            }

            if (alpha >= beta) {
                // Beta截断 - 保存Killer Move
                updateKillerMoves(move, depth);
                break;
            }
        }

        // 存入置换表
        mTTKey[ttIndex] = key;
        mTTDepth[ttIndex] = depth;
        mTTScore[ttIndex] = bestScore;
        mTTMove[ttIndex] = bestMove;

        return bestScore;
    }

    /**
     * 静态搜索 - 只搜索吃子走法，避免水平线效应
     */
    private int quiescenceSearch(int alpha, int beta) {
        mNodesSearched++;

        int standPat = evaluate();

        if (standPat >= beta) return beta;
        if (standPat > alpha) alpha = standPat;

        // 只生成吃子走法
        List<Move> captures = generateCaptureMoves();
        sortMovesByMVVLVA(captures);

        for (Move move : captures) {
            makeMove(move);
            int score = -quiescenceSearch(-beta, -alpha);
            unmakeMove(move);

            if (score > alpha) {
                alpha = score;
            }
            if (alpha >= beta) break;
        }

        return alpha;
    }

    private List<Move> generateCaptureMoves() {
        List<Move> allMoves = generateMoves();
        List<Move> captures = new ArrayList<>();
        for (Move move : allMoves) {
            if (move.captured != EMPTY) {
                captures.add(move);
            }
        }
        return captures;
    }

    // ========== 走法执行与还原 ==========

    private void makeMove(Move move) {
        mBoard[move.to] = move.piece;
        mBoard[move.from] = EMPTY;
        mRedTurn = !mRedTurn;
    }

    private void unmakeMove(Move move) {
        mBoard[move.from] = move.piece;
        mBoard[move.to] = move.captured;
        mRedTurn = !mRedTurn;
    }

    // ========== 走法排序 ==========

    private void sortMoves(List<Move> moves) {
        // 评分并排序
        for (Move move : moves) {
            moveSortValue(move);
        }
        moves.sort((a, b) -> Integer.compare(b.captured == EMPTY ? PIECE_VALUE[b.captured] : PIECE_VALUE[b.captured] + 100,
                a.captured == EMPTY ? PIECE_VALUE[a.captured] : PIECE_VALUE[a.captured] + 100));
    }

    private void sortMovesByMVVLVA(List<Move> moves) {
        moves.sort((a, b) -> {
            int scoreA = PIECE_VALUE[a.captured] * 10 - PIECE_VALUE[a.piece];
            int scoreB = PIECE_VALUE[b.captured] * 10 - PIECE_VALUE[b.piece];
            return Integer.compare(scoreB, scoreA);
        });
    }

    private void moveSortValue(Move move) {
        // 只是占位，实际排序在sortMoves中
    }

    private void updateKillerMoves(Move move, int depth) {
        if (depth < mKillerMoves.length) {
            if (mKillerMoves[depth][0] != move.from * TOTAL_SQUARES + move.to) {
                mKillerMoves[depth][1] = mKillerMoves[depth][0];
                mKillerMoves[depth][0] = move.from * TOTAL_SQUARES + move.to;
            }
        }
    }

    // ========== 将军检查 ==========

    private boolean isInCheck(boolean redKing) {
        // 找到将/帅的位置
        int jiangPos = -1;
        int jiangPiece = redKing ? R_JIANG : B_JIANG;

        for (int i = 0; i < TOTAL_SQUARES; i++) {
            if (mBoard[i] == jiangPiece) {
                jiangPos = i;
                break;
            }
        }

        if (jiangPos == -1) return false; // 老将不存在

        // 临时切换视角检查
        boolean savedTurn = mRedTurn;
        mRedTurn = !redKing; // 检查敌方是否能攻击当前位置

        // 简单检查：遍历所有攻击可能性
        int col = jiangPos % COLS;
        int row = jiangPos / 9;

        boolean inCheck = false;

        // 检查车/炮的攻击（沿直线）
        inCheck = checkJiangAttackedByJuPao(jiangPos, col, row, redKing);
        if (inCheck) {
            mRedTurn = savedTurn;
            return true;
        }

        // 检查马的攻击
        inCheck = checkJiangAttackedByMa(jiangPos, col, row, redKing);
        if (inCheck) {
            mRedTurn = savedTurn;
            return true;
        }

        // 检查兵的攻击
        inCheck = checkJiangAttackedByBing(jiangPos, col, row, redKing);
        if (inCheck) {
            mRedTurn = savedTurn;
            return true;
        }

        // 将帅对脸
        inCheck = checkJiangFaceToFace(jiangPos, redKing);

        mRedTurn = savedTurn;
        return inCheck;
    }

    private boolean checkJiangAttackedByJuPao(int jiangPos, int col, int row, boolean redKing) {
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        int enemyJu = redKing ? B_JU : R_JU;
        int enemyPao = redKing ? B_PAO : R_PAO;

        for (int[] dir : dirs) {
            int nc = col + dir[0];
            int nr = row + dir[1];
            boolean paoScreen = false; // 炮架

            while (nc >= 0 && nc < COLS && nr >= 0 && nr < ROWS) {
                int piece = mBoard[nr * COLS + nc];
                if (piece != EMPTY) {
                    if (!paoScreen) {
                        if (piece == enemyJu) return true; // 被车威胁
                        paoScreen = true; // 第一个棋子作为炮架
                    } else {
                        if (piece == enemyPao) return true; // 被炮威胁
                        break; // 两个棋子都不能威胁
                    }
                }
                nc += dir[0];
                nr += dir[1];
            }
        }
        return false;
    }

    private boolean checkJiangAttackedByMa(int jiangPos, int col, int row, boolean redKing) {
        int enemyMa = redKing ? B_MA : R_MA;

        for (int i = 0; i < MA_MOVE_RAW.length; i++) {
            int cacCol = col - MA_MOVE_RAW[i][0]; // 攻击者的位置
            int cacRow = row - MA_MOVE_RAW[i][1];
            int legCol = col - MA_LEG_RAW[i][0]; // 蹩马腿的位置
            int legRow = row - MA_LEG_RAW[i][1];

            if (cacCol >= 0 && cacCol < COLS && cacRow >= 0 && cacRow < ROWS
                    && legCol >= 0 && legCol < COLS && legRow >= 0 && legRow < ROWS) {
                // 检查攻击者位置是否是敌马
                if (mBoard[cacRow * COLS + cacCol] == enemyMa) {
                    // 检查蹩马腿
                    if (mBoard[legRow * COLS + legCol] == EMPTY) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean checkJiangAttackedByBing(int jiangPos, int col, int row, boolean redKing) {
        int enemyBing = redKing ? B_BING : R_BING;
        // 敌方兵的攻击方向
        int attackDir = redKing ? 1 : -1; // 黑兵向上走，可以攻击下方

        // 前方攻击
        int frontRow = row + attackDir;
        if (frontRow >= 0 && frontRow < ROWS) {
            if (mBoard[frontRow * COLS + col] == enemyBing) return true;
        }

        // 左右攻击（过河后的兵）
        if (col > 0 && mBoard[row * COLS + col - 1] == enemyBing) return true;
        if (col < COLS - 1 && mBoard[row * COLS + col + 1] == enemyBing) return true;

        return false;
    }

    private boolean checkJiangFaceToFace(int jiangPos, boolean redKing) {
        for (int i = 0; i < TOTAL_SQUARES; i++) {
            int enemyJiang = redKing ? B_JIANG : R_JIANG;
            if (mBoard[i] == enemyJiang) {
                if (i % COLS == jiangPos % COLS) { // 同一列
                    int minR = Math.min(jiangPos / 9, i / 9) + 1;
                    int maxR = Math.max(jiangPos / 9, i / 9);
                    boolean clear = true;
                    for (int r = minR; r < maxR; r++) {
                        if (mBoard[r * COLS + (jiangPos % COLS)] != EMPTY) {
                            clear = false;
                            break;
                        }
                    }
                    if (clear) return true;
                }
            }
        }
        return false;
    }

    // ========== 辅助方法 ==========

    private boolean isRedPiece(int piece) {
        return piece >= R_JIANG && piece <= R_BING;
    }

    private boolean isEnemy(int piece1, int piece2) {
        return (isRedPiece(piece1) != isRedPiece(piece2));
    }

    /**
     * Zobrist Hashing的简化版
     */
    private long computeZobristKey() {
        long key = 0;
        int seed = 0;
        for (int i = 0; i < Math.min(TOTAL_SQUARES, 32); i++) {
            if (mBoard[i] != EMPTY) {
                key ^= ((long) (i * 31 + mBoard[i])) << (seed % 56);
                seed++;
            }
        }
        if (mRedTurn) key ^= 0x1L;
        return key;
    }

    /**
     * 生成FEN字符串
     */
    public String generateFEN() {
        if (mBoard == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int row = 0; row < ROWS; row++) {
            int emptyCount = 0;
            for (int col = 0; col < COLS; col++) {
                int piece = mBoard[row * COLS + col];
                if (piece == EMPTY) {
                    emptyCount++;
                } else {
                    if (emptyCount > 0) {
                        sb.append(emptyCount);
                        emptyCount = 0;
                    }
                    sb.append(mapPieceToChar(piece));
                }
            }
            if (emptyCount > 0) sb.append(emptyCount);
            if (row < ROWS - 1) sb.append('/');
        }
        sb.append(mRedTurn ? " w" : " b");
        return sb.toString();
    }
}
