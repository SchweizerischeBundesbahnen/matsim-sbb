/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.config;

import org.matsim.core.config.ReflectiveConfigGroup;


public class PtMergerConfigGroup extends ReflectiveConfigGroup {

        static public final String GROUP_NAME = "PtMerger";

        private String networkFile = "";
        private String lineToDeleteFile = "";
        private String scheduleFile = "";
        private String vehiclesFile = "";
        private String prefix = "";
        private String output = "./output_merger";

        public PtMergerConfigGroup() {
            super(GROUP_NAME);
        }

        @StringGetter("output")
        public String getOutput() {
            return output;
        }

        @StringSetter("output")
        void setOutput(String file) {
            this.output = file;
        }

        @StringGetter("lineToDeleteFile")
        public String getLineToDeleteFile() {
            return lineToDeleteFile;
        }

        @StringSetter("lineToDeleteFile")
        void setLineToDeleteFile(String file) {
            this.lineToDeleteFile = file;
        }

        @StringGetter("prefix")
        public String getPrefix() {
            return prefix;
        }

        @StringSetter("prefix")
        void setPrefix(String file) {
            this.prefix = file;
        }

        @StringGetter("vehiclesFile")
        public String getVehiclesFile() {
            return vehiclesFile;
        }

        @StringSetter("vehiclesFile")
        void setVehiclesFile(String file) {
            this.vehiclesFile = file;
        }

        @StringGetter("scheduleFile")
        public String getScheduleFile() {
            return scheduleFile;
        }

        @StringSetter("scheduleFile")
        void setScheduleFile(String file) {
            this.scheduleFile = file;
        }

        @StringGetter("networkFile")
        public String getNetworkFile() {
            return networkFile;
        }

        @StringSetter("networkFile")
        void setNetworkFile(String file) {
            this.networkFile = file;
        }


}
