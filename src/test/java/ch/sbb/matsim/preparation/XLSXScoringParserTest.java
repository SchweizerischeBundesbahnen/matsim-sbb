/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2018.
 */

package ch.sbb.matsim.preparation;

import ch.sbb.matsim.config.PostProcessingConfigGroup;
import ch.sbb.matsim.config.SBBBehaviorGroupsConfigGroup;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;
import org.matsim.testcases.MatsimTestUtils;

public class XLSXScoringParserTest {
    @Rule
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

        XLSXScoringParser scoringParser = new XLSXScoringParser();

        scoringParser.parseXLSXWorkbook(workbook, config);

        PlanCalcScoreConfigGroup planCalcScore = config.planCalcScore();

        Assert.assertEquals (utilOfLineSwitch, planCalcScore.getUtilityOfLineSwitch(), 0);
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

        XLSXScoringParser scoringParser = new XLSXScoringParser();

        scoringParser.parseXLSXWorkbook(workbook, config);

        PlanCalcScoreConfigGroup planCalcScore = config.planCalcScore();

        Assert.assertEquals (constantCar, planCalcScore.getOrCreateModeParams(mode).getConstant(), 0);
    }

    /*
    * set behaviorGroup scoring parameter (season_ticket = "none", mode car, constant)
    * */
    @Test
    public void testParseBehaviorGroupScoringParams() {
        double constant = -1.22;
        String behaviorGroupName = "season_ticket";
        String sheetName = "Abobesitz";

        Config config = ConfigUtils.createConfig();
        SBBBehaviorGroupsConfigGroup behaviorGroupConfig = new SBBBehaviorGroupsConfigGroup();
        config.addModule(behaviorGroupConfig);

        XSSFWorkbook workbook = new XSSFWorkbook();
        workbook.createSheet(sheetName);

        Sheet scoringParamsSheet = workbook.getSheet(sheetName);
        scoringParamsSheet.createRow(0);
        scoringParamsSheet.getRow(0).createCell(0, CellType.STRING).setCellValue("season_ticket");
        scoringParamsSheet.getRow(0).createCell(2, CellType.STRING).setCellValue("car");
        scoringParamsSheet.createRow(1);
        scoringParamsSheet.getRow(1).createCell(0, CellType.STRING).setCellValue("none");
        scoringParamsSheet.getRow(1).createCell(1, CellType.STRING).setCellValue(XLSXScoringParser.CONSTANT);
        scoringParamsSheet.getRow(1).createCell(2, CellType.NUMERIC).setCellValue(constant);

        XLSXScoringParser scoringParser = new XLSXScoringParser();

        scoringParser.parseXLSXWorkbook(workbook, config);

        SBBBehaviorGroupsConfigGroup behaviorGroupConfigGroup = (SBBBehaviorGroupsConfigGroup) config.getModules().get(SBBBehaviorGroupsConfigGroup.GROUP_NAME);

        Assert.assertEquals (constant, behaviorGroupConfig.getBehaviorGroupParams().get(behaviorGroupName).getPersonGroupTypeParams("none").getModeCorrectionParams().get("car").getConstant(), 0);
    }
}
