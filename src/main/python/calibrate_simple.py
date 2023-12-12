#!/usr/bin/env python
# -*- coding: utf-8 -*-

from matsim.calibration import create_calibration, ASCCalibrator, utils, study_as_df

# %%

modes = ["walk", "car", "ride", "pt", "bike"]
fixed_mode = "walk"
initial = {
    "bike": -0.55,
    "pt": -0.03,
    "car": -0.15,
    "ride": -0.94
}

target = {
    "walk": 0.287333402174228,
    "bike": 0.0744672136924372,
    "pt": 0.162237636757827,
    "car": 0.395614933034831,
    "ride": 0.0803468143406766
}


def filter_persons(df):
    # Only regular persons are relevant
    return df[df.subpopulation == "regular"]


def filter_modes(df):
    # walk_main will be just walk
    df.loc[df.main_mode == "walk_main", "main_mode"] = "walk"

    return df[df.main_mode.isin(modes)]


def cli(jvm_args, jar, config, params_path, run_dir, trial_number, run_args):
    return "java %s -jar %s %s %s --config:controler.runId %03d --params %s %s" % (
        jvm_args, jar, config, run_dir, trial_number, params_path, run_args
    )


study, obj = create_calibration("calib",
                                ASCCalibrator(modes, initial, target,
                                              lr=utils.linear_scheduler(start=0.25, interval=20)),
                                "../../../target/matsim-sbb-4.0.6-SNAPSHOT-jar-with-dependencies.jar",
                                "../../../sim/0.01-ref-2020/config_scoring_parsed.xml",
                                args="--config:controler.lastIteration 400",
                                jvm_args="-Xmx12G -Xmx12G -XX:+AlwaysPreTouch -XX:+UseParallelGC",
                                custom_cli=cli,
                                transform_persons=filter_persons,
                                transform_trips=filter_modes,
                                chain_runs=utils.default_chain_scheduler, debug=False)

# %%

study.optimize(obj, 5)

df = study_as_df(study)
df.to_csv("report.csv")
