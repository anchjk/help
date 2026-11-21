package vn.evnqt1.helpdesk.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import vn.evnqt1.helpdesk.core.AlertRuleEngine;
import vn.evnqt1.helpdesk.core.MatchSuggester;
import vn.evnqt1.helpdesk.core.StatsCalculator;
import vn.evnqt1.helpdesk.core.StatsReport;
import vn.evnqt1.helpdesk.core.ShiftOccurrence;
import vn.evnqt1.helpdesk.core.ShiftResolver;
import vn.evnqt1.helpdesk.core.TextNormalizer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Transactional repository. It never performs network requests. */
public final class HelpDeskRepository {
    private final LocalDatabase helper;
    public HelpDeskRepository(Context context) { helper = LocalDatabase.get(context); }

    public static final class Record {
        public String id, code, title, description, equipmentName, kks, systemArea, location,
                severity, status, responsible, drawingNumber, discipline, currentRevision;
        public long detectedAt, createdAt, updatedAt, deletedAt;
        public int sessionCount, entryCount, revisionCount;
    }
    public static final class WorkInput {
        public long workAt;
        public String workType="Ghi nhận hiện tượng", description, actions, findings, result,
                author, participants, explicitShiftId, refName, refCode;
    }
    public static final class ShiftInput {
        public String date, code, roster, note, slot, source;
        public long startAt, endAt;
    }
    public static final class HistoryItem {
        public String id, type, description, actions, findings, result, shift, author, refName, refCode;
        public long workAt, recordedAt;
    }
    /** Hồ sơ được gợi ý cho một phiếu công việc, kèm điểm và lý do (ví dụ "Trùng mã KKS"). */
    public static final class Suggestion {
        public Record record; public int score; public String reason;
    }
    public static final class AlertItem {
        public String id, anomalyId, anomalyCode, title, type, reason, condition;
        public int priority, isRead;
        public long triggeredAt;
    }

    private static String id() { return UUID.randomUUID().toString(); }
    private static ContentValues cv() { return new ContentValues(); }

    public String createAnomaly(String title, String description, String equipment, String kks,
                                String system, String location, String severity, String responsible,
                                long detectedAt) {
        if(title==null||title.trim().isEmpty())throw new IllegalArgumentException("Tiêu đề bất thường là bắt buộc");
        if(equipment==null||equipment.trim().isEmpty())throw new IllegalArgumentException("Tên thiết bị mô tả là bắt buộc");
        SQLiteDatabase db=helper.getWritableDatabase(); db.beginTransaction();
        try {
            String id=id(), code=nextCode(db,"anomalies","code","ABN-"); long now=System.currentTimeMillis();
            ContentValues v=cv(); v.put("id",id); v.put("code",code); v.put("title",title.trim());
            v.put("description",description); v.put("equipment_name",equipment.trim());
            v.put("kks", blankToNull(kks)); v.put("system_area",system); v.put("location",location);
            v.put("detected_at",detectedAt); v.put("created_at",now); v.put("updated_at",now);
            v.put("severity",severity); v.put("status","Chưa xử lý"); v.put("responsible",responsible);
            db.insertOrThrow("anomalies",null,v);
            history(db,id,null,"Chưa xử lý","CREATE",now,"", "Tạo hồ sơ " + code);
            audit(db,"ANOMALY",id,"CREATE",null,code,now,"",title);
            db.setTransactionSuccessful(); return id;
        } finally { db.endTransaction(); }
    }

    public String createDrawing(String number, String title, String description, String discipline,
                                String systemArea, String relatedEquipment) {
        if (number==null || number.trim().isEmpty()) throw new IllegalArgumentException("Số bản vẽ là bắt buộc");
        SQLiteDatabase db=helper.getWritableDatabase(); db.beginTransaction();
        try {
            String id=id(), code=nextCode(db,"drawings","code","DRW-"); long now=System.currentTimeMillis();
            ContentValues v=cv(); v.put("id",id); v.put("code",code); v.put("drawing_number",number);
            v.put("title",title); v.put("description",description); v.put("discipline",discipline);
            v.put("system_area",systemArea); v.put("related_equipment",relatedEquipment);
            v.put("created_at",now); v.put("updated_at",now); db.insertOrThrow("drawings",null,v);
            audit(db,"DRAWING",id,"CREATE",null,number.trim(),now,"",title);
            db.setTransactionSuccessful(); return id;
        } finally { db.endTransaction(); }
    }

    public List<Record> listAnomalies(boolean includeDeleted) { return listAnomalies(includeDeleted, 0); }
    /** limit &gt; 0 chỉ lấy các hồ sơ mới cập nhật nhất (nhanh hơn cho màn hình tổng quan). */
    public List<Record> listAnomalies(boolean includeDeleted, int limit) {
        List<Record> out=new ArrayList<>(); String sql="SELECT a.*, (SELECT COUNT(*) FROM repair_sessions s WHERE s.anomaly_id=a.id) sc, (SELECT COUNT(*) FROM work_entries w WHERE w.entity_id=a.id AND w.entity_type='ANOMALY') ec FROM anomalies a";
        if(!includeDeleted) sql += " WHERE a.deleted_at IS NULL"; sql += " ORDER BY a.updated_at DESC"; if(limit>0) sql += " LIMIT "+limit;
        try(Cursor c=helper.getReadableDatabase().rawQuery(sql,null)) { while(c.moveToNext()) out.add(anomaly(c)); }
        return out;
    }

    public List<Record> listDrawings(boolean includeDeleted) { return listDrawings(includeDeleted, 0); }
    public List<Record> listDrawings(boolean includeDeleted, int limit) {
        List<Record> out=new ArrayList<>(); String sql="SELECT d.*, (SELECT COUNT(*) FROM drawing_work_sessions s WHERE s.drawing_id=d.id) sc, (SELECT COUNT(*) FROM drawing_revisions r WHERE r.drawing_id=d.id) rc, (SELECT COUNT(*) FROM drawing_work_entries w WHERE w.drawing_id=d.id) ec FROM drawings d";
        if(!includeDeleted) sql += " WHERE d.deleted_at IS NULL"; sql += " ORDER BY d.updated_at DESC"; if(limit>0) sql += " LIMIT "+limit;
        try(Cursor c=helper.getReadableDatabase().rawQuery(sql,null)) { while(c.moveToNext()) out.add(drawing(c)); }
        return out;
    }

    public Record getAnomaly(String id) { try(Cursor c=helper.getReadableDatabase().rawQuery("SELECT a.*, (SELECT COUNT(*) FROM repair_sessions s WHERE s.anomaly_id=a.id) sc, (SELECT COUNT(*) FROM work_entries w WHERE w.entity_id=a.id AND w.entity_type='ANOMALY') ec FROM anomalies a WHERE a.id=?",new String[]{id})) { return c.moveToFirst()?anomaly(c):null; } }
    public Record getDrawing(String id) { try(Cursor c=helper.getReadableDatabase().rawQuery("SELECT d.*, (SELECT COUNT(*) FROM drawing_work_sessions s WHERE s.drawing_id=d.id) sc, (SELECT COUNT(*) FROM drawing_revisions r WHERE r.drawing_id=d.id) rc, (SELECT COUNT(*) FROM drawing_work_entries w WHERE w.drawing_id=d.id) ec FROM drawings d WHERE d.id=?",new String[]{id})) { return c.moveToFirst()?drawing(c):null; } }

    public void updateAnomalyStatus(String anomalyId, String newStatus, String operator, String reason) {
        SQLiteDatabase db=helper.getWritableDatabase(); db.beginTransaction();
        try {
            String old=null; try(Cursor c=db.rawQuery("SELECT status FROM anomalies WHERE id=?",new String[]{anomalyId})) { if(c.moveToFirst()) old=c.getString(0); }
            if(old==null) throw new IllegalArgumentException("Không tìm thấy hồ sơ bất thường");
            long now=System.currentTimeMillis(); ContentValues v=cv(); v.put("status",newStatus); v.put("updated_at",now);
            if("Đã xử lý".equals(newStatus)) v.put("resolved_at",now); else v.putNull("resolved_at");
            if("Chưa xử lý".equals(old) && "Đã xử lý".equals(newStatus)) v.put("reopened",0);
            if("Đã xử lý".equals(old) && !"Đã xử lý".equals(newStatus)) v.put("reopened",1);
            db.update("anomalies",v,"id=?",new String[]{anomalyId});
            history(db,anomalyId,old,newStatus,"STATUS_CHANGE",now,operator,reason);
            audit(db,"ANOMALY",anomalyId,"STATUS_CHANGE",old,newStatus,now,operator,reason);
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    public String addAnomalyWork(String anomalyId, WorkInput input) {
        if(input.description==null || input.description.trim().isEmpty()) throw new IllegalArgumentException("Mô tả công việc không được trống");
        SQLiteDatabase db=helper.getWritableDatabase(); db.beginTransaction();
        try {
            long now=System.currentTimeMillis(); ShiftOccurrence shift=resolveShift(db,input.workAt,input.explicitShiftId);
            String sessionId=null;
            if(shift!=null) sessionId=findSession(db,"repair_sessions","anomaly_id",anomalyId,shift.id);
            if(sessionId==null) {
                int next=maxSession(db,"repair_sessions","anomaly_id",anomalyId)+1; sessionId=id();
                ContentValues s=cv(); s.put("id",sessionId); s.put("anomaly_id",anomalyId); s.put("session_no",next);
                if(shift!=null) { s.put("shift_occurrence_id",shift.id); s.put("shift_code",shift.code); s.put("shift_start",shift.startMillis); s.put("shift_end",shift.endMillis); s.put("roster_snapshot",shift.roster); }
                s.put("participants",input.participants); s.put("started_at",input.workAt); s.put("last_updated_at",now); s.put("result_status", "Chưa xử lý");
                db.insertOrThrow("repair_sessions",null,s);
            }
            ContentValues w=cv(); String workId=id(); w.put("id",workId); w.put("entity_type","ANOMALY"); w.put("entity_id",anomalyId); w.put("session_id",sessionId);
            w.put("work_at",input.workAt); w.put("recorded_at",now); w.put("work_type",input.workType); w.put("description",input.description);
            w.put("actions",input.actions); w.put("findings",input.findings); w.put("result",input.result); w.put("author",input.author);
            w.put("participants",input.participants); w.put("shift_snapshot",shift==null?"Chưa xác định ca":shift.code+" | "+shift.shiftDate+" | "+shift.roster);
            w.put("ref_name",blankToNull(input.refName)); w.put("ref_code",blankToNull(input.refCode));
            db.insertOrThrow("work_entries",null,w);
            ContentValues up=cv(); up.put("last_updated_at",now); if(input.result!=null) up.put("result_status",input.result); db.update("repair_sessions",up,"id=?",new String[]{sessionId});
            String status=null;
            if("Đã xử lý".equals(input.result)) status="Đã xử lý";
            else if("Xử lý nhưng chưa hoàn thành".equals(input.result)) status="Đang xử lý";
            if(status!=null) updateStatusInTransaction(db,anomalyId,status,input.author,"Cập nhật từ công việc: "+input.description,now);
            ContentValues a=cv(); a.put("updated_at",now); db.update("anomalies",a,"id=?",new String[]{anomalyId});
            db.setTransactionSuccessful(); return workId;
        } finally { db.endTransaction(); }
    }

    public String addDrawingWork(String drawingId, WorkInput input) {
        if(input.description==null || input.description.trim().isEmpty()) throw new IllegalArgumentException("Mô tả công việc không được trống");
        SQLiteDatabase db=helper.getWritableDatabase(); db.beginTransaction();
        try {
            long now=System.currentTimeMillis(); ShiftOccurrence shift=resolveShift(db,input.workAt,input.explicitShiftId);
            String sessionId=null; if(shift!=null) sessionId=findSession(db,"drawing_work_sessions","drawing_id",drawingId,shift.id);
            if(sessionId==null) {
                int next=maxSession(db,"drawing_work_sessions","drawing_id",drawingId)+1; sessionId=id();
                ContentValues s=cv(); s.put("id",sessionId); s.put("drawing_id",drawingId); s.put("session_no",next);
                if(shift!=null) { s.put("shift_occurrence_id",shift.id); s.put("shift_code",shift.code); s.put("shift_start",shift.startMillis); s.put("shift_end",shift.endMillis); s.put("roster_snapshot",shift.roster); }
                s.put("participants",input.participants); s.put("started_at",input.workAt); s.put("last_updated_at",now); db.insertOrThrow("drawing_work_sessions",null,s);
            }
            String workId=id(); ContentValues w=cv(); w.put("id",workId); w.put("drawing_work_session_id",sessionId); w.put("drawing_id",drawingId);
            w.put("work_at",input.workAt); w.put("recorded_at",now); w.put("work_type",input.workType); w.put("description",input.description);
            w.put("actions",input.actions); w.put("findings",input.findings); w.put("result",input.result); w.put("author",input.author);
            w.put("participants",input.participants); w.put("shift_snapshot",shift==null?"Chưa xác định ca":shift.code+" | "+shift.shiftDate+" | "+shift.roster);
            w.put("ref_name",blankToNull(input.refName)); w.put("ref_code",blankToNull(input.refCode));
            db.insertOrThrow("drawing_work_entries",null,w);
            ContentValues up=cv(); up.put("last_updated_at",now); db.update("drawing_work_sessions",up,"id=?",new String[]{sessionId});
            ContentValues d=cv(); d.put("updated_at",now); db.update("drawings",d,"id=?",new String[]{drawingId});
            db.setTransactionSuccessful(); return workId;
        } finally { db.endTransaction(); }
    }

    public String addDrawingRevision(String drawingId, String previous, String next, long revisionAt,
                                     String description, String reason, String affectedArea,
                                     String author, String reviewers, String notes) {
        if(next==null || next.trim().isEmpty()) throw new IllegalArgumentException("Revision mới là bắt buộc");
        SQLiteDatabase db=helper.getWritableDatabase(); db.beginTransaction();
        try {
            long now=System.currentTimeMillis(); String id=id(); ContentValues v=cv(); v.put("id",id); v.put("drawing_id",drawingId);
            v.put("revision_label",next.trim()); v.put("previous_revision_label",blankToNull(previous)); v.put("revision_date",revisionAt); v.put("recorded_at",now);
            v.put("change_description",description); v.put("change_reason",reason); v.put("affected_area",affectedArea); v.put("author",author); v.put("reviewers",reviewers); v.put("notes",notes);
            db.insertOrThrow("drawing_revisions",null,v);
            ContentValues d=cv(); d.put("current_revision",next.trim()); d.put("updated_at",now); db.update("drawings",d,"id=?",new String[]{drawingId});
            audit(db,"DRAWING_REVISION",id,"CREATE",previous,next.trim(),now,author,description);
            db.setTransactionSuccessful(); return id;
        } finally { db.endTransaction(); }
    }

    public List<HistoryItem> history(String entityType, String entityId) {
        List<HistoryItem> out=new ArrayList<>(); SQLiteDatabase db=helper.getReadableDatabase();
        String sql="ANOMALY".equals(entityType)?"SELECT work_at,recorded_at,work_type,description,actions,findings,result,shift_snapshot,author,id,ref_name,ref_code FROM work_entries WHERE entity_id=? ORDER BY work_at":"SELECT work_at,recorded_at,work_type,description,actions,findings,result,shift_snapshot,author,id,ref_name,ref_code FROM drawing_work_entries WHERE drawing_id=? ORDER BY work_at";
        try(Cursor c=db.rawQuery(sql,new String[]{entityId})) { while(c.moveToNext()) { HistoryItem h=new HistoryItem(); h.workAt=c.getLong(0); h.recordedAt=c.getLong(1); h.type=c.getString(2); h.description=c.getString(3); h.actions=c.getString(4); h.findings=c.getString(5); h.result=c.getString(6); h.shift=c.getString(7); h.author=c.getString(8); h.id=c.getString(9); h.refName=c.getString(10); h.refCode=c.getString(11); out.add(h); } }
        return out;
    }

    // ---------------------------------------------------------------- thống kê
    static final String SQL_STATS_ITEMS = "SELECT id,code,title,description,equipment_name,kks,system_area,severity,status,detected_at,resolved_at,reopened FROM anomalies WHERE deleted_at IS NULL";

    /** Thống kê bất thường: tồn tại hiện nay + phát sinh mới / đã xử lý trong khoảng [from, to). Tính offline trên SQLite của app. */
    public StatsReport stats(long from, long to, long now) {
        List<StatsReport.Item> items = new ArrayList<>();
        try (Cursor c = helper.getReadableDatabase().rawQuery(SQL_STATS_ITEMS, null)) {
            while (c.moveToNext()) {
                StatsReport.Item i = new StatsReport.Item();
                i.id = c.getString(0); i.code = c.getString(1); i.title = c.getString(2); i.description = c.getString(3); i.equipment = c.getString(4); i.kks = c.getString(5); i.system = c.getString(6);
                i.severity = c.getString(7); i.status = c.getString(8); i.detectedAt = c.getLong(9); i.resolvedAt = c.isNull(10) ? 0 : c.getLong(10); i.reopened = c.getInt(11) > 0;
                items.add(i);
            }
        }
        return StatsCalculator.compute(items, from, to, now, vn.evnqt1.helpdesk.core.ScheduleRules.ZONE);
    }

    /** Gợi ý tối đa {@code max} bất thường liên quan nhất theo tên thiết bị + KKS (+ nội dung). Danh sách rỗng nếu không có hồ sơ đủ gần. */
    public List<Suggestion> suggestAnomalies(String equipment, String kks, String content, int max) {
        List<Record> all=listAnomalies(false); java.util.HashMap<String,Record> byId=new java.util.HashMap<>(); List<MatchSuggester.Candidate> cands=new ArrayList<>();
        for(Record r:all){ byId.put(r.id,r); cands.add(new MatchSuggester.Candidate(r.id,r.equipmentName,r.kks,n(r.title)+" "+n(r.description),!"Đã xử lý".equals(r.status),r.updatedAt)); }
        return toSuggestions(MatchSuggester.rankAnomalies(equipment,kks,content,cands,max),byId);
    }

    /** Gợi ý bản vẽ theo số bản vẽ hoặc tên/tiêu đề (+ nội dung). */
    public List<Suggestion> suggestDrawings(String number, String title, String content, int max) {
        List<Record> all=listDrawings(false); java.util.HashMap<String,Record> byId=new java.util.HashMap<>(); List<MatchSuggester.Candidate> cands=new ArrayList<>();
        for(Record r:all){ byId.put(r.id,r); cands.add(new MatchSuggester.Candidate(r.id,r.drawingNumber,r.title,n(r.description)+" "+n(r.discipline)+" "+n(r.systemArea),true,r.updatedAt)); }
        return toSuggestions(MatchSuggester.rankDrawings(number,title,content,cands,max),byId);
    }

    private static List<Suggestion> toSuggestions(List<MatchSuggester.Match> matches,java.util.Map<String,Record> byId) {
        List<Suggestion> out=new ArrayList<>();
        for(MatchSuggester.Match m:matches){ Record r=byId.get(m.candidate.id); if(r==null) continue; Suggestion s=new Suggestion(); s.record=r; s.score=m.score; s.reason=m.reason; out.add(s); }
        return out;
    }
    private static String n(String s){return s==null?"":s;}

    public List<String[]> revisions(String drawingId) {
        List<String[]> out=new ArrayList<>(); try(Cursor c=helper.getReadableDatabase().rawQuery("SELECT revision_label,previous_revision_label,revision_date,change_description,change_reason,affected_area,author,reviewers,notes FROM drawing_revisions WHERE drawing_id=? ORDER BY revision_date DESC",new String[]{drawingId})) { while(c.moveToNext()) { String[] row=new String[9]; for(int i=0;i<9;i++) row[i]=c.isNull(i)?"":c.getString(i); out.add(row); } } return out;
    }

    public void addShift(ShiftInput shift) {
        if(shift.endAt<=shift.startAt) throw new IllegalArgumentException("Giờ kết thúc phải sau giờ bắt đầu");
        long now=System.currentTimeMillis(); ContentValues v=cv(); v.put("id",id()); v.put("shift_date",shift.date); v.put("shift_code",shift.code); v.put("start_at",shift.startAt); v.put("end_at",shift.endAt); v.put("roster",shift.roster); v.put("note",shift.note); v.put("slot",shift.slot); v.put("source",shift.source==null?"manual":shift.source); v.put("created_at",now); v.put("updated_at",now);
        helper.getWritableDatabase().insertOrThrow("shift_occurrences",null,v);
    }

    /**
     * Lưu lịch ca của một tháng từ bản nháp (tệp Excel/CSV): cập nhật tại chỗ các lần ca trùng (ngày + ca) để các phiên công việc
     * đã gắn vẫn giữ liên kết; xóa các lần ca khác nằm trong khoảng thời gian của tháng để không bị chồng lấn.
     * Trả về {thêm mới, cập nhật, xóa}.
     */
    public int[] replaceMonthSchedule(List<vn.evnqt1.helpdesk.core.ScheduleRules.Occurrence> items, String note) {
        if(items==null||items.isEmpty()) throw new IllegalArgumentException("Không có lần ca nào để lưu");
        long from=Long.MAX_VALUE,to=Long.MIN_VALUE; for(vn.evnqt1.helpdesk.core.ScheduleRules.Occurrence o:items){from=Math.min(from,o.startMillis);to=Math.max(to,o.endMillis);}
        SQLiteDatabase db=helper.getWritableDatabase(); int added=0,updated=0,removed=0; db.beginTransaction();
        try {
            long now=System.currentTimeMillis(); java.util.HashSet<String> keep=new java.util.HashSet<>();
            for(vn.evnqt1.helpdesk.core.ScheduleRules.Occurrence o:items) {
                String existing=null; try(Cursor c=db.rawQuery("SELECT id FROM shift_occurrences WHERE shift_date=? AND slot=? LIMIT 1",new String[]{o.date,o.slot})) { if(c.moveToFirst()) existing=c.getString(0); }
                ContentValues v=cv(); v.put("shift_code",o.code); v.put("start_at",o.startMillis); v.put("end_at",o.endMillis); v.put("roster",o.roster); v.put("note",note); v.put("source","ai_image"); v.put("updated_at",now);
                if(existing!=null) { db.update("shift_occurrences",v,"id=?",new String[]{existing}); keep.add(existing); updated++; }
                else { String nid=id(); v.put("id",nid); v.put("shift_date",o.date); v.put("slot",o.slot); v.put("created_at",now); db.insertOrThrow("shift_occurrences",null,v); keep.add(nid); added++; }
            }
            java.util.ArrayList<String> stale=new java.util.ArrayList<>();
            try(Cursor c=db.rawQuery("SELECT id FROM shift_occurrences WHERE start_at<? AND end_at>?",new String[]{String.valueOf(to),String.valueOf(from)})) { while(c.moveToNext()) if(!keep.contains(c.getString(0))) stale.add(c.getString(0)); }
            for(String sid:stale) { db.delete("shift_occurrences","id=?",new String[]{sid}); removed++; }
            audit(db,"SHIFT_SCHEDULE","month","IMPORT",null,added+"/"+updated+"/"+removed,now,"",note);
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
        return new int[]{added,updated,removed};
    }

    /** Đếm các lần ca đã có sẽ bị ghi đè/xóa nếu lưu khoảng thời gian này (để cảnh báo người dùng). */
    public int countShiftsOverlapping(long from,long to) {
        try(Cursor c=helper.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM shift_occurrences WHERE start_at<? AND end_at>?",new String[]{String.valueOf(to),String.valueOf(from)})) { return c.moveToFirst()?c.getInt(0):0; }
    }

    /** Xóa toàn bộ lần ca trong khoảng [from,to); phiên công việc cũ vẫn giữ ảnh chụp ca/nhân sự. */
    public int deleteShiftsBetween(long from,long to) {
        return helper.getWritableDatabase().delete("shift_occurrences","start_at>=? AND start_at<?",new String[]{String.valueOf(from),String.valueOf(to)});
    }

    /** Cập nhật danh sách nhân sự của một kíp cho các lần ca trong khoảng [from,to). */
    public int updateCrewRoster(String code,String roster,long from,long to) {
        ContentValues v=cv(); v.put("roster",roster); v.put("updated_at",System.currentTimeMillis());
        return helper.getWritableDatabase().update("shift_occurrences",v,"shift_code=? AND start_at>=? AND start_at<?",new String[]{code,String.valueOf(from),String.valueOf(to)});
    }

    /** Các lần ca có thời điểm bắt đầu trong [from,to), sắp theo thời gian tăng dần. */
    public List<ShiftOccurrence> shiftsBetween(long from,long to) {
        List<ShiftOccurrence> out=new ArrayList<>();
        try(Cursor c=helper.getReadableDatabase().rawQuery("SELECT id,shift_date,shift_code,start_at,end_at,roster,slot FROM shift_occurrences WHERE start_at>=? AND start_at<? ORDER BY start_at",new String[]{String.valueOf(from),String.valueOf(to)})) { while(c.moveToNext()) out.add(new ShiftOccurrence(c.getString(0),c.getString(1),c.getString(2),c.getLong(3),c.getLong(4),c.getString(5),c.getString(6))); }
        return out;
    }

    /** Lần ca đang diễn ra tại thời điểm now, hoặc null nếu chưa có lịch/chồng lấn. */
    public ShiftOccurrence currentShift(long now) {
        List<ShiftOccurrence> list=new ArrayList<>();
        try(Cursor c=helper.getReadableDatabase().rawQuery("SELECT id,shift_date,shift_code,start_at,end_at,roster,slot FROM shift_occurrences WHERE start_at<=? AND end_at>?",new String[]{String.valueOf(now),String.valueOf(now)})) { while(c.moveToNext()) list.add(new ShiftOccurrence(c.getString(0),c.getString(1),c.getString(2),c.getLong(3),c.getLong(4),c.getString(5),c.getString(6))); }
        ShiftResolver.Result r=ShiftResolver.resolve(now,list); return r.kind==ShiftResolver.Result.Kind.MATCHED?r.occurrence:null;
    }

    /** Đổi giờ bắt đầu/kết thúc của các ca có phân loại Sáng/Chiều/Đêm trong [from,to) theo giờ ca mới (HH:mm). */
    public int retimeSlots(long from,long to,String[] start,String[] end) {
        SQLiteDatabase db=helper.getWritableDatabase(); int changed=0; db.beginTransaction();
        try {
            java.util.ArrayList<String[]> rows=new java.util.ArrayList<>();
            try(Cursor c=db.rawQuery("SELECT id,shift_date,slot FROM shift_occurrences WHERE slot IS NOT NULL AND start_at>=? AND start_at<?",new String[]{String.valueOf(from),String.valueOf(to)})) { while(c.moveToNext()) rows.add(new String[]{c.getString(0),c.getString(1),c.getString(2)}); }
            String[] keys=vn.evnqt1.helpdesk.core.ScheduleDraft.SLOT_KEYS;
            for(String[] r:rows) {
                int s=-1; for(int i=0;i<3;i++) if(keys[i].equals(r[2])) s=i; if(s<0) continue;
                java.time.LocalTime st=vn.evnqt1.helpdesk.core.ScheduleRules.parseTime(start[s]), en=vn.evnqt1.helpdesk.core.ScheduleRules.parseTime(end[s]); if(st==null||en==null) continue;
                java.time.LocalDate date=java.time.LocalDate.parse(r[1]);
                java.time.LocalDateTime a=java.time.LocalDateTime.of(date,st), b=java.time.LocalDateTime.of(date,en); if(!b.isAfter(a)) b=b.plusDays(1);
                ContentValues v=cv(); v.put("start_at",a.atZone(vn.evnqt1.helpdesk.core.ScheduleRules.ZONE).toInstant().toEpochMilli()); v.put("end_at",b.atZone(vn.evnqt1.helpdesk.core.ScheduleRules.ZONE).toInstant().toEpochMilli()); v.put("updated_at",System.currentTimeMillis());
                changed+=db.update("shift_occurrences",v,"id=?",new String[]{r[0]});
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
        return changed;
    }

    public int countShifts() { return (int)count("shift_occurrences",null); }

    public List<ShiftOccurrence> shifts() {
        List<ShiftOccurrence> out=new ArrayList<>(); try(Cursor c=helper.getReadableDatabase().rawQuery("SELECT id,shift_date,shift_code,start_at,end_at,roster,slot FROM shift_occurrences ORDER BY start_at DESC",null)) { while(c.moveToNext()) out.add(new ShiftOccurrence(c.getString(0),c.getString(1),c.getString(2),c.getLong(3),c.getLong(4),c.getString(5),c.getString(6))); } return out;
    }

    public void refreshAlerts(long now) {
        SQLiteDatabase db=helper.getWritableDatabase(); List<AlertRuleEngine.AnomalySnapshot> rows=new ArrayList<>();
        try(Cursor c=db.rawQuery("SELECT id,CASE WHEN kks IS NOT NULL AND TRIM(kks)!='' THEN kks ELSE equipment_name END,description,status,severity,detected_at,updated_at,reopened FROM anomalies WHERE deleted_at IS NULL",null)) {
            while(c.moveToNext()) rows.add(new AlertRuleEngine.AnomalySnapshot(c.getString(0),c.getString(1),c.getString(2),c.getString(3),c.getString(4),c.getLong(5),c.getLong(6),c.getInt(7)==1));
        }
        long overdue=settingLong(db,"alert_overdue_days",7)*86_400_000L, stale=settingLong(db,"alert_stale_days",3)*86_400_000L;
        int repeat=(int)settingLong(db,"alert_repeat_count",3); long window=settingLong(db,"alert_repeat_days",30)*86_400_000L;
        List<AlertRuleEngine.Candidate> candidates=AlertRuleEngine.evaluate(rows,now,overdue,stale,repeat,window);
        db.beginTransaction();
        try {
            java.util.HashSet<String> active=new java.util.HashSet<>();
            for(AlertRuleEngine.Candidate candidate:candidates) {
                String key=candidate.dedupeKey; active.add(key); String existing=null;
                try(Cursor c=db.rawQuery("SELECT id FROM alerts WHERE deduplication_key=? AND condition_status='active'",new String[]{key})) { if(c.moveToFirst()) existing=c.getString(0); }
                if(existing==null) {
                    ContentValues v=cv(); v.put("id",id()); v.put("anomaly_id",candidate.anomalyId); v.put("alert_type",candidate.type); v.put("severity",candidate.priority);
                    v.put("triggered_at",now); v.put("last_triggered_at",now); v.put("reason_snapshot",candidate.reason); v.put("threshold_snapshot","Local rule engine"); v.put("deduplication_key",key);
                    v.put("is_read",0); v.put("condition_status","active"); v.put("created_at",now); v.put("updated_at",now); db.insertOrThrow("alerts",null,v);
                } else { ContentValues v=cv(); v.put("last_triggered_at",now); v.put("reason_snapshot",candidate.reason); v.put("updated_at",now); db.update("alerts",v,"id=?",new String[]{existing}); }
            }
            try(Cursor c=db.rawQuery("SELECT id,deduplication_key FROM alerts WHERE condition_status='active'",null)) {
                while(c.moveToNext()) if(!active.contains(c.getString(1))) { ContentValues v=cv(); v.put("condition_status","expired"); v.put("expired_at",now); v.put("updated_at",now); db.update("alerts",v,"id=?",new String[]{c.getString(0)}); }
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    public List<AlertItem> alerts() {
        List<AlertItem> out=new ArrayList<>(); String sql="SELECT al.id,al.anomaly_id,a.code,a.title,al.alert_type,al.reason_snapshot,al.condition_status,al.severity,al.is_read,al.triggered_at FROM alerts al JOIN anomalies a ON a.id=al.anomaly_id ORDER BY al.triggered_at DESC";
        try(Cursor c=helper.getReadableDatabase().rawQuery(sql,null)) { while(c.moveToNext()) { AlertItem x=new AlertItem(); x.id=c.getString(0);x.anomalyId=c.getString(1);x.anomalyCode=c.getString(2);x.title=c.getString(3);x.type=c.getString(4);x.reason=c.getString(5);x.condition=c.getString(6);x.priority=c.getInt(7);x.isRead=c.getInt(8);x.triggeredAt=c.getLong(9);out.add(x);} } return out;
    }
    public void markAlertRead(String alertId) { ContentValues v=cv();v.put("is_read",1);v.put("read_at",System.currentTimeMillis());helper.getWritableDatabase().update("alerts",v,"id=?",new String[]{alertId}); }

    public void softDelete(String type, String entityId) { String table="ANOMALY".equals(type)?"anomalies":"drawings"; ContentValues v=cv();v.put("deleted_at",System.currentTimeMillis());v.put("updated_at",System.currentTimeMillis());helper.getWritableDatabase().update(table,v,"id=?",new String[]{entityId}); }
    public void restore(String type, String entityId) { String table="ANOMALY".equals(type)?"anomalies":"drawings"; ContentValues v=cv();v.putNull("deleted_at");v.put("updated_at",System.currentTimeMillis());helper.getWritableDatabase().update(table,v,"id=?",new String[]{entityId}); }
    public List<Record> trash() { List<Record> all=new ArrayList<>(); all.addAll(listAnomalies(true)); all.removeIf(r->r.deletedAt==0); all.addAll(listDrawings(true)); all.removeIf(r->r.deletedAt==0); return all; }
    public void cleanupTrash(long now) {
        SQLiteDatabase db=helper.getWritableDatabase(); java.util.ArrayList<String> obsoletePaths=new java.util.ArrayList<>();
        db.beginTransaction(); try {
            String cutoff=String.valueOf(now-30L*86_400_000L);
            collectExpiredAttachments(db,"ANOMALY", "anomalies", cutoff, obsoletePaths);
            collectExpiredAttachments(db,"DRAWING", "drawings", cutoff, obsoletePaths);
            db.delete("anomalies","deleted_at IS NOT NULL AND deleted_at<?",new String[]{cutoff});
            db.delete("drawings","deleted_at IS NOT NULL AND deleted_at<?",new String[]{cutoff});
            db.setTransactionSuccessful();
        } finally {db.endTransaction();}
        for(String path:obsoletePaths){
            boolean referenced=false;try(Cursor c=db.rawQuery("SELECT 1 FROM attachments WHERE local_path=? LIMIT 1",new String[]{path})){referenced=c.moveToFirst();}
            if(!referenced)try{new java.io.File(path).delete();}catch(Exception ignored){}
        }
    }
    private static void collectExpiredAttachments(SQLiteDatabase db,String ownerType,String table,String cutoff,List<String> paths){
        java.util.ArrayList<String[]> rows=new java.util.ArrayList<>();
        try(Cursor c=db.rawQuery("SELECT at.local_path,at.id FROM attachments at JOIN "+table+" p ON p.id=at.owner_id WHERE at.owner_type=? AND p.deleted_at IS NOT NULL AND p.deleted_at<?",new String[]{ownerType,cutoff})){
            while(c.moveToNext())rows.add(new String[]{c.getString(0),c.getString(1)});
        }
        for(String[] row:rows){paths.add(row[0]);db.delete("attachments","id=?",new String[]{row[1]});}
    }

    public long count(String table, String where) { String sql="SELECT COUNT(*) FROM "+table+(where==null?"":" WHERE "+where); try(Cursor c=helper.getReadableDatabase().rawQuery(sql,null)){return c.moveToFirst()?c.getLong(0):0;} }
    public void setSetting(String key,String value) { ContentValues v=cv();v.put("key",key);v.put("value",value);v.put("updated_at",System.currentTimeMillis());helper.getWritableDatabase().insertWithOnConflict("app_settings",null,v,SQLiteDatabase.CONFLICT_REPLACE); }
    public String getSetting(String key,String fallback) { try(Cursor c=helper.getReadableDatabase().rawQuery("SELECT value FROM app_settings WHERE key=?",new String[]{key})){return c.moveToFirst()?c.getString(0):fallback;} }

    public List<Record> search(String query) {
        String q=TextNormalizer.normalize(query); if(q.isEmpty()) return Collections.emptyList(); List<Record> out=new ArrayList<>();
        for(Record r:listAnomalies(false)) if(TextNormalizer.contains(r.code+" "+r.title+" "+r.description+" "+r.equipmentName+" "+r.kks+" "+r.location+" "+r.systemArea,q)) out.add(r);
        for(Record r:listDrawings(false)) if(TextNormalizer.contains(r.code+" "+r.drawingNumber+" "+r.title+" "+r.description+" "+r.discipline+" "+r.systemArea,q)) out.add(r);
        return out;
    }

    private static void updateStatusInTransaction(SQLiteDatabase db,String id,String status,String operator,String details,long now) {
        String old=null; try(Cursor c=db.rawQuery("SELECT status FROM anomalies WHERE id=?",new String[]{id})){if(c.moveToFirst())old=c.getString(0);} if(old==null || old.equals(status))return;
        ContentValues v=cv();v.put("status",status);v.put("updated_at",now);if("Đã xử lý".equals(status))v.put("resolved_at",now);else v.putNull("resolved_at");
        if("Đã xử lý".equals(old)&&!"Đã xử lý".equals(status))v.put("reopened",1); db.update("anomalies",v,"id=?",new String[]{id});history(db,id,old,status,"STATUS_CHANGE",now,operator,details);
    }
    private static void history(SQLiteDatabase db,String anomalyId,String old,String next,String action,long time,String operator,String details) {ContentValues h=cv();h.put("id",id());h.put("anomaly_id",anomalyId);h.put("old_value",old);h.put("new_value",next);h.put("action",action);h.put("timestamp",time);h.put("operator",operator);h.put("details",details);db.insert("status_history",null,h);}
    private static void audit(SQLiteDatabase db,String type,String entity,String action,String old,String next,long time,String operator,String details) {ContentValues v=cv();v.put("id",id());v.put("entity_type",type);v.put("entity_id",entity);v.put("action",action);v.put("old_value",old);v.put("new_value",next);v.put("timestamp",time);v.put("operator",operator);v.put("details",details);db.insert("audit_log",null,v);}
    private static String nextCode(SQLiteDatabase db,String table,String column,String prefix) {int max=0;try(Cursor c=db.rawQuery("SELECT "+column+" FROM "+table+" WHERE "+column+" LIKE ?",new String[]{prefix+"%"})){while(c.moveToNext()){String s=c.getString(0);try{max=Math.max(max,Integer.parseInt(s.substring(prefix.length())));}catch(Exception ignored){}}}return prefix+String.format(java.util.Locale.US,"%06d",max+1);}
    private static String blankToNull(String s){return s==null||s.trim().isEmpty()?null:s.trim();}
    private static long settingLong(SQLiteDatabase db,String key,long fallback){try(Cursor c=db.rawQuery("SELECT value FROM app_settings WHERE key=?",new String[]{key})){return c.moveToFirst()?Long.parseLong(c.getString(0)):fallback;}catch(Exception e){return fallback;}}
    private static int maxSession(SQLiteDatabase db,String table,String fk,String id){try(Cursor c=db.rawQuery("SELECT COALESCE(MAX(session_no),0) FROM "+table+" WHERE "+fk+"=?",new String[]{id})){return c.moveToFirst()?c.getInt(0):0;}}
    private static String findSession(SQLiteDatabase db,String table,String fk,String entityId,String shiftId){try(Cursor c=db.rawQuery("SELECT id FROM "+table+" WHERE "+fk+"=? AND shift_occurrence_id=?",new String[]{entityId,shiftId})){return c.moveToFirst()?c.getString(0):null;}}
    private static ShiftOccurrence resolveShift(SQLiteDatabase db,long workAt,String explicitId){
        if(explicitId!=null&&!explicitId.isEmpty()){try(Cursor c=db.rawQuery("SELECT id,shift_date,shift_code,start_at,end_at,roster FROM shift_occurrences WHERE id=?",new String[]{explicitId})){if(c.moveToFirst())return new ShiftOccurrence(c.getString(0),c.getString(1),c.getString(2),c.getLong(3),c.getLong(4),c.getString(5));}}
        List<ShiftOccurrence> shifts=new ArrayList<>();try(Cursor c=db.rawQuery("SELECT id,shift_date,shift_code,start_at,end_at,roster FROM shift_occurrences WHERE start_at<=? AND end_at>?",new String[]{String.valueOf(workAt),String.valueOf(workAt)})){while(c.moveToNext())shifts.add(new ShiftOccurrence(c.getString(0),c.getString(1),c.getString(2),c.getLong(3),c.getLong(4),c.getString(5)));}
        ShiftResolver.Result r=ShiftResolver.resolve(workAt,shifts);return r.kind==ShiftResolver.Result.Kind.MATCHED?r.occurrence:null;
    }
    private static Record anomaly(Cursor c){Record r=new Record();r.id=c.getString(c.getColumnIndexOrThrow("id"));r.code=c.getString(c.getColumnIndexOrThrow("code"));r.title=c.getString(c.getColumnIndexOrThrow("title"));r.description=c.getString(c.getColumnIndexOrThrow("description"));r.equipmentName=c.getString(c.getColumnIndexOrThrow("equipment_name"));r.kks=c.getString(c.getColumnIndexOrThrow("kks"));r.systemArea=c.getString(c.getColumnIndexOrThrow("system_area"));r.location=c.getString(c.getColumnIndexOrThrow("location"));r.severity=c.getString(c.getColumnIndexOrThrow("severity"));r.status=c.getString(c.getColumnIndexOrThrow("status"));r.responsible=c.getString(c.getColumnIndexOrThrow("responsible"));r.detectedAt=c.getLong(c.getColumnIndexOrThrow("detected_at"));r.createdAt=c.getLong(c.getColumnIndexOrThrow("created_at"));r.updatedAt=c.getLong(c.getColumnIndexOrThrow("updated_at"));r.deletedAt=c.isNull(c.getColumnIndexOrThrow("deleted_at"))?0:c.getLong(c.getColumnIndexOrThrow("deleted_at"));r.sessionCount=c.getInt(c.getColumnIndexOrThrow("sc"));r.entryCount=c.getInt(c.getColumnIndexOrThrow("ec"));return r;}
    private static Record drawing(Cursor c){Record r=new Record();r.id=c.getString(c.getColumnIndexOrThrow("id"));r.code=c.getString(c.getColumnIndexOrThrow("code"));r.drawingNumber=c.getString(c.getColumnIndexOrThrow("drawing_number"));r.title=c.getString(c.getColumnIndexOrThrow("title"));r.description=c.getString(c.getColumnIndexOrThrow("description"));r.discipline=c.getString(c.getColumnIndexOrThrow("discipline"));r.systemArea=c.getString(c.getColumnIndexOrThrow("system_area"));r.currentRevision=c.getString(c.getColumnIndexOrThrow("current_revision"));r.createdAt=c.getLong(c.getColumnIndexOrThrow("created_at"));r.updatedAt=c.getLong(c.getColumnIndexOrThrow("updated_at"));r.deletedAt=c.isNull(c.getColumnIndexOrThrow("deleted_at"))?0:c.getLong(c.getColumnIndexOrThrow("deleted_at"));r.sessionCount=c.getInt(c.getColumnIndexOrThrow("sc"));r.revisionCount=c.getInt(c.getColumnIndexOrThrow("rc"));r.entryCount=c.getInt(c.getColumnIndexOrThrow("ec"));return r;}
}
