// Opt-in planning-only benchmark. Run after other native-rig checks; never use in an actual save.
var stressPending = [];
var stressNext = null;
var stressDelay = 0;
ServerEvents.tick(event => {
    if (stressNext != null && --stressDelay <= 0) { var action = stressNext; stressNext = null; action(); }
    for (var i = stressPending.length - 1; i >= 0; i--) {
        var pending = stressPending[i];
        if (!pending.future.isDone()) continue;
        stressPending.splice(i, 1);
        try { pending.complete(pending.future.get()); }
        catch (error) { console.error('[Graph Stress] FAILED: ' + error); stressNext = null; }
    }
});
ServerEvents.commandRegistry(event => {
    var Pos = Java.loadClass('net.minecraft.core.BlockPos');
    var Key = Java.loadClass('appeng.api.stacks.AEItemKey');
    var Stack = Java.loadClass('appeng.api.stacks.GenericStack');
    var Pattern = Java.loadClass('appeng.api.crafting.PatternDetailsHelper');
    var Processing = Java.loadClass('appeng.crafting.pattern.AEProcessingPattern');
    var Action = Java.loadClass('appeng.api.config.Actionable');
    var Source = Java.loadClass('appeng.me.helpers.MachineSource');
    var Probe = Java.loadClass('org.gtlcore.test.GraphStressProbe');
    var cases = [['chain128', 128, 1], ['chain512', 512, 1], ['chain2048', 2048, 1], ['chain8192', 8192, 1],
                 ['shared8x8', 8, 8], ['shared16x8', 16, 8], ['shared24x8', 24, 8], ['shared64x8', 64, 8]];
    function later(action) { stressNext = action; stressDelay = 20; }
    function key(label, layer, branch) {
        return Key['of(net.minecraft.world.item.ItemStack)'](Item.of('minecraft:paper', {graph_stress: label, layer: layer, branch: branch}));
    }
    function register(name, amount) {
    event.register(event.commands.literal(name).requires(source => source.hasPermission(4)).executes(ctx => {
        if (stressPending.length !== 0 || stressNext != null) throw new Error('Benchmark already active');
        var level = ctx.source.level, index = 0;
        var cpu = level.getBlockEntity(new Pos(1, 65, 0));
        var grid = cpu.getMainNode().getNode().getGrid(), source = new Source(cpu);
        function nextCase() {
            if (index >= cases.length) { Probe.remove(); console.info('[Graph Stress] SUITE COMPLETE'); return; }
            var spec = cases[index++], label = spec[0], depth = spec[1], width = spec[2], patterns = [];
            var raw = key(label, 0, 0), target = key(label, depth, 0);
            function pattern(inputs, output) {
                patterns.push(new Processing(Key['of(net.minecraft.world.item.ItemStack)'](Pattern.encodeProcessingPattern(inputs, [output]))));
            }
            for (var layer = 1; layer <= depth; layer++) for (var branch = 0; branch < width; branch++) {
                if (width === 1) pattern([new Stack(key(label, layer-1, 0), 1)], new Stack(key(label, layer, 0), 1));
                else if (layer === 1) pattern([new Stack(raw, 2)], new Stack(key(label, layer, branch), 2));
                else pattern([new Stack(key(label, layer-1, branch), 1), new Stack(key(label, layer-1, (branch+1)%width), 1)],
                    new Stack(key(label, layer, branch), 2));
            }
            level.getBlockEntity(new Pos(0, 65, 1)).getInternalInventory().setItemDirect(0, Item.of('ae2:item_storage_cell_256k'));
            // Stock is mounted as an isolated long-count MEStorage by the probe.
            Probe.install(grid, patterns, new Stack(raw, amount + 4096));
            console.info('[Graph Stress] CASE label=' + label + ' depth=' + depth + ' width=' + width + ' registered_patterns=' + patterns.length + ' amount=' + amount);
            var sample = 0;
            function runSample() {
                var oldResult, graphResult;
                function old(done) { stressPending.push({future: Probe.baseline(grid, level, source, target, amount), complete: value => { oldResult = value; done(); }}); }
                function graph(done) { stressPending.push({future: Probe.graph(grid, level, source, target, amount), complete: value => { graphResult = value; done(); }}); }
                function finish() {
                    var valid = Probe.report(label, sample, amount, oldResult, graphResult);
                    if (!valid) { console.info('[Graph Stress] case stopped after failure or inequality: ' + label); later(nextCase); return; }
                    if (++sample < 8) later(runSample); else later(nextCase);
                }
                // Serial A/B, alternating order. Tick delay between samples is excluded from each timer.
                if (sample % 2 === 0) old(() => graph(finish)); else graph(() => old(finish));
            }
            later(runSample);
        }
        later(nextCase);
        return 1;
    }));
    }
    register('graphstress', 1048576);
    register('graphstress100m', 100000000);
    register('graphstress3b', 3000000000);
});
