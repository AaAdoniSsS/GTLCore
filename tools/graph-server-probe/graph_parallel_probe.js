// Isolated multi-assembler regression. Never install into a real save.
var ParallelPos = Java.loadClass('net.minecraft.core.BlockPos');
var parallelPending = null, parallelRunning = false, parallelTicks = 0, parallelSeed = 1;
var parallelCheckpoint = false;
ServerEvents.tick(event => {
    if (parallelPending != null && parallelPending.future.isDone()) {
        var task = parallelPending; parallelPending = null;
        try { task.complete(task.future.get()); } catch (e) { console.error('[Graph Parallel] FAILED ' + e); }
    }
    if (!parallelRunning) return;
    var level = event.server.overworld(), cpu = level.getBlockEntity(new ParallelPos(33,65,0));
    var graph = cpu.getCluster().craftingLogic.gtlcore$graphController();
    if (parallelCheckpoint && graph.ownsTask()) {
        var runtimeField = graph.getClass().getDeclaredField('runtime'); runtimeField.setAccessible(true);
        var runtime = runtimeField.get(graph), flights = runtime.snapshot().obligations().flights().size();
        if (runtime.dispatches() >= 12 && flights >= 2) {
            parallelCheckpoint = false; parallelRunning = false;
            console.info('[Graph Parallel] checkpoint link='+graph.link().getCraftingID()+' flights='+flights+' expected='+runtime.expected()+' dispatches='+runtime.dispatches());
            event.server.runCommandSilent('save-all'); event.server.runCommandSilent('stop'); return;
        }
    }
    if (graph.ownsTask() && ++parallelTicks < 12000) return;
    parallelRunning = false;
    Java.loadClass('org.gtlcore.test.GraphParallelProbe').finish();
    if (graph.ownsTask()) throw new Error('Parallel test timed out');
    var Key = Java.loadClass('appeng.api.stacks.AEItemKey');
    var counts = cpu.getMainNode().getNode().getGrid().getStorageService().getInventory().getAvailableStacks();
    function amount(id) { return counts.get(Key['of(net.minecraft.world.item.ItemStack)'](Item.of('minecraft:' + id))); }
    var templates = amount('netherite_upgrade_smithing_template');
    var diamondUnits = amount('diamond') + 9*amount('diamond_block') + 7*templates;
    var rackUnits = amount('netherrack') + templates;
    console.info('[Graph Parallel] completed templates=' + templates + ' diamond_units=' + diamondUnits + ' rack_units=' + rackUnits);
    if (diamondUnits !== 2700+7*parallelSeed || rackUnits !== 2000+parallelSeed || templates < 100+parallelSeed)
        throw new Error('Parallel production material balance changed');
});
ServerEvents.commandRegistry(event => {
    var Key = Java.loadClass('appeng.api.stacks.AEItemKey');
    var Source = Java.loadClass('appeng.me.helpers.MachineSource');
    var Pattern = Java.loadClass('appeng.api.crafting.PatternDetailsHelper');
    var Resource = Java.loadClass('net.minecraft.resources.ResourceLocation');
    var Strategy = Java.loadClass('appeng.api.networking.crafting.CalculationStrategy');
    var Action = Java.loadClass('appeng.api.config.Actionable');
    var Probe = Java.loadClass('org.gtlcore.test.GraphParallelProbe');
    function key(id) { return Key['of(net.minecraft.world.item.ItemStack)'](Item.of('minecraft:' + id)); }
    function command(name, action) { event.register(event.commands.literal(name).requires(s=>s.hasPermission(4)).executes(ctx=>{ try { action(ctx.source.level,ctx.source.server); return 1; } catch(e) { console.error('[Graph Parallel] command FAILED ' + e); throw e; } })); }
    command('graphparallelplace', (level,server) => {
        server.runCommandSilent('forceload add 32 0');
        server.runCommandSilent('setblock 32 65 0 ae2:creative_energy_cell');
        server.runCommandSilent('setblock 33 65 0 ae2:64k_crafting_storage');
        for (var pos of [[33,66,0],[33,65,1],[33,66,1]]) server.runCommandSilent('setblock '+pos.join(' ')+' ae2:crafting_accelerator');
        server.runCommandSilent('setblock 34 65 0 ae2:pattern_provider');
        server.runCommandSilent('setblock 32 65 1 ae2:drive');
        for (var pos of [[35,65,0],[34,66,0],[34,64,0],[34,65,-1],[34,65,1]]) server.runCommandSilent('setblock '+pos.join(' ')+' ae2:molecular_assembler');
    });
    function load(level, seeds) {
        parallelSeed = seeds;
        var cpu = level.getBlockEntity(new ParallelPos(33,65,0));
        if (cpu.getCluster().isBusy()) throw new Error('CPU busy');
        var grid = cpu.getMainNode().getNode().getGrid(), source = new Source(cpu);
        level.getBlockEntity(new ParallelPos(32,65,1)).getInternalInventory().setItemDirect(0,Item.of('ae2:item_storage_cell_64k'));
        var patterns = level.getBlockEntity(new ParallelPos(34,65,0)).getLogic().getPatternInv();
        function recipe(id) { return level.getRecipeManager().byKey(new Resource(id)).orElseThrow(); }
        var inputs = ['diamond','netherite_upgrade_smithing_template','diamond','diamond','netherrack','diamond','diamond','diamond','diamond'].map(id=>Item.of('minecraft:'+id));
        patterns.setItemDirect(0,Pattern.encodeCraftingPattern(recipe('gtlgraphprobe:template_duplication'),inputs,Item.of('minecraft:netherite_upgrade_smithing_template',2),false,false));
        var unpack = [Item.of('minecraft:diamond_block')]; for(var i=1;i<9;i++) unpack.push(Item.of('minecraft:air'));
        patterns.setItemDirect(1,Pattern.encodeCraftingPattern(recipe('gtlgraphprobe:diamond_unpack'),unpack,Item.of('minecraft:diamond',9),false,false));
        for(var entry of [['diamond_block',300],['netherrack',2000],['netherite_upgrade_smithing_template',seeds]]) {
            var inserted=grid.getStorageService().getInventory().insert(key(entry[0]),entry[1],Action.MODULATE,source);
            if(inserted!==entry[1]) throw new Error('Insufficient test storage');
        }
        console.info('[Graph Parallel] ready seeds='+seeds+' copro='+cpu.getCluster().getCoProcessors());
    }
    command('graphparallelload',level=>load(level,1));
    command('graphparallelload4',level=>load(level,4));
    command('graphparallelcheckpoint',()=>{parallelCheckpoint=true;});
    command('graphparallelresume',level=>{
        var cpu=level.getBlockEntity(new ParallelPos(33,65,0)), graph=cpu.getCluster().craftingLogic.gtlcore$graphController();
        if(!graph.ownsTask()) throw new Error('Expected saved in-flight graph task');
        var field=graph.getClass().getDeclaredField('runtime'); field.setAccessible(true);
        var runtime=field.get(graph);
        console.info('[Graph Parallel] resumed link='+graph.link().getCraftingID()+' flights='+runtime.snapshot().obligations().flights().size()+' expected='+runtime.expected());
        parallelSeed=1; parallelTicks=0; parallelRunning=true;
        Probe.begin('resumed-template100-seed1');
    });
    command('graphparallelstart',level=>{
        if(parallelPending!=null||parallelRunning) throw new Error('Parallel test active');
        var cpu=level.getBlockEntity(new ParallelPos(33,65,0)), grid=cpu.getMainNode().getNode().getGrid(), source=new Source(cpu);
        parallelPending={future:grid.getCraftingService().beginCraftingCalculation(level,()=>source,key('netherite_upgrade_smithing_template'),100,Strategy.REPORT_MISSING_ITEMS),complete:plan=>{
            Probe.begin('template100-seed'+parallelSeed);
            var result=grid.getCraftingService().submitJob(plan,null,cpu.getCluster(),false,source);
            if(!result.successful()) throw new Error('Parallel submit '+result.errorCode());
            parallelRunning=true; parallelTicks=0;
            console.info('[Graph Parallel] accepted plan_bytes='+plan.bytes());
        }};
    });
});
