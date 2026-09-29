package org.gtlcore.gtlcore.utils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class TagExprFilterTest {

    private static int assertions;

    private TagExprFilterTest() {}

    public static void main(String[] args) {
        rejectsCrushedOresWithUnrelatedTags();
        evaluatesAcrossTags();
        preservesLegacyPatterns();
        rejectsIncompleteExpressions();
        invalidatesCompiledExpressions();
        preservesUntaggedPolicy();
        checksTruthTables();
        System.out.println("TagExprFilter: " + assertions + " assertions passed.");
    }

    private static void rejectsCrushedOresWithUnrelatedTags() {
        Set<String> crushed = Set.of("forge:crushed_ores", "forge:crushed_ores/iron", "example:extra_tag");
        check("crushed_ores*", crushed, true);
        check("!crushed_ores*", crushed, false);
        check("!(crushed_ores*)", crushed, false);
        check("!*", crushed, false);
        check("!crushed_ores*", Set.of("forge:dusts/iron"), true);
        check("!crushed_ores*", Set.of("forge:purified_ores"), true);
        check("!crushed_ores*", Set.of("forge:refined_ores"), true);
    }

    private static void evaluatesAcrossTags() {
        Set<String> both = Set.of("forge:crushed_ores/iron", "example:extra_tag");
        check("crushed_ores*&extra_tag", both, true);
        check("crushed_ores*|extra_tag", both, true);
        check("crushed_ores*^extra_tag", both, false);
        check("crushed_ores*&!extra_tag", both, false);
        check("!(crushed_ores*|extra_tag)", both, false);
        check("crushed_ores*&(!dusts*|extra_tag)", both, true);
        check("(crushed_ores*)&extra_tag", both, true);
        check("(dusts*)|extra_tag", both, true);
        check("!!crushed_ores*", both, true);
    }

    private static void preservesLegacyPatterns() {
        check("circuits/*", Set.of("gtceu:circuits/lv"), true);
        check("circuits/*", Set.of("example:circuits/lv"), true);
        check("gtceu:circuits/*", Set.of("gtceu:circuits/lv"), true);
        check("forge:circuits/*", Set.of("gtceu:circuits/lv"), false);
        check("*:circuits/*", Set.of("gtceu:circuits/lv"), true);
        check("*gold*", Set.of("forge:double_plates/gold"), true);
        check("*gold*", Set.of("gold:plates/iron"), false);
        check("*", Set.of("forge:dusts/iron"), true);
        check("**dusts**", Set.of("forge:dusts/iron"), true);
        check("dusts/*", Set.of("forge:dusts/iron"), true);
        check("dusts/*", Set.of("forge:tiny_dusts/iron"), false);
        check("*dusts/*", Set.of("forge:tiny_dusts/iron"), true);
        check("dusts/*iron", Set.of("forge:dusts/wrought_iron"), true);
        check("dusts/*iron", Set.of("forge:dusts/iron_extra"), false);
        check("foo.bar", Set.of("forge:fooXbar"), false);
        check("foo.bar", Set.of("forge:foo.bar"), true);
        check("dusts/[iron]", Set.of("forge:dusts/[iron]"), true);
        check(" \t !crushed_ores* \n & dusts* ", Set.of("forge:dusts/iron"), true);
    }

    private static void rejectsIncompleteExpressions() {
        List<String> invalid = List.of("", " ", "!", "!!", "(", ")", "()", "!()", "a|", "a&", "a^",
                "|a", "&a", "^a", "a||b", "a&&b", "a^^b", "a(b)", "(a", "a)", "(a))", "(a|)",
                "!(a|)", "a|!", "a|()", "a b", "a!b", "a|(!)", "a|b&");
        for (String expression : invalid) {
            for (Set<String> tags : List.of(Set.<String>of(), Set.of("forge:a"), Set.of("forge:b"),
                    Set.of("forge:a", "forge:b", "forge:other"))) {
                check(expression, tags, false);
            }
        }
    }

    private static void invalidatesCompiledExpressions() {
        var cache = new TagExprFilter.CachedExpression();
        Set<String> tags = Set.of("forge:crushed_ores/iron", "example:extra_tag");
        require(cache.matches("crushed_ores*", tags));
        require(!cache.matches("!crushed_ores*", tags));
        require(!cache.matches("crushed_ores*|", tags));
        require(cache.matches("crushed_ores*", tags));
        require(!cache.matches("crushed_ores*", Set.of("forge:dusts/iron")));
        require(cache.matches("crushed_ores*", tags));
    }

    private static void preservesUntaggedPolicy() {
        var cache = new TagExprFilter.CachedExpression();
        for (String expression : List.of("*", "!*", "!crushed_ores*", "dusts*", "!(dusts*|crushed_ores*)")) {
            require(!cache.matches(expression, Set.of()));
        }
    }

    private static void checksTruthTables() {
        for (int mask = 0; mask < 8; mask++) {
            Set<String> tags = new HashSet<>();
            tags.add("example:unrelated");
            boolean a = (mask & 1) != 0;
            boolean b = (mask & 2) != 0;
            boolean c = (mask & 4) != 0;
            if (a) tags.add("forge:a");
            if (b) tags.add("gtceu:b");
            if (c) tags.add("example:c");
            check("!a", tags, !a);
            check("a&b", tags, a && b);
            check("a|b", tags, a || b);
            check("a^b", tags, a ^ b);
            check("!(a|b)", tags, !(a || b));
            check("!a&!b", tags, !a && !b);
            check("!(a&b)", tags, !(a && b));
            check("!a|!b", tags, !a || !b);
            check("a&b|c", tags, (a && b) || c);
            check("a|b&c", tags, (a || b) && c);
            check("a|(b&c)", tags, a || (b && c));
            check("(a^b)^c", tags, a ^ b ^ c);
            check("!(a&(!b|c))", tags, !(a && (!b || c)));
        }
    }

    private static void check(String expression, Set<String> tags, boolean expected) {
        boolean actual = TagExprFilter.parseExpression(expression).matches(tags);
        if (actual != expected) {
            throw new AssertionError(expression + " with " + tags + ": expected " + expected + ", got " + actual);
        }
        assertions++;
    }

    private static void require(boolean condition) {
        if (!condition) {
            throw new AssertionError("Cache/policy regression");
        }
        assertions++;
    }
}
