// Only for the isolated test world: real ExtendedAE cell in an AE drive.
var waterProbeActive = false;
ServerEvents.tick(event => {
    if (!waterProbeActive) return;
    try { Java.loadClass('org.gtlcore.test.WaterByproductProbe').tick(); }
    catch (error) { waterProbeActive = false; console.error('[Water Graph] HARNESS FAILED ' + error); }
});
ServerEvents.commandRegistry(event => {
    var Pos = Java.loadClass('net.minecraft.core.BlockPos');
    function register(name, action) { event.register(event.commands.literal(name).requires(s => s.hasPermission(4)).executes(ctx => {
        try { action(ctx.source.level, ctx.source.server); return 1; }
        catch (error) { console.error('[Water Graph] COMMAND FAILED ' + error); throw error; }
    })); }
    register('watergraphplace', (level, server) => {
        server.runCommandSilent('forceload add 144 0');
        server.runCommandSilent('setblock 145 66 0 minecraft:air');
        server.runCommandSilent('setblock 145 65 0 minecraft:air');
        server.runCommandSilent('setblock 144 65 0 ae2:creative_energy_cell');
        server.runCommandSilent('setblock 145 65 0 ae2:64k_crafting_storage');
        server.runCommandSilent('setblock 145 66 0 ae2:crafting_accelerator');
        server.runCommandSilent('setblock 144 65 1 ae2:drive[facing=south]');
    });
    register('watergraphload', level => {
        var drive = level.getBlockEntity(new Pos(144, 65, 1));
        drive.getInternalInventory().setItemDirect(0, Item.of('expatternprovider:infinity_cell'));
    });
    register('watergraphstart', level => {
        var cpu = level.getBlockEntity(new Pos(145, 65, 0));
        var Key = Java.loadClass('appeng.api.stacks.AEItemKey');
        function key(label) { return Key['of(net.minecraft.world.item.ItemStack)'](Item.of('minecraft:paper', {water_graph: label})); }
        var FluidKey = Java.loadClass('appeng.api.stacks.AEFluidKey');
        var Fluids = Java.loadClass('net.minecraft.world.level.material.Fluids');
        var Source = Java.loadClass('appeng.me.helpers.MachineSource');
        Java.loadClass('org.gtlcore.test.WaterByproductProbe').start(cpu.getMainNode().getGrid(), cpu.getCluster(), level,
            new Source(cpu), key('raw'), key('intermediate'), key('product'), key('unused'), FluidKey['of(net.minecraft.world.level.material.Fluid)'](Fluids.WATER));
        waterProbeActive = true;
    });
});
