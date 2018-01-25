/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2018.
 */

package ch.sbb.matsim.preparation;

import ch.sbb.matsim.RunSBB;
import ch.sbb.matsim.config.PostProcessingConfigGroup;
import ch.sbb.matsim.config.SBBBehaviorGroupsConfigGroup;
import ch.sbb.matsim.config.SBBPopulationSamplerConfigGroup;
import ch.sbb.matsim.config.SBBTransitConfigGroup;
import org.apache.log4j.Logger;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.ConfigWriter;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class ConfigParser {

    static private final String SCORING_SHEET_LABEL = "ScoringParams";
    static private final String MATSIM_PARAMS_LABEL = "MATSim Param Name";
    static private final String GENERAL_PARAMS_LABEL = "general";
    private static final String UTL_OF_LINE_SWITCH = "utilityOfLineSwitch" ;
    private static final String WAITING_PT  = "waitingPt";
    private static final String EARLY_DEPARTURE = "earlyDeparture";
    private static final String LATE_ARRIVAL = "lateArrival";
    private static final String PERFORMING = "performing";
    private static final String WAITING  = "waiting";
    private static final String MARGINAL_UTL_OF_MONEY = "marginalUtilityOfMoney" ;
    private static final String CONSTANT = "constant";
    private static final String MARGINAL_UTILITY_OF_TRAVELING = "marginalUtilityOfTraveling_util_hr";
    private static final String MARGINAL_UTILITY_OF_DISTANCE = "marginalUtilityOfDistance_util_m";
    private static final String MONETARY_DISTANCE_RATE = "monetaryDistanceRate";

    static private final String[] GENERAL_PARAM_LABEL_VALUES = new String[] {UTL_OF_LINE_SWITCH, WAITING_PT, EARLY_DEPARTURE, LATE_ARRIVAL, WAITING, PERFORMING, MARGINAL_UTL_OF_MONEY};
    static private final String[] MODE_PARAM_LABEL_VALUES = new String[] {CONSTANT, MARGINAL_UTILITY_OF_DISTANCE, MARGINAL_UTILITY_OF_TRAVELING, MONETARY_DISTANCE_RATE};
    static private final Set<String> GENERAL_PARAM_LABELS = new HashSet<>(Arrays.asList(GENERAL_PARAM_LABEL_VALUES));
    static private final Set<String> MODE_PARAM_LABELS = new HashSet<>(Arrays.asList(MODE_PARAM_LABEL_VALUES));

    static private final String DUMMY_GROUP_SHEET_LABEL = "DummyGroupForScoringOnlyDefault";
    static private final String DUMMY_GROUP_NAME = "DummyDistanceCorrection";
    static private final String SEASON_CARD_SHEET_LABEL = "Abobesitz";
    static private final String SEASON_CARD_NAME = "Abobesitz";
    static private final String CAR_AVAIL_SHEET_LABEL = "PW_Verf";
    static private final String CAR_AVAIL_NAME = "PW Verfuegbarkeit";
    static private final String LAND_USE_SHEET_LABEL = "Raumtyp";
    static private final String LAND_USE_NAME = "Raumtyp";

    static private final Map<String, String> BEHAVIOR_GROUP_LABELS = new HashMap<>();
    static {
        BEHAVIOR_GROUP_LABELS.put(DUMMY_GROUP_SHEET_LABEL, DUMMY_GROUP_NAME);
        BEHAVIOR_GROUP_LABELS.put(SEASON_CARD_SHEET_LABEL, SEASON_CARD_NAME);
        BEHAVIOR_GROUP_LABELS.put(CAR_AVAIL_SHEET_LABEL, CAR_AVAIL_NAME);
        BEHAVIOR_GROUP_LABELS.put(LAND_USE_SHEET_LABEL, LAND_USE_NAME);
    }

    private static Logger log = Logger.getLogger(RunSBB.class);

    public static void main(final String[] args) {
        final String configIn = args[0];
        final String configOut = args[1];
        final String xlsx = args[2];

        final Config config = ConfigUtils.loadConfig(configIn, new PostProcessingConfigGroup(), new SBBTransitConfigGroup(),
                new SBBBehaviorGroupsConfigGroup(),new SBBPopulationSamplerConfigGroup());

        PlanCalcScoreConfigGroup planCalcScore = config.planCalcScore();

        try {
            FileInputStream inputStream = new FileInputStream(xlsx);
            Workbook workbook = WorkbookFactory.create(inputStream);

            Sheet scoringParamsSheet = workbook.getSheet(SCORING_SHEET_LABEL);

            if (workbook != null) {
                parseScoringParamsSheet(scoringParamsSheet, planCalcScore);
            }

            for (Map.Entry<String, String> entry : BEHAVIOR_GROUP_LABELS.entrySet()) {
                Sheet behaviorGroupParamsSheet = workbook.getSheet(entry.getKey());

                if (behaviorGroupParamsSheet != null) {
                    parseBehaviorGroupParamsSheet(entry.getValue(), behaviorGroupParamsSheet, planCalcScore);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InvalidFormatException e) {
            e.printStackTrace();
        }

        new ConfigWriter(config).write(configOut);
    }

    protected static void parseScoringParamsSheet(Sheet scoringParamsSheet, PlanCalcScoreConfigGroup planCalcScore) {
        Map<Integer, PlanCalcScoreConfigGroup.ModeParams> modeParamsConfig = new TreeMap<>();
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
                            if (cell.getStringCellValue().equals(GENERAL_PARAMS_LABEL)) {
                                generalParamsCol = col;
                            } else {
                                String mode = cell.getStringCellValue();
                                PlanCalcScoreConfigGroup.ModeParams modeParams = planCalcScore.getOrCreateModeParams(mode);
                                modeParamsConfig.put(col, modeParams);
                            }
                        }
                    }
                } else if (MODE_PARAM_LABELS.contains(rowLabel)) {
                    for (Map.Entry<Integer, PlanCalcScoreConfigGroup.ModeParams> entry : modeParamsConfig.entrySet()) {
                        Cell cell = row.getCell(entry.getKey());
                        PlanCalcScoreConfigGroup.ModeParams modeParams = entry.getValue();

                        if ((cell.getCellTypeEnum() == CellType.NUMERIC) || (cell.getCellTypeEnum() == CellType.FORMULA)) {
                            switch (rowLabel) {
                                case CONSTANT:
                                    modeParams.setConstant(cell.getNumericCellValue());
                                    break;
                                case MARGINAL_UTILITY_OF_DISTANCE:
                                    modeParams.setMarginalUtilityOfDistance(cell.getNumericCellValue());
                                    break;
                                case MARGINAL_UTILITY_OF_TRAVELING:
                                    modeParams.setMarginalUtilityOfTraveling(cell.getNumericCellValue());
                                    break;
                                case MONETARY_DISTANCE_RATE:
                                    modeParams.setMonetaryDistanceRate(cell.getNumericCellValue());
                                    break;
                            }
                        }
                    }
                } else if (GENERAL_PARAM_LABELS.contains(rowLabel)) {
                    Cell cell = row.getCell(generalParamsCol);

                    if ((cell.getCellTypeEnum() == CellType.NUMERIC) || (cell.getCellTypeEnum() == CellType.FORMULA)) {
                        planCalcScore.addParam(rowLabel, String.valueOf(cell.getNumericCellValue()));
                    }
                }
            }
        }
    }

    protected static void parseBehaviorGroupParamsSheet(String behaviorGroupName, Sheet bgParamsSheet, PlanCalcScoreConfigGroup planCalcScore) {

        /*
        SBBBehaviorGroupsConfigGroup bgConfigGroup = (SBBBehaviorGroupsConfigGroup) config.getModules().get(SBBBehaviorGroupsConfigGroup.GROUP_NAME);

        SBBBehaviorGroupsConfigGroup.BehaviorGroupParams bgParams = new SBBBehaviorGroupsConfigGroup.BehaviorGroupParams();
        bgParams.setBehaviorGroupName("Abobesitz");
        bgParams.setPersonAttribute("season_ticket");

        SBBBehaviorGroupsConfigGroup.PersonGroupTypes types = new SBBBehaviorGroupsConfigGroup.PersonGroupTypes();
        types.setPersonGroupType("none");
        SBBBehaviorGroupsConfigGroup.ModeCorrection modeCorrection = new SBBBehaviorGroupsConfigGroup.ModeCorrection();
        modeCorrection.setMode("pt");
        modeCorrection.setConstant(99);
        modeCorrection.setMargUtilOfTime(99);
        modeCorrection.setMargUtilOfDistance(99);
        modeCorrection.setDistanceRate(99);

        types.addModeCorrection(modeCorrection);

        bgParams.addPersonGroupType(types);

        bgConfigGroup.addBehaviorGroupParams(bgParams);
*/
    }
}
