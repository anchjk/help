package vn.evnqt1.helpdesk.data;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Lưu tệp người dùng chọn (ảnh, PDF, Word, Excel) vào vùng riêng của ứng dụng; không dùng dịch vụ lưu ảnh bên ngoài. */
public final class AttachmentStore {
    /** Loại tệp cho phép (dùng cho hộp chọn tệp và kiểm tra lại khi lưu). */
    public static final String[] PICKER_MIME_TYPES = {
            "image/*", "application/pdf", "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"};
    private static final String[] DOC_EXT = {"pdf", "doc", "docx", "xls", "xlsx", "xlsm"};
    private static final long MAX_BYTES = 100L * 1024 * 1024;

    /** Tệp đã chọn nhưng chưa lưu. */
    public static final class Source {
        public final Uri uri; public final String name, mime; public final long size;
        public Source(Uri uri, String name, String mime, long size) { this.uri = uri; this.name = name; this.mime = mime; this.size = size; }
    }

    /** Tệp đã lưu. */
    public static final class Item {
        public String id, ownerType, ownerId, workEntryId, name, mime, path;
        public long size, addedAt;
        public boolean isImage() { return mime != null && mime.startsWith("image/"); }
    }

    private final Context context;
    public AttachmentStore(Context context) { this.context = context.getApplicationContext(); }

    public static String authority(Context c) { return c.getPackageName() + ".attachments"; }
    public static Uri uriFor(Context c, String attachmentId) { return Uri.parse("content://" + authority(c) + "/" + attachmentId); }

    /** Đọc tên/kích thước/loại của tệp được chọn và kiểm tra loại tệp được hỗ trợ. */
    public Source inspect(Uri uri) {
        ContentResolver r = context.getContentResolver();
        String name = null; long size = -1;
        try (Cursor c = r.query(uri, new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                if (!c.isNull(0)) name = c.getString(0);
                if (!c.isNull(1)) size = c.getLong(1);
            }
        } catch (RuntimeException ignored) { }
        if (name == null || name.trim().isEmpty()) name = "tep_" + System.currentTimeMillis();
        String mime = resolveMime(r.getType(uri), name);
        if (!isSupported(name, mime)) throw new IllegalArgumentException("“" + name + "”: chỉ hỗ trợ ảnh, PDF, Word (.doc/.docx), Excel (.xls/.xlsx)");
        if (size > MAX_BYTES) throw new IllegalArgumentException("“" + name + "”: tệp vượt giới hạn 100 MB");
        return new Source(uri, name, mime, size);
    }

    public static boolean isSupported(String name, String mime) {
        if (mime != null && mime.startsWith("image/")) return true;
        String ext = extensionOf(name);
        for (String e : DOC_EXT) if (e.equals(ext)) return true;
        if (mime != null) for (String m : PICKER_MIME_TYPES) if (m.equals(mime)) return true;
        return false;
    }

    private static String resolveMime(String reported, String name) {
        if (reported != null && !reported.isEmpty() && !"application/octet-stream".equals(reported)) return reported;
        String ext = extensionOf(name);
        String m = ext.isEmpty() ? null : MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        return m != null ? m : (reported == null ? "application/octet-stream" : reported);
    }

    private static String extensionOf(String name) {
        if (name == null) return "";
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return "";
        String e = name.substring(dot + 1).toLowerCase(Locale.ROOT);
        return e.matches("[a-z0-9]{1,8}") ? e : "";
    }

    /** Lưu tệp và gắn vào hồ sơ (ownerType = ANOMALY/DRAWING); workEntryId khác null nếu tệp thuộc một phiếu công việc. */
    public String importUri(Uri uri, String ownerType, String ownerId, String workEntryId, String role, String caption) throws Exception {
        Source src = inspect(uri);
        ContentResolver resolver = context.getContentResolver();
        String ext = extensionOf(src.name);
        if (ext.isEmpty()) { String e = MimeTypeMap.getSingleton().getExtensionFromMimeType(src.mime); ext = e == null ? "" : e; }
        File dir = new File(context.getFilesDir(), "attachments");
        if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("Không tạo được thư mục tệp");
        File dest = new File(dir, "tep_" + UUID.randomUUID() + (ext.isEmpty() ? "" : "." + ext));
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        long size = 0;
        try (InputStream in = resolver.openInputStream(uri); FileOutputStream out = new FileOutputStream(dest)) {
            if (in == null) throw new IllegalStateException("Không đọc được tệp nguồn");
            byte[] b = new byte[8192]; int n;
            while ((n = in.read(b)) != -1) {
                size += n;
                if (size > MAX_BYTES) throw new IllegalArgumentException("Tệp vượt giới hạn 100 MB");
                out.write(b, 0, n); sha.update(b, 0, n);
            }
        } catch (Exception e) { dest.delete(); throw e; }
        String id = UUID.randomUUID().toString();
        ContentValues v = new ContentValues();
        v.put("id", id); v.put("owner_type", ownerType); v.put("owner_id", ownerId);
        if (workEntryId != null) v.put("work_entry_id", workEntryId);
        v.put("local_path", dest.getAbsolutePath()); v.put("mime_type", src.mime); v.put("file_name", src.name);
        v.put("file_hash", hex(sha.digest())); v.put("captured_at", System.currentTimeMillis()); v.put("caption", caption);
        v.put("size", size); v.put("file_role", role);
        try { LocalDatabase.get(context).getWritableDatabase().insertOrThrow("attachments", null, v); }
        catch (RuntimeException e) { dest.delete(); throw e; }
        return id;
    }

    private static final String COLS = "id,owner_type,owner_id,work_entry_id,file_name,mime_type,local_path,size,captured_at";

    /** Tệp của một hồ sơ, mới nhất trước. */
    public List<Item> list(String ownerType, String ownerId) {
        List<Item> out = new ArrayList<>();
        try (Cursor c = LocalDatabase.get(context).getReadableDatabase().rawQuery("SELECT " + COLS + " FROM attachments WHERE owner_type=? AND owner_id=? ORDER BY captured_at DESC", new String[]{ownerType, ownerId})) {
            while (c.moveToNext()) out.add(item(c));
        }
        return out;
    }

    public Item find(String id) {
        try (Cursor c = LocalDatabase.get(context).getReadableDatabase().rawQuery("SELECT " + COLS + " FROM attachments WHERE id=?", new String[]{id})) {
            return c.moveToFirst() ? item(c) : null;
        }
    }

    /** Xóa tệp khỏi hồ sơ; chỉ xóa file vật lý khi không còn bản ghi nào dùng. */
    public void delete(String id) {
        Item it = find(id);
        if (it == null) return;
        android.database.sqlite.SQLiteDatabase db = LocalDatabase.get(context).getWritableDatabase();
        db.delete("attachments", "id=?", new String[]{id});
        boolean referenced;
        try (Cursor c = db.rawQuery("SELECT 1 FROM attachments WHERE local_path=? LIMIT 1", new String[]{it.path})) { referenced = c.moveToFirst(); }
        if (!referenced) { try { new File(it.path).delete(); } catch (RuntimeException ignored) { } }
    }

    private static Item item(Cursor c) {
        Item i = new Item();
        i.id = c.getString(0); i.ownerType = c.getString(1); i.ownerId = c.getString(2); i.workEntryId = c.isNull(3) ? null : c.getString(3);
        i.name = c.isNull(4) ? "tệp" : c.getString(4); i.mime = c.getString(5); i.path = c.getString(6);
        i.size = c.isNull(7) ? 0 : c.getLong(7); i.addedAt = c.isNull(8) ? 0 : c.getLong(8);
        return i;
    }

    public static String sizeLabel(long bytes) {
        if (bytes <= 0) return "";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.US, "%.0f KB", bytes / 1024.0);
        return String.format(Locale.US, "%.1f MB", bytes / 1048576.0);
    }

    public static void copy(File src, File dst) throws Exception { try (FileInputStream in = new FileInputStream(src); FileOutputStream out = new FileOutputStream(dst)) { byte[] b = new byte[8192]; int n; while ((n = in.read(b)) != -1) out.write(b, 0, n); } }
    private static String hex(byte[] bytes) { StringBuilder b = new StringBuilder(); for (byte x : bytes) b.append(String.format(Locale.US, "%02x", x)); return b.toString(); }
}
