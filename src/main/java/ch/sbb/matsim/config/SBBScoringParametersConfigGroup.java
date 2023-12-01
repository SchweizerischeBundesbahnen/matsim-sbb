/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.config;

import org.matsim.core.config.ReflectiveConfigGroup;

import java.util.Map;

/**
 *
 */
public class SBBScoringParametersConfigGroup extends ReflectiveConfigGroup {

	static public final String GROUP_NAME = "SBBScoringParameters";

	public static final String PARAM_SCORING_PARAMETERS_EXCEL_PATH = "scoringParametersExcelPath";
	private String scoringParametersExcelPath;

	public SBBScoringParametersConfigGroup() {
		super(GROUP_NAME);
	}

	@StringSetter(PARAM_SCORING_PARAMETERS_EXCEL_PATH)
	public void setScoringParametersExcelPath(String scoringParametersExcelPath) {
		this.scoringParametersExcelPath = scoringParametersExcelPath;
	}

	@StringGetter(PARAM_SCORING_PARAMETERS_EXCEL_PATH)
	public String getScoringParametersExcelPath() {
		return this.scoringParametersExcelPath;
	}

	@Override
	public Map<String, String> getComments() {
		Map<String, String> map = super.getComments();
		map.put(PARAM_SCORING_PARAMETERS_EXCEL_PATH, "Path to Excel file containing the Scoring Parameters.");
		return map;
	}
}
