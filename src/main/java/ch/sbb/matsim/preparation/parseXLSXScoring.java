/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2018.
 */

package ch.sbb.matsim.preparation;

import ch.sbb.matsim.RunSBB;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigWriter;

import java.io.FileInputStream;
import java.io.IOException;

public class parseXLSXScoring {
    public static void main(final String[] args) {
        final String configIn = args[0];
        final String configOut = args[1];
        final String xlsx = args[2];

        final Config config = RunSBB.buildConfig(configIn);

        XLSXScoringParser scoringParser = new XLSXScoringParser();

        try {
            FileInputStream inputStream = new FileInputStream(xlsx);
            Workbook workbook = WorkbookFactory.create(inputStream);

            scoringParser.parseXLSXWorkbook(workbook, config);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InvalidFormatException e) {
            e.printStackTrace();
        }

        new ConfigWriter(config).write(configOut);
    }
}
