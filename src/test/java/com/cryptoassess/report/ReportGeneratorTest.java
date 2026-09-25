package com.cryptoassess.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.Test;

class ReportGeneratorTest {

	private static ReportData sample() {
		return new ReportData("营销业务系统", "2026 年度自查", 3, Instant.parse("2026-09-24T08:00:00Z"),
				new BigDecimal("62.50"), null,
				List.of(new ReportData.LayerScore("应用和数据", new BigDecimal("0.50"), new BigDecimal("30")),
						new ReportData.LayerScore("物理和环境", null, new BigDecimal("10"))),
				List.of(new ReportData.Gap("客户数据库", "应用和数据", "FIX/T 0001-2026#5.4.3", "重要数据存储机密性", "不符合",
						"D×", "字段使用 AES-256-GCM 加密", "改用 SM4 并由密码机管理密钥", "已现场核查"),
						new ReportData.Gap("运维 VPN", "网络和通信", "FIX/T 0001-2026#5.2.5", "安全接入认证", "部分符合",
								"D√ A× K√ Ra=0.5", "仅口令认证", "增加基于数字证书的接入认证", null)),
				12, List.of("deepseek-chat"), List.of("judge.v2"), "algorithms.v2-draft", false, "scoring.v2-draft", false);
	}

	private static XWPFDocument read(byte[] bytes) throws Exception {
		return new XWPFDocument(new ByteArrayInputStream(bytes));
	}

	@Test
	void coverCarriesSystemLevelDateAndDisclaimer() throws Exception {
		try (XWPFDocument doc = read(new ReportGenerator().generate(sample()))) {
			String text = doc.getParagraphs().stream().map(XWPFParagraph::getText).collect(Collectors.joining("\n"));
			assertThat(text).contains("营销业务系统").contains("第三级").contains("2026-09-24")
				.contains("辅助自查草稿，需测评人员确认")
				.doesNotContain("测评结论：");
		}
	}

	@Test
	void scoreTableAndGapListComeFromData() throws Exception {
		try (XWPFDocument doc = read(new ReportGenerator().generate(sample()))) {
			List<XWPFTable> tables = doc.getTables();
			String scores = tables.get(0).getText();
			assertThat(scores).contains("总分").contains("62.50").contains("应用和数据").contains("0.50")
				.contains("全部不适用");
			String gaps = tables.get(1).getText();
			assertThat(gaps).contains("FIX/T 0001-2026#5.4.3").contains("不符合").contains("改用 SM4 并由密码机管理密钥")
				.contains("已现场核查").contains("FIX/T 0001-2026#5.2.5").contains("D√ A× K√ Ra=0.5");
		}
	}

	@Test
	void missingTotalShowsTheReasonInsteadOfANumber() throws Exception {
		ReportData data = sample();
		ReportData noTotal = new ReportData(data.systemName(), data.projectName(), 3, data.generatedAt(), null,
				"密码应用管理要求的所有安全层面都不适用，量化公式分母为 0，不出总分，转人工判断", data.layers(), data.gaps(),
				data.findingCount(), data.models(), data.promptVersions(), "algorithms.v2", true, "scoring.v2", true);
		try (XWPFDocument doc = read(new ReportGenerator().generate(noTotal))) {
			assertThat(doc.getTables().get(0).getText()).contains("不出总分").contains("转人工判断");
		}
	}

	@Test
	void appendixListsModelPromptAndRuleVersionsWithDraftWarning() throws Exception {
		try (XWPFDocument doc = read(new ReportGenerator().generate(sample()))) {
			String all = doc.getParagraphs().stream().map(XWPFParagraph::getText).collect(Collectors.joining("\n"))
					+ doc.getTables().stream().map(XWPFTable::getText).collect(Collectors.joining("\n"));
			assertThat(all).contains("deepseek-chat").contains("judge.v2").contains("algorithms.v2-draft")
				.contains("scoring.v2-draft").contains("草稿");
		}
	}

	@Test
	void noGapsStillProducesValidDocument() throws Exception {
		ReportData data = sample();
		ReportData empty = new ReportData(data.systemName(), data.projectName(), 2, data.generatedAt(), null, null,
				List.of(), List.of(), 0, List.of(), List.of(), "algorithms.v1", true, "scoring.v1", true);
		try (XWPFDocument doc = read(new ReportGenerator().generate(empty))) {
			String text = doc.getParagraphs().stream().map(XWPFParagraph::getText).collect(Collectors.joining("\n"));
			assertThat(text).contains("第二级").contains("未发现差距项");
		}
	}

}
