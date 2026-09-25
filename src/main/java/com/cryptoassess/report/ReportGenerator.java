package com.cryptoassess.report;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Component;

/**
 * 用 Apache POI（XWPF）生成 .docx 报告草稿：封面、得分表、差距清单与整改建议、附录。
 */
@Component
public class ReportGenerator {

	static final String DISCLAIMER = "辅助自查草稿，需测评人员确认";

	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.of("Asia/Shanghai"));

	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC+8'")
		.withZone(ZoneId.of("Asia/Shanghai"));

	private static final String[] LEVELS = { "", "第一级", "第二级", "第三级", "第四级" };

	public byte[] generate(ReportData data) {
		try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			cover(doc, data);
			scores(doc, data);
			gaps(doc, data);
			appendix(doc, data);
			doc.write(out);
			return out.toByteArray();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	private void cover(XWPFDocument doc, ReportData data) {
		paragraph(doc, "商用密码应用安全性评估 自查报告", 22, true, ParagraphAlignment.CENTER);
		paragraph(doc, "（" + DISCLAIMER + "）", 14, true, ParagraphAlignment.CENTER);
		paragraph(doc, "", 12, false, ParagraphAlignment.LEFT);
		paragraph(doc, "被测系统：" + data.systemName(), 14, false, ParagraphAlignment.CENTER);
		paragraph(doc, "安全保护等级：" + LEVELS[data.level()], 14, false, ParagraphAlignment.CENTER);
		paragraph(doc, "评估项目：" + data.projectName(), 14, false, ParagraphAlignment.CENTER);
		paragraph(doc, "生成日期：" + DATE.format(data.generatedAt()), 14, false, ParagraphAlignment.CENTER);
		paragraph(doc, "本报告由密评智能助手根据录入的系统信息生成，判定与得分仅供自查参考，不能替代商用密码检测机构出具的正式测评报告。", 11, false,
				ParagraphAlignment.LEFT);
		doc.createParagraph().setPageBreak(true);
	}

	private void scores(XWPFDocument doc, ReportData data) {
		heading(doc, "一、得分");
		XWPFTable table = doc.createTable();
		row(table, true, "项目", "权重", "得分");
		row(table, false, "总分", "—", (data.total() != null) ? data.total().toPlainString()
				: "不出总分：" + ((data.totalNote() == null) ? "全部不适用" : data.totalNote()));
		for (ReportData.LayerScore layer : data.layers()) {
			row(table, false, layer.layer(), layer.weight().stripTrailingZeros().toPlainString(),
					(layer.score() == null) ? "全部不适用" : layer.score().toPlainString());
		}
		paragraph(doc, "按《商用密码应用安全性评估量化评估规则（2023 版）》计算：层面得分为 0-1 之间的测评单元加权平均，"
				+ "总分 = 70 × 技术层面加权平均 + 30 × 管理层面加权平均；计算明细见系统内评分记录。", 10, false, ParagraphAlignment.LEFT);
	}

	private void gaps(XWPFDocument doc, ReportData data) {
		heading(doc, "二、差距清单与整改建议");
		if (data.gaps().isEmpty()) {
			paragraph(doc, "未发现差距项（共 " + data.findingCount() + " 条判定，均为符合或不适用）。", 11, false, ParagraphAlignment.LEFT);
			return;
		}
		paragraph(doc, "共 " + data.findingCount() + " 条判定，其中差距项 " + data.gaps().size() + " 条（不符合、部分符合）。", 11, false,
				ParagraphAlignment.LEFT);
		XWPFTable table = doc.createTable();
		row(table, true, "序号", "测评对象", "条款", "判定", "证据", "整改建议", "复核备注");
		int i = 1;
		for (ReportData.Gap gap : data.gaps()) {
			row(table, false, String.valueOf(i++), gap.objectName() + "（" + gap.layer() + "）",
					gap.clauseRef() + " " + nullToEmpty(gap.clauseTitle()), gap.judgment() + ((gap.dimensions() == null) ? "" : "（" + gap.dimensions() + "）"),
					nullToEmpty(gap.evidence()),
					nullToEmpty(gap.remediation()), nullToEmpty(gap.reviewerNote()));
		}
	}

	private void appendix(XWPFDocument doc, ReportData data) {
		heading(doc, "附录：生成信息");
		XWPFTable table = doc.createTable();
		row(table, true, "项", "值");
		row(table, false, "模型", data.models().isEmpty() ? "—" : String.join("、", data.models()));
		row(table, false, "提示词版本", data.promptVersions().isEmpty() ? "—" : String.join("、", data.promptVersions()));
		row(table, false, "算法规则表版本", data.algorithmRuleVersion() + (data.algorithmRulesReviewed() ? "" : "（草稿，未经核对）"));
		row(table, false, "评分规则版本", data.scoringRuleVersion() + (data.scoringRulesReviewed() ? "" : "（草稿，未经核对）"));
		row(table, false, "生成时间", TIME.format(data.generatedAt()));
		if (!data.algorithmRulesReviewed() || !data.scoringRulesReviewed()) {
			paragraph(doc, "注意：本报告使用的规则表为草稿，判定与得分需测评人员重新核对。", 11, true, ParagraphAlignment.LEFT);
		}
		paragraph(doc, DISCLAIMER + "。", 11, true, ParagraphAlignment.LEFT);
	}

	private static void heading(XWPFDocument doc, String text) {
		paragraph(doc, text, 16, true, ParagraphAlignment.LEFT);
	}

	private static void paragraph(XWPFDocument doc, String text, int size, boolean bold, ParagraphAlignment align) {
		XWPFParagraph p = doc.createParagraph();
		p.setAlignment(align);
		XWPFRun run = p.createRun();
		run.setText(text);
		run.setFontSize(size);
		run.setBold(bold);
		run.setFontFamily("宋体");
	}

	/** 新表自带一行，第一次写表头时复用它。 */
	private static void row(XWPFTable table, boolean header, String... cells) {
		XWPFTableRow row = (header && table.getNumberOfRows() == 1 && table.getRow(0).getCell(0).getText().isEmpty())
				? table.getRow(0) : table.createRow();
		for (int i = 0; i < cells.length; i++) {
			if (row.getCell(i) == null) {
				row.addNewTableCell();
			}
			XWPFParagraph p = row.getCell(i).getParagraphs().get(0);
			XWPFRun run = p.createRun();
			run.setText(cells[i]);
			run.setBold(header);
			run.setFontSize(10);
		}
	}

	private static String nullToEmpty(String value) {
		return (value == null) ? "" : value;
	}

}
