package com.catpaw.chesshelper.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.catpaw.chesshelper.R;
import com.catpaw.chesshelper.ui.MainActivity;

import java.nio.ByteBuffer;

/**
 * 屏幕截屏服务
 * 使用MediaProjection API实现无感知截屏
 */
public class ScreenCaptureService extends Service {

    private static final String TAG = "ScreenCapture";
    private static final String CHANNEL_ID = "chess_capture_channel";
    private static final int NOTIFICATION_ID = 1001;

    private static ScreenCaptureService sInstance;

    private MediaProjectionManager mProjectionManager;
    private MediaProjection mMediaProjection;
    private VirtualDisplay mVirtualDisplay;
    private ImageReader mImageReader;

    private int mScreenWidth;
    private int mScreenHeight;
    private int mScreenDensity;

    private CaptureCallback mPendingCallback;
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    public interface CaptureCallback {
        void onScreenCaptured(Bitmap bitmap);
        void onCaptureFailed(String reason);
    }

    public static ScreenCaptureService getInstance() {
        return sInstance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        sInstance = this;
        initScreenMetrics();
        createNotificationChannel();
        Log.i(TAG, "截屏服务已创建");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, createNotification());
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopCapture();
        sInstance = null;
    }

    private void initScreenMetrics() {
        WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics metrics = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(metrics);
        mScreenWidth = metrics.widthPixels;
        mScreenHeight = metrics.heightPixels;
        mScreenDensity = metrics.densityDpi;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "象棋识别服务", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("用于识别象棋棋盘的后台服务");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }
    }

    private Notification createNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("象棋助手运行中")
                .setContentText("正在识别象棋棋盘...")
                .setSmallIcon(R.drawable.ic_chess)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    /**
     * 初始化MediaProjection（需要在Activity中请求权限后传入resultCode和data）
     */
    public void initProjection(int resultCode, Intent data) {
        mProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        if (mProjectionManager == null) return;

        mMediaProjection = mProjectionManager.getMediaProjection(resultCode, data);
        if (mMediaProjection == null) {
            Log.e(TAG, "获取MediaProjection失败");
            return;
        }

        // 注册回调
        mMediaProjection.registerCallback(new MediaProjection.Callback() {
            @Override
            public void onStop() {
                Log.w(TAG, "MediaProjection已停止");
                stopCapture();
            }
        }, mHandler);

        Log.i(TAG, "MediaProjection初始化成功");
    }

    /**
     * 设置预先准备好的MediaProjection（从已授权的状态恢复）
     */
    public void setMediaProjection(MediaProjection projection) {
        mMediaProjection = projection;
    }

    /**
     * 执行截屏
     */
    public void captureScreen(CaptureCallback callback) {
        if (mMediaProjection == null) {
            mPendingCallback = callback;
            // 通知MainActivity请求截屏权限
            Intent intent = new Intent("com.catpaw.chesshelper.REQUEST_PROJECTION");
            sendBroadcast(intent);
            return;
        }

        if (mImageReader != null) {
            mImageReader.close();
        }

        mImageReader = ImageReader.newInstance(
                mScreenWidth, mScreenHeight, PixelFormat.RGBA_8888, 2);

        mVirtualDisplay = mMediaProjection.createVirtualDisplay(
                "ChessCapture",
                mScreenWidth, mScreenHeight, mScreenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mImageReader.getSurface(),
                null, mHandler);

        mHandler.postDelayed(() -> acquireLatestImage(callback), 300);
    }

    /**
     * 从ImageReader获取最新的帧
     */
    private void acquireLatestImage(CaptureCallback callback) {
        Image image = null;
        try {
            image = mImageReader.acquireLatestImage();
            if (image == null) {
                // 重试
                mHandler.postDelayed(() -> acquireLatestImage(callback), 100);
                return;
            }

            Image.Plane[] planes = image.getPlanes();
            ByteBuffer buffer = planes[0].getBuffer();
            int pixelStride = planes[0].getPixelStride();
            int rowStride = planes[0].getRowStride();
            int rowPadding = rowStride - pixelStride * mScreenWidth;

            Bitmap bitmap = Bitmap.createBitmap(
                    mScreenWidth + rowPadding / pixelStride,
                    mScreenHeight,
                    Bitmap.Config.ARGB_8888);
            bitmap.copyPixelsFromBuffer(buffer);

            // 裁剪掉padding
            Bitmap cropped = Bitmap.createBitmap(bitmap, 0, 0, mScreenWidth, mScreenHeight);
            bitmap.recycle();

            callback.onScreenCaptured(cropped);

        } catch (Exception e) {
            Log.e(TAG, "获取图像失败", e);
            callback.onCaptureFailed(e.getMessage());
        } finally {
            if (image != null) {
                image.close();
            }
        }
    }

    /**
     * 停止截屏并释放资源
     */
    public void stopCapture() {
        if (mVirtualDisplay != null) {
            mVirtualDisplay.release();
            mVirtualDisplay = null;
        }
        if (mImageReader != null) {
            mImageReader.close();
            mImageReader = null;
        }
    }

    /**
     * 获取屏幕尺寸
     */
    public int getScreenWidth() { return mScreenWidth; }
    public int getScreenHeight() { return mScreenHeight; }
    public int getScreenDensity() { return mScreenDensity; }
    public boolean isProjectionReady() { return mMediaProjection != null; }
}
