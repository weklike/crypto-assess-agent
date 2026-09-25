package com.cryptoassess.assessment;

import java.util.List;

/**
 * GET /api/assessments/{id} 的返回：项目状态、测评对象、步骤记录、差距项和得分。
 */
public record AssessmentDetail(AssessProject project, List<ObjectView> objects, List<AssessStep> steps,
		List<AssessFinding> findings, List<AssessScore> scores) {

	public record ObjectView(Long id, String layer, String name, String description, CryptoMeasures measures) {

		static ObjectView of(AssessObject object) {
			return new ObjectView(object.id(), object.layer(), object.name(), object.description(), object.measures());
		}

	}

}
