package vn.evnqt1.helpdesk.data;

import android.content.Context;
import android.net.Uri;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Offline export of reports. Scope: ALL, ANOMALY, DRAWING. */
public final class ReportExporter {
    private final Context context; private final HelpDeskRepository repo;
    public ReportExporter(Context c,HelpDeskRepository r){context=c;repo=r;}
    private static final SimpleDateFormat DATE=new SimpleDateFormat("dd/MM/yyyy HH:mm",new Locale("vi","VN"));
    static { DATE.setTimeZone(java.util.TimeZone.getTimeZone("Asia/Ho_Chi_Minh")); }
    private List<String[]> rows(String scope,long from,long to){
        List<String[]> out=new ArrayList<>();
        out.add(new String[]{"EVNQT1 HELP DESK - BÁO CÁO", "Phạm vi: "+scope, "Tạo lúc: "+DATE.format(new Date())});
        String[] header={"Loại dữ liệu","Mã hồ sơ","Thiết bị / Số bản vẽ","Tiêu đề / Nội dung","Ngày giờ","Trạng thái / Revision","Người / Ca","Ghi chú"};
        if(!"DRAWING".equals(scope)){
            out.add(new String[]{"I. BẤT THƯỜNG THIẾT BỊ"});out.add(header);
            for(HelpDeskRepository.Record r:repo.listAnomalies(false)){
                if(r.detectedAt>=from&&r.detectedAt<=to)out.add(new String[]{"Hồ sơ bất thường",r.code,n(r.equipmentName),n(r.title),DATE.format(new Date(r.detectedAt)),n(r.status)+" / "+n(r.severity),n(r.responsible),n(r.description)});
                for(HelpDeskRepository.HistoryItem h:repo.history("ANOMALY",r.id))if(h.workAt>=from&&h.workAt<=to)
                    out.add(new String[]{"Công việc bất thường",r.code,n(r.equipmentName),n(h.description),DATE.format(new Date(h.workAt)),n(h.result),n(h.author)+" / "+n(h.shift),n(h.actions)+" "+n(h.findings)});
            }
        }
        if(!"ANOMALY".equals(scope)){
            out.add(new String[]{"II. CÔNG VIỆC VÀ CHỈNH SỬA BẢN VẼ"});out.add(header);
            for(HelpDeskRepository.Record r:repo.listDrawings(false)){
                if(r.updatedAt>=from&&r.updatedAt<=to)out.add(new String[]{"Hồ sơ bản vẽ",r.code,n(r.drawingNumber),n(r.title),DATE.format(new Date(r.updatedAt)),"Revision hiện tại: "+n(r.currentRevision),n(r.discipline),n(r.description)});
                for(HelpDeskRepository.HistoryItem h:repo.history("DRAWING",r.id))if(h.workAt>=from&&h.workAt<=to)
                    out.add(new String[]{"Phiên công việc bản vẽ",r.code,n(r.drawingNumber),n(h.description),DATE.format(new Date(h.workAt)),n(h.result),n(h.author)+" / "+n(h.shift),n(h.actions)+" "+n(h.findings)});
                for(String[] rev:repo.revisions(r.id)){long revAt=parseLong(rev[2]);if(revAt>=from&&revAt<=to)
                    out.add(new String[]{"DrawingRevision",r.code,n(r.drawingNumber),n(rev[3]),DATE.format(new Date(revAt)),n(rev[1])+" → "+n(rev[0]),n(rev[6])+" / "+n(rev[7]),"Lý do: "+n(rev[4])+"; Vùng: "+n(rev[5])+"; "+n(rev[8])});}
            }
        }
        return out;
    }
    public void exportCsv(Uri uri,String scope,long from,long to)throws Exception{
        List<String[]> rows=rows(scope,from,to);try(OutputStream out=context.getContentResolver().openOutputStream(uri,"w")){if(out==null)throw new Exception("Không mở được tệp đích");out.write(new byte[]{(byte)0xEF,(byte)0xBB,(byte)0xBF});for(String[] row:rows){StringBuilder line=new StringBuilder();for(int i=0;i<row.length;i++){if(i>0)line.append(',');line.append('"').append(row[i].replace("\"","\"\"")).append('"');}line.append("\r\n");out.write(line.toString().getBytes(StandardCharsets.UTF_8));}}
    }
    public void exportXlsx(Uri uri,String scope,long from,long to)throws Exception{
        List<String[]> rows=rows(scope,from,to);try(OutputStream os=context.getContentResolver().openOutputStream(uri,"w");ZipOutputStream zip=new ZipOutputStream(os)){
            put(zip,"[Content_Types].xml","<?xml version=\"1.0\" encoding=\"UTF-8\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/></Types>");
            put(zip,"_rels/.rels","<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>");
            put(zip,"xl/workbook.xml","<?xml version=\"1.0\" encoding=\"UTF-8\"?><workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets><sheet name=\"Bao cao\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
            put(zip,"xl/_rels/workbook.xml.rels","<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/></Relationships>");
            StringBuilder sheet=new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");
            for(int r=0;r<rows.size();r++){sheet.append("<row r=\"").append(r+1).append("\">");for(int c=0;c<rows.get(r).length;c++){String ref=col(c)+(r+1);sheet.append("<c r=\"").append(ref).append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">").append(xml(rows.get(r)[c])).append("</t></is></c>");}sheet.append("</row>");}sheet.append("</sheetData></worksheet>");put(zip,"xl/worksheets/sheet1.xml",sheet.toString());
        }
    }
    public void exportPdf(Uri uri,String scope,long from,long to)throws Exception{
        List<String[]> data=rows(scope,from,to);PdfDocument doc=new PdfDocument();Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setTextSize(10);int pageNo=1,y=42;PdfDocument.Page page=null;Canvas canvas=null;
        for(int i=0;i<data.size();i++){
            if(page==null||y>780){if(page!=null)doc.finishPage(page);PdfDocument.PageInfo info=new PdfDocument.PageInfo.Builder(595,842,pageNo++).create();page=doc.startPage(info);canvas=page.getCanvas();p.setTextSize(14);canvas.drawText("EVNQT1 HELP DESK - BÁO CÁO",32,28,p);p.setTextSize(10);y=52;}
            StringBuilder line=new StringBuilder();for(String cell:data.get(i)){if(line.length()>0)line.append(" | ");line.append(cell);}String text=line.toString();
            while(text.length()>88){canvas.drawText(text.substring(0,88),32,y,p);text=text.substring(88);y+=15;if(y>780){doc.finishPage(page);PdfDocument.PageInfo info=new PdfDocument.PageInfo.Builder(595,842,pageNo++).create();page=doc.startPage(info);canvas=page.getCanvas();y=40;}}
            canvas.drawText(text,32,y,p);y+=18;
        }
        if(page!=null)doc.finishPage(page);try(OutputStream out=context.getContentResolver().openOutputStream(uri,"w")){if(out==null)throw new Exception("Không mở được tệp PDF");doc.writeTo(out);}finally{doc.close();}
    }
    private static void put(ZipOutputStream z,String name,String text)throws Exception{z.putNextEntry(new ZipEntry(name));z.write(text.getBytes(StandardCharsets.UTF_8));z.closeEntry();}
    private static String xml(String s){return n(s).replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;");}
    private static long parseLong(String s){try{return Long.parseLong(s);}catch(Exception e){return 0;}}
    private static String col(int n){String s="";do{s=(char)('A'+n%26)+s;n=n/26-1;}while(n>=0);return s;}
    private static String n(String s){return s==null?"":s;}
}
