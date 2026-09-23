// Isolated test world only; builds the actual registered preview shape.
var arrayProbePending = [];
ServerEvents.tick(event => {
    for (var i = arrayProbePending.length - 1; i >= 0; i--) {
        var request = arrayProbePending[i];
        if (!request.future.isDone()) continue;
        arrayProbePending.splice(i, 1);
        try { request.complete(request.future.get()); }
        catch (error) { console.error('[Graph Array] FAILED: ' + error); }
    }
});
ServerEvents.commandRegistry(event => {
    var Pos = Java.loadClass('net.minecraft.core.BlockPos');
    var Direction = Java.loadClass('net.minecraft.core.Direction');
    var Definitions = Java.loadClass('org.gtlcore.gtlcore.common.data.machines.AdditionalMultiBlockMachine');
    var Source = Java.loadClass('appeng.me.helpers.MachineSource');
    var Action = Java.loadClass('appeng.api.config.Actionable');
    var Pattern = Java.loadClass('appeng.api.crafting.PatternDetailsHelper');
    var Key = Java.loadClass('appeng.api.stacks.AEItemKey');
    var Stack = Java.loadClass('appeng.api.stacks.GenericStack');
    var Strategy = Java.loadClass('appeng.api.networking.crafting.CalculationStrategy');
    var Resource = Java.loadClass('net.minecraft.resources.ResourceLocation');
    var controllerPos, interfacePos, powerPos, providerPos, chestPos, drivePos;
    function key(id) { return Key['of(net.minecraft.world.item.ItemStack)'](Item.of(id)); }
    function be(level, pos) { return level.getBlockEntity(pos); }
    function probe(name, action) {
        event.register(event.commands.literal(name).requires(source => source.hasPermission(4)).executes(ctx => {
            try { action(ctx.source.level, ctx.source.server); return 1; }
            catch (error) { console.error('[Graph Array] ' + name + ' FAILED: ' + error); throw error; }
        }));
    }
    function locate(level) {
        for (var x = 100; x < 122; x++) for (var y = 65; y < 88; y++) for (var z = 0; z < 22; z++) {
            var pos = new Pos(x, y, z), id = String(level.getBlock(pos).id);
            if (id === 'gtceu:transfinite_computation_array') controllerPos = pos;
            if (id === 'gtceu:me_crafting_cpu_interface') interfacePos = pos;
        }
        if (controllerPos == null || interfacePos == null) throw new Error('Array controller/interface absent: controller=' + controllerPos + ' interface=' + interfacePos);
        // Registered preview faces NORTH; keep the external rig outside the pattern.
        powerPos = new Pos(120, interfacePos.y, interfacePos.z);
        providerPos = powerPos.offset(1, 0, 0);
        chestPos = powerPos.offset(2, 0, 0);
        drivePos = powerPos.offset(0, 0, 1);
    }
    probe('grapharrayplace', (level, server) => {
        server.runCommandSilent('forceload add 96 -16 144 48');
        var blocks = Definitions.TRANSFINITE_COMPUTATION_ARRAY.getMatchingShapes().get(0).getBlocks();
        var count = 0;
        for (var x = 0; x < blocks.length; x++) for (var y = 0; y < blocks[x].length; y++) for (var z = 0; z < blocks[x][y].length; z++) {
            var info = blocks[x][y][z];
            if (info == null) continue;
            level.setBlockAndUpdate(new Pos(100 + x, 65 + y, z), info.getBlockState()); count++;
        }
        console.info('[Graph Array] placed blocks=' + count + ' dimensions=' + blocks.length + '/' + blocks[0].length + '/' + blocks[0][0].length);
        locate(level);
        console.info('[Graph Array] controller=' + controllerPos + ' interface=' + interfacePos);
    });
    probe('grapharraywire', (level, server) => {
        locate(level);
        var PartHelper = Java.loadClass('appeng.api.parts.PartHelper');
        for (var x = interfacePos.x; x <= 120; x++) PartHelper.setPart(level, new Pos(x, interfacePos.y, -1), null, null, Item.of('ae2:fluix_glass_cable').getItem());
        PartHelper.setPart(level, new Pos(120, interfacePos.y, 0), null, null, Item.of('ae2:fluix_glass_cable').getItem());
        function place(pos, id) { server.runCommandSilent('setblock ' + pos.x + ' ' + pos.y + ' ' + pos.z + ' ' + id); }
        place(interfacePos.offset(0, 0, -1), 'ae2:creative_energy_cell');
        place(powerPos, 'ae2:creative_energy_cell');
        place(providerPos, 'expatternprovider:ex_pattern_provider');
        place(chestPos, 'ae2:molecular_assembler');
        place(drivePos, 'ae2:drive[facing=south]');
        console.info('[Graph Array] external ExtendedAE provider/assembler/drive connected at ' + powerPos);
    });
    probe('grapharraystatus', level => {
        locate(level);
        var host = be(level, controllerPos).getMetaMachine();
        console.info('[Graph Array] formed=' + host.isFormed() + ' operational=' + host.isOperational() + ' grid=' + host.getGrid());
        if (!host.isOperational()) throw new Error('Real array not operational');
    });
    probe('grapharrayload', level => {
        locate(level);
        var recipe = level.getRecipeManager().byKey(new Resource('minecraft:oak_planks')).orElseThrow();
        var inputs = []; for (var i = 0; i < 9; i++) inputs.push(Item.of('minecraft:air'));
        inputs[0] = Item.of('minecraft:oak_log');
        be(level, providerPos).getLogic().getPatternInv().setItemDirect(0, Pattern.encodeCraftingPattern(recipe, inputs, Item.of('minecraft:oak_planks', 4), false, false));
        be(level, drivePos).getInternalInventory().setItemDirect(0, Item.of('ae2:item_storage_cell_1k'));
        var host = be(level, controllerPos).getMetaMachine();
        host.getGrid().getStorageService().getInventory().insert(key('minecraft:oak_log'), 4, Action.MODULATE, host.getActionSource());
    });
    probe('grapharraysubmit', level => {
        locate(level);
        var host = be(level, controllerPos).getMetaMachine(), network = host.getGrid(), source = host.getActionSource();
        var future = network.getCraftingService().beginCraftingCalculation(level, () => source, key('minecraft:oak_planks'), 16, Strategy.REPORT_MISSING_ITEMS);
        arrayProbePending.push({future: future, complete: plan => {
            var result = network.getCraftingService().submitJob(plan, null, host.getCapacityCpu(), false, source);
            if (!result.successful()) throw new Error('Real array submit: ' + result.errorCode());
            console.info('[Graph Array] selected capacity CPU accepted graph order');
        }});
    });
    probe('grapharraydone', level => {
        locate(level);
        var host = be(level, controllerPos).getMetaMachine(), inventory = new (Java.loadClass('appeng.api.stacks.KeyCounter'))();
        host.getGrid().getStorageService().getInventory().getAvailableStacks(inventory);
        var active = 0; host.forEachActiveCpu(cpu => { if (cpu.isBusy()) active++; });
        if (active !== 0 || inventory.get(key('minecraft:oak_planks')) !== 16 || inventory.get(key('minecraft:oak_log')) !== 0) throw new Error('Array accounting mismatch');
        Java.loadClass('org.gtlcore.test.LegacyCalls').checkZero();
        console.info('[Graph Array] PASS: formed transfinite array / ExtendedAE provider / molecular assembler, exact native delivery, old core calls zero');
    });
    probe('grapharraycycleload', (level, server) => {
        locate(level);
        server.runCommandSilent('setblock ' + chestPos.x + ' ' + chestPos.y + ' ' + chestPos.z + ' minecraft:chest');
        server.runCommandSilent('setblock 119 ' + powerPos.y + ' 1 merequester:requester');
        be(level, drivePos).getInternalInventory().setItemDirect(0, Item.of('ae2:item_storage_cell_1k'));
        var patterns = be(level, providerPos).getLogic().getPatternInv();
        for (var i = 0; i < patterns.size(); i++) patterns.setItemDirect(i, Item.of('minecraft:air'));
        function encode(inputs, outputs) {
            return Pattern.encodeProcessingPattern(inputs.map(id => new Stack(key(id), 1)), outputs.map(id => new Stack(key(id), 1)));
        }
        patterns.setItemDirect(0, encode(['minecraft:stone', 'minecraft:coal'], ['minecraft:iron_ingot']));
        patterns.setItemDirect(1, encode(['minecraft:iron_ingot', 'minecraft:oak_log'], ['minecraft:gold_ingot']));
        patterns.setItemDirect(2, encode(['minecraft:gold_ingot', 'minecraft:diamond'], ['minecraft:emerald', 'minecraft:stone']));
    });
    probe('grapharraycyclestart', level => {
        locate(level);
        var host = be(level, controllerPos).getMetaMachine(), storage = host.getGrid().getStorageService().getInventory();
        for (var id of ['minecraft:coal', 'minecraft:oak_log', 'minecraft:diamond'])
            if (storage.insert(key(id), 3, Action.MODULATE, host.getActionSource()) !== 3) throw new Error('Fuel insertion');
        if (storage.insert(key('minecraft:stone'), 1, Action.MODULATE, host.getActionSource()) !== 1) throw new Error('Seed insertion');
        var requester = be(level, new Pos(119, powerPos.y, 1));
        requester.getRequests().setStack(0, new Stack(key('minecraft:emerald'), 3));
        requester.getRequests().get(0).updateBatch(3); requester.getRequests().get(0).updateState(true);
    });
    probe('grapharraycycleadvance', level => {
        locate(level);
        var chest = be(level, chestPos), materials = {};
        for (var i = 0; i < chest.getContainerSize(); i++) {
            var stack = chest.getItem(i);
            if (!stack.isEmpty()) materials[String(stack.id)] = (materials[String(stack.id)] || 0) + stack.count;
        }
        var input, fuel, outputs;
        if (materials['minecraft:stone']) { input='minecraft:stone'; fuel='minecraft:coal'; outputs=['minecraft:iron_ingot']; }
        else if (materials['minecraft:iron_ingot']) { input='minecraft:iron_ingot'; fuel='minecraft:oak_log'; outputs=['minecraft:gold_ingot']; }
        else if (materials['minecraft:gold_ingot']) { input='minecraft:gold_ingot'; fuel='minecraft:diamond'; outputs=['minecraft:stone','minecraft:emerald']; }
        else throw new Error('No next physical cycle stage');
        if (materials[input] !== 1 || materials[fuel] !== 1 || Object.keys(materials).length !== 2) throw new Error('Cycle physical quantity mismatch ' + JSON.stringify(materials));
        for (var i = 0; i < chest.getContainerSize(); i++) chest.setItem(i, Item.of('minecraft:air'));
        var host = be(level, controllerPos).getMetaMachine();
        for (var id of outputs) if (host.getGrid().getStorageService().getInventory().insert(key(id), 1, Action.MODULATE, host.getActionSource()) !== 1) throw new Error('Cycle return rejected');
        console.info('[Graph Array] real in-flight stage consumed ' + input + ' + ' + fuel + '; returned=' + outputs.join(','));
    });
    probe('grapharraycyclewaiting', level => {
        locate(level);
        var host = be(level, controllerPos).getMetaMachine(), jobs = 0;
        host.forEachActiveCpu(cpu => {
            var logic = cpu.getCraftingLogic();
            if (!cpu.isBusy()) return;
            jobs++;
            console.info('[Graph Array] cycle link=' + logic.getLastLink().getCraftingID() + ' iron=' + logic.getWaitingFor(key('minecraft:iron_ingot')) + ' gold=' + logic.getWaitingFor(key('minecraft:gold_ingot')) + ' stone=' + logic.getWaitingFor(key('minecraft:stone')) + ' emerald=' + logic.getWaitingFor(key('minecraft:emerald')));
        });
        if (jobs !== 1) throw new Error('Cycle active jobs=' + jobs);
    });
    probe('grapharraycycledone', level => {
        locate(level);
        var host = be(level, controllerPos).getMetaMachine(), inventory = new (Java.loadClass('appeng.api.stacks.KeyCounter'))(), active = 0;
        host.getGrid().getStorageService().getInventory().getAvailableStacks(inventory);
        host.forEachActiveCpu(cpu => { if (cpu.isBusy()) active++; });
        if (active !== 0 || inventory.get(key('minecraft:emerald')) !== 3 || inventory.get(key('minecraft:stone')) !== 1) throw new Error('Cycle final delivery/seed balance');
        for (var id of ['minecraft:iron_ingot','minecraft:gold_ingot','minecraft:coal','minecraft:oak_log','minecraft:diamond'])
            if (inventory.get(key(id)) !== 0) throw new Error('Cycle unexpected remainder ' + id);
        be(level, new Pos(119, powerPos.y, 1)).getRequests().get(0).updateState(false);
        Java.loadClass('org.gtlcore.test.LegacyCalls').checkZero();
        console.info('[Graph Array] PASS: real transfinite/ExtendedAE/requester three-stage cycle, exactly 3 emeralds, 1 restored catalyst, no remaining fuel, no old core calls');
    });
    function chainKey(index) { return Key['of(net.minecraft.world.item.ItemStack)'](Item.of('minecraft:paper', {graph_probe: index})); }
    probe('grapharraybenchload', level => {
        locate(level);
        be(level, new Pos(119, powerPos.y, 1)).getRequests().get(0).updateState(false);
        var patterns = be(level, providerPos).getLogic().getPatternInv();
        if (patterns.size() < 32) throw new Error('Fixture needs 32 pattern slots');
        for (var i = 0; i < patterns.size(); i++) patterns.setItemDirect(i, Item.of('minecraft:air'));
        for (var i = 0; i < 32; i++) patterns.setItemDirect(i, Pattern.encodeProcessingPattern(
            [new Stack(chainKey(i), 1)], [new Stack(chainKey(i + 1), 1)]));
        be(level, drivePos).getInternalInventory().setItemDirect(0, Item.of('ae2:item_storage_cell_1k'));
        var host = be(level, controllerPos).getMetaMachine();
        if (host.getGrid().getStorageService().getInventory().insert(chainKey(0), 1000, Action.MODULATE, host.getActionSource()) !== 1000) throw new Error('Benchmark stock insertion');
        console.info('[Graph Array] A/B fixture: 32-step NBT-distinct processing chain, 1000 initial units, same ExtendedAE provider');
    });
    probe('grapharraybenchmark', level => {
        locate(level);
        var host = be(level, controllerPos).getMetaMachine(), samples = 0;
        function next() {
            var future = Java.loadClass('org.gtlcore.test.GraphPacketProbe').benchmark(host.getGrid(), level, host.getActionSource(), chainKey(32), 1000);
            arrayProbePending.push({future: future, complete: result => {
                console.info('[chain32] ' + result); if (++samples < 10) next();
            }});
        }
        next();
    });
});
