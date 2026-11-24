package top.aameow.erpnext.business;

import java.io.*;
import java.math.BigInteger;
import java.util.*;
import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;

/** Blank starter, not a certified company report. No customer data, findings, seals or signatures. */
final class InspectionBaseStarter {
    private InspectionBaseStarter(){}
    static final Set<String> OPTIONAL=Set.of("buildings","facilities","instruments","summaries");
    private static BigInteger n(int n){return BigInteger.valueOf(n);}
    private static void text(XWPFParagraph p,String value,int size,boolean bold){
        p.setSpacingAfter(100);p.setSpacingBefore(0);
        var r=p.createRun();r.setFontFamily("宋体");r.setFontSize(size);r.setBold(bold);r.setText(value);
    }
    private static void paragraph(XWPFDocument d,String value){text(d.createParagraph(),value,11,false);}
    private static void heading(XWPFDocument d,String title,boolean page){
        var p=d.createParagraph();p.setPageBreak(page);p.setKeepNext(true);
        p.getCTP().getPPr().addNewOutlineLvl().setVal(n(0));
        text(p,title,15,true);
    }
    private static void cell(XWPFTableCell cell,String value,int width,boolean header){
        cell.setWidth(Integer.toString(width));cell.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.CENTER);
        var p=cell.getParagraphs().get(0);p.setAlignment(header?ParagraphAlignment.CENTER:ParagraphAlignment.LEFT);
        text(p,value,10,header);
    }
    private static void facts(XWPFDocument d,String[][] entries){
        var t=d.createTable(entries.length,2);t.setWidth(9600);
        for(int i=0;i<entries.length;i++){cell(t.getRow(i).getCell(0),entries[i][0],2600,true);cell(t.getRow(i).getCell(1),"{{erp.general."+entries[i][1]+"}}",7000,false);}
    }
    private static void repeat(XWPFDocument d,String section,String[] labels,String[] fields){
        int width=9600/labels.length;var t=d.createTable(2,labels.length);t.setWidth(9600);
        for(int i=0;i<labels.length;i++){cell(t.getRow(0).getCell(i),labels[i],width,true);cell(t.getRow(1).getCell(i),"{{erp."+section+".*."+fields[i]+"}}",width,false);}
        t.getRow(0).setRepeatHeader(true);t.getRow(0).setCantSplitRow(true);
    }
    static byte[] create(Set<String> sections)throws IOException{
        if(!OPTIONAL.containsAll(sections))throw new IllegalArgumentException("未知公共模板分区");
        try(var d=new XWPFDocument();var out=new ByteArrayOutputStream()){
            var body=d.getDocument().getBody();var sect=body.isSetSectPr()?body.getSectPr():body.addNewSectPr();
            var size=sect.addNewPgSz();size.setW(n(11906));size.setH(n(16838));size.setOrient(STPageOrientation.PORTRAIT);
            var margins=sect.addNewPgMar();margins.setTop(n(1134));margins.setBottom(n(1134));margins.setLeft(n(1134));margins.setRight(n(1134));margins.setHeader(n(567));margins.setFooter(n(567));margins.setGutter(n(0));
            var title=d.createParagraph();title.setAlignment(ParagraphAlignment.CENTER);title.setSpacingBefore(1000);text(title,"消防设施检测报告",26,true);
            var p=d.createParagraph();p.setAlignment(ParagraphAlignment.CENTER);text(p,"{{erp.report.title}}",16,false);
            paragraph(d,"报告编号：{{erp.report.reportNo}}");
            facts(d,new String[][]{{"工程名称","projectName"},{"工程地址","address"},{"委托单位","commissioningUnit"},{"检测日期","inspectionDates"},{"报告日期","reportDate"}});
            paragraph(d,"编制、审核、批准及盖章：由公司按实际报告要求人工完成。");
            var toc=d.createParagraph();toc.setPageBreak(true);toc.setKeepNext(true);toc.setAlignment(ParagraphAlignment.CENTER);text(toc,"目 录",18,true);
            paragraph(d,InspectionTemplateComposer.TOC);
            heading(d,"一、工程概况",true);
            facts(d,new String[][]{{"建设单位","constructionUnit"},{"设计单位","designUnit"},{"施工单位","contractor"},{"监理单位","supervisionUnit"},{"建筑总面积","totalArea"},{"工程类别","projectCategory"}});
            paragraph(d,"{{erp.general.introduction}}");
            if(sections.contains("buildings"))repeat(d,"buildings",new String[]{"建筑单体","结构","使用性质","地上层数","地下层数","高度","地上面积","地下面积"},new String[]{"name","structure","use","aboveGroundFloors","belowGroundFloors","height","aboveGroundArea","belowGroundArea"});
            if(sections.contains("facilities")){heading(d,"共用设施与服务范围",false);repeat(d,"facilities",new String[]{"系统","设施名称","位置","服务范围","说明"},new String[]{"system","name","location","servedAreas","description"});}
            heading(d,"二、检测范围及依据",true);paragraph(d,"{{erp.general.scope}}");paragraph(d,"{{erp.general.basis}}");
            if(sections.contains("instruments")){heading(d,"检测仪器",false);repeat(d,"instruments",new String[]{"名称","型号","编号","检定有效期"},new String[]{"name","model","serialNo","calibrationExpiry"});}
            heading(d,"三、检测明细",true);paragraph(d,InspectionTemplateComposer.MARKER);
            heading(d,"四、检测结论",true);
            if(sections.contains("summaries"))repeat(d,"summaries",new String[]{"消防系统","系统判定","备注"},new String[]{"system","judgment","notes"});
            paragraph(d,"{{erp.conclusion.text}}");
            var footer=d.createFooter(HeaderFooterType.DEFAULT);var fp=footer.getParagraphs().isEmpty()?footer.createParagraph():footer.getParagraphs().get(0);
            fp.setAlignment(ParagraphAlignment.CENTER);fp.createRun().setText("第 ");fp.getCTP().addNewFldSimple().setInstr(" PAGE ");fp.createRun().setText(" 页 / 共 ");fp.getCTP().addNewFldSimple().setInstr(" NUMPAGES ");fp.createRun().setText(" 页");
            d.enforceUpdateFields();d.write(out);return out.toByteArray();
        }
    }
}
