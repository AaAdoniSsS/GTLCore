// Local fixture only. Runs in the isolated graph-validation-world, never shipped.
var graphProbePending = [];
ServerEvents.tick(event => {
    for (var i = graphProbePending.length - 1; i >= 0; i--) {
        var pending = graphProbePending[i];
        if (!pending.future.isDone()) continue;
        graphProbePending.splice(i, 1);
        try { pending.complete(pending.future.get()); }
        catch (error) { console.error('[Graph Probe] async FAILED: ' + error); }
    }
});
ServerEvents.commandRegistry(event => {
    var C = event.commands;
    function probe(name, action) {
        event.register(C.literal(name).requires(source => source.hasPermission(4)).executes(ctx => {
            try { action(ctx.source.level, ctx.source.server); return 1; }
            catch (error) { console.error('[Graph Probe] ' + name + ' FAILED: ' + error); throw error; }
        }));
    }
    var Pos = Java.loadClass('net.minecraft.core.BlockPos');
    var Key = Java.loadClass('appeng.api.stacks.AEItemKey');
    var Source = Java.loadClass('appeng.me.helpers.MachineSource');
    var Action = Java.loadClass('appeng.api.config.Actionable');
    var Pattern = Java.loadClass('appeng.api.crafting.PatternDetailsHelper');
    var Resource = Java.loadClass('net.minecraft.resources.ResourceLocation');
    var Strategy = Java.loadClass('appeng.api.networking.crafting.CalculationStrategy');
    var Seconds = Java.loadClass('java.util.concurrent.TimeUnit');
    var Counter = Java.loadClass('appeng.api.stacks.KeyCounter');
    var Summary = Java.loadClass('appeng.menu.me.crafting.CraftingPlanSummary');
    var Buffer = Java.loadClass('net.minecraft.network.FriendlyByteBuf');
    var PacketProbe = Java.loadClass('org.gtlcore.test.GraphPacketProbe');
    probe('graphisolation', () => Java.loadClass('org.gtlcore.test.LegacyCalls').checkZero());
    function key(id) { return Key['of(net.minecraft.world.item.ItemStack)'](Item.of(id)); }
    function block(level, x, z) { return level.getBlockEntity(new Pos(x, 65, z)); }
    function grid(level) { return block(level, 1, 0).getMainNode().getNode().getGrid(); }
    probe('graphcancelrequester', level => {
        var request = block(level, -1, 0).getRequests().get(0);
        request.updateState(false);
        var graph = block(level, 1, 0).getCluster().craftingLogic.gtlcore$graphController();
        graph.cancel();
        console.info('[Graph Probe] cancelled requester order while output was in flight');
    });
    probe('graphbenchmark', level => {
        var samples = 0;
        function next() {
            var future = PacketProbe.benchmark(grid(level), level, new Source(block(level, 1, 0)), key('minecraft:oak_planks'), 2000);
            graphProbePending.push({future: future, complete: result => {
                console.info(result); if (++samples < 10) next();
            }});
        }
        next();
    });
    var wildcardSelected;
    probe('graphwildcardplace', (level, server) => server.runCommandSilent('setblock 0 65 -1 gtceu:me_wildcard_pattern_buffer'));
    probe('graphwildcardload', level => {
        var machine = block(level, 0, -1).getMetaMachine();
        machine.setFrontFacing(Java.loadClass('net.minecraft.core.Direction').SOUTH);
        PacketProbe.installWildcard(machine, Item.of('wildcard_pattern:wildcard_pattern'), key('minecraft:coal'), 1);
        block(level, 0, 1).getInternalInventory().setItemDirect(0, Item.of('ae2:item_storage_cell_1k'));
        var patterns = machine.getAvailablePatterns();
        console.info('[Graph Probe] wildcard active=' + machine.getMainNode().isActive() + ' network_same=' + (machine.getMainNode().getGrid() === grid(level)));
        wildcardSelected = patterns.get(patterns.size() - 1);
        for (var input of wildcardSelected.getInputs()) {
            var stack = input.getPossibleInputs()[0];
            grid(level).getStorageService().getInventory().insert(stack.what(), stack.amount() * input.getMultiplier() * 3, Action.MODULATE, new Source(block(level, 1, 0)));
        }
    });
    probe('graphwildcardsubmit', level => {
        var cpu = block(level, 1, 0), network = grid(level), source = new Source(cpu);
        if (network.getCraftingService().getCraftingFor(wildcardSelected.getOutputs()[0].what()).isEmpty()) throw new Error('Wildcard is not registered on CPU grid');
        var future = network.getCraftingService().beginCraftingCalculation(level, () => source, wildcardSelected.getOutputs()[0].what(), 3, Strategy.REPORT_MISSING_ITEMS);
        graphProbePending.push({future: future, complete: plan => {
            var result = network.getCraftingService().submitJob(plan, null, cpu.getCluster(), false, source);
            if (!result.successful()) throw new Error('Wildcard submit: ' + result.errorCode());
        }});
    });
    probe('graphwildcardslots', level => PacketProbe.wildcardSlots(block(level, 0, -1).getMetaMachine(), wildcardSelected, 3));
    probe('graphwildcardedit', level => {
        block(level, 1, 0).getCluster().craftingLogic.gtlcore$graphController().cancel();
        var machine = block(level, 0, -1).getMetaMachine();
        PacketProbe.installWildcard(machine, Item.of('wildcard_pattern:wildcard_pattern'), key('minecraft:coal'), 2);
        var replacements = machine.getAvailablePatterns();
        wildcardSelected = replacements.get(replacements.size() - 1);
        PacketProbe.wildcardSlots(machine, wildcardSelected, 0);
        var stock = new Counter(); grid(level).getStorageService().getInventory().getAvailableStacks(stock);
        for (var input of wildcardSelected.getInputs()) {
            var stack = input.getPossibleInputs()[0];
            if (stock.get(stack.what()) !== stack.amount() * input.getMultiplier() * 3) throw new Error('Wildcard refund mismatch');
        }
        console.info('[Graph Probe] PASS: wildcard replacement refunded real slot inputs exactly once');
    });
    probe('graphwildcardregistry', level => {
        var registered = grid(level).getCraftingService().getCraftingFor(wildcardSelected.getOutputs()[0].what());
        for (var pattern of registered) if (pattern.getOutputs()[0].amount() !== 2) throw new Error('Stale wildcard provider binding');
        if (registered.isEmpty()) throw new Error('Wildcard update missing');
        console.info('[Graph Probe] PASS: updated expanded patterns registered with current output quantity');
    });
    var PartHelper = Java.loadClass('appeng.api.parts.PartHelper');
    var Direction = Java.loadClass('net.minecraft.core.Direction');
    var P2P = Java.loadClass('appeng.me.service.P2PService');
    probe('graphp2pplace', (level, server) => {
        server.runCommandSilent('setblock 3 65 0 minecraft:air');
        var cable = Item.of('ae2:fluix_glass_cable').getItem();
        var item = Item.of('mae2:pattern_p2p_tunnel').getItem();
        for (var x = 3; x <= 7; x++) PartHelper.setPart(level, new Pos(x, 65, 0), null, null, cable);
        PartHelper.setPart(level, new Pos(3, 65, 0), Direction.WEST, null, item);
        PartHelper.setPart(level, new Pos(7, 65, 0), Direction.EAST, null, item);
        server.runCommandSilent('setblock 8 65 0 minecraft:chest');
    });
    probe('graphp2pload', level => {
        var input = PartHelper.getPart(level, new Pos(3, 65, 0), Direction.WEST);
        var output = PartHelper.getPart(level, new Pos(7, 65, 0), Direction.EAST);
        PacketProbe.tunnelOutput(output, true);
        P2P.get(input.getMainNode().getGrid()).updateFreq(input, 77);
        P2P.get(output.getMainNode().getGrid()).updateFreq(output, 77);
        block(level, 0, 1).getInternalInventory().setItemDirect(0, Item.of('ae2:item_storage_cell_1k'));
        var patterns = block(level, 2, 0).getLogic().getPatternInv();
        for (var i = 0; i < patterns.size(); i++) patterns.setItemDirect(i, Item.of('minecraft:air'));
        patterns.setItemDirect(0, processing('minecraft:stone', 'minecraft:iron_ingot'));
        grid(level).getStorageService().getInventory().insert(key('minecraft:stone'), 3, Action.MODULATE, new Source(block(level, 1, 0)));
    });
    probe('graphp2psubmit', level => {
        var cpu = block(level, 1, 0), network = grid(level), source = new Source(cpu);
        var tunnel = PartHelper.getPart(level, new Pos(3, 65, 0), Direction.WEST);
        if (tunnel.getTargets().size() !== 1) throw new Error('P2P route missing');
        var future = network.getCraftingService().beginCraftingCalculation(level, () => source, key('minecraft:iron_ingot'), 3, Strategy.REPORT_MISSING_ITEMS);
        graphProbePending.push({future: future, complete: plan => {
            var result = network.getCraftingService().submitJob(plan, null, cpu.getCluster(), false, source);
            if (!result.successful()) throw new Error('P2P submit: ' + result.errorCode());
        }});
    });
    probe('graphp2pfinish', level => {
        var chest = block(level, 8, 0);
        if (chest.getItem(0).id !== 'minecraft:stone' || chest.getItem(0).count !== 3) throw new Error('P2P physical input mismatch');
        chest.setItem(0, Item.of('minecraft:air'));
        grid(level).getStorageService().getInventory().insert(key('minecraft:iron_ingot'), 3, Action.MODULATE, new Source(block(level, 1, 0)));
        console.info('[Graph Probe] PASS: real MAE2 pattern P2P routed all 3 inputs to remote chest, returned 3 outputs');
    });
    probe('graphsetup', (level, server) => {
        server.runCommandSilent('setblock 0 65 1 ae2:drive[facing=south]');
        server.runCommandSilent('setblock 3 65 0 ae2:molecular_assembler');
        console.info('[Graph Probe] Setup blocks placed');
    });
    probe('graphload', level => {
        block(level, 0, 1).getInternalInventory().setItemDirect(0, Item.of('ae2:item_storage_cell_1k'));
        var recipe = level.getRecipeManager().byKey(new Resource('minecraft:oak_planks')).orElseThrow();
        var inputs = [];
        for (var i = 0; i < 9; i++) inputs.push(Item.of('minecraft:air'));
        inputs[0] = Item.of('minecraft:oak_log');
        var encoded = Pattern.encodeCraftingPattern(recipe, inputs, Item.of('minecraft:oak_planks', 4), false, false);
        block(level, 2, 0).getLogic().getPatternInv().setItemDirect(0, encoded);
        console.info('[Graph Probe] Real crafting pattern and storage cell installed');
    });
    probe('graphsubmit', level => {
        var cpu = block(level, 1, 0);
        var network = grid(level);
        var source = new Source(cpu);
        var added = network.getStorageService().getInventory().insert(key('minecraft:oak_log'), 4, Action.MODULATE, source);
        if (added !== 4) throw new Error('Initial insertion=' + added);
        var future = network.getCraftingService().beginCraftingCalculation(level, () => source,
            key('minecraft:oak_planks'), 16, Strategy.REPORT_MISSING_ITEMS);
        graphProbePending.push({future: future, complete: plan => {
        console.info('[Graph Probe] plan=' + plan.getClass().getName() + ' bytes=' + plan.bytes() + ' simulation=' + plan.simulation());
        summary(network, source, plan);
        var submitted = network.getCraftingService().submitJob(plan, null, cpu.getCluster(), false, source);
        if (!submitted.successful()) throw new Error('Submit error=' + submitted.errorCode() + ': ' + submitted.errorDetail());
        console.info('[Graph Probe] graph_owner=' + cpu.getCluster().craftingLogic.gtlcore$graphController().ownsTask());
        }});
    });
    probe('graphstatus', level => {
        var inventory = new Counter();
        grid(level).getStorageService().getInventory().getAvailableStacks(inventory);
        var logs = inventory.get(key('minecraft:oak_log'));
        var planks = inventory.get(key('minecraft:oak_planks'));
        var graph = block(level, 1, 0).getCluster().craftingLogic.gtlcore$graphController();
        console.info('[Graph Probe] graph_owner=' + graph.ownsTask() + ' logs=' + logs + ' planks=' + planks);
        if (graph.ownsTask() || logs !== 0 || planks !== 16) throw new Error('Order has not completed with the expected material balance');
        console.info('[Graph Probe] PASS: real native CPU + registered pattern + molecular assembler + storage delivery');
    });
    function summary(network, source, plan) {
        PacketProbe.ring(plan);
        var view = Summary.fromJob(network, source, plan);
        PacketProbe.roundTrip(view);
        console.info('[Graph Probe] PASS: summary, entries=' + view.getEntries().size() + ' simulation=' + view.isSimulation());
    }
    probe('graphsummary', level => {
        var network = grid(level);
        var source = new Source(block(level, 1, 0));
        var future = network.getCraftingService().beginCraftingCalculation(level, () => source,
            key('minecraft:oak_planks'), 400, Strategy.REPORT_MISSING_ITEMS);
        graphProbePending.push({future: future, complete: plan => {
        console.info('[Graph Probe] summary plan=' + plan.getClass().getName() + ' simulation=' + plan.simulation());
        summary(network, source, plan);
        }});
    });
    probe('graphtrees', () => PacketProbe.trees(key('minecraft:oak_planks'), key('minecraft:oak_log'), key('minecraft:stick'), key('minecraft:iron_ingot')));
    probe('graphwildcards', level => PacketProbe.wildcards(level, Item.of('wildcard_pattern:wildcard_pattern'), key('minecraft:coal'), key('minecraft:diamond')));
    probe('graphcatalog', level => PacketProbe.catalog(grid(level), level, new Source(block(level, 1, 0)), block(level, 2, 0).getMainNode().getNode(), key('minecraft:oak_planks')));
    var Stack = Java.loadClass('appeng.api.stacks.GenericStack');
    function processing(from, to) {
        return Pattern.encodeProcessingPattern([new Stack(key(from), 1)], [new Stack(key(to), 1)]);
    }
    probe('graphreplanload', (level, server) => {
        server.runCommandSilent('setblock 3 65 0 minecraft:chest');
        block(level, 0, 1).getInternalInventory().setItemDirect(0, Item.of('ae2:item_storage_cell_1k'));
        var patterns = block(level, 2, 0).getLogic().getPatternInv();
        for (var i = 0; i < patterns.size(); i++) patterns.setItemDirect(i, Item.of('minecraft:air'));
        patterns.setItemDirect(0, processing('minecraft:stone', 'minecraft:iron_ingot'));
        patterns.setItemDirect(1, processing('minecraft:iron_ingot', 'minecraft:gold_ingot'));
        var network = grid(level), cpu = block(level, 1, 0), source = new Source(cpu);
        network.getStorageService().getInventory().insert(key('minecraft:stone'), 1, Action.MODULATE, source);
        var future = network.getCraftingService().beginCraftingCalculation(level, () => source,
            key('minecraft:gold_ingot'), 1, Strategy.REPORT_MISSING_ITEMS);
        graphProbePending.push({future: future, complete: plan => {
            var result = network.getCraftingService().submitJob(plan, null, cpu.getCluster(), false, source);
            if (!result.successful()) throw new Error('Replan fixture submit: ' + result.errorCode());
            console.info('[Graph Probe] real processing order started; wait for stone in chest before editing suffix');
        }});
    });
    probe('graphreplanedit', level => {
        var graph = block(level, 1, 0).getCluster().craftingLogic.gtlcore$graphController();
        if (graph.waiting(key('minecraft:iron_ingot')) !== 1) throw new Error('r1 not accepted');
        var patterns = block(level, 2, 0).getLogic().getPatternInv();
        patterns.setItemDirect(1, processing('minecraft:iron_ingot', 'minecraft:diamond'));
        patterns.setItemDirect(2, processing('minecraft:diamond', 'minecraft:gold_ingot'));
        console.info('[Graph Probe] changed only the uncommitted processing suffix while iron is in flight');
    });
    function returnOutput(level, from, to) {
        var chest = block(level, 3, 0);
        if (chest.getItem(0).id !== from) throw new Error('Expected physical input ' + from + ' got ' + chest.getItem(0));
        chest.setItem(0, Item.of('minecraft:air'));
        var inserted = grid(level).getStorageService().getInventory().insert(key(to), 1, Action.MODULATE, new Source(block(level, 1, 0)));
        if (inserted !== 1) throw new Error('Return rejected');
        console.info('[Graph Probe] physically consumed ' + from + '; returned ' + to);
    }
    probe('graphreturniron', level => returnOutput(level, 'minecraft:stone', 'minecraft:iron_ingot'));
    probe('graphreturndiamond', level => returnOutput(level, 'minecraft:iron_ingot', 'minecraft:diamond'));
    probe('graphreturngold', level => returnOutput(level, 'minecraft:diamond', 'minecraft:gold_ingot'));
    probe('graphreplanstatus', level => {
        var graph = block(level, 1, 0).getCluster().craftingLogic.gtlcore$graphController();
        var inventory = new Counter(); grid(level).getStorageService().getInventory().getAvailableStacks(inventory);
        if (graph.ownsTask() || inventory.get(key('minecraft:gold_ingot')) !== 1 || !block(level, 3, 0).getItem(0).isEmpty())
            throw new Error('Replanned order not settled');
        console.info('[Graph Probe] PASS: real CPU/provider suffix replan, old in-flight output retained, 3 physical pushes, exact final delivery');
    });
    probe('graphrequesterplace', (level, server) => {
        server.runCommandSilent('setblock -1 65 0 merequester:requester');
        server.runCommandSilent('setblock 3 65 0 ae2:molecular_assembler');
    });
    probe('graphrequesterstart', level => {
        var requester = block(level, -1, 0);
        var network = grid(level), source = new Source(block(level, 1, 0));
        network.getStorageService().getInventory().insert(key('minecraft:oak_log'), 4, Action.MODULATE, source);
        requester.getRequests().setStack(0, new Stack(key('minecraft:oak_planks'), 16));
        var request = requester.getRequests().get(0);
        request.updateAmount(16); request.updateBatch(16); request.updateState(true);
        console.info('[Graph Probe] real ME Requester enabled: 16 planks, batch=16');
    });
    probe('graphrequesterstatus', level => {
        var requester = block(level, -1, 0);
        var inventory = new Counter(); grid(level).getStorageService().getInventory().getAvailableStacks(inventory);
        console.info('[Graph Probe] requester links=' + requester.getRequestedJobs().size() + ' planks=' + inventory.get(key('minecraft:oak_planks')));
        if (block(level, 1, 0).getCluster().craftingLogic.gtlcore$graphController().ownsTask() || inventory.get(key('minecraft:oak_planks')) !== 16)
            throw new Error('Requester failed delivery');
        requester.getRequests().get(0).updateState(false);
        console.info('[Graph Probe] PASS: actual ME Requester request/submit/link/export through graph native CPU');
    });
    probe('graphrequesterprocessload', (level, server) => {
        var requester = block(level, -1, 0);
        requester.getRequests().get(0).updateState(false);
        server.runCommandSilent('setblock 3 65 0 minecraft:chest');
        block(level, 0, 1).getInternalInventory().setItemDirect(0, Item.of('ae2:item_storage_cell_1k'));
        var patterns = block(level, 2, 0).getLogic().getPatternInv();
        for (var i = 0; i < patterns.size(); i++) patterns.setItemDirect(i, Item.of('minecraft:air'));
        patterns.setItemDirect(0, processing('minecraft:stone', 'minecraft:iron_ingot'));
        grid(level).getStorageService().getInventory().insert(key('minecraft:stone'), 1, Action.MODULATE, new Source(block(level, 1, 0)));
        requester.getRequests().setStack(0, new Stack(key('minecraft:iron_ingot'), 1));
        requester.getRequests().get(0).updateBatch(1);
        requester.getRequests().get(0).updateState(true);
        console.info('[Graph Probe] requester processing fixture ready');
    });
    probe('graphrequesterinflight', level => {
        var graph = block(level, 1, 0).getCluster().craftingLogic.gtlcore$graphController();
        var requester = block(level, -1, 0);
        if (!graph.ownsTask() || graph.waiting(key('minecraft:iron_ingot')) !== 1 || requester.getRequestedJobs().size() !== 1)
            throw new Error('Requester in-flight account/link missing');
        console.info('[Graph Probe] requester in-flight link=' + graph.link().getCraftingID() + ' expected_iron=1 requester_links=1');
    });
    probe('graphrequesterrestorestatus', level => {
        var graph = block(level, 1, 0).getCluster().craftingLogic.gtlcore$graphController();
        var inventory = new Counter(); grid(level).getStorageService().getInventory().getAvailableStacks(inventory);
        var requester = block(level, -1, 0);
        if (graph.ownsTask() || requester.getRequestedJobs().size() !== 0 || inventory.get(key('minecraft:iron_ingot')) !== 1)
            throw new Error('Requester restored job not settled exactly once');
        requester.getRequests().get(0).updateState(false);
        console.info('[Graph Probe] PASS: real requester/native CPU restored in-flight link, exact single return/delivery/export');
    });
    probe('graphnetwork', level => {
        for (var x = 0; x <= 3; x++) {
            var be = block(level, x, 0);
            var node = be.getMainNode().getNode();
            console.info('[Graph Probe] x=' + x + ' block=' + be.getBlockState() + ' active=' + node.isActive() + ' grid=' + node.getGrid());
        }
        var be = block(level, 0, 1);
        var node = be.getMainNode().getNode();
        console.info('[Graph Probe] drive=' + be.getBlockState() + ' active=' + node.isActive() + ' grid=' + node.getGrid());
    });
});
