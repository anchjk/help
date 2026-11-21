package vn.evnqt1.helpdesk.data;

import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import android.content.Context;
import android.net.Uri;
import android.database.sqlite.SQLiteDatabase;

import org.json.JSONObject;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.zip.*;

/** Password-encrypted, single-file backup. Import uses atomic replace with rollback copy. */
public final class BackupManager {
    private static final byte[] MAGIC=new byte[]{'E','L','B','1'};
    private static final int ITERATIONS=210_000;
    private final Context context;
    public BackupManager(Context c){context=c.getApplicationContext();}

    public void exportBackup(Uri destination,String password)throws Exception{
        if(password==null||password.length()<10)throw new IllegalArgumentException("Mật khẩu backup phải có ít nhất 10 ký tự");
        checkpoint(LocalDatabase.get(context).getWritableDatabase());
        File work=new File(context.getCacheDir(),"backup-work");deleteTree(work);if(!work.mkdirs())throw new IOException("Không tạo được thư mục backup");
        File zipFile=new File(work,"payload.zip");File dbFile=context.getDatabasePath(LocalDatabase.DB_NAME);
        File dbSnapshot=new File(work,LocalDatabase.DB_NAME);AttachmentStore.copy(dbFile,dbSnapshot);
        JSONObject manifest=new JSONObject();manifest.put("schemaVersion",LocalDatabase.DB_VERSION);manifest.put("createdAt",System.currentTimeMillis());manifest.put("databaseSha256",sha256(dbSnapshot));manifest.put("format","EVNQT1-LOCAL-BACKUP");
        try(ZipOutputStream zip=new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zipFile)))){
            put(zip,"manifest.json",manifest.toString());addFile(zip,dbSnapshot,"database/"+LocalDatabase.DB_NAME);
            File attachments=new File(context.getFilesDir(),"attachments");if(attachments.exists())addTree(zip,attachments,"attachments");
        }
        byte[] salt=new byte[16],iv=new byte[12];SecureRandom random=new SecureRandom();random.nextBytes(salt);random.nextBytes(iv);byte[] key=derive(password,salt);
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,iv));
        try(InputStream in=new BufferedInputStream(new FileInputStream(zipFile));OutputStream raw=context.getContentResolver().openOutputStream(destination,"w")){
            if(raw==null)throw new IOException("Không mở được tệp backup đích");raw.write(MAGIC);raw.write(salt);raw.write(iv);try(CipherOutputStream out=new CipherOutputStream(raw,cipher)){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
        }finally{deleteTree(work);}
    }

    public JSONObject preview(Uri source,String password)throws Exception{
        File dir=decryptToTemp(source,password);try{JSONObject manifest=readManifest(dir);File db=new File(dir,"database/"+LocalDatabase.DB_NAME);if(!db.exists())throw new IOException("Backup không có database");if(!sha256(db).equals(manifest.getString("databaseSha256")))throw new IOException("Checksum database không khớp");
            SQLiteDatabase check=SQLiteDatabase.openDatabase(db.getAbsolutePath(),null,SQLiteDatabase.OPEN_READONLY);JSONObject result=new JSONObject();result.put("schemaVersion",manifest.getInt("schemaVersion"));result.put("createdAt",manifest.getLong("createdAt"));result.put("anomalies",tableCount(check,"anomalies"));result.put("drawings",tableCount(check,"drawings"));result.put("shifts",tableCount(check,"shift_occurrences"));result.put("attachments",countFiles(new File(dir,"attachments")));check.close();return result;
        }finally{deleteTree(dir);}}

    public void restoreReplace(Uri source,String password)throws Exception{
        File dir=decryptToTemp(source,password);File current=context.getDatabasePath(LocalDatabase.DB_NAME);File rollback=new File(context.getCacheDir(),"database-before-restore.db");File rollbackAttachments=new File(context.getCacheDir(),"attachments-before-restore");
        try{
            JSONObject manifest=readManifest(dir);if(manifest.getInt("schemaVersion")>LocalDatabase.DB_VERSION)throw new IOException("Backup được tạo bằng phiên bản ứng dụng mới hơn");
            File incoming=new File(dir,"database/"+LocalDatabase.DB_NAME);if(!incoming.exists()||!sha256(incoming).equals(manifest.getString("databaseSha256")))throw new IOException("Database backup không hợp lệ");
            checkpoint(LocalDatabase.get(context).getWritableDatabase());
            if(current.exists())AttachmentStore.copy(current,rollback);
            File currentAttachments=new File(context.getFilesDir(),"attachments");if(currentAttachments.exists()){deleteTree(rollbackAttachments);copyTree(currentAttachments,rollbackAttachments);}
            LocalDatabase.closeSingleton();
            try{
                deleteQuietly(new File(current.getAbsolutePath()+"-wal"));deleteQuietly(new File(current.getAbsolutePath()+"-shm"));
                AttachmentStore.copy(incoming,current);
                File incomingAttachments=new File(dir,"attachments");if(incomingAttachments.exists()){File dst=new File(context.getFilesDir(),"attachments");deleteTree(dst);copyTree(incomingAttachments,dst);}
                SQLiteDatabase verify=SQLiteDatabase.openDatabase(current.getAbsolutePath(),null,SQLiteDatabase.OPEN_READONLY);verify.close();
                LocalDatabase.get(context).getReadableDatabase();
            }catch(Exception failure){LocalDatabase.closeSingleton();if(rollback.exists())AttachmentStore.copy(rollback,current);if(rollbackAttachments.exists()){deleteTree(new File(context.getFilesDir(),"attachments"));copyTree(rollbackAttachments,new File(context.getFilesDir(),"attachments"));}LocalDatabase.get(context).getReadableDatabase();throw failure;}
        }finally{deleteTree(dir);deleteQuietly(rollback);deleteTree(rollbackAttachments);}
    }

    private File decryptToTemp(Uri source,String password)throws Exception{
        File dir=new File(context.getCacheDir(),"restore-"+System.nanoTime());if(!dir.mkdirs())throw new IOException("Không tạo được thư mục tạm");File zip=new File(dir,"payload.zip");
        try(InputStream raw=context.getContentResolver().openInputStream(source)){
            if(raw==null)throw new IOException("Không đọc được tệp backup");byte[] magic=readN(raw,4);if(!Arrays.equals(magic,MAGIC))throw new IOException("Định dạng backup không hợp lệ");byte[] salt=readN(raw,16),iv=readN(raw,12),key=derive(password,salt);
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,iv));
            try(CipherInputStream in=new CipherInputStream(raw,cipher);OutputStream out=new BufferedOutputStream(new FileOutputStream(zip))){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
        }catch(Exception e){deleteTree(dir);throw new IOException("Sai mật khẩu hoặc backup bị hỏng",e);}
        try(ZipInputStream zipIn=new ZipInputStream(new BufferedInputStream(new FileInputStream(zip)))){ZipEntry e;while((e=zipIn.getNextEntry())!=null){File target=new File(dir,e.getName());String canonical=target.getCanonicalPath();if(!canonical.startsWith(dir.getCanonicalPath()+File.separator))throw new IOException("Đường dẫn backup không an toàn");if(e.isDirectory()){target.mkdirs();}else{File parent=target.getParentFile();if(!parent.exists()&&!parent.mkdirs())throw new IOException("Không tạo được thư mục giải nén");try(OutputStream out=new BufferedOutputStream(new FileOutputStream(target))){byte[] b=new byte[8192];int n;while((n=zipIn.read(b))!=-1)out.write(b,0,n);}}zipIn.closeEntry();}}
        deleteQuietly(zip);return dir;
    }
    private static void checkpoint(SQLiteDatabase db){try(android.database.Cursor c=db.rawQuery("PRAGMA wal_checkpoint(FULL)",null)){while(c.moveToNext()){} }}
    private static JSONObject readManifest(File dir)throws Exception{File f=new File(dir,"manifest.json");if(!f.exists())throw new IOException("Thiếu manifest");return new JSONObject(readText(f));}
    private static byte[] derive(String password,byte[] salt)throws Exception{PBEKeySpec spec=new PBEKeySpec(password.toCharArray(),salt,ITERATIONS,256);try{return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();}finally{spec.clearPassword();}}
    private static byte[] readN(InputStream in,int length)throws Exception{byte[] b=new byte[length];int off=0;while(off<length){int n=in.read(b,off,length-off);if(n<0)throw new EOFException();off+=n;}return b;}
    private static String sha256(File f)throws Exception{MessageDigest md=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(f)){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)md.update(b,0,n);}StringBuilder s=new StringBuilder();for(byte x:md.digest())s.append(String.format(java.util.Locale.US,"%02x",x));return s.toString();}
    private static long tableCount(SQLiteDatabase db,String table){try(android.database.Cursor c=db.rawQuery("SELECT COUNT(*) FROM "+table,null)){return c.moveToFirst()?c.getLong(0):0;}}
    private static int countFiles(File f){if(!f.exists())return 0;if(f.isFile())return 1;int n=0;File[] list=f.listFiles();if(list!=null)for(File x:list)n+=countFiles(x);return n;}
    private static void put(ZipOutputStream z,String name,String content)throws Exception{z.putNextEntry(new ZipEntry(name));z.write(content.getBytes("UTF-8"));z.closeEntry();}
    private static void addFile(ZipOutputStream z,File f,String name)throws Exception{z.putNextEntry(new ZipEntry(name));try(InputStream in=new FileInputStream(f)){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)z.write(b,0,n);}z.closeEntry();}
    private static void addTree(ZipOutputStream z,File root,String prefix)throws Exception{File[] fs=root.listFiles();if(fs==null)return;for(File f:fs){if(f.isDirectory())addTree(z,f,prefix+"/"+f.getName());else addFile(z,f,prefix+"/"+f.getName());}}
    private static void copyTree(File src,File dst)throws Exception{if(src.isDirectory()){if(!dst.exists()&&!dst.mkdirs())throw new IOException();File[] fs=src.listFiles();if(fs!=null)for(File f:fs)copyTree(f,new File(dst,f.getName()));}else AttachmentStore.copy(src,dst);}
    private static String readText(File f)throws Exception{try(InputStream in=new FileInputStream(f);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toString("UTF-8");}}
    private static void deleteQuietly(File f){if(f.exists())f.delete();}
    private static void deleteTree(File f){if(f==null||!f.exists())return;if(f.isDirectory()){File[] fs=f.listFiles();if(fs!=null)for(File x:fs)deleteTree(x);}f.delete();}
}
