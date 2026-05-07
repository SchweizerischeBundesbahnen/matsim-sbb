package ch.sbb.matsim.plans.models.utils;

import org.junit.jupiter.api.Test;
import org.matsim.utils.objectattributes.attributable.Attributable;
import org.matsim.utils.objectattributes.attributable.Attributes;
import org.matsim.utils.objectattributes.attributable.AttributesImpl;

import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link BuildPredicate}.
 */
public class BuildPredicateTest {

    /**
     * A minimal Attributable implementation backed by an {@link AttributesImpl}.
     * Also exposes a few getter-style methods to exercise the reflection lookup path.
     */
    public static class TestEntity implements Attributable {
        private final Attributes attributes = new AttributesImpl();
        private double age;
        private boolean active;
        private String type;

        public void setAge(double age) {
            this.age = age;
            attributes.putAttribute("age", age);
        }

        public void setActive(boolean active) {
            this.active = active;
            attributes.putAttribute("active", active);
        }

        public void setType(String type) {
            this.type = type;
            attributes.putAttribute("type", type);
        }

        public double getAge() {
            return age;
        }


        @Override
        public Attributes getAttributes() {
            return attributes;
        }
    }

    private TestEntity entity(double age, boolean active, String type) {
        TestEntity e = new TestEntity();
        e.setAge(age);
        e.setActive(active);
        e.setType(type);
        return e;
    }

    @Test
    public void emptyExpressionMatchesEverything() {
        Predicate<TestEntity> p = BuildPredicate.buildPredicate("", TestEntity.class);
        assertTrue(p.test(entity(10, true, "x")));

        Predicate<TestEntity> p2 = BuildPredicate.buildPredicate(null, TestEntity.class);
        assertTrue(p2.test(entity(10, true, "x")));
    }

    @Test
    public void numericComparisons() {
        TestEntity youngActive = entity(15, true, "work");
        TestEntity oldInactive = entity(70, false, "home");

        assertTrue(BuildPredicate.buildPredicate("age>=15", TestEntity.class).test(youngActive));
        assertTrue(BuildPredicate.buildPredicate("age>=15", TestEntity.class).test(oldInactive));
        assertFalse(BuildPredicate.buildPredicate("age>15", TestEntity.class).test(youngActive));
        assertTrue(BuildPredicate.buildPredicate("age>15", TestEntity.class).test(oldInactive));
        assertTrue(BuildPredicate.buildPredicate("age<=15", TestEntity.class).test(youngActive));
        assertTrue(BuildPredicate.buildPredicate("age=15", TestEntity.class).test(youngActive));
        assertFalse(BuildPredicate.buildPredicate("age=15", TestEntity.class).test(oldInactive));
    }

    @Test
    public void booleanComparison() {
        Predicate<TestEntity> activeTrue = BuildPredicate.buildPredicate("active=true", TestEntity.class);
        Predicate<TestEntity> activeFalse = BuildPredicate.buildPredicate("active=false", TestEntity.class);

        assertTrue(activeTrue.test(entity(1, true, "x")));
        assertFalse(activeTrue.test(entity(1, false, "x")));
        assertFalse(activeFalse.test(entity(1, true, "x")));
        assertTrue(activeFalse.test(entity(1, false, "x")));
    }

    @Test
    public void stringComparison() {
        Predicate<TestEntity> isWork = BuildPredicate.buildPredicate("type='work'", TestEntity.class);
        Predicate<TestEntity> notWork = BuildPredicate.buildPredicate("type!='work'", TestEntity.class);

        assertTrue(isWork.test(entity(1, true, "work")));
        assertFalse(isWork.test(entity(1, true, "home")));
        assertFalse(notWork.test(entity(1, true, "work")));
        assertTrue(notWork.test(entity(1, true, "home")));
    }

    @Test
    public void andOrCombinations() {
        Predicate<TestEntity> p = BuildPredicate.buildPredicate(
                "age>=18 & age<65 & active=true", TestEntity.class);
        assertTrue(p.test(entity(30, true, "x")));
        assertFalse(p.test(entity(30, false, "x")));
        assertFalse(p.test(entity(15, true, "x")));
        assertFalse(p.test(entity(70, true, "x")));

        Predicate<TestEntity> p2 = BuildPredicate.buildPredicate(
                "type='work' | type='education'", TestEntity.class);
        assertTrue(p2.test(entity(1, true, "work")));
        assertTrue(p2.test(entity(1, true, "education")));
        assertFalse(p2.test(entity(1, true, "leisure")));
    }

    @Test
    public void compareTwoAttributes() {
        // origin/type style attributes via raw attribute names (no slashes)
        TestEntity e = entity(1, true, "work");
        e.getAttributes().putAttribute("type2", "work");
        TestEntity e2 = entity(1, true, "work");
        e2.getAttributes().putAttribute("type2", "home");

        Predicate<TestEntity> sameType = BuildPredicate.buildPredicate("type=type2", TestEntity.class);
        assertTrue(sameType.test(e));
        assertFalse(sameType.test(e2));

        Predicate<TestEntity> diffType = BuildPredicate.buildPredicate("type!=type2", TestEntity.class);
        assertFalse(diffType.test(e));
        assertTrue(diffType.test(e2));
    }

    @Test
    public void unknownAttributeReturnsFalseAndDoesNotThrow() {
        Predicate<TestEntity> p = BuildPredicate.buildPredicate("doesNotExist>0", TestEntity.class);
        assertFalse(p.test(entity(1, true, "x")));
    }

    @Test
    public void reflectionCacheIsConsistentAcrossCalls() {
        // Calling the same predicate many times should return stable results;
        // this primarily exercises the cached Method/Field lookup path.
        Predicate<TestEntity> p = BuildPredicate.buildPredicate("age>=18", TestEntity.class);
        for (int i = 0; i < 1000; i++) {
            assertTrue(p.test(entity(20, true, "x")));
            assertFalse(p.test(entity(10, true, "x")));
        }
    }
}

