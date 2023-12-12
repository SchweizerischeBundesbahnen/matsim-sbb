#!/usr/bin/env python
# -*- coding: utf-8 -*-

import pandas as pd

from sklearn.cluster import KMeans


def compute_grouped_share(df, groups):
    data = []

    for g in groups:
        if type(g) == int:
            tf = df[df.residence_msr_id == g]
        else:
            tf = df[df.residence_msr_id.isin(g)]

        print("Group", g, "size", tf["mzmv.1"].sum())

        grouped = tf.groupby("main_mode").sum()["mzmv.1"]
        shares = grouped / grouped.sum()

        for mode, share in shares.items():
            data.append({
                "residence_msr_id": str(g) if type(g) == int else ",".join(str(x) for x in g),
                "main_mode": mode,
                "share": share
            })

    return pd.DataFrame(data)


if __name__ == "__main__":
    df = pd.read_excel("report_residence_msr.xlsx", sheet_name=2, skiprows=1)

    def norm(x):
        total = x["mzmv.1"].sum()
        x["mzmv.1"] /= total
        x = x.set_index("main_mode")

        return x["mzmv.1"]

    X = df.groupby(["residence_msr_id"]).apply(norm)

    clustering = KMeans(n_clusters=8, random_state=0, n_init="auto").fit(X.to_numpy())
    print(clustering.labels_)
    for i in range(clustering.n_clusters):
        idx = clustering.labels_ == i
        Y = X[idx]
        print(Y)

    # Manually defined residence groups
    groups = [
        82,
        99,
        105,
        84,
        (70, 71, 72, 73, 74, 75, 76, 77, 78),
        47,
        11,
        8,
        1,
        81,
        26,
        106,
        (102, 103),
        (87, 86, 91),
        (61, 62, 63, 64, 65, 66, 67, 68),
        (40, 41, 42, 43),
        (2, 3),
        (5, 6),
        53
    ]

    compute_grouped_share(df, groups).to_csv("ref_by_residence.csv", index=False)
