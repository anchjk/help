package vn.evnqt1.helpdesk.data;

import android.content.Context;

import java.security.KeyStore;

/** Dọn dữ liệu còn sót của chức năng AI đã gỡ (khóa API và khóa mã hóa trong Android Keystore). An toàn khi chạy nhiều lần. */
public final class LegacyCleanup {
    private LegacyCleanup() {}

    public static void run(Context context) {
        try { context.deleteSharedPreferences("secure_api_settings"); } catch (RuntimeException ignored) { }
        try {
            KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
            ks.load(null);
            if (ks.containsAlias("evnqt1.helpdesk.api.key")) ks.deleteEntry("evnqt1.helpdesk.api.key");
        } catch (Exception ignored) { }
    }
}
