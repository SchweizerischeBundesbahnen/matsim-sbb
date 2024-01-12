#!/bin/bash

python3 -m venv venv
source venv/bin/activate

pip install --upgrade pip
# TODO: needs to be changed when branch is merged
pip install "matsim-tools[calibration] @ git+https://github.com/matsim-vsp/matsim-python-tools.git@calibration"