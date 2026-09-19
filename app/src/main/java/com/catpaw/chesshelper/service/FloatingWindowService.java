package com.catpaw.chesshelper.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.catpaw.chesshelper.R;
import com.catpaw.chesshelper.ui.MainActivity;

/**
 * 悬浮窗服务
 *
 * 功能：
 * 1. 实时显示当前棋局步数
 * 2. 显示AI建议的最佳走法
 * 3. 显示当前轮到哪方走棋
 * 4. 显示引擎分析的评分
 * 5. 可拖拽移动位置
 */
public class FloatingWindowService extends Service {

    private static final String TAG = "FloatingWindow";
    private static final String CHANNEL_ID = "floating_window_channel";
    private static final int NOTIFICATION_ID = 1002;

    private WindowManager mWindowManager;
    private View mFloatingView;
    private WindowManager.LayoutParams mParams;

    // UI组件
    private TextView mTvMoveCount;
    private TextView mTvCurrentTurn;
    private TextView mTvBestMove;
    private TextView mTvScore;
    private TextView mTvFEN;
    private LinearLayout mMainContainer;

    // 拖拽相关
    private float mTouchStartX;
    private float mTouchStartY;
    private float mStartX;
    private float mStartY;
    private boolean mDragging = false;

    // 数据
    private static String sCurrentFEN = "";
    private static int sMoveCount = 0;
    private static boolean sIsRedTurn = true;
    private static String sBestMove = "--";
    private static int sScore = 0;

    public interface OnBestMoveClickListener {
        void onBestMoveClicked(String move);
    }

    private static OnBestMoveClickListener sMoveClickListener;
    public static FloatingWindowService sInstance;

    public static void setOnBestMoveClickListener(OnBestMoveClickListener listener) {
        sMoveClickListener = listener;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        sInstance = this;
        mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        createNotificationChannel();
        showFloatingWindow();
        Log.i(TAG, "悬浮窗服务已启动");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mFloatingView != null && mFloatingView.isAttachedToWindow()) {
            mWindowManager.removeView(mFloatingView);
        }
        sInstance = null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, createNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, createNotification());
        }
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "象棋悬浮窗", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("实时显示象棋AI分析结果");
            channel.setShowBadge(false);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }
    }

    private Notification createNotification() {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("象棋AI辅助")
                .setContentText("正在分析棋局...")
                .setSmallIcon(R.drawable.ic_chess)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setSilent(true)
                .build();
    }

    /**
     * 创建并显示悬浮窗
     */
    private void showFloatingWindow() {
        if (mFloatingView != null) return;

        mFloatingView = View.inflate(this, R.layout.floating_chess_helper, null);

        // 初始化UI组件
        mMainContainer = mFloatingView.findViewById(R.id.fl_container);
        mTvMoveCount = mFloatingView.findViewById(R.id.tv_move_count);
        mTvCurrentTurn = mFloatingView.findViewById(R.id.tv_current_turn);
        mTvBestMove = mFloatingView.findViewById(R.id.tv_best_move);
        mTvScore = mFloatingView.findViewById(R.id.tv_score);
        mTvFEN = mFloatingView.findViewById(R.id.tv_fen);

        // 设置悬浮窗参数
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        mParams = new WindowManager.LayoutParams(
                dpToPx(200), WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);

        mParams.gravity = Gravity.TOP | Gravity.START;
        mParams.x = 50;
        mParams.y = 200;

        mWindowManager.addView(mFloatingView, mParams);

        // 设置拖拽监听
        setupTouchListener();

        // 点击最佳走法可以展开更多细节
        mTvBestMove.setOnClickListener(v -> {
            if (sMoveClickListener != null && !sBestMove.equals("--")) {
                sMoveClickListener.onBestMoveClicked(sBestMove);
            }
        });

        // 最小化/展开按钮
        View minimizeBtn = mFloatingView.findViewById(R.id.btn_minimize);
        if (minimizeBtn != null) {
            minimizeBtn.setOnClickListener(v -> toggleMinimize());
        }

        // 同步当前数据
        updateDisplay();
    }

    /**
     * 设置触摸拖拽
     */
    private void setupTouchListener() {
        mFloatingView.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    mTouchStartX = event.getRawX();
                    mTouchStartY = event.getRawY();
                    mStartX = mParams.x;
                    mStartY = mParams.y;
                    mDragging = false;
                    return true;

                case MotionEvent.ACTION_MOVE:
                    float dx = event.getRawX() - mTouchStartX;
                    float dy = event.getRawY() - mTouchStartY;
                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                        mDragging = true;
                        mParams.x = (int) (mStartX + dx);
                        mParams.y = (int) (mStartY + dy);
                        mWindowManager.updateViewLayout(mFloatingView, mParams);
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                    if (!mDragging) {
                        v.performClick();
                    }
                    return true;
            }
            return false;
        });
    }

    /**
     * 最小化/展开切换
     */
    private void toggleMinimize() {
        // 切换显示详细信息
        View detailPanel = mFloatingView.findViewById(R.id.panel_detail);
        if (detailPanel != null) {
            int visibility = detailPanel.getVisibility() == View.VISIBLE
                    ? View.GONE : View.VISIBLE;
            detailPanel.setVisibility(visibility);
        }
    }

    /**
     * 更新显示数据
     */
    private void updateDisplay() {
        if (mTvMoveCount != null) {
            mTvMoveCount.setText(String.format("步数: %d", sMoveCount));
        }
        if (mTvCurrentTurn != null) {
            mTvCurrentTurn.setText(sIsRedTurn ? "红方走棋" : "黑方走棋");
            mTvCurrentTurn.setTextColor(sIsRedTurn ? Color.parseColor("#D32F2F") : Color.parseColor("#333333"));
        }
        if (mTvBestMove != null) {
            mTvBestMove.setText("建议: " + sBestMove);
        }
        if (mTvScore != null) {
            String scoreStr = sScore > 0 ? "+" + sScore : String.valueOf(sScore);
            mTvScore.setText("评分: " + scoreStr);
            mTvScore.setTextColor(sScore >= 0 ? Color.parseColor("#1976D2") : Color.parseColor("#D32F2F"));
        }
        if (mTvFEN != null) {
            // 截断FEN避免显示过长
            String displayFEN = sCurrentFEN.length() > 30
                    ? sCurrentFEN.substring(0, 30) + "..." : sCurrentFEN;
            mTvFEN.setText(displayFEN);
        }
    }

    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }

    // ==================== 静态更新方法 ====================

    /**
     * 更新棋盘状态（从AccessibilityService调用）
     */
    public static void updateBoardState(String fen, int moveCount, boolean isRedTurn) {
        sCurrentFEN = fen;
        sMoveCount = moveCount;
        sIsRedTurn = isRedTurn;

        FloatingWindowService instance = sInstance;
        if (instance != null) {
            new Handler(Looper.getMainLooper()).post(instance::updateDisplay);
        }
    }

    /**
     * 更新AI分析结果
     */
    public static void updateAnalysisResult(String bestMove, int score) {
        sBestMove = bestMove;
        sScore = score;

        FloatingWindowService instance = sInstance;
        if (instance != null) {
            new Handler(Looper.getMainLooper()).post(instance::updateDisplay);
        }
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        stopSelf();
    }

    public static void startFloating(Context context) {
        Intent intent = new Intent(context, FloatingWindowService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    public static void stopFloating(Context context) {
        Intent intent = new Intent(context, FloatingWindowService.class);
        context.stopService(intent);
    }
}
