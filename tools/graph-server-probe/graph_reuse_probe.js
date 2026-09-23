// Isolated world only: real crafting patterns, two orders on the same CPU, and cancellation.
var reusePending = [];
var reuseCancelAt = 0;
var reuseExpandAt = 0;
var reuseExpandY = 66;
var reuseLevel;
ServerEvents.recipes(event => {
    event.shaped('2x minecraft:netherite_upgrade_smithing_template', ['DTD', 'DND', 'DDD'], {
        D: 'minecraft:diamond', T: 'minecraft:netherite_upgrade_smithing_template', N: 'minecraft:netherrack'
    }).id('gtlgraphprobe:template_duplication');
    event.shapeless('9x minecraft:diamond', ['minecraft:diamond_block']).id('gtlgraphprobe:diamond_unpack');
});
ServerEvents.tick(event => {
    if ((reuseCancelAt > 0 || reuseExpandAt > 0) && reuseLevel != null) {
        var Pos = Java.loadClass('net.minecraft.core.BlockPos');
        var graph = reuseLevel.getBlockEntity(new Pos(1, 65, 0)).getCluster().craftingLogic.gtlcore$graphController();
        var field = graph.getClass().getDeclaredField('runtime'); field.setAccessible(true);
        var runtime = field.get(graph);
        if (runtime != null && reuseExpandAt > 0 && runtime.dispatches() >= reuseExpandAt) {
            console.info('[Graph Reuse] append co-processor after dispatches=' + runtime.dispatches());
            event.server.runCommandSilent('setblock 1 ' + reuseExpandY + ' 0 ae2:crafting_accelerator');
            reuseExpandY++; reuseExpandAt = 0;
        } else if (runtime != null && reuseCancelAt > 0 && runtime.dispatches() >= reuseCancelAt) {
            console.info('[Graph Reuse] cancel after dispatches=' + runtime.dispatches());
            graph.cancel(); reuseCancelAt = 0;
        }
    }
    for (var i = reusePending.length - 1; i >= 0; i--) {
        var pending = reusePending[i];
        if (!pending.future.isDone()) continue;
        reusePending.splice(i, 1);
        try { pending.complete(pending.future.get()); }
        catch (error) { console.error('[Graph Reuse] FAILED: ' + error); }
    }
});
ServerEvents.commandRegistry(event => {
    var Pos = Java.loadClass('net.minecraft.core.BlockPos');
    var Key = Java.loadClass('appeng.api.stacks.AEItemKey');
    var Source = Java.loadClass('appeng.me.helpers.MachineSource');
    var Pattern = Java.loadClass('appeng.api.crafting.PatternDetailsHelper');
    var Action = Java.loadClass('appeng.api.config.Actionable');
    var Strategy = Java.loadClass('appeng.api.networking.crafting.CalculationStrategy');
    var Resource = Java.loadClass('net.minecraft.resources.ResourceLocation');
    var Probe = Java.loadClass('org.gtlcore.test.GraphPacketProbe');
    function key(id) { return Key['of(net.minecraft.world.item.ItemStack)'](Item.of(id)); }
    function block(level, x, z) { return level.getBlockEntity(new Pos(x, 65, z)); }
    function grid(level) { return block(level, 1, 0).getMainNode().getNode().getGrid(); }
    function command(name, action) {
        event.register(event.commands.literal(name).requires(source => source.hasPermission(4)).executes(ctx => {
            try { action(ctx.source.level, ctx.source.server); return 1; }
            catch (error) { console.error('[Graph Reuse] ' + name + ' FAILED: ' + error); throw error; }
        }));
    }
    command('graphreuseload', (level, server) => {
        server.runCommandSilent('setblock 0 65 -1 minecraft:air');
        server.runCommandSilent('setblock 3 65 0 ae2:molecular_assembler');
        var patterns = block(level, 2, 0).getLogic().getPatternInv();
        for (var i = 0; i < patterns.size(); i++) patterns.setItemDirect(i, Item.of('minecraft:air'));
        function recipe(id) { return level.getRecipeManager().byKey(new Resource(id)).orElseThrow(); }
        var inputs = ['diamond', 'netherite_upgrade_smithing_template', 'diamond', 'diamond', 'netherrack', 'diamond', 'diamond', 'diamond', 'diamond'].map(id => Item.of('minecraft:' + id));
        patterns.setItemDirect(0, Pattern.encodeCraftingPattern(recipe('gtlgraphprobe:template_duplication'), inputs,
            Item.of('minecraft:netherite_upgrade_smithing_template', 2), false, false));
        var diamond = [Item.of('minecraft:diamond_block')];
        for (var i = 1; i < 9; i++) diamond.push(Item.of('minecraft:air'));
        patterns.setItemDirect(1, Pattern.encodeCraftingPattern(recipe('gtlgraphprobe:diamond_unpack'), diamond,
            Item.of('minecraft:diamond', 9), false, false));
        block(level, 0, 1).getInternalInventory().setItemDirect(0, Item.of('ae2:item_storage_cell_4k'));
        var storage = grid(level).getStorageService().getInventory(), source = new Source(block(level, 1, 0));
        for (var entry of [['diamond_block', 84], ['netherrack', 200], ['netherite_upgrade_smithing_template', 1]])
            if (storage.insert(key('minecraft:' + entry[0]), entry[1], Action.MODULATE, source) !== entry[1]) throw new Error('Fixture stock insert');
        console.info('[Graph Reuse] actual template self-copy + diamond unpack patterns installed; 84 blocks, 200 netherrack, 1 seed');
    });
    function submit(level, count, cancelAt, expandAt, rejectBinding) {
        var cpu = block(level, 1, 0), network = grid(level), source = new Source(cpu);
        var future = network.getCraftingService().beginCraftingCalculation(level, () => source,
            key('minecraft:netherite_upgrade_smithing_template'), count, Strategy.REPORT_MISSING_ITEMS);
        reusePending.push({future: future, complete: plan => {
            Probe.inspectBindings(network, level, plan);
            if (rejectBinding) {
                Probe.rejectStaleBinding(network, level, source, cpu.getCluster(), plan);
                return;
            }
            var result = network.getCraftingService().submitJob(plan, null, cpu.getCluster(), false, source);
            console.info('[Graph Reuse] submit amount=' + count + ' simulation=' + plan.simulation() + ' result=' + result.errorCode());
            if (!result.successful()) throw new Error('Submit failed: ' + result.errorCode());
            reuseLevel = level; reuseCancelAt = cancelAt; reuseExpandAt = expandAt || 0;
        }});
    }
    command('graphreusefirst', level => submit(level, 100, 86));
    command('graphreusesecond', level => submit(level, 100, 0));
    command('graphreuseone', level => submit(level, 1, 0));
    command('graphreuseexpand', level => {
        reuseExpandY = 66;
        while (!level.getBlockState(new Pos(1, reuseExpandY, 0)).isAir()) reuseExpandY++;
        submit(level, 100, 0, 12);
    });
    command('graphreusestale', level => submit(level, 1, 0, 0, true));
    command('graphreuserefill', level => {
        var storage = grid(level).getStorageService().getInventory(), source = new Source(block(level, 1, 0));
        for (var entry of [['diamond_block', 1000], ['diamond', 14], ['netherrack', 3000], ['netherite_upgrade_smithing_template', 1]])
            if (storage.insert(key('minecraft:' + entry[0]), entry[1], Action.MODULATE, source) !== entry[1]) throw new Error('Refill insert');
        console.info('[Graph Reuse] stock refilled; same patterns, catalog and CPU retained');
    });
    command('graphreuserecover', level => {
        var Box = Java.loadClass('net.minecraft.world.phys.AABB');
        var Drop = Java.loadClass('net.minecraft.world.entity.item.ItemEntity');
        var storage = grid(level).getStorageService().getInventory(), source = new Source(block(level, 1, 0)), recovered = 0;
        // The test rig may float above a default flat world's y=-60 ground.
        for (var entity of level.getEntitiesOfClass(Drop, new Box(-32, level.getMinBuildHeight(), -32, 32, 75, 32))) {
            var stack = entity.getItem(), input = Key['of(net.minecraft.world.item.ItemStack)'](stack);
            var accepted = storage.insert(input, stack.count, Action.MODULATE, source);
            stack.shrink(accepted); recovered += accepted;
            if (stack.isEmpty()) entity.discard();
        }
        console.info('[Graph Reuse] CPU rebuild drops returned to ME=' + recovered);
    });
    command('graphreusestatus', level => {
        var stock = new (Java.loadClass('appeng.api.stacks.KeyCounter'))();
        grid(level).getStorageService().getInventory().getAvailableStacks(stock);
        var graph = block(level, 1, 0).getCluster().craftingLogic.gtlcore$graphController();
        var field = graph.getClass().getDeclaredField('runtime'); field.setAccessible(true);
        var runtime = field.get(graph);
        console.info('[Graph Reuse] busy=' + block(level, 1, 0).getCluster().isBusy() +
            ' diamonds=' + stock.get(key('minecraft:diamond')) + ' blocks=' + stock.get(key('minecraft:diamond_block')) +
            ' netherrack=' + stock.get(key('minecraft:netherrack')) + ' templates=' + stock.get(key('minecraft:netherite_upgrade_smithing_template')) +
            ' runtime=' + (runtime == null ? 'none' : runtime.state() + ' ' + runtime.reason() + ' dispatches=' + runtime.dispatches()));
        if (runtime == null) console.info('[Graph Reuse] conserved_diamond_units=' +
            (stock.get(key('minecraft:diamond')) + 9 * stock.get(key('minecraft:diamond_block')) + 7 * stock.get(key('minecraft:netherite_upgrade_smithing_template'))) +
            ' conserved_netherrack_units=' + (stock.get(key('minecraft:netherrack')) + stock.get(key('minecraft:netherite_upgrade_smithing_template'))));
    });
});
