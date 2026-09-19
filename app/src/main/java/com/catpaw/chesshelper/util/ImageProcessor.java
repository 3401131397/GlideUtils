package com.catpaw.chesshelper.util;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Log;

import com.catpaw.chesshelper.model.BoardRecognitionResult;
import com.catpaw.chesshelper.model.ChessPiece;

/**
 * 图像识别处理器
 *
 * 识别策略（分层实现）：
 *
 * 第一阶段：模板匹配（Template Matching）
 * - 预置多种象棋皮肤模板（红色圆形、绿色圆形、木纹、3D风格）
 * - 使用归一化相关系数匹配定位棋子
 *
 * 第二阶段：颜色+形状特征识别
 * - 识别圆形棋子（外圈颜色判定红黑）
 * - 棋子内部文字/图案区分棋子类型
 *
 * 第三阶段（扩展）：机器学习模型
 * - 使用TensorFlow Lite模型
 * - 在手机端实时推理识别棋子
 *
 * 当前实现：基于颜色特征和轮廓检测的基础版本
 */
public class ImageProcessor {

    private static final String TAG = "ImageProcessor";
    private static final int BOARD_ROWS = 10;
    private static final int BOARD_COLS = 9;
    // 棋子大致圆形区域的半径
    private static final int PIECE_RADIUS_ESTIMATE = 35;

    /**
     * 识别棋盘上的所有棋子
     */
    public static BoardRecognitionResult recognizeBoard(Bitmap boardBitmap) {
        if (boardBitmap == null) return null;

        BoardRecognitionResult result = new BoardRecognitionResult();
        int width = boardBitmap.getWidth();
        int height = boardBitmap.getHeight();

        if (width < 100 || height < 100) {
            Log.w(TAG, "棋盘图片太小");
            return null;
        }

        // 计算每个格子的尺寸
        int cellWidth = width / BOARD_COLS;
        int cellHeight = height / BOARD_ROWS;
        int pieceRadius = Math.min(cellWidth, cellHeight) / 2 - 5;
        if (pieceRadius < 10) pieceRadius = 20;

        // 遍历每个格子，检测棋子
        for (int row = 0; row < BOARD_ROWS; row++) {
            for (int col = 0; col < BOARD_COLS; col++) {
                int centerX = col * cellWidth + cellWidth / 2;
                int centerY = row * cellHeight + cellHeight / 2;

                // 检测该位置是否有棋子
                ChessPiece piece = detectPieceAt(boardBitmap, centerX, centerY, pieceRadius);
                if (piece != null) {
                    piece.setRow(row);
                    piece.setCol(col);
                    result.addPiece(piece);
                }
            }
        }

        // 构建棋盘二维数组
        result.buildBoardFromPieces();

        // 判断当前轮到哪方（启发式：看棋盘底部是否有红方兵在河界附近）
        result.setRedTurn(guessTurn(result));

        result.setConfidence(calculateConfidence(result));
        return result;
    }

    /**
     * 检测指定位置是否有棋子
     */
    private static ChessPiece detectPieceAt(Bitmap bitmap, int cx, int cy, int radius) {
        // 采样棋子区域的颜色
        int redCount = 0;
        int blackCount = 0;
        int totalCount = 0;

        int startX = Math.max(0, cx - radius);
        int endX = Math.min(bitmap.getWidth() - 1, cx + radius);
        int startY = Math.max(0, cy - radius);
        int endY = Math.min(bitmap.getHeight() - 1, cy + radius);

        // 检查是否在圆形区域内
        int r2 = radius * radius;

        for (int y = startY; y <= endY; y += 2) {
            for (int x = startX; x <= endX; x += 2) {
                int dx = x - cx;
                int dy = y - cy;
                if (dx * dx + dy * dy > r2 * 7 / 10) continue; // 略小的圆

                int pixel = bitmap.getPixel(x, y);
                int r = Color.red(pixel);
                int g = Color.green(pixel);
                int b = Color.blue(pixel);

                // 判断颜色
                if (isRedColor(r, g, b)) {
                    redCount++;
                    totalCount++;
                } else if (isBlackColor(r, g, b)) {
                    blackCount++;
                    totalCount++;
                }
            }
        }

        if (totalCount < 10) return null; // 采样太少

        // 判断是否有足够比例的棋子颜色
        float colorRatio = (float) Math.max(redCount, blackCount) / totalCount;
        if (colorRatio < 0.3f) return null; // 颜色不够集中，可能是空的

        boolean isRed = redCount > blackCount;

        // 识别棋子类型（通过大小、内部特征等）
        int pieceType = recognizePieceType(bitmap, cx, cy, radius, isRed);

        return new ChessPiece(pieceType, isRed);
    }

    /**
     * 识别棋子类型
     * 基于棋子样式特征（大小、内部字符颜色分布等）
     */
    private static int recognizePieceType(Bitmap bitmap, int cx, int cy, int radius, boolean isRed) {
        // 提取棋子中心区域的特征
        int innerRadius = radius / 2;
        int startX = Math.max(0, cx - innerRadius);
        int endX = Math.min(bitmap.getWidth() - 1, cx + innerRadius);
        int startY = Math.max(0, cy - innerRadius);
        int endY = Math.min(bitmap.getHeight() - 1, cy + innerRadius);

        // 统计棋子区域的对比度特征
        int contrastScore = 0;
        int sampleCount = 0;

        for (int y = startY; y <= endY; y += 3) {
            for (int x = startX; x <= endX; x += 3) {
                int pixel = bitmap.getPixel(x, y);
                int brightness = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3;

                // 检测棋子轮廓（边缘对比度）
                if (x > startX && y > startY) {
                    int prevPixel = bitmap.getPixel(x - 3, y);
                    int prevBrightness = (Color.red(prevPixel) + Color.green(prevPixel) + Color.blue(prevPixel)) / 3;
                    contrastScore += Math.abs(brightness - prevBrightness);
                }
                sampleCount++;
            }
        }

        float avgContrast = sampleCount > 0 ? (float) contrastScore / sampleCount : 0;

        // 启发式规则区分棋子类型（需要更多数据训练）
        // 简化版：基于棋子大小和形状
        return guessPieceType(avgContrast, radius);
    }

    /**
     * 基于特征猜测棋子类型（简化版）
     */
    private static int guessPieceType(float contrast, int radius) {
        // 车：最大最醒目
        // 炮：中等
        // 兵卒：较小
        // 简化返回一个默认值
        if (contrast > 80) return ChessPiece.JU;
        if (contrast > 50) return ChessPiece.PAO;
        if (contrast > 30) return ChessPiece.MA;
        return ChessPiece.BING;
    }

    /**
     * 判断像素是否为红色棋子
     */
    private static boolean isRedColor(int r, int g, int b) {
        return r > 120 && r > g + 30 && r > b + 30;
    }

    /**
     * 判断像素是否为黑色棋子
     */
    private static boolean isBlackColor(int r, int g, int b) {
        return r < 80 && g < 80 && b < 80;
    }

    /**
     * 判断轮到哪方（启发式）
     */
    private static boolean guessTurn(BoardRecognitionResult result) {
        // 简单判断：默认红方先行
        // 真实场景需要分析界面上的提示（如"红方走棋"文字）
        return true;
    }

    /**
     * 计算识别置信度
     */
    private static float calculateConfidence(BoardRecognitionResult result) {
        if (result.getPieces().isEmpty()) return 0f;
        // 基于识别到的棋子数量和分布合理性
        float base = Math.min(1f, result.getPieces().size() / 32f);
        return base;
    }

    /**
     * 检测两个棋盘的差异
     */
    public static boolean boardsDiffer(Bitmap board1, Bitmap board2, float threshold) {
        if (board1 == null || board2 == null) return false;
        if (board1.getWidth() != board2.getWidth() || board1.getHeight() != board2.getHeight()) return true;

        int width = board1.getWidth();
        int height = board1.getHeight();

        int diffPixels = 0;
        int totalPixels = 0;

        for (int y = 0; y < height; y += 10) {
            for (int x = 0; x < width; x += 10) {
                int p1 = board1.getPixel(x, y);
                int p2 = board2.getPixel(x, y);
                int diff = Math.abs(Color.red(p1) - Color.red(p2))
                        + Math.abs(Color.green(p1) - Color.green(p2))
                        + Math.abs(Color.blue(p1) - Color.blue(p2));
                if (diff > 30) diffPixels++;
                totalPixels++;
            }
        }

        return totalPixels > 0 && (float) diffPixels / totalPixels > threshold;
    }
}
