package vn.evnqt1.helpdesk.data;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Cho ứng dụng ngoài (trình xem PDF/Word/Excel/ảnh) đọc một tệp đính kèm theo URI tạm thời.
 * Không export; chỉ cấp quyền đọc qua FLAG_GRANT_READ_URI_PERMISSION khi người dùng bấm mở tệp.
 * Chỉ phục vụ tệp có bản ghi trong bảng attachments và nằm trong thư mục attachments của ứng dụng.
 */
public final class AttachmentProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }

    private AttachmentStore.Item lookup(Uri uri) {
        String id = uri.getLastPathSegment();
        return id == null ? null : new AttachmentStore(getContext()).find(id);
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        AttachmentStore.Item it = lookup(uri);
        if (it == null) return null;
        String[] cols = projection != null ? projection : new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
        MatrixCursor mc = new MatrixCursor(cols, 1);
        Object[] row = new Object[cols.length];
        for (int i = 0; i < cols.length; i++) {
            if (OpenableColumns.DISPLAY_NAME.equals(cols[i])) row[i] = it.name;
            else if (OpenableColumns.SIZE.equals(cols[i])) row[i] = new File(it.path).length();
        }
        mc.addRow(row);
        return mc;
    }

    @Override public String getType(Uri uri) { AttachmentStore.Item it = lookup(uri); return it == null ? null : it.mime; }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new SecurityException("Chỉ cho phép đọc");
        AttachmentStore.Item it = lookup(uri);
        if (it == null) throw new FileNotFoundException("Không có tệp");
        try {
            File base = new File(getContext().getFilesDir(), "attachments").getCanonicalFile();
            File f = new File(it.path).getCanonicalFile();
            if (!f.getPath().startsWith(base.getPath() + File.separator)) throw new SecurityException("Đường dẫn không hợp lệ");
            return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
        } catch (java.io.IOException e) { throw new FileNotFoundException(e.getMessage()); }
    }

    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
}
