#!/usr/bin/env python
# -*- coding: utf-8 -*-

import pandas as pd

from matsim.calibration import create_calibration, ASCGroupCalibrator, utils, study_as_df

# %%

modes = ["walk", "car", "ride", "pt", "bike"]
fixed_mode = "walk"
initial = {
    "bike": -0.55,
    "pt": -0.03,
    "car": -0.15,
    "ride": -0.94
}


# walk_main has 0.25 asc

def filter_persons(df):
    # Only regular persons are relevant
    df = df[df.subpopulation == "regular"]

    # Convert to correct types
    df.current_edu.fillna("null", inplace=True)
    df.car_available = df.car_available.astype("int").astype("string")
    df.residence_msr_id = df.residence_msr_id.astype("int").astype("string")

    return df


def filter_modes(df):
    # walk_main will be just walk
    df.loc[df.main_mode == "walk_main", "main_mode"] = "walk"

    return df[df.main_mode.isin(modes)]


def cli(jvm_args, jar, config, params_path, run_dir, trial_number, run_args):
    return "java %s -jar %s %s %s --config:controler.runId %03d --params %s %s" % (
        jvm_args, jar, config, run_dir, trial_number, params_path, run_args
    )


target = pd.read_csv("ref_data.csv", dtype={"car_available": "string"},
                     na_values=["", "na"], keep_default_na=False)

car_available = target[target.car_available == "0"]
car_available.loc[car_available.main_mode == "car", "share"] = 0

# Set car share in ref to 0 because MATSim share will always be 0
target.loc[target.car_available == "0", "share"] = car_available.share / car_available.share.sum()

residence = pd.read_csv("ref_by_residence.csv")

# Merge the residence groups into target
target = pd.concat([target, residence])

study, obj = create_calibration("calib",
                                ASCGroupCalibrator(modes, initial, target,
                                                   multi_groups=["residence_msr_id"],
                                                   config_format="sbb",
                                                   lr=utils.linear_scheduler(start=0.25, interval=20)),
                                "../../../target/matsim-sbb-4.0.6-SNAPSHOT-jar-with-dependencies.jar",
                                "../../../sim/0.01-ref-2020/config_scoring_parsed.xml",
                                args="--config:controler.lastIteration 400",
                                jvm_args="-Xmx12G -Xmx12G -XX:+AlwaysPreTouch -XX:+UseParallelGC",
                                custom_cli=cli,
                                transform_persons=filter_persons,
                                transform_trips=filter_modes,
                                chain_runs=utils.default_chain_scheduler, debug=True)

study.optimize(obj, 5)

df = study_as_df(study)
df.to_csv("report.csv")
