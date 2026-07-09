package org.gtlcore.gtlcore.mixin.ae2.service;

import org.gtlcore.gtlcore.integration.ae2.energy.AE2EnergyDebugLogger;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.hooks.ticking.TickHandler;
import appeng.me.Grid;
import appeng.me.service.EnergyService;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EnergyService.class)
public abstract class EnergyServiceMixin {

    @Unique
    private static final double GTLCORE$ENERGY_LOG_EPSILON = 1.0E-9D;

    @Shadow(remap = false)
    @Final
    private Grid grid;

    @Unique
    private final AE2EnergyDebugLogger.EnergyCounters gtlcore$energyCounters = new AE2EnergyDebugLogger.EnergyCounters();

    @Unique
    private double gtlcore$storedAtTickStart = Double.NaN;

    @Shadow(remap = false)
    public abstract double getStoredPower();

    @Shadow(remap = false)
    public abstract double getMaxStoredPower();

    @Shadow(remap = false)
    public abstract double getIdlePowerUsage();

    @Shadow(remap = false)
    public abstract double getChannelPowerUsage();

    @Shadow(remap = false)
    public abstract double getAvgPowerUsage();

    @Shadow(remap = false)
    public abstract double getAvgPowerInjection();

    @Shadow(remap = false)
    public abstract boolean isNetworkPowered();

    @Inject(method = "onServerStartTick", at = @At("HEAD"), remap = false)
    private void gtlcore$rememberEnergyAtTickStart(CallbackInfo ci) {
        if (AE2EnergyDebugLogger.isEnabled()) {
            gtlcore$storedAtTickStart = getStoredPower();
        }
    }

    @Inject(method = "onServerEndTick", at = @At("RETURN"), remap = false)
    private void gtlcore$writeEnergyTick(CallbackInfo ci) {
        if (!AE2EnergyDebugLogger.isEnabled()) {
            gtlcore$resetEnergyCounters();
            gtlcore$storedAtTickStart = Double.NaN;
            return;
        }

        double storedEnd = getStoredPower();
        double storedStart = Double.isNaN(gtlcore$storedAtTickStart) ? storedEnd : gtlcore$storedAtTickStart;
        if (gtlcore$hasRealEnergyActivity(storedStart, storedEnd)) {
            AE2EnergyDebugLogger.writeTick(
                    grid,
                    TickHandler.instance().getCurrentTick(),
                    grid.size(),
                    isNetworkPowered(),
                    storedStart,
                    storedEnd,
                    getMaxStoredPower(),
                    getIdlePowerUsage(),
                    getChannelPowerUsage(),
                    getAvgPowerUsage(),
                    getAvgPowerInjection(),
                    gtlcore$energyCounters);
        }

        gtlcore$resetEnergyCounters();
        gtlcore$storedAtTickStart = storedEnd;
    }

    @Inject(method = "extractAEPower", at = @At("RETURN"), remap = false)
    private void gtlcore$recordExtractRequest(
                                              double amount,
                                              Actionable mode,
                                              PowerMultiplier multiplier,
                                              CallbackInfoReturnable<Double> cir) {
        if (!AE2EnergyDebugLogger.isEnabled()) {
            return;
        }

        double extracted = cir.getReturnValue();
        if (mode == Actionable.MODULATE) {
            gtlcore$energyCounters.extractRequest += amount;
            gtlcore$energyCounters.extracted += extracted;
        } else {
            gtlcore$energyCounters.simulateExtractRequest += amount;
            gtlcore$energyCounters.simulateExtracted += extracted;
        }
    }

    @Inject(method = "extractProviderPower", at = @At("RETURN"), remap = false)
    private void gtlcore$recordProviderExtract(
                                               double amount,
                                               Actionable mode,
                                               CallbackInfoReturnable<Double> cir) {
        if (!AE2EnergyDebugLogger.isEnabled()) {
            return;
        }

        double extracted = cir.getReturnValue();
        if (mode == Actionable.MODULATE) {
            gtlcore$energyCounters.providerExtractRequest += amount;
            gtlcore$energyCounters.providerExtracted += extracted;
        } else {
            gtlcore$energyCounters.providerSimExtractRequest += amount;
            gtlcore$energyCounters.providerSimExtracted += extracted;
        }
    }

    @Inject(method = "injectPower", at = @At("RETURN"), remap = false)
    private void gtlcore$recordInjectRequest(
                                             double amount,
                                             Actionable mode,
                                             CallbackInfoReturnable<Double> cir) {
        if (!AE2EnergyDebugLogger.isEnabled()) {
            return;
        }

        double remainder = cir.getReturnValue();
        if (mode == Actionable.MODULATE) {
            gtlcore$energyCounters.injectInput += amount;
            gtlcore$energyCounters.injectRemainder += remainder;
        } else {
            gtlcore$energyCounters.simulateInjectInput += amount;
            gtlcore$energyCounters.simulateInjectRemainder += remainder;
        }
    }

    @Inject(method = "injectProviderPower", at = @At("RETURN"), remap = false)
    private void gtlcore$recordProviderInject(
                                              double amount,
                                              Actionable mode,
                                              CallbackInfoReturnable<Double> cir) {
        if (!AE2EnergyDebugLogger.isEnabled()) {
            return;
        }

        double remainder = cir.getReturnValue();
        if (mode == Actionable.MODULATE) {
            gtlcore$energyCounters.providerInjectInput += amount;
            gtlcore$energyCounters.providerInjectRemainder += remainder;
        } else {
            gtlcore$energyCounters.providerSimInjectInput += amount;
            gtlcore$energyCounters.providerSimInjectRemainder += remainder;
        }
    }

    @Unique
    private boolean gtlcore$hasRealEnergyActivity(double storedStart, double storedEnd) {
        return Math.abs(storedEnd - storedStart) > GTLCORE$ENERGY_LOG_EPSILON || gtlcore$energyCounters.extractRequest > GTLCORE$ENERGY_LOG_EPSILON || gtlcore$energyCounters.extracted > GTLCORE$ENERGY_LOG_EPSILON || gtlcore$energyCounters.providerExtractRequest > GTLCORE$ENERGY_LOG_EPSILON || gtlcore$energyCounters.providerExtracted > GTLCORE$ENERGY_LOG_EPSILON || gtlcore$energyCounters.injectInput > GTLCORE$ENERGY_LOG_EPSILON || gtlcore$energyCounters.providerInjectInput > GTLCORE$ENERGY_LOG_EPSILON;
    }

    @Unique
    private void gtlcore$resetEnergyCounters() {
        gtlcore$energyCounters.extractRequest = 0.0D;
        gtlcore$energyCounters.extracted = 0.0D;
        gtlcore$energyCounters.simulateExtractRequest = 0.0D;
        gtlcore$energyCounters.simulateExtracted = 0.0D;
        gtlcore$energyCounters.providerExtractRequest = 0.0D;
        gtlcore$energyCounters.providerExtracted = 0.0D;
        gtlcore$energyCounters.providerSimExtractRequest = 0.0D;
        gtlcore$energyCounters.providerSimExtracted = 0.0D;
        gtlcore$energyCounters.injectInput = 0.0D;
        gtlcore$energyCounters.injectRemainder = 0.0D;
        gtlcore$energyCounters.simulateInjectInput = 0.0D;
        gtlcore$energyCounters.simulateInjectRemainder = 0.0D;
        gtlcore$energyCounters.providerInjectInput = 0.0D;
        gtlcore$energyCounters.providerInjectRemainder = 0.0D;
        gtlcore$energyCounters.providerSimInjectInput = 0.0D;
        gtlcore$energyCounters.providerSimInjectRemainder = 0.0D;
    }
}
