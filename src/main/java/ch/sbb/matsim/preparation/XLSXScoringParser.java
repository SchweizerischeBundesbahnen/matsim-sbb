/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2018.
 */

package ch.sbb.matsim.preparation;

import ch.sbb.matsim.config.SBBBehaviorGroupsConfigGroup;
import org.apache.log4j.Logger;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.matsim.core.config.Config;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * XLSXScoringParser.
 *
 * <p>import scoring paremeters
 * <ul><li>general</li><li>mode parameters</li><li>mode parameters specific to behavior groups</li></ul>
 * from XLSX file (using Apache POI), merge with MATSim config.xml file.</p>
 * <p>Write data to output XML file.</p>
 * <p>If parameters or modes are present in both MATSim config.xml and XLSX file, the XLSX file wins.</p>
 * <p>If parameters or modes are present in MATSim config.xml but not in XLSX file, they remain.</p>
 *
 * @author paschke
 */
public class XLSXScoringParser {

    static final String SCORING_SHEET = "ScoringParams";
    static final String MATSIM_PARAMS_LABEL = "MATSim Param Name";
    static final String GENERAL_PARAMS_LABEL = "general";
    static final String UTL_OF_LINE_SWITCH = "utilityOfLineSwitch" ;
    static final String WAITING_PT  = "waitingPt";
    static final String EARLY_DEPARTURE = "earlyDeparture";
    static final String LATE_ARRIVAL = "lateArrival";
    static final String PERFORMING = "performing";
    static final String WAITING  = "waiting";
    static final String MARGINAL_UTL_OF_MONEY = "marginalUtilityOfMoney" ;
    static final String CONSTANT = "constant";
    static final String MARGINAL_UTILITY_OF_TRAVELING = "marginalUtilityOfTraveling_util_hr";
    static final String MARGINAL_UTILITY_OF_DISTANCE = "marginalUtilityOfDistance_util_m";
    static final String MONETARY_DISTANCE_RATE = "monetaryDistanceRate";

    static final String[] GENERAL_PARAMS_ARRAY = new String[] {UTL_OF_LINE_SWITCH, WAITING_PT, EARLY_DEPARTURE, LATE_ARRIVAL, WAITING, PERFORMING, MARGINAL_UTL_OF_MONEY};
    static final String[] MODE_PARAMS_ARRAY = new String[] {CONSTANT, MARGINAL_UTILITY_OF_DISTANCE, MARGINAL_UTILITY_OF_TRAVELING, MONETARY_DISTANCE_RATE};
    static final Set<String> GENERAL_PARAMS = new HashSet<>(Arrays.asList(GENERAL_PARAMS_ARRAY));
    static final Set<String> MODE_PARAMS = new HashSet<>(Arrays.asList(MODE_PARAMS_ARRAY));

    static final String BEHAVIOR_GROUP_LABEL = "BehaviorGroup";

    private final static Logger log = Logger.getLogger(XLSXScoringParser.class);

    /**
     * parseXLSXWorkbook
     *
     * <p>a Workbook instance is expected to contain:
     * <ul><li>one sheet containing global and mode-specific scoring parameters</li>
     * <li>several sheets containing scoring parameters specific to behavior groups</li></ul></p>
     *
     * @param workbook (required)
     * @param config (required) MATSim config instance, must contain ALL configured config modules, otherwise
     *               their settings will be deleted from the config output.
     */
    public static void parseXLSXWorkbook(Workbook workbook, Config config) {
        PlanCalcScoreConfigGroup planCalcScore = config.planCalcScore();
        SBBBehaviorGroupsConfigGroup behaviorGroupConfigGroup = (SBBBehaviorGroupsConfigGroup) config.getModules().get(SBBBehaviorGroupsConfigGroup.GROUP_NAME);

        for (int sheetIndex = 0; sheetIndex < workbook.getNumberOfSheets(); sheetIndex++) {
            Sheet paramsSheet = workbook.getSheetAt(sheetIndex);

            if (paramsSheet.getSheetName().equals(SCORING_SHEET)) {
                parseScoringParamsSheet(paramsSheet, planCalcScore);
                log.info("parsed general scoring parameters sheet: " + SCORING_SHEET);
            } else {
                parseBehaviorGroupParamsSheet(paramsSheet, behaviorGroupConfigGroup);
                log.info("parsed behaviorGroup scoring parameters sheet: " + paramsSheet.getSheetName());
            }

        }
    }

    /**
     * parseScoringParamsSheet
     *
     * <p>parse global and mode-specific scoring parameters</p>
     * <p>this method will iterate over the sheet rows and try to find the string "MATSim Param Name" in the
     * first column. Strings found in the same row and present in the MODES array will be interpreted as modes</p>
     *
     * @param scoringParamsSheet (required) the workbook sheet, usually labelled "ScoringParams"
     * @param planCalcScore (required) MATSim configGroup instance
     */
    protected static void parseScoringParamsSheet(Sheet scoringParamsSheet, PlanCalcScoreConfigGroup planCalcScore) {
        Map<Integer, PlanCalcScoreConfigGroup.ModeParams> modeParamsConfig = new TreeMap<>();
        Set<String> modes = new TreeSet<>();
        Integer generalParamsCol = null;

        for (Row row : scoringParamsSheet) {
            Cell firstCell = row.getCell(0);

            if ((firstCell != null) && (firstCell.getCellTypeEnum() == CellType.STRING)) {
                String rowLabel = firstCell.getStringCellValue();

                if (rowLabel.equals(MATSIM_PARAMS_LABEL)) {
                    int lastColumn = row.getLastCellNum();

                    for (int col = 1; col < lastColumn; col++) {
                        Cell cell = row.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);

                        if ((cell != null) && (cell.getCellTypeEnum() == CellType.STRING)) {
                            String mode = cell.getStringCellValue();

                            if (mode.equals(GENERAL_PARAMS_LABEL)) {
                                generalParamsCol = col;
                            } else if (!modes.contains(mode)) {
                                PlanCalcScoreConfigGroup.ModeParams modeParams = planCalcScore.getOrCreateModeParams(mode);
                                modeParamsConfig.put(col, modeParams);
                                modes.add(mode);
                            }
                        }
                    }

                    log.info("found parameters for modes: " + modes.toString());
                } else if (MODE_PARAMS.contains(rowLabel)) {
                    for (Map.Entry<Integer, PlanCalcScoreConfigGroup.ModeParams> entry : modeParamsConfig.entrySet()) {
                        Cell cell = row.getCell(entry.getKey());
                        PlanCalcScoreConfigGroup.ModeParams modeParams = entry.getValue();

                        if ((cell.getCellTypeEnum() == CellType.NUMERIC) || (cell.getCellTypeEnum() == CellType.FORMULA)) {
                            try {
                                double numericCellValue = cell.getNumericCellValue();

                                switch (rowLabel) {
                                    case CONSTANT:
                                        modeParams.setConstant(numericCellValue);
                                        break;
                                    case MARGINAL_UTILITY_OF_DISTANCE:
                                        modeParams.setMarginalUtilityOfDistance(numericCellValue);
                                        break;
                                    case MARGINAL_UTILITY_OF_TRAVELING:
                                        modeParams.setMarginalUtilityOfTraveling(numericCellValue);
                                        break;
                                    case MONETARY_DISTANCE_RATE:
                                        modeParams.setMonetaryDistanceRate(numericCellValue);
                                        break;
                                }
                            } catch (IllegalStateException e) {
                                log.warn("could not parse parameter " + rowLabel + " for mode " + modeParams.getMode() + ". Cell value is not numeric.");
                            }
                        }
                    }
                } else if (GENERAL_PARAMS.contains(rowLabel)) {
                    Cell cell = row.getCell(generalParamsCol);

                    if ((cell.getCellTypeEnum() == CellType.NUMERIC) || (cell.getCellTypeEnum() == CellType.FORMULA)) {
                        try {
                            planCalcScore.addParam(rowLabel, String.valueOf(cell.getNumericCellValue()));
                        } catch (IllegalStateException e) {
                            log.warn("could not parse parameter " + rowLabel + ". Cell value is not numeric.");
                        }
                    }
                }
            }
        }
    }

    /**
     * parseBehaviorGroupParamsSheet
     *
     * <p>parse scoring parameters specific to one behavior group</p>
     * <p>this method will iterate over the sheet rows and try to find the behavior group name string in the
     * first column. Strings found in the same row will be interpreted as modes</p>
     * <p>The method will prepare a modeCorrection instance for each combination of behavior group and mode.
     * e.g.: season_ticket: none / mode: car, season_ticket: none / mode: ride ... season_ticket: Generalabo / mode: bike
     * To avoid cluttering the final config.xml file, only the modeCorrections with non-null values will be added.</p>
     *
     * @param behaviorGroupParamsSheet (required) the workbook sheet
     * @param behaviorGroupsConfigGroup (required) specific MATSim configGroup instance
     */
    protected static void parseBehaviorGroupParamsSheet(Sheet behaviorGroupParamsSheet, SBBBehaviorGroupsConfigGroup behaviorGroupsConfigGroup) {
        Map<Integer, String> modes = new TreeMap<>();
        String personAttributeKey = null;

        /** Value - {@value}, temporary container for ModeCorrection instances */
        Map<String, Map<String, SBBBehaviorGroupsConfigGroup.ModeCorrection>> modeCorrections = new HashMap<>();

        SBBBehaviorGroupsConfigGroup.BehaviorGroupParams behaviorGroupParams = null;

        for (Row row : behaviorGroupParamsSheet) {
            Cell firstCell = row.getCell(0);

            if (firstCell != null) {
                String rowLabel;

                if (firstCell.getCellTypeEnum() == CellType.STRING) {
                    rowLabel = firstCell.getStringCellValue();
                } else if (firstCell.getCellTypeEnum() == CellType.NUMERIC) {
                    rowLabel = String.valueOf((int) firstCell.getNumericCellValue());
                } else {
                    continue;
                }

                if (rowLabel.equals(BEHAVIOR_GROUP_LABEL)) {
                    Row belowRow = behaviorGroupParamsSheet.getRow(row.getRowNum() + 1);
                    Cell belowCell = belowRow.getCell(0);

                    if (belowCell.getCellTypeEnum() == CellType.STRING) {
                        personAttributeKey = belowCell.getStringCellValue();
                        behaviorGroupParams = new SBBBehaviorGroupsConfigGroup.BehaviorGroupParams();
                        behaviorGroupParams.setBehaviorGroupName(behaviorGroupParamsSheet.getSheetName());
                        behaviorGroupParams.setPersonAttribute(personAttributeKey);
                    }

                    continue;
                }

                if ((personAttributeKey != null) && (rowLabel.equals(personAttributeKey))) {
                    int lastColumn = row.getLastCellNum();

                    for (int col = 1; col < lastColumn; col++) {
                        Cell cell = row.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);

                        if ((cell != null) && (cell.getCellTypeEnum() == CellType.STRING)) {
                            String mode = cell.getStringCellValue();

                            if (!modes.containsValue(mode)) {
                                modes.put(col, mode);
                            }
                        }
                    }
                } else {
                    Cell secondCell = row.getCell(1);

                    if ((secondCell != null) && (secondCell.getCellTypeEnum() == CellType.STRING)) {
                        String parameterLabel = secondCell.getStringCellValue();

                        if (MODE_PARAMS.contains(parameterLabel)) {
                            if (!modeCorrections.containsKey(rowLabel)) {
                                modeCorrections.put(rowLabel, new HashMap<>());

                                for (Map.Entry<Integer, String> modeEntry : modes.entrySet()) {
                                    SBBBehaviorGroupsConfigGroup.ModeCorrection modeCorrection = new SBBBehaviorGroupsConfigGroup.ModeCorrection();
                                    modeCorrection.setMode(modeEntry.getValue());

                                    modeCorrections.get(rowLabel).put(modeEntry.getValue(), modeCorrection);
                                }
                            }

                            for (Map.Entry<Integer, String> entry : modes.entrySet()) {
                                SBBBehaviorGroupsConfigGroup.ModeCorrection modeCorrection = modeCorrections.get(rowLabel).get(entry.getValue());
                                Cell cell = row.getCell(entry.getKey());

                                if ((cell.getCellTypeEnum() == CellType.NUMERIC) || (cell.getCellTypeEnum() == CellType.FORMULA)) {
                                    try {
                                        double numericCellValue = cell.getNumericCellValue();

                                        switch (parameterLabel) {
                                            case CONSTANT:
                                                modeCorrection.setConstant(numericCellValue);
                                                break;
                                            case MARGINAL_UTILITY_OF_DISTANCE:
                                                modeCorrection.setMargUtilOfDistance(numericCellValue);
                                                break;
                                            case MARGINAL_UTILITY_OF_TRAVELING:
                                                modeCorrection.setMargUtilOfTime(numericCellValue);
                                                break;
                                            case MONETARY_DISTANCE_RATE:
                                                modeCorrection.setDistanceRate(numericCellValue);
                                                break;
                                        }
                                    } catch (IllegalStateException e) {
                                        log.warn("could not parse parameter " + rowLabel + " for mode " + entry.getValue() + ". Cell value is not numeric.");
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        /** iterate over modeCorrections, only add those with non-null values */
        if (behaviorGroupParams != null) {
            for (Map.Entry<String, Map<String, SBBBehaviorGroupsConfigGroup.ModeCorrection>> modeCorrectionsEntry : modeCorrections.entrySet()) {
                String personAttributeValues = modeCorrectionsEntry.getKey();
                Map<String, SBBBehaviorGroupsConfigGroup.ModeCorrection> modeCorrectionsPerMode = modeCorrectionsEntry.getValue();

                SBBBehaviorGroupsConfigGroup.PersonGroupAttributeValues attributeValues = new SBBBehaviorGroupsConfigGroup.PersonGroupAttributeValues();
                attributeValues.setPersonGroupAttributeValues(personAttributeValues);

                for (SBBBehaviorGroupsConfigGroup.ModeCorrection modeCorrection : modeCorrectionsPerMode.values()) {
                    if (modeCorrection.isSet()) {
                        attributeValues.addModeCorrection(modeCorrection);
                        log.info("adding modeCorrection for " + personAttributeKey + "/" + personAttributeValues + " for mode " + modeCorrection.getMode());
                    }
                }

                if (!attributeValues.getModeCorrectionParams().isEmpty()) {
                    behaviorGroupParams.addPersonGroupByAttribute(attributeValues);
                }
            }

            behaviorGroupsConfigGroup.addBehaviorGroupParams(behaviorGroupParams);
        }
    }
}
