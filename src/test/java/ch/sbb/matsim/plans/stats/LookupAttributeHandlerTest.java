package ch.sbb.matsim.plans.stats;

import org.junit.jupiter.api.Test;
import org.matsim.utils.objectattributes.attributable.Attributable;
import org.matsim.utils.objectattributes.attributable.Attributes;
import org.matsim.utils.objectattributes.attributable.AttributesImpl;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link LookupAttributeHandler}.
 */
public class LookupAttributeHandlerTest {

    /** Simple Attributable parent that exposes a child via a getter. */
    public static class Parent implements Attributable {
        private final Attributes attrs = new AttributesImpl();
        private final Child zone;

        public Parent(Child zone) {
            this.zone = zone;
        }

        public Child getZone() {
            return zone;
        }

        @Override
        public Attributes getAttributes() {
            return attrs;
        }
    }

    /** Child Attributable referenced by Parent. */
    public static class Child implements Attributable {
        private final Attributes attrs = new AttributesImpl();
        private final int sl3Id;

        public Child(int sl3Id) {
            this.sl3Id = sl3Id;
            attrs.putAttribute("sl3_id", sl3Id);
        }

        public int getSl3Id() {
            return sl3Id;
        }

        @Override
        public Attributes getAttributes() {
            return attrs;
        }
    }

    @Test
    public void mapAttributeReplacesSlashWithUnderscore() {
        assertEquals("zone_sl3_id", LookupAttributeHandler.mapAttribute("zone/sl3_id"));
        assertEquals("origin_activity_type",
                LookupAttributeHandler.mapAttribute("origin/activity/type"));
        assertEquals("plain", LookupAttributeHandler.mapAttribute("plain"));
    }

    @Test
    public void normalizeToLowerRightLowercasesEachSegment() {
        // Only the first character of every segment is lowercased; the rest is preserved.
        assertEquals("originActivity/type",
                LookupAttributeHandler.normalizeToLowerRight("OriginActivity/Type"));
        assertEquals("a/b/c", LookupAttributeHandler.normalizeToLowerRight("A/B/C"));
        assertEquals("foo/barBaz",
                LookupAttributeHandler.normalizeToLowerRight("Foo/BarBaz"));
    }

    @Test
    public void findAttributesWithSlashReturnsOnlyShortenedNames() {
        LookupAttributeHandler h = new LookupAttributeHandler();
        // Foo/BarBaz normalizes to "foo/barBaz". mapAttribute on the normalized form is "foo_barBaz",
        // which differs from "foo/barBaz", so the entry is kept.
        Map<String, String> attrs = h.findAttributesWithSlash("[Foo/BarBaz] + 1");
        assertTrue(attrs.containsKey("Foo/BarBaz"));
        assertEquals("foo/barBaz", attrs.get("Foo/BarBaz"));

        // an expression without a slash returns nothing
        assertTrue(h.findAttributesWithSlash("[plain] + 1").isEmpty());
        // null is handled
        assertTrue(h.findAttributesWithSlash(null).isEmpty());
    }

    @Test
    public void replaceAttributesRewritesSlashesToUnderscores() {
        LookupAttributeHandler h = new LookupAttributeHandler();
        // "Zone/Sl3_id" normalizes to "zone/sl3_id" and is replaced with the underscored form
        // "zone_sl3_id".
        String replaced = h.replaceAttributes("[Zone/Sl3_id] = 1");
        assertTrue(replaced.contains("zone_sl3_id"), "got: " + replaced);
        assertFalse(replaced.contains("Zone/Sl3_id"), "got: " + replaced);
    }

    @Test
    public void addLookupVariableStoresPerTable() {
        LookupAttributeHandler h = new LookupAttributeHandler();
        h.addLookupVariable("Persons", "zone/sl3_id", "zone_sl3_id");
        h.addLookupVariable("Persons", "work_zone/sl3_id", "work_zone_sl3_id");
        h.addLookupVariable("Trips", "person/age", "person_age");

        assertEquals("zone_sl3_id", h.lookupVariables.get("Persons").get("zone/sl3_id"));
        assertEquals("work_zone_sl3_id",
                h.lookupVariables.get("Persons").get("work_zone/sl3_id"));
        assertEquals("person_age", h.lookupVariables.get("Trips").get("person/age"));
    }

    @Test
    public void getFieldValueResolvesGetterChain() {
        Child child = new Child(42);
        Parent parent = new Parent(child);

        // Walk getter chain Parent -> Child -> sl3_id (via attribute)
        Object v = LookupAttributeHandler.getFieldValue(parent, "zone/sl3_id", Parent.class);
        // The handler converts numbers to Double at the leaf
        assertNotNull(v);
        assertInstanceOf(Double.class, v);
        assertEquals(42.0, (Double) v, 1e-9);
    }

    @Test
    public void getFieldValueReturnsNullForUnknownPath() {
        Parent parent = new Parent(null);
        Object v = LookupAttributeHandler.getFieldValue(parent, "zone/sl3_id", Parent.class);
        assertNull(v);
    }

    @Test
    public void getFieldObjectFallsBackToAttributesMap() {
        Parent p = new Parent(null);
        p.getAttributes().putAttribute("custom", "hello");
        Object v = LookupAttributeHandler.getFieldObject(p, "custom", Parent.class);
        assertEquals("hello", v);
    }

    @Test
    public void cachingIsConsistentAcrossManyCalls() {
        Child child = new Child(7);
        Parent parent = new Parent(child);
        for (int i = 0; i < 1000; i++) {
            Object v = LookupAttributeHandler.getFieldValue(parent, "zone/sl3_id", Parent.class);
            assertEquals(7.0, (Double) v, 1e-9);
        }
    }
}

