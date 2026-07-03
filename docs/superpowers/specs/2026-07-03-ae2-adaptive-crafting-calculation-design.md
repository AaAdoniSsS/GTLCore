# AE2 Adaptive Crafting Calculation Design

## Goal

Improve AE2 crafting order calculation so large orders finish faster without causing server tick spikes, UI stalls, or long single-thread CPU bursts.

The design keeps GTLCore's existing fast crafting-tree algorithms and adds a cooperative, adaptive budget layer around them. Small jobs should still complete immediately. Large or complex jobs should make steady progress across ticks instead of running unbounded.

## Current Context

GTLCore targets AE2 15.4.10 on Minecraft 1.20.1. The relevant integration points are:

- `CraftingCalculationMixin`: redirects AE2 `CraftingTreeNode.request` to GTLCore `LEGACY`, `FAST`, or `ULTRA_FAST` request paths.
- `CraftingTreeNodeMixin` and `CraftingTreeProcessMixin`: implement the fast request strategies.
- `TickHandlerMixin`: currently disables AE2's original crafting simulation registration and tick-sliced simulation driver.
- `CraftingCpuLogicMixin`: optimizes execution after a plan has been submitted to a CPU.
- `ConfigHolder` and `AE2CalculationMode`: already provide user-facing AE2 calculation configuration.

The main risk is concentrated work during the calculation phase. GTLCore can calculate aggressively, but large plans need a budgeted pause/resume mechanism so they do not monopolize CPU time.

## Non-Goals

- Do not rewrite AE2's crafting planner from scratch.
- Do not replace GTLCore's existing `FAST` and `ULTRA_FAST` algorithms.
- Do not optimize submitted-job execution first; that path is already customized in `CraftingCpuLogicMixin`.
- Do not require users to tune JVM, OS, or external server settings to get safe behavior.

## Recommended Approach

Add an `ADAPTIVE` calculation mode that combines GTLCore's fast tree traversal with cooperative scheduling.

`ADAPTIVE` should:

- Use the existing `FAST` or `ULTRA_FAST` request implementation as its inner algorithm.
- Track elapsed calculation time and operation count at existing `handlePausing()` call sites.
- Let small jobs bypass pausing and complete in one pass.
- Pause large jobs when they exceed their current per-job budget.
- Resume paused jobs from `TickHandler` on later server ticks.
- Decrease the budget when many jobs are active or server tick time is high.
- Increase the budget when there are few jobs and tick time is healthy.
- Fall back to safer behavior on branch failure, cancellation, interruption, or suspected invalid plans.

## Architecture

### 1. Calculation Budget Controller

Add a small controller owned by each `CraftingCalculation` instance through mixin state.

Responsibilities:

- Store whether the job is done, running, paused, or canceled.
- Store the current budget in microseconds.
- Measure elapsed time with AE2's existing `Stopwatch` style.
- Decide whether `handlePausing()` should continue or yield.
- Support fast bypass for small jobs.

This should reuse AE2's existing monitor/wait-notify pattern where practical, because AE2 already uses it to coordinate calculation threads with server ticks.

### 2. Global Adaptive Scheduler

Restore the concept of registered crafting simulations, but under GTLCore control.

Responsibilities:

- Keep a per-level collection of active crafting calculations.
- On level end tick, distribute a total calculation budget across active jobs.
- Remove finished jobs.
- Never call into the planner unbounded from the server thread.

The scheduler should reuse `TickHandler` as the integration point because AE2 already expects crafting simulation to be driven there, and GTLCore already has a `TickHandlerMixin`.

### 3. Complexity and Small-Job Bypass

Avoid slowing down normal usage.

The initial version should use conservative signals that are already available:

- Requested amount.
- Whether the calculation has multiple paths.
- Number of pause checks reached.
- Elapsed time since the job began.

If a job completes before it reaches the small-job threshold, it should not pay the scheduling overhead.

### 4. Branch Failure and Fallback

`ULTRA_FAST` is intentionally aggressive. In `ADAPTIVE` mode:

- Try the configured fast strategy first.
- If multi-branch calculation fails or produces suspicious missing-output behavior, retry that calculation with `FAST`.
- If `FAST` also fails unexpectedly, fall back to `LEGACY` for correctness.
- Log the fallback at debug/info level with the requested key, amount, and mode transition.

This keeps the no-lag goal from turning into incorrect plans.

### 5. Short-Lived Calculation Cache

Add a narrow cache only after the scheduler is in place.

Cache key:

- Requested `AEKey`.
- Requested amount.
- Calculation strategy.
- Current calculation identity.
- Pattern or branch identity.

Cache value:

- Compact failed-branch marker.
- Reusable input-template lookup result for the current calculation.

Cache policy:

- Scope the cache to one calculation.
- Clear it when the calculation finishes.
- Do not share full `ICraftingPlan` objects between requests in the first implementation.
- Do not cache partial or simulated missing-only plans.

This avoids retrying known-bad branches while avoiding stale-plan bugs. A cross-request full-plan cache can be added later only if it has explicit grid, provider, and storage version invalidation.

## Configuration

Extend existing config instead of hardcoding behavior:

- Add `ADAPTIVE` to `AE2CalculationMode`.
- Add `ae2CraftingMinBudgetMicros`.
- Add `ae2CraftingMaxBudgetMicros`.
- Add `ae2CraftingSmallJobBypassChecks`.
- Add `ae2CraftingFallbackOnFailure`.

Suggested defaults:

- `ae2CalculationMode = ADAPTIVE`.
- `ae2CraftingMinBudgetMicros = 500`.
- `ae2CraftingMaxBudgetMicros = 5000`.
- `ae2CraftingSmallJobBypassChecks = 256`.
- Fallback enabled.

The maximum budget matches AE2's default 5 ms crafting calculation budget while allowing GTLCore to shrink work slices when the server is under load. The minimum budget keeps every active job moving without letting one job dominate a tick.

## Data Flow

1. User opens the craft confirm flow.
2. AE2 creates a `CraftingCalculation` through `CraftingService.beginCraftingCalculation`.
3. GTLCore registers the calculation with the adaptive scheduler.
4. The calculation starts on AE2's crafting pool.
5. The request path uses GTLCore `FAST` or `ULTRA_FAST` tree logic.
6. Existing `handlePausing()` call sites check the controller budget.
7. If the budget is still available, calculation continues.
8. If the budget is exhausted, the calculation yields.
9. `TickHandler` gives the job another budget slice on a later tick.
10. When the plan completes, `CraftConfirmMenu` receives the future result as it does today.
11. Submission and CPU execution continue through existing GTLCore CPU logic.

## Error Handling

- Cancellation should wake paused calculation threads and allow the future to finish promptly.
- Interrupted calculation should throw `InterruptedException` and clean itself out of the scheduler.
- Scheduler exceptions should not crash every active calculation; remove only the failing job where possible.
- Fallback retries should be limited to avoid repeated expensive retries.
- If all modes fail, surface AE2's normal failure behavior rather than creating a partial plan.

## Performance Expectations

Small orders:

- Same or near-same latency as current `ULTRA_FAST`.
- No extra server tick dependency unless the job crosses the bypass threshold.

Large orders:

- Lower maximum tick spike than current unbounded calculation.
- Faster completion than pure AE2 legacy slicing when the server has headroom.
- Stable behavior with multiple simultaneous players ordering crafts.

## Testing Plan

Build-level checks:

- `compileJava`.
- `spotlessCheck`.

Unit or harness-style checks where feasible:

- Controller pauses after budget exhaustion.
- Controller does not pause before small-job threshold.
- Finished jobs are removed from the scheduler.
- Cancellation wakes a paused job.
- Fallback transitions from `ULTRA_FAST` to `FAST` to `LEGACY` no more than once per calculation.

Manual or integration checks:

- Small single-item craft confirmation remains fast.
- Large recursive order does not create visible server tick spikes.
- Multiple simultaneous craft confirmations each make progress.
- Replanning the same request does not reuse stale full plans.
- A single calculation does not repeatedly retry the same failed branch.
- Submitted jobs still execute through existing CPU logic.

## Implementation Order

1. Add config entries and `ADAPTIVE` mode.
2. Add the calculation controller interface and mixin-backed state.
3. Restore registration and scheduler-driven resume without changing tree algorithms.
4. Wire `ADAPTIVE` mode to use fast tree logic with budget checks.
5. Add fallback behavior.
6. Add short-lived failed-branch cache.
7. Leave cross-request full-plan caching out of the first implementation.

## Final Design Decisions

- Inner strategy: use `ULTRA_FAST` for single-branch calculations and `FAST` for multi-branch calculations.
- Fallback: retry once with `FAST` if `ULTRA_FAST` fails unexpectedly, then retry once with `LEGACY` if `FAST` fails unexpectedly.
- First cache scope: failed-branch and input-template lookup cache within one calculation.
- Full-plan cache: excluded from the first implementation to prioritize correctness.
