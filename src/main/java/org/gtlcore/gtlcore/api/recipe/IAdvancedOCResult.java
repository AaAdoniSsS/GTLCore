package org.gtlcore.gtlcore.api.recipe;

public interface IAdvancedOCResult {

    void init(long eut, int duration, int parallel, long parallelEUt, int baseOCLevel, int totalOCLevel, double durationFactor, double voltageFactor);

    int getBaseOCLevel();

    double getDurationFactor();

    double getVoltageFactor();

    boolean isSubTickOverclockAvailable();

    void setSubTickOverclockAvailable(boolean available);
}
