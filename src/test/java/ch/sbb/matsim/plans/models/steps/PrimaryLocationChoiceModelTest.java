package ch.sbb.matsim.plans.models.steps;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link PrimaryLocationChoiceModel}.
 *
 * <p>The full {@code runForActivity}/{@code runForAttractionGroup} pipeline is heavily wired
 * (Scenario, Households, Facilities, Zones, Matrices, ModeImpedanceFormulaRepository,
 * MobilityToolOwnershipModel, YAML config, kalif matrices, ...). Setting all of that up in a
 * unit test would essentially reproduce a full integration test fixture.
 *
 * <p>Instead, we focus on the mathematical core that is independent of MATSim infrastructure:
 * the IPF (Iterative Proportional Fitting / Furness) routine. It is the most algorithmically
 * dense piece of {@link PrimaryLocationChoiceModel} and the most likely place for regressions.
 */
class PrimaryLocationChoiceModelTest {

    private static final double EPS = 1e-6;

    /** Each row of probabilities must sum to 1 (it's a discrete choice distribution per origin). */
    private static void assertRowsSumToOne(double[][] probs) {
        for (int i = 0; i < probs.length; i++) {
            double sum = 0.0;
            for (double v : probs[i]) sum += v;
            assertEquals(1.0, sum, 1e-9, "Row " + i + " probabilities must sum to 1, but was " + sum);
        }
    }

    /** Marginal column targets implied by sampling rowTargets[i] persons from row i. */
    private static double[] colMarginals(double[][] probs, double[] rowTargets) {
        int nDest = probs[0].length;
        double[] colSums = new double[nDest];
        for (int i = 0; i < probs.length; i++) {
            for (int j = 0; j < nDest; j++) {
                colSums[j] += probs[i][j] * rowTargets[i];
            }
        }
        return colSums;
    }

    @Test
    void emptyInput_returnsEmptyMatrix() {
        double[][] probs = PrimaryLocationChoiceModel.runIPF(new double[0][0], new double[0], new double[0], 10);
        assertEquals(0, probs.length);
    }

    @Test
    void zeroColumnTargets_returnsZeroMatrix() {
        double[][] utility = {{0.0, 0.0}, {0.0, 0.0}};
        double[] rowTargets = {1.0, 1.0};
        double[] colTargets = {0.0, 0.0};

        double[][] probs = PrimaryLocationChoiceModel.runIPF(utility, rowTargets, colTargets, 5);

        assertEquals(2, probs.length);
        assertEquals(2, probs[0].length);
        for (double[] row : probs) {
            for (double v : row) {
                assertEquals(0.0, v, EPS);
            }
        }
    }

    @Test
    void uniformUtilityAndUniformTargets_yieldUniformProbabilities() {
        // 3 origins, 3 destinations; everything symmetric.
        double[][] utility = {
                {0.0, 0.0, 0.0},
                {0.0, 0.0, 0.0},
                {0.0, 0.0, 0.0}
        };
        double[] rowTargets = {10.0, 10.0, 10.0};
        double[] colTargets = {1.0, 1.0, 1.0};

        double[][] probs = PrimaryLocationChoiceModel.runIPF(utility, rowTargets, colTargets, 50);

        assertRowsSumToOne(probs);
        for (double[] row : probs) {
            for (double v : row) {
                assertEquals(1.0 / 3.0, v, EPS, "Expected uniform 1/3 distribution");
            }
        }
    }

    @Test
    void higherUtilityDestinationGetsHigherProbability() {
        // Single origin so column targets do not over-constrain the choice.
        // Destination 1 has higher utility => should attract more probability mass.
        double[][] utility = {
                {0.0, 1.0}
        };
        double[] rowTargets = {10.0};
        double[] colTargets = {1.0, 1.0}; // equal nominal capacity, but only one origin

        double[][] probs = PrimaryLocationChoiceModel.runIPF(utility, rowTargets, colTargets, 100);

        assertRowsSumToOne(probs);
        // With a single origin the col-marginal constraint is automatically satisfied by 50/50,
        // because col targets get scaled to sum to rowTargets. So this case actually equalises.
        // Use asymmetric col targets instead to expose utility:
        double[][] utility2 = {
                {0.0, 1.0},
                {0.0, 1.0}
        };
        double[] rowTargets2 = {5.0, 5.0};
        // Column 1 has *more* capacity than column 0 — combined with higher utility it must dominate.
        double[] colTargets2 = {1.0, 3.0};

        double[][] probs2 = PrimaryLocationChoiceModel.runIPF(utility2, rowTargets2, colTargets2, 200);
        assertRowsSumToOne(probs2);
        for (int i = 0; i < 2; i++) {
            assertTrue(probs2[i][1] > probs2[i][0],
                    "Higher-utility & higher-capacity destination should dominate, got " + Arrays.toString(probs2[i]));
        }
    }

    @Test
    void minusMaxValueUtility_yieldsZeroProbabilityForThatCell() {
        // Destination 1 from origin 0 is forbidden (-Double.MAX_VALUE).
        double[][] utility = {
                {0.0, -Double.MAX_VALUE},
                {0.0, 0.0}
        };
        double[] rowTargets = {1.0, 1.0};
        double[] colTargets = {1.0, 1.0};

        double[][] probs = PrimaryLocationChoiceModel.runIPF(utility, rowTargets, colTargets, 100);

        assertRowsSumToOne(probs);
        assertEquals(0.0, probs[0][1], EPS, "Forbidden cell must have zero probability");
        assertEquals(1.0, probs[0][0], EPS, "Origin 0 must allocate 100% to its only allowed destination");
    }

    @Test
    void ipf_balancesColumnMarginalsTowardTargets() {
        // Asymmetric utility + asymmetric column targets — IPF should reshape probabilities so that
        // column marginals (weighted by row targets) match the proportional column targets.
        double[][] utility = {
                {0.5, -0.2, 0.1},
                {0.1, 0.3, -0.4},
                {-0.1, 0.0, 0.5}
        };
        double[] rowTargets = {30.0, 50.0, 20.0};
        double[] colTargets = {60.0, 10.0, 30.0};

        double[][] probs = PrimaryLocationChoiceModel.runIPF(utility, rowTargets, colTargets, 200);

        assertRowsSumToOne(probs);

        // Total persons is 100; column targets {60, 10, 30} already sum to 100, so the IPF should
        // reproduce those marginals very accurately.
        double[] computedColMarginals = colMarginals(probs, rowTargets);
        assertArrayEquals(new double[]{60.0, 10.0, 30.0}, computedColMarginals, 1e-3);
    }

    @Test
    void ipf_scalesColumnTargetsToMatchRowTotal() {
        // Column targets sum to 10, row targets sum to 100. IPF first scales col targets up by 10x,
        // then balances. Resulting column marginals should equal scaled targets {50, 30, 20}.
        double[][] utility = new double[2][3]; // all zeros => uniform absent constraints
        double[] rowTargets = {60.0, 40.0};
        double[] colTargets = {5.0, 3.0, 2.0};

        double[][] probs = PrimaryLocationChoiceModel.runIPF(utility, rowTargets, colTargets, 200);

        assertRowsSumToOne(probs);

        double[] computedColMarginals = colMarginals(probs, rowTargets);
        // Scaling factor = 100/10 = 10 => expected marginals {50, 30, 20}
        assertArrayEquals(new double[]{50.0, 30.0, 20.0}, computedColMarginals, 1e-3);
    }

    @Test
    void ipf_isStableAcrossManyIterations() {
        // Verify the algorithm converges and additional iterations do not destabilise the solution.
        double[][] utility = {
                {0.2, 0.1, -0.3},
                {-0.1, 0.4, 0.2},
                {0.0, 0.0, 0.5}
        };
        double[] rowTargets = {10.0, 20.0, 15.0};
        double[] colTargets = {15.0, 15.0, 15.0};

        double[][] probs50 = PrimaryLocationChoiceModel.runIPF(utility, rowTargets, colTargets, 50);
        double[][] probs500 = PrimaryLocationChoiceModel.runIPF(utility, rowTargets, colTargets, 500);

        for (int i = 0; i < probs50.length; i++) {
            assertArrayEquals(probs50[i], probs500[i], 1e-6,
                    "IPF should be stable: 50- and 500-iteration results must agree on row " + i);
        }
    }

    @Test
    void ipf_inputArraysAreNotMutated() {
        // The algorithm makes defensive copies of rowTargets/colTargets — verify that.
        double[][] utility = {{0.0, 0.0}, {0.0, 0.0}};
        double[] rowTargets = {3.0, 7.0};
        double[] colTargets = {1.0, 4.0};

        double[] rowSnapshot = Arrays.copyOf(rowTargets, rowTargets.length);
        double[] colSnapshot = Arrays.copyOf(colTargets, colTargets.length);

        PrimaryLocationChoiceModel.runIPF(utility, rowTargets, colTargets, 25);

        assertArrayEquals(rowSnapshot, rowTargets, 0.0, "rowTargets must not be mutated by IPF");
        assertArrayEquals(colSnapshot, colTargets, 0.0, "colTargets must not be mutated by IPF");
    }

    @Test
    void singleOriginSingleDestination_yieldsProbabilityOne() {
        double[][] utility = {{2.5}};
        double[] rowTargets = {42.0};
        double[] colTargets = {5.0};

        double[][] probs = PrimaryLocationChoiceModel.runIPF(utility, rowTargets, colTargets, 10);

        assertEquals(1, probs.length);
        assertEquals(1, probs[0].length);
        assertEquals(1.0, probs[0][0], EPS);
    }

    @Test
    void allDestinationsForbiddenForOneOrigin_yieldsZeroRow() {
        // Origin 0 has no feasible destination; origin 1 has a normal choice.
        double[][] utility = {
                {-Double.MAX_VALUE, -Double.MAX_VALUE},
                {0.0, 0.5}
        };
        double[] rowTargets = {3.0, 5.0};
        double[] colTargets = {1.0, 1.0};

        double[][] probs = PrimaryLocationChoiceModel.runIPF(utility, rowTargets, colTargets, 100);

        // Origin 0 row sums to 0 (no valid choice), origin 1 row sums to 1.
        assertEquals(0.0, probs[0][0] + probs[0][1], EPS,
                "Origin with no feasible destination should produce a zero row");
        assertEquals(1.0, probs[1][0] + probs[1][1], EPS);
        assertTrue(probs[1][1] > probs[1][0],
                "Higher-utility destination should still dominate for the feasible origin");
    }
}


