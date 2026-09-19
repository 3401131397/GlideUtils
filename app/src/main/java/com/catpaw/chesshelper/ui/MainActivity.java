package com.catpaw.chesshelper.ui;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.catpaw.chesshelper.R;
import com.catpaw.chesshelper.service.ChessAccessibilityService;
import com.catpaw.chesshelper.service.FloatingWindowService;
import com.catpaw.chesshelper.service.ScreenCaptureService;

/**
 * 主界面Activity
 * 负责权限申请、服务控制、状态显示
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int REQUEST_MEDIA_PROJECTION = 1001;

    private Button mBtnToggleService;
    private Button mBtnToggleFloating;
    private Button mBtnSettings;
    private Button mBtnTestEngine;
    private TextView mTvStatus;
    private TextView mTvFEN;
    private TextView mTvBestMove;
    private TextView mTvMoveCount;
    private TextView mTvAnalysis;
    private TextView mTvScore;

    private final ActivityResultLauncher<Intent> mProjectionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                            ScreenCaptureService service = ScreenCaptureService.getInstance();
                            if (service != null) {
                                service.initProjection(result.getResultCode(), result.getData());
                            }
                            Toast.makeText(this, "截屏授权成功", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(this, "截屏授权被拒绝", Toast.LENGTH_SHORT).show();
                        }
                    });

    private final BroadcastReceiver mProjectionRequestReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            requestScreenCapture();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        checkPermissions();
        updateUI();

        // 注册广播接收器
        registerReceiver(mProjectionRequestReceiver,
                new IntentFilter("com.catpaw.chesshelper.REQUEST_PROJECTION"));

        // 注册识别回调
        ChessAccessibilityService.setRecognitionCallback(
                new ChessAccessibilityService.RecognitionCallback() {
                    @Override
                    public void onBoardRecognized(
                            com.catpaw.chesshelper.model.BoardRecognitionResult result) {
                        runOnUiThread(() -> {
                            mTvFEN.setText(ChessAccessibilityService.sInstance != null
                                    ? ChessAccessibilityService.sInstance.getCurrentFEN() : "");
                            mTvMoveCount.setText(String.valueOf(
                                    ChessAccessibilityService.sInstance != null
                                            ? ChessAccessibilityService.sInstance.getMoveCount() : 0));
                        });
                    }

                    @Override
                    public void onMoveCalculated(String bestMove, int score, String analysis) {
                        runOnUiThread(() -> {
                            mTvBestMove.setText(bestMove);
                            mTvAnalysis.setText(analysis);
                            FloatingWindowService.updateAnalysisResult(bestMove, score);
                        });
                    }

                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> mTvStatus.setText("错误: " + error));
                    }
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(mProjectionRequestReceiver);
        } catch (Exception ignored) {
        }
    }

    private void initViews() {
        mBtnToggleService = findViewById(R.id.btn_toggle_service);
        mBtnToggleFloating = findViewById(R.id.btn_toggle_floating);
        mBtnSettings = findViewById(R.id.btn_settings);
        mBtnTestEngine = findViewById(R.id.btn_test_engine);
        mTvStatus = findViewById(R.id.tv_status);
        mTvFEN = findViewById(R.id.tv_fen);
        mTvBestMove = findViewById(R.id.tv_best_move);
        mTvMoveCount = findViewById(R.id.tv_move_count);
        mTvAnalysis = findViewById(R.id.tv_analysis);
        mTvScore = findViewById(R.id.tv_score);

        mBtnToggleService.setOnClickListener(v -> toggleService());
        mBtnToggleFloating.setOnClickListener(v -> toggleFloating());
        mBtnSettings.setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));
        mBtnTestEngine.setOnClickListener(v -> testEngine());
    }

    private void checkPermissions() {
        // 检查悬浮窗权限
        if (!Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
            Toast.makeText(this, "请授予悬浮窗权限", Toast.LENGTH_LONG).show();
        }
    }

    private void toggleService() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请先授予悬浮窗权限", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!isAccessibilityServiceEnabled()) {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
            Toast.makeText(this, "请在无障碍设置中启用象棋助手服务", Toast.LENGTH_LONG).show();
            return;
        }

        // 启动截屏服务
        Intent captureIntent = new Intent(this, ScreenCaptureService.class);
        startService(captureIntent);

        // 请求截屏权限
        requestScreenCapture();
    }

    private void toggleFloating() {
        if (!Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
            return;
        }

        Intent intent = new Intent(this, FloatingWindowService.class);
        if (FloatingWindowService.sInstance != null) {
            stopService(intent);
            mBtnToggleFloating.setText("开启悬浮窗");
            Toast.makeText(this, "悬浮窗已关闭", Toast.LENGTH_SHORT).show();
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
            mBtnToggleFloating.setText("关闭悬浮窗");
            Toast.makeText(this, "悬浮窗已开启", Toast.LENGTH_SHORT).show();
        }
    }

    private void requestScreenCapture() {
        MediaProjectionManager projectionManager =
                (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        if (projectionManager != null) {
            Intent intent = projectionManager.createScreenCaptureIntent();
            mProjectionLauncher.launch(intent);
        }
    }

    private boolean isAccessibilityServiceEnabled() {
        String serviceId = getPackageName() + "/" + ChessAccessibilityService.class.getName();
        int accessibilityEnabled = 0;
        try {
            accessibilityEnabled = Settings.Secure.getInt(
                    getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED);
        } catch (Settings.SettingNotFoundException e) {
            Log.e(TAG, "无障碍设置未找到", e);
        }

        if (accessibilityEnabled != 1) return false;

        String settingValue = Settings.Secure.getString(
                getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return settingValue != null && settingValue.contains(serviceId);
    }

    private void updateUI() {
        boolean isServiceOn = ChessAccessibilityService.sInstance != null;
        boolean isFloatingOn = FloatingWindowService.sInstance != null;

        mBtnToggleService.setText(isServiceOn ? "关闭服务" : "启动服务");
        mBtnToggleFloating.setText(isFloatingOn ? "关闭悬浮窗" : "开启悬浮窗");

        mTvStatus.setText(isServiceOn ? "服务运行中" : "服务未启动");
    }

    private void testEngine() {
        // 测试本地引擎
        new Thread(() -> {
            try {
                com.catpaw.chesshelper.ai.ChessEngine engine = new com.catpaw.chesshelper.ai.ChessEngine();
                com.catpaw.chesshelper.ai.ChessEngine.EngineResult result =
                        engine.searchBestMove(
                                "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR%20w", 4);

                runOnUiThread(() -> {
                    mTvBestMove.setText("测试: " + result.bestMove);
                    mTvFEN.setText(engine.generateFEN());
                    mTvAnalysis.setText(result.analysis);
                    mTvScore.setText("评分: " + result.score);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    mTvAnalysis.setText("引擎错误: " + e.getMessage());
                    Toast.makeText(this, "引擎测试失败", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
        Toast.makeText(this, "开始测试本地引擎...", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUI();
        if (ChessAccessibilityService.sInstance != null) {
            mTvFEN.setText(ChessAccessibilityService.sInstance.getCurrentFEN());
            mTvMoveCount.setText(String.valueOf(
                    ChessAccessibilityService.sInstance.getMoveCount()));
        }
    }
}
