package com.cryptoassess.qa;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * 引用只认本次检索结果里的条款：即便条款在知识库中存在，不在本次结果里也算无效（模型没有看到它的原文）。
 */
public class CitationValidator {

	public enum Reason {

		/** 知识库里没有这个条款，多半是模型编造 */
		NOT_FOUND,
		/** 条款存在，但不在本次提供给模型的检索结果中 */
		NOT_IN_RESULTS

	}

	public record InvalidCitation(String clauseRef, Reason reason) {
	}

	public record CitationCheck(List<String> valid, List<InvalidCitation> invalid) {

		public int invalidCount() {
			return this.invalid.size();
		}

	}

	private final Predicate<String> existsInKnowledgeBase;

	public CitationValidator(Predicate<String> existsInKnowledgeBase) {
		this.existsInKnowledgeBase = existsInKnowledgeBase;
	}

	public CitationCheck validate(List<String> cited, List<String> retrieved) {
		Set<String> allowed = new HashSet<>(retrieved);
		List<String> valid = new ArrayList<>();
		List<InvalidCitation> invalid = new ArrayList<>();
		for (String ref : cited) {
			if (allowed.contains(ref)) {
				valid.add(ref);
			}
			else {
				invalid.add(new InvalidCitation(ref,
						this.existsInKnowledgeBase.test(ref) ? Reason.NOT_IN_RESULTS : Reason.NOT_FOUND));
			}
		}
		return new CitationCheck(List.copyOf(valid), List.copyOf(invalid));
	}

}
