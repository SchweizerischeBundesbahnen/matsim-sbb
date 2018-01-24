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

    private static Logger log = Logger.getLogger(RunSBB.class);

    public static void main(final String[] args) {
        final String configIn = args[0];
        final String configOut = args[1];
        final String xlsx = args[2];

        final Config config = ConfigUtils.loadConfig(configIn, new PostProcessingConfigGroup(), new SBBTransitConfigGroup(),
                new SBBBehaviorGroupsConfigGroup(),new SBBPopulationSamplerConfigGroup());

        Map<String, Double> generalParams = new TreeMap<>();
        Integer generalParamsCol = null;
        Map<Integer, String> modes = new TreeMap<Integer, String>();
        Map<String, Map<String, Double>> modeParamsMap = new TreeMap<String, Map<String, Double>>();

        try {
            FileInputStream inputStream = new FileInputStream(xlsx);
            Workbook workbook = WorkbookFactory.create(inputStream);
            Sheet scoringParamsSheet = workbook.getSheet(SCORING_SHEET_LABEL);

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
                                    modes.put(col, cell.getStringCellValue());
                                }
                            }
                        }

                        continue;
                    } else if (MODE_PARAM_LABELS.contains(rowLabel)) {
                        for (Map.Entry<Integer, String> entry : modes.entrySet()) {
                            if (!modeParamsMap.containsKey(entry.getValue())) {
                                modeParamsMap.put(entry.getValue(), new TreeMap<String, Double>());
                            }

                            Cell cell = row.getCell(entry.getKey());

                            if (cell.getCellTypeEnum() == CellType.NUMERIC) {
                                modeParamsMap.get(entry.getValue()).put(rowLabel, cell.getNumericCellValue());
                            } else if (cell.getCellTypeEnum() == CellType.FORMULA) {
                                modeParamsMap.get(entry.getValue()).put(rowLabel, cell.getNumericCellValue());
                            }
                        }
                    } else if (GENERAL_PARAM_LABELS.contains(rowLabel)) {
                        Cell cell = row.getCell(generalParamsCol);

                        if (cell.getCellTypeEnum() == CellType.NUMERIC) {
                            generalParams.put(rowLabel, cell.getNumericCellValue());
                        } else if (cell.getCellTypeEnum() == CellType.FORMULA) {
                            generalParams.put(rowLabel, cell.getNumericCellValue());
                        }
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InvalidFormatException e) {
            e.printStackTrace();
        }

        PlanCalcScoreConfigGroup planCalcScore = config.planCalcScore();

        for (Map.Entry<String, Double> entry : generalParams.entrySet()) {
            planCalcScore.addParam(entry.getKey(), String.valueOf(entry.getValue()));
        }

        for (Map.Entry<String, Map<String, Double>> entry : modeParamsMap.entrySet()) {
            String mode = entry.getKey();

            PlanCalcScoreConfigGroup.ModeParams modeParams = planCalcScore.getOrCreateModeParams(mode);

            for (Map.Entry<String, Double> paramEntry : entry.getValue().entrySet()) {
                switch (paramEntry.getKey()) {
                    case CONSTANT:
                        modeParams.setConstant(paramEntry.getValue());
                        break;
                    case MARGINAL_UTILITY_OF_DISTANCE:
                        modeParams.setMarginalUtilityOfDistance(paramEntry.getValue());
                        break;
                    case MARGINAL_UTILITY_OF_TRAVELING:
                        modeParams.setMarginalUtilityOfTraveling(paramEntry.getValue());
                        break;
                    case MONETARY_DISTANCE_RATE:
                        modeParams.setMonetaryDistanceRate(paramEntry.getValue());
                        break;
                }
            }
        }

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
        new ConfigWriter(config).write(configOut);
    }
}
