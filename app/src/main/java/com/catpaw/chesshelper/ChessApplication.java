package com.catpaw.chesshelper;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

/**
 * 应用入口
 */
public class ChessApplication extends Application {

    public static final String CHANNEL_ID_MAIN = "chess_main_channel";

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannels();
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel mainChannel = new NotificationChannel(
                    CHANNEL_ID_MAIN,
                    "象棋助手主服务",
                    NotificationManager.IMPORTANCE_LOW);
            mainChannel.setDescription("象棋AI辅助主服务通知");
            mainChannel.setShowBadge(false);

            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                nm.createNotificationChannel(mainChannel);
            }
        }
    }
}
