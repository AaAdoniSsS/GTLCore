// Optional ADD fixture in the isolated test world. No real pack/world modification.
var addonPending = null, addonPrevious = null;
ServerEvents.tick(event => {
    if (addonPending != null && addonPending.future.isDone()) {
        var task = addonPending; addonPending = null;
        try { task.complete(task.future.get()); } catch (e) { console.error('[Graph ADD] FAILED ' + e); }
    }
});
ServerEvents.commandRegistry(event => {
    var Pos = Java.loadClass('net.minecraft.core.BlockPos');
    var Key = Java.loadClass('appeng.api.stacks.AEItemKey');
    var Source = Java.loadClass('appeng.me.helpers.MachineSource');
    var Strategy = Java.loadClass('appeng.api.networking.crafting.CalculationStrategy');
    var Action = Java.loadClass('appeng.api.config.Actionable');
    var Probe = Java.loadClass('org.gtlcore.test.GraphAddonProbe');
    function key(id) { return Key['of(net.minecraft.world.item.ItemStack)'](Item.of('minecraft:'+id)); }
    function command(name, action) { event.register(event.commands.literal(name).requires(s=>s.hasPermission(4)).executes(ctx=>{ try { action(ctx.source.level,ctx.source.server); return 1; } catch(e) { console.error('[Graph ADD] command FAILED '+e); throw e; } })); }
    command('graphaddoneffective', level => Probe.effectivePatterns(level,key('redstone'),Key['of(net.minecraft.world.item.ItemStack)'](Item.of('minecraft:paper',{graph_addon_probe:'identity'}))));
    command('graphaddonplace', (level, server) => {
        server.runCommandSilent('forceload add 64 0');
        server.runCommandSilent('setblock 64 65 0 ae2:creative_energy_cell');
        server.runCommandSilent('setblock 65 65 0 ae2:64k_crafting_storage');
        server.runCommandSilent('setblock 64 65 1 ae2:drive');
        var found=false;
        for(var id of Java.loadClass('net.minecraftforge.registries.ForgeRegistries').BLOCKS.getKeys()) {
            var name=''+id;
            if(name.startsWith('gtladditions:')&&name.includes('super_pattern_buffer')&&!name.includes('proxy')) {
                server.runCommandSilent('setblock 66 65 0 '+name); found=true;
                console.info('[Graph ADD] placed actual buffer='+name); break;
            }
        }
        if(!found) throw new Error('ADD super pattern buffer registry id missing');
    });
    command('graphaddonwire',level=>{
        Java.loadClass('appeng.api.parts.PartHelper').setPart(level,new Pos(65,65,1),null,null,Item.of('ae2:fluix_glass_cable').getItem());
        level.getBlockEntity(new Pos(66,65,0)).getMetaMachine().setFrontFacing(Java.loadClass('net.minecraft.core.Direction').WEST);
    });
    command('graphaddonload4', level => {
        level.getBlockEntity(new Pos(64,65,1)).getInternalInventory().setItemDirect(0,Item.of('ae2:item_storage_cell_64k'));
        Probe.configure(level,4,key('redstone'),key('paper'));
    });
    command('graphaddonstock', level => {
        var cpu=level.getBlockEntity(new Pos(65,65,0)), grid=cpu.getMainNode().getNode().getGrid();
        var drive=level.getBlockEntity(new Pos(64,65,1));
        var inserted=grid.getStorageService().getInventory().insert(key('redstone'),100,Action.MODULATE,new Source(cpu));
        console.info('[Graph ADD] stock inserted='+inserted+' drive='+drive.getInternalInventory().getStackInSlot(0)+' same_grid='+(drive.getMainNode().getNode().getGrid()===grid)+' powered='+grid.getEnergyService().isNetworkPowered());
        if(inserted!=100) throw new Error('ADD fixture stock');
    });
    command('graphaddonmode8', level=>Probe.configure(level,8,key('redstone'),key('paper')));
    function plan(level, multiplier) {
        if(addonPending!=null) throw new Error('ADD request active');
        var cpu=level.getBlockEntity(new Pos(65,65,0)), grid=cpu.getMainNode().getNode().getGrid(), source=new Source(cpu);
        addonPending={future:grid.getCraftingService().beginCraftingCalculation(level,()=>source,key('paper'),8,Strategy.REPORT_MISSING_ITEMS),complete:value=>{
            Probe.inspectPlan(value,multiplier,key('redstone'));
            addonPrevious=value;
        }};
    }
    command('graphaddonplan4',level=>plan(level,4));
    command('graphaddonplan8',level=>plan(level,8));
    command('graphaddonstale',level=>{
        if(addonPrevious==null) throw new Error('Need previous FOA plan');
        var cpu=level.getBlockEntity(new Pos(65,65,0)), grid=cpu.getMainNode().getNode().getGrid(), source=new Source(cpu);
        var result=grid.getCraftingService().submitJob(addonPrevious,null,cpu.getCluster(),false,source);
        var stock=grid.getStorageService().getInventory().getAvailableStacks().get(key('redstone'));
        if(result.successful()||stock!==100||cpu.getCluster().isBusy()) throw new Error('Stale FOA plan accepted or moved material');
        console.info('[Graph ADD] PASS stale FOA plan rejected before extraction: '+result.errorCode()+' stock='+stock);
    });
});
