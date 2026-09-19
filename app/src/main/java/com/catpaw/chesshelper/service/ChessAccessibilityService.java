package com.catpaw.chesshelper.service;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.catpaw.chesshelper.ai.ChessEngine;
import com.catpaw.chesshelper.ai.CloudAIProvider;
import com.catpaw.chesshelper.config.AppConfig;
import com.catpaw.chesshelper.model.BoardRecognitionResult;
import com.catpaw.chesshelper.model.ChessPiece;
import com.catpaw.chesshelper.model.FENGenerator;
import com.catpaw.chesshelper.util.ImageProcessor;

import java.util.ArrayList;
import java.util.List;

/**
 * 象棋识别无障碍服务
 *
 * 核心功能：
 * 1. 监听屏幕变化事件，检测象棋棋盘
 * 2. 通过截屏获取棋盘图像
 * 3. 使用图像识别解析棋子位置
 * 4. 生成FEN格式的棋局字符串
 * 5. 将棋局信息传递给AI引擎计算最佳走法
 *
 * 识别策略：
 * - 优先通过视图文本（如"中国象棋"、棋子名称"车马炮兵"等）快速判断象棋应用
 * - 通过悬浮窗触发手动/自动截屏识别区域
 * - 使用模板匹配识别棋子（支持多种皮肤）
 */
public class ChessAccessibilityService extends AccessibilityService implements ScreenCaptureService.CaptureCallback {

    private static final String TAG = "ChessAccService";
    private static final long RECORD_INTERVAL_MS = 1500; // 识别间隔

    private volatile boolean mRecognizing = false;
    private volatile boolean mCapturing = false;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private ScreenCaptureService mScreenCaptureService;

    // 识别状态
    private Rect mBoardRect = null; // 棋盘区域（需要在设置中配置或手动选择）
    private Bitmap mLastBoardBitmap = null;
    private String mCurrentFEN = "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR%20w"; // 默认开局
    private int mMoveCount = 0;
    private boolean mIsRedTurn = true;

    // AI引擎
    private ChessEngine mLocalEngine;
    private CloudAIProvider mCloudProvider;
    private AppConfig mConfig;

    // 识别回调
    public interface RecognitionCallback {
        void onBoardRecognized(BoardRecognitionResult result);
        void onMoveCalculated(String bestMove, int score, String analysis);
        void onError(String error);
    }

    private static RecognitionCallback sCallback;

    public static void setRecognitionCallback(RecognitionCallback callback) {
        sCallback = callback;
    }

    public static ChessAccessibilityService sInstance;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        sInstance = this;
        mConfig = AppConfig.getInstance(this);
        mLocalEngine = new ChessEngine();
        mCloudProvider = new CloudAIProvider(mConfig);
        Log.i(TAG, "象棋助手无障碍服务已连接");

        // 启动截屏服务
        Intent captureIntent = new Intent(this, ScreenCaptureService.class);
        startService(captureIntent);

        // 根据配置启动自动识别
        if (mConfig.isAutoRecognitionEnabled()) {
            startAutoRecognition();
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!mConfig.isServiceEnabled()) return;

        int eventType = event.getEventType();
        switch (eventType) {
            case AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED:
                if (shouldRecognize()) {
                    mHandler.postDelayed(this::performRecognition, RECORD_INTERVAL_MS);
                }
                break;

            case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
                // 检测是否进入了象棋应用
                CharSequence className = event.getClassName();
                if (className != null) {
                    String pkg = className.toString();
                    if (isChessApp(pkg)) {
                        Log.i(TAG, "检测到象棋应用: " + pkg);
                    }
                }
                break;

            case AccessibilityEvent.TYPE_VIEW_SCROLLED:
                // 检测走棋后界面的变化
                if (mConfig.isServiceEnabled() && !mCapturing) {
                    mHandler.postDelayed(this::checkForBoardChanges, 500);
                }
                break;

            default:
                break;
        }
    }

    @Override
    public void onInterrupt() {
        Log.w(TAG, "无障碍服务被中断");
        stopAutoRecognition();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopAutoRecognition();
        if (mScreenCaptureService != null) {
            mScreenCaptureService.stopCapture();
        }
        sInstance = null;
    }

    // ==================== 核心识别逻辑 ====================

    /**
     * 执行一次完整的棋盘识别流程
     */
    private void performRecognition() {
        if (mRecognizing || mCapturing) return;
        mRecognizing = true;

        // 1. 使用MediaProjection截屏
        captureScreenAndRecognize();
    }

    /**
     * 截屏并进行棋盘识别
     */
    private void captureScreenAndRecognize() {
        mCapturing = true;

        // 获取ScreenCaptureService实例进行截屏
        if (mScreenCaptureService == null) {
            mScreenCaptureService = ScreenCaptureService.getInstance();
        }

        if (mScreenCaptureService != null) {
            mScreenCaptureService.captureScreen(this);
        } else {
            mCapturing = false;
            mRecognizing = false;
            notifyError("截屏服务未就绪");
        }
    }

    @Override
    public void onScreenCaptured(Bitmap screenshot) {
        mCapturing = false;

        if (screenshot == null) {
            mRecognizing = false;
            notifyError("截屏失败");
            return;
        }

        // 在后台线程处理图像识别
        new Thread(() -> {
            try {
                // 裁剪棋盘区域
                Bitmap boardBitmap = cropBoardRegion(screenshot);

                if (boardBitmap == null) {
                    notifyError("未检测到棋盘区域");
                    mRecognizing = false;
                    return;
                }

                // 识别棋子
                BoardRecognitionResult result = ImageProcessor.recognizeBoard(boardBitmap);

                if (result != null) {
                    // 生成FEN
                    String newFEN = FENGenerator.generateFEN(result.getBoard());

                    // 检测是否有棋步变化
                    if (!newFEN.equals(mCurrentFEN)) {
                        mCurrentFEN = newFEN;
                        mMoveCount++;
                        mIsRedTurn = result.isRedTurn();
                        Log.i(TAG, "检测到棋步变化，FEN: " + mCurrentFEN);

                        // 通知UI更新
                        notifyBoardChange(result);

                        // 调用AI计算最佳走法
                        calculateBestMove(newFEN, result.isRedTurn());
                    }
                }

                // 保存上一次截图用于对比
                mLastBoardBitmap = boardBitmap;

            } catch (Exception e) {
                Log.e(TAG, "识别失败", e);
                notifyError("识别失败: " + e.getMessage());
            } finally {
                mRecognizing = false;
            }
        }).start();
    }

    @Override
    public void onCaptureFailed(String reason) {
        mCapturing = false;
        mRecognizing = false;
        notifyError("截屏失败: " + reason);
    }

    /**
     * 裁剪棋盘区域
     */
    @Nullable
    private Bitmap cropBoardRegion(Bitmap fullScreen) {
        if (mConfig.useManualBoardRect() && mBoardRect != null) {
            // 使用手动设置的区域
            try {
                return Bitmap.createBitmap(fullScreen, mBoardRect.left, mBoardRect.top,
                        mBoardRect.width(), mBoardRect.height());
            } catch (Exception e) {
                Log.e(TAG, "裁剪棋盘区域失败", e);
            }
        }

        // 自动检测棋盘区域（基于常见象棋APP的布局特征）
        return autoDetectBoardRegion(fullScreen);
    }

    /**
     * 自动检测棋盘区域
     * 通过颜色特征（棋盘格子的红黑/棕黄色调）检测
     */
    @Nullable
    private Bitmap autoDetectBoardRegion(Bitmap screenshot) {
        int width = screenshot.getWidth();
        int height = screenshot.getHeight();

        // 棋盘通常在屏幕上半部分
        int searchTop = height / 10;
        int searchBottom = height * 3 / 4;

        // 计算每一行的"棋盘特征分数"
        int maxScore = 0;
        int bestTop = searchTop;
        int bestBottom = searchBottom;

        for (int y = searchTop; y < searchBottom; y += 10) {
            int score = calculateBoardScore(screenshot, y, width);
            if (score > maxScore) {
                maxScore = score;
                bestTop = y;
            }
        }

        if (maxScore < 100) return null; // 没找到棋盘

        // 确定棋盘的高度和宽度
        int boardHeight = (int) ((searchBottom - bestTop) * 0.65);
        int boardWidth = (int) (boardHeight * 0.9); // 象棋棋盘比例约9:10

        int centerX = width / 2;
        int left = Math.max(0, centerX - boardWidth / 2);
        int right = Math.min(width, centerX + boardWidth / 2);

        try {
            return Bitmap.createBitmap(screenshot, left, bestTop, right - left, boardHeight);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 计算某一行是否是棋盘的一部分
     * 基于棋盘格子的颜色特征
     */
    private int calculateBoardScore(Bitmap bitmap, int y, int width) {
        int score = 0;
        for (int x = 0; x < width; x += 5) {
            int pixel = bitmap.getPixel(x, y);
            int r = (pixel >> 16) & 0xFF;
            int g = (pixel >> 8) & 0xFF;
            int b = pixel & 0xFF;

            // 检测棋盘常见的颜色（米黄色、棕色格子、绿色棋盘等）
            if ((r > 180 && g > 150 && b < 120) || // 米黄
                (r > 100 && g > 80 && b < 60) ||    // 棕色格子
                (r < 100 && g > 100 && b < 100)) {   // 绿色棋盘
                score++;
            }
        }
        return score;
    }

    /**
     * 调用AI计算最佳走法
     */
    private void calculateBestMove(String fen, boolean isRedTurn) {
        AppConfig.AIProvider provider = mConfig.getAIProvider();

        switch (provider) {
            case LOCAL:
                calculateByLocalEngine(fen, isRedTurn);
                break;

            case OPENAI:
            case CUSTOM_API:
                calculateByCloudAI(fen, isRedTurn);
                break;

            case AUTO:

                // 优先本地，超时则云端
                calculateByLocalEngine(fen, isRedTurn);
                mHandler.postDelayed(() -> {
                    if (sCallback != null) {
                        calculateByCloudAI(fen, isRedTurn);
                    }
                }, 3000);
                break;
        }
    }

    /**
     * 本地引擎计算
     */
    private void calculateByLocalEngine(String fen, boolean isRedTurn) {
        new Thread(() -> {
            try {
                ChessEngine.EngineResult result = mLocalEngine.searchBestMove(fen, mConfig.getEngineDepth());
                if (result != null && sCallback != null) {
                    mHandler.post(() -> sCallback.onMoveCalculated(
                            result.bestMove, result.score, result.analysis));
                }
            } catch (Exception e) {
                Log.e(TAG, "本地引擎计算失败", e);
            }
        }).start();
    }

    /**
     * 云端AI计算
     */
    private void calculateByCloudAI(String fen, boolean isRedTurn) {
        new Thread(() -> {
            try {
                String response = mCloudProvider.getBestMove(fen, isRedTurn);
                if (response != null && sCallback != null) {
                    mHandler.post(() -> sCallback.onMoveCalculated(
                            response, 0, "云端AI建议走法"));
                }
            } catch (Exception e) {
                Log.e(TAG, "云端AI调用失败", e);
                mHandler.post(() -> notifyError("云端AI调用失败: " + e.getMessage()));
            }
        }).start();
    }

    // ==================== 棋步检测 ====================

    private void checkForBoardChanges() {
        // 通过比较前后截图的差异检测走棋
        if (mLastBoardBitmap != null) {
            captureScreenAndRecognize();
        }
    }

    private boolean shouldRecognize() {
        return mConfig.isServiceEnabled() && !mRecognizing && !mCapturing;
    }

    private boolean isChessApp(String packageName) {
        return packageName.contains("chess") ||
               packageName.contains("xiangqi") ||
               packageName.contains("中国象棋") ||
               packageName.contains("qqgame"); // QQ象棋等
    }

    // ==================== 通知回调 ====================

    private void notifyBoardChange(BoardRecognitionResult result) {
        if (sCallback != null) {
            mHandler.post(() -> sCallback.onBoardRecognized(result));
        }
        // 更新悬浮窗
        FloatingWindowService.updateBoardState(mCurrentFEN, mMoveCount, mIsRedTurn);
    }

    private void notifyError(String error) {
        if (sCallback != null) {
            mHandler.post(() -> sCallback.onError(error));
        }
    }

    // ==================== 自动识别控制 ====================

    private Runnable mAutoRecognitionRunnable;

    public void startAutoRecognition() {
        if (mAutoRecognitionRunnable != null) return;
        mAutoRecognitionRunnable = new Runnable() {
            @Override
            public void run() {
                if (mConfig.isServiceEnabled()) {
                    performRecognition();
                }
                mHandler.postDelayed(this, mConfig.getRecognitionInterval());
            }
        };
        mHandler.postDelayed(mAutoRecognitionRunnable, 2000);
        Log.i(TAG, "自动识别已启动");
    }

    public void stopAutoRecognition() {
        if (mAutoRecognitionRunnable != null) {
            mHandler.removeCallbacks(mAutoRecognitionRunnable);
            mAutoRecognitionRunnable = null;
        }
        Log.i(TAG, "自动识别已停止");
    }

    // ==================== 公共方法 ====================

    public String getCurrentFEN() {
        return mCurrentFEN;
    }

    public int getMoveCount() {
        return mMoveCount;
    }

    public boolean isRedTurn() {
        return mIsRedTurn;
    }

    public void setBoardRect(Rect rect) {
        mBoardRect = rect;
    }

    /**
     * 尝试通过无障碍API获取棋子位置
     * 某些象棋APP的视图结构可以直接获取棋子
     */
    public List<ChessPiece> getPiecesFromAccessibility() {
        List<ChessPiece> pieces = new ArrayList<>();
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null) return pieces;

        try {
            findChessPieces(rootNode, pieces);
        } finally {
            rootNode.recycle();
        }

        return pieces;
    }

    /**
     * 递归搜索棋子节点
     * 根据contentDescription或text识别棋子
     */
    private void findChessPieces(AccessibilityNodeInfo node, List<ChessPiece> pieces) {
        if (node == null) return;

        CharSequence desc = node.getContentDescription();
        CharSequence text = node.getText();

        // 解析棋子描述
        if (desc != null) {
            String descStr = desc.toString();
            ChessPiece piece = parsePieceFromDescription(descStr);
            if (piece != null) {
                Rect bounds = new Rect();
                node.getBoundsInScreen(bounds);
                piece.setBounds(bounds);
                pieces.add(piece);
            }
        }

        // 递归子节点
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                findChessPieces(child, pieces);
                child.recycle();
            }
        }
    }

    /**
     * 从描述中解析棋子
     * 格式如 "红车"、"黑炮"
     */
    @Nullable
    private ChessPiece parsePieceFromDescription(String desc) {
        if (desc == null || desc.length() < 2) return null;

        boolean isRed = desc.startsWith("红") || desc.startsWith("红方");
        String pieceName = isRed ? desc.substring(1) : (desc.startsWith("黑") ? desc.substring(1) : desc);

        int pieceType = mapPieceNameToType(pieceName);
        if (pieceType == -1) return null;

        return new ChessPiece(pieceType, isRed);
    }

    /**
     * 将中文棋子名映射为内部类型
     */
    private int mapPieceNameToType(String name) {
        switch (name) {
            case "車": case "车": case "俥": return ChessPiece.JU;
            case "馬": case "马": case "傌": return ChessPiece.MA;
            case "相": case "象": return ChessPiece.XIANG;
            case "仕": case "士": return ChessPiece.SHI;
            case "帥": case "帅": case "将": case "將": return ChessPiece.JIANG;
            case "炮": case "砲": case "包": return ChessPiece.PAO;
            case "兵": case "卒": case "兵卒": return ChessPiece.BING;
            default: return -1;
        }
    }
}
