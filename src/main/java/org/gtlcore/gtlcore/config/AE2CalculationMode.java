package org.gtlcore.gtlcore.config;

import lombok.Getter;

@Getter
public enum AE2CalculationMode {

    LEGACY("legacy", "使用原版AE2合成算法"),
    FAST("fast", "使用快速合成算法(可能导致极端情况下计算失败)"),
    ULTRA_FAST("ultra_fast", "使用超快速合成算法(不会在不同合成路径间均分)");

    private final String name;
    private final String description;

    AE2CalculationMode(String name, String description) {
        this.name = name;
        this.description = description;
    }

    @Override
    public String toString() {
        return name;
    }
}
