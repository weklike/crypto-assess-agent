package com.cryptoassess.eval.report;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 生成 Markdown 表格的小工具。
 */
public final class MarkdownTable {

	private final List<String> header;

	private final List<List<String>> rows = new ArrayList<>();

	public MarkdownTable(String... header) {
		this.header = List.of(header);
	}

	public MarkdownTable row(Object... cells) {
		List<String> row = new ArrayList<>();
		for (Object cell : cells) {
			row.add(format(cell));
		}
		this.rows.add(row);
		return this;
	}

	public String render() {
		StringBuilder out = new StringBuilder();
		out.append("| ").append(String.join(" | ", this.header)).append(" |\n");
		out.append("|").append(" --- |".repeat(this.header.size())).append('\n');
		for (List<String> row : this.rows) {
			out.append("| ").append(String.join(" | ", row)).append(" |\n");
		}
		return out.toString();
	}

	public static String format(Object cell) {
		if (cell == null) {
			return "—";
		}
		if (cell instanceof Double d) {
			return String.format(Locale.ROOT, "%.4f", d);
		}
		return String.valueOf(cell).replace("|", "\\|").replace("\n", " ");
	}

	public static String percent(long part, long total) {
		return (total == 0) ? "—" : String.format(Locale.ROOT, "%.0f%%", 100.0 * part / total);
	}

}
