var asyncGraphActive=false;
ServerEvents.tick(event=>{
    if(!asyncGraphActive) return;
    try { Java.loadClass('org.gtlcore.test.AsyncGraphProbe').tick(); }
    catch(error) { asyncGraphActive=false; console.error('[Async Graph] FAILED '+error); }
});
ServerEvents.commandRegistry(event=>{
    var Pos=Java.loadClass('net.minecraft.core.BlockPos');
    var Key=Java.loadClass('appeng.api.stacks.AEItemKey');
    var Source=Java.loadClass('appeng.me.helpers.MachineSource');
    function register(name,action) { event.register(event.commands.literal(name).requires(s=>s.hasPermission(4)).executes(ctx=>{action(ctx.source.level,ctx.source.server);return 1;})); }
    register('asyncgraphplace',(level,server)=>{
        server.runCommandSilent('forceload add 112 0');
        server.runCommandSilent('setblock 112 65 0 ae2:creative_energy_cell');
        server.runCommandSilent('setblock 113 65 0 ae2:64k_crafting_storage');
        server.runCommandSilent('setblock 112 65 1 gtceu:me_extended_async_export_buffer');
    });
    function start(level,cancel) {
        var cpu=level.getBlockEntity(new Pos(113,65,0));
        var hatch=level.getBlockEntity(new Pos(112,65,1)).getMetaMachine();
        var raw=Key['of(net.minecraft.world.item.ItemStack)'](Item.of('minecraft:paper',{async_graph:'raw'}));
        var output=Key['of(net.minecraft.world.item.ItemStack)'](Item.of('minecraft:paper',{async_graph:'product'}));
        Java.loadClass('org.gtlcore.test.AsyncGraphProbe').start(cpu.getMainNode().getNode().getGrid(),cpu.getCluster(),hatch,level,new Source(cpu),raw,output,cancel);
        asyncGraphActive=true;
    }
    register('asyncgraphnormal',level=>start(level,false));
    register('asyncgraphcancel',level=>start(level,true));
});
