package vn.evnqt1.helpdesk.data;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/** Local-only relational store. Schema changes must increment DB_VERSION and add migrations. */
public final class LocalDatabase extends SQLiteOpenHelper {
    public static final String DB_NAME = "evnqt1_helpdesk.db";
    public static final int DB_VERSION = 4;
    private static volatile LocalDatabase instance;

    public static LocalDatabase get(Context context) {
        if (instance == null) synchronized (LocalDatabase.class) {
            if (instance == null) instance = new LocalDatabase(context.getApplicationContext());
        }
        return instance;
    }
    private LocalDatabase(Context context) { super(context, DB_NAME, null, DB_VERSION); setWriteAheadLoggingEnabled(true); }
    public static void closeSingleton() { synchronized (LocalDatabase.class) { if (instance != null) { instance.close(); instance = null; } } }

    @Override public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE equipment(id TEXT PRIMARY KEY, canonical_name TEXT NOT NULL, aliases TEXT, kks TEXT, system_area TEXT, area TEXT, description TEXT, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX idx_equipment_kks ON equipment(kks)");

        db.execSQL("CREATE TABLE anomalies(id TEXT PRIMARY KEY, code TEXT NOT NULL UNIQUE, title TEXT NOT NULL, description TEXT, equipment_id TEXT, equipment_name TEXT NOT NULL, kks TEXT, system_area TEXT, location TEXT, detected_at INTEGER NOT NULL, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, severity TEXT NOT NULL, status TEXT NOT NULL, responsible TEXT, resolved_at INTEGER, deleted_at INTEGER, reopened INTEGER NOT NULL DEFAULT 0, FOREIGN KEY(equipment_id) REFERENCES equipment(id) ON DELETE SET NULL)");
        db.execSQL("CREATE INDEX idx_anomaly_status ON anomalies(status)");
        db.execSQL("CREATE INDEX idx_anomaly_kks ON anomalies(kks)");
        db.execSQL("CREATE INDEX idx_anomaly_detected ON anomalies(detected_at)");
        db.execSQL("CREATE INDEX idx_anomaly_updated ON anomalies(updated_at)");

        db.execSQL("CREATE TABLE drawings(id TEXT PRIMARY KEY, code TEXT NOT NULL UNIQUE, drawing_number TEXT NOT NULL, title TEXT, description TEXT, aliases TEXT, discipline TEXT, system_area TEXT, related_equipment TEXT, current_revision TEXT, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, deleted_at INTEGER)");
        db.execSQL("CREATE INDEX idx_drawing_number ON drawings(drawing_number)");
        db.execSQL("CREATE INDEX idx_drawing_updated ON drawings(updated_at)");

        db.execSQL("CREATE TABLE shift_occurrences(id TEXT PRIMARY KEY, shift_date TEXT NOT NULL, shift_code TEXT NOT NULL, start_at INTEGER NOT NULL, end_at INTEGER NOT NULL, roster TEXT, note TEXT, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, slot TEXT, source TEXT, CHECK(end_at > start_at))");
        db.execSQL("CREATE INDEX idx_shift_times ON shift_occurrences(start_at, end_at)");
        db.execSQL("CREATE INDEX idx_shift_date_slot ON shift_occurrences(shift_date, slot)");

        db.execSQL("CREATE TABLE repair_sessions(id TEXT PRIMARY KEY, anomaly_id TEXT NOT NULL, session_no INTEGER NOT NULL, shift_occurrence_id TEXT, shift_code TEXT, shift_start INTEGER, shift_end INTEGER, roster_snapshot TEXT, participants TEXT, started_at INTEGER NOT NULL, last_updated_at INTEGER NOT NULL, result_status TEXT, summary TEXT, FOREIGN KEY(anomaly_id) REFERENCES anomalies(id) ON DELETE CASCADE, FOREIGN KEY(shift_occurrence_id) REFERENCES shift_occurrences(id) ON DELETE SET NULL)");
        db.execSQL("CREATE INDEX idx_repair_anomaly ON repair_sessions(anomaly_id, session_no)");
        db.execSQL("CREATE UNIQUE INDEX uq_repair_occurrence ON repair_sessions(anomaly_id, shift_occurrence_id) WHERE shift_occurrence_id IS NOT NULL");

        db.execSQL("CREATE TABLE drawing_work_sessions(id TEXT PRIMARY KEY, drawing_id TEXT NOT NULL, session_no INTEGER NOT NULL, shift_occurrence_id TEXT, shift_code TEXT, shift_start INTEGER, shift_end INTEGER, roster_snapshot TEXT, participants TEXT, started_at INTEGER NOT NULL, last_updated_at INTEGER NOT NULL, summary TEXT, FOREIGN KEY(drawing_id) REFERENCES drawings(id) ON DELETE CASCADE, FOREIGN KEY(shift_occurrence_id) REFERENCES shift_occurrences(id) ON DELETE SET NULL)");
        db.execSQL("CREATE INDEX idx_dws_drawing ON drawing_work_sessions(drawing_id, session_no)");
        db.execSQL("CREATE UNIQUE INDEX uq_dws_occurrence ON drawing_work_sessions(drawing_id, shift_occurrence_id) WHERE shift_occurrence_id IS NOT NULL");

        db.execSQL("CREATE TABLE work_entries(id TEXT PRIMARY KEY, entity_type TEXT NOT NULL, entity_id TEXT NOT NULL, session_id TEXT, work_at INTEGER NOT NULL, recorded_at INTEGER NOT NULL, work_type TEXT, description TEXT NOT NULL, actions TEXT, findings TEXT, result TEXT, status_change TEXT, author TEXT, participants TEXT, shift_snapshot TEXT, ref_name TEXT, ref_code TEXT, FOREIGN KEY(session_id) REFERENCES repair_sessions(id) ON DELETE CASCADE)");
        db.execSQL("CREATE INDEX idx_work_entity ON work_entries(entity_type, entity_id, work_at)");
        db.execSQL("CREATE TABLE drawing_work_entries(id TEXT PRIMARY KEY, drawing_work_session_id TEXT NOT NULL, drawing_id TEXT NOT NULL, work_at INTEGER NOT NULL, recorded_at INTEGER NOT NULL, work_type TEXT, description TEXT NOT NULL, actions TEXT, findings TEXT, result TEXT, author TEXT, participants TEXT, shift_snapshot TEXT, ref_name TEXT, ref_code TEXT, FOREIGN KEY(drawing_work_session_id) REFERENCES drawing_work_sessions(id) ON DELETE CASCADE, FOREIGN KEY(drawing_id) REFERENCES drawings(id) ON DELETE CASCADE)");
        db.execSQL("CREATE INDEX idx_dwe_drawing ON drawing_work_entries(drawing_id, work_at)");

        db.execSQL("CREATE TABLE drawing_revisions(id TEXT PRIMARY KEY, drawing_id TEXT NOT NULL, revision_label TEXT NOT NULL, previous_revision_label TEXT, revision_date INTEGER NOT NULL, recorded_at INTEGER NOT NULL, change_description TEXT, change_reason TEXT, affected_area TEXT, drawing_work_session_id TEXT, related_work_entry_ids TEXT, author TEXT, reviewers TEXT, notes TEXT, FOREIGN KEY(drawing_id) REFERENCES drawings(id) ON DELETE CASCADE, FOREIGN KEY(drawing_work_session_id) REFERENCES drawing_work_sessions(id) ON DELETE SET NULL)");
        db.execSQL("CREATE INDEX idx_revision_drawing ON drawing_revisions(drawing_id, revision_date)");

        db.execSQL("CREATE TABLE attachments(id TEXT PRIMARY KEY, owner_type TEXT NOT NULL, owner_id TEXT NOT NULL, local_path TEXT NOT NULL, mime_type TEXT, file_name TEXT, file_hash TEXT, captured_at INTEGER, caption TEXT, size INTEGER, file_role TEXT, work_entry_id TEXT)");
        db.execSQL("CREATE INDEX idx_attachment_owner ON attachments(owner_type, owner_id)");
        db.execSQL("CREATE INDEX idx_attachment_work ON attachments(work_entry_id)");

        db.execSQL("CREATE TABLE alerts(id TEXT PRIMARY KEY, anomaly_id TEXT NOT NULL, alert_type TEXT NOT NULL, severity INTEGER NOT NULL, triggered_at INTEGER NOT NULL, last_triggered_at INTEGER NOT NULL, reason_snapshot TEXT, threshold_snapshot TEXT, deduplication_key TEXT NOT NULL, is_read INTEGER NOT NULL DEFAULT 0, read_at INTEGER, condition_status TEXT NOT NULL, expired_at INTEGER, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, FOREIGN KEY(anomaly_id) REFERENCES anomalies(id) ON DELETE CASCADE)");
        db.execSQL("CREATE INDEX idx_alert_active ON alerts(condition_status, is_read, triggered_at)");
        db.execSQL("CREATE UNIQUE INDEX uq_alert_dedupe_active ON alerts(deduplication_key) WHERE condition_status='active'");

        db.execSQL("CREATE TABLE status_history(id TEXT PRIMARY KEY, anomaly_id TEXT NOT NULL, old_value TEXT, new_value TEXT, action TEXT NOT NULL, timestamp INTEGER NOT NULL, operator TEXT, details TEXT, FOREIGN KEY(anomaly_id) REFERENCES anomalies(id) ON DELETE CASCADE)");
        db.execSQL("CREATE INDEX idx_status_history ON status_history(anomaly_id, timestamp)");
        db.execSQL("CREATE TABLE audit_log(id TEXT PRIMARY KEY, entity_type TEXT NOT NULL, entity_id TEXT NOT NULL, action TEXT NOT NULL, old_value TEXT, new_value TEXT, timestamp INTEGER NOT NULL, operator TEXT, details TEXT)");

        db.execSQL("CREATE TABLE app_settings(key TEXT PRIMARY KEY, value TEXT NOT NULL, updated_at INTEGER NOT NULL)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Migration có thứ tự: mỗi phiên bản chỉ chạy các bước còn thiếu, không mất dữ liệu cũ.
        if (oldVersion < 1) { onCreate(db); return; }
        if (oldVersion < 2) {
            // v2: lịch ca nhập từ ảnh lưu thêm ca (Sáng/Chiều/Đêm) và nguồn dữ liệu.
            db.execSQL("ALTER TABLE shift_occurrences ADD COLUMN slot TEXT");
            db.execSQL("ALTER TABLE shift_occurrences ADD COLUMN source TEXT");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_shift_date_slot ON shift_occurrences(shift_date, slot)");
        }
        if (oldVersion < 3) {
            // v3: gỡ hoàn toàn chức năng AI cũ (chat, cấu hình) - xóa lịch sử hội thoại và cấu hình AI cũ.
            db.execSQL("DROP TABLE IF EXISTS chat_messages");
            db.execSQL("DROP TABLE IF EXISTS chat_conversations");
            db.execSQL("DELETE FROM app_settings WHERE key LIKE 'ai\\_%' ESCAPE '\\'");
        }
        if (oldVersion < 4) {
            // v4: phiếu công việc lưu thêm tên thiết bị/KKS (hoặc số/tiêu đề bản vẽ) người dùng đã nhập; tệp đính kèm gắn được vào từng phiếu.
            db.execSQL("ALTER TABLE work_entries ADD COLUMN ref_name TEXT");
            db.execSQL("ALTER TABLE work_entries ADD COLUMN ref_code TEXT");
            db.execSQL("ALTER TABLE drawing_work_entries ADD COLUMN ref_name TEXT");
            db.execSQL("ALTER TABLE drawing_work_entries ADD COLUMN ref_code TEXT");
            db.execSQL("ALTER TABLE attachments ADD COLUMN work_entry_id TEXT");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_attachment_work ON attachments(work_entry_id)");
        }
    }
}
