package org.gtlcore.gtlcore.api.recipe;

import com.gregtechceu.gtceu.api.recipe.content.ContentModifier;

public interface IAdvancedContentModifier {

    void setDivision(long numerator, long denominator);

    static ContentModifier preciseDivision(long numerator, long denominator) {
        var modifier = new ContentModifier(0, 0);
        ((IAdvancedContentModifier) modifier).setDivision(numerator, denominator);
        return modifier;
    }

    static ContentModifier preciseMultiplier(long multiplier) {
        var modifier = new ContentModifier(multiplier, 0);
        ((IAdvancedContentModifier) modifier).setDivision(multiplier, 1);
        return modifier;
    }
}
