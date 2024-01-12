/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2018.
 */

package ch.sbb.matsim.preparation;

import ch.sbb.matsim.config.SBBBehaviorGroupsConfigGroup;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.Assert;
import org.junit.jupiter.api.Test;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.ScoringConfigGroup;
import org.matsim.testcases.MatsimTestUtils;

public class XLSXScoringParserTest {


	public MatsimTestUtils utils = new MatsimTestUtils();

	/*
	 * set general scoring parameter
	 * */
	@Test
	public void testParseGeneralScoringParams() {
		double utilOfLineSwitch = -0.42;

		Config config = ConfigUtils.createConfig();

		XSSFWorkbook workbook = new XSSFWorkbook();
		workbook.createSheet(XLSXScoringParser.SCORING_SHEET);

		Sheet scoringParamsSheet = workbook.getSheet(XLSXScoringParser.SCORING_SHEET);
		scoringParamsSheet.createRow(0);
		scoringParamsSheet.getRow(0).createCell(0, CellType.STRING).setCellValue(XLSXScoringParser.MATSIM_PARAMS_LABEL);
		scoringParamsSheet.getRow(0).createCell(1, CellType.STRING).setCellValue(XLSXScoringParser.GENERAL_PARAMS_LABEL);
		scoringParamsSheet.createRow(1);
		scoringParamsSheet.getRow(1).createCell(0, CellType.STRING).setCellValue(XLSXScoringParser.UTL_OF_LINE_SWITCH);
		scoringParamsSheet.getRow(1).createCell(1, CellType.NUMERIC).setCellValue(utilOfLineSwitch);

		XLSXScoringParser.parseXLSXWorkbook(workbook, config);

		ScoringConfigGroup planCalcScore = config.scoring();

		Assert.assertEquals(utilOfLineSwitch, planCalcScore.getUtilityOfLineSwitch(), 0);
	}

	/*
	 * set mode scoring parameter
	 * */
	@Test
	public void testParseModeScoringParams() {
		double constantCar = -1.22;
		String mode = "car";

		Config config = ConfigUtils.createConfig();

		XSSFWorkbook workbook = new XSSFWorkbook();
		workbook.createSheet(XLSXScoringParser.SCORING_SHEET);

		Sheet scoringParamsSheet = workbook.getSheet(XLSXScoringParser.SCORING_SHEET);
		scoringParamsSheet.createRow(0);
		scoringParamsSheet.getRow(0).createCell(0, CellType.STRING).setCellValue(XLSXScoringParser.MATSIM_PARAMS_LABEL);
		scoringParamsSheet.getRow(0).createCell(1, CellType.STRING).setCellValue("car");
		scoringParamsSheet.createRow(1);
		scoringParamsSheet.getRow(1).createCell(0, CellType.STRING).setCellValue(XLSXScoringParser.CONSTANT);
		scoringParamsSheet.getRow(1).createCell(1, CellType.NUMERIC).setCellValue(constantCar);

		XLSXScoringParser.parseXLSXWorkbook(workbook, config);

		ScoringConfigGroup planCalcScore = config.scoring();

		Assert.assertEquals(constantCar, planCalcScore.getOrCreateModeParams(mode).getConstant(), 0);
	}

	/*
	 * set behaviorGroup scoring parameter (season_ticket = "none", mode car, constant)
	 * */
	@Test
	public void testParseBehaviorGroupScoringParams() {
		double constant = -1.22;
		double marginalUtilityOfDistance = 0;
		double marginalUtilityOfTraveling = 0;
		double monetaryDistanceRate = 0;
		String behaviorGroupName = "season_ticket";
		String sheetName = "Abobesitz";

		Config config = ConfigUtils.createConfig();
		SBBBehaviorGroupsConfigGroup behaviorGroupConfig = new SBBBehaviorGroupsConfigGroup();
		config.addModule(behaviorGroupConfig);

		XSSFWorkbook workbook = new XSSFWorkbook();
		workbook.createSheet(sheetName);

		Sheet scoringParamsSheet = workbook.getSheet(sheetName);
		scoringParamsSheet.createRow(0);
		scoringParamsSheet.getRow(0).createCell(0, CellType.STRING).setCellValue(XLSXScoringParser.BEHAVIOR_GROUP_LABEL);
		scoringParamsSheet.createRow(1);
		scoringParamsSheet.getRow(1).createCell(0, CellType.STRING).setCellValue(behaviorGroupName);
		scoringParamsSheet.getRow(1).createCell(2, CellType.STRING).setCellValue("car");
		scoringParamsSheet.createRow(2);
		scoringParamsSheet.getRow(2).createCell(0, CellType.STRING).setCellValue("none");
		scoringParamsSheet.getRow(2).createCell(1, CellType.STRING).setCellValue(XLSXScoringParser.CONSTANT);
		scoringParamsSheet.getRow(2).createCell(2, CellType.NUMERIC).setCellValue(constant);
		scoringParamsSheet.createRow(3);
		scoringParamsSheet.getRow(3).createCell(0, CellType.STRING).setCellValue("none");
		scoringParamsSheet.getRow(3).createCell(1, CellType.STRING).setCellValue(XLSXScoringParser.MARGINAL_UTILITY_OF_DISTANCE);
		scoringParamsSheet.getRow(3).createCell(2, CellType.NUMERIC).setCellValue(marginalUtilityOfDistance);
		scoringParamsSheet.createRow(4);
		scoringParamsSheet.getRow(4).createCell(0, CellType.STRING).setCellValue("none");
		scoringParamsSheet.getRow(4).createCell(1, CellType.STRING).setCellValue(XLSXScoringParser.MARGINAL_UTILITY_OF_TRAVELING);
		scoringParamsSheet.getRow(4).createCell(2, CellType.NUMERIC).setCellValue(marginalUtilityOfTraveling);
		scoringParamsSheet.createRow(5);
		scoringParamsSheet.getRow(5).createCell(0, CellType.STRING).setCellValue("none");
		scoringParamsSheet.getRow(5).createCell(1, CellType.STRING).setCellValue(XLSXScoringParser.MONETARY_DISTANCE_RATE);
		scoringParamsSheet.getRow(5).createCell(2, CellType.NUMERIC).setCellValue(monetaryDistanceRate);

		XLSXScoringParser.parseXLSXWorkbook(workbook, config);

		Assert.assertEquals(constant, behaviorGroupConfig.getBehaviorGroupParams().get(sheetName).getPersonGroupByAttribute("none").getModeCorrectionParams().get("car").getConstant(), 0);
	}
}
