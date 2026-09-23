// Isolated fixture only. The writer is deliberately stalled; never run in a real save.
ServerEvents.commandRegistry(event => {
    event.register(event.commands.literal('asyncoutputbench').requires(source=>source.hasPermission(4)).executes(ctx=>{
        var Pos=Java.loadClass('net.minecraft.core.BlockPos');
        var Ingredient=Java.loadClass('org.gtlcore.gtlcore.api.recipe.ingredient.LongIngredient');
        var Key=Java.loadClass('appeng.api.stacks.AEItemKey');
        var item=Item.of('minecraft:gold_ingot',{async_output_probe:'benchmark'});
        var ingredient=Ingredient['create(net.minecraft.world.item.ItemStack)'](item); ingredient.setActualAmount(100);
        Java.loadClass('org.gtlcore.test.AsyncOutputProbe').benchmark(ctx.source.level,new Pos(80,65,0),ingredient,Key['of(net.minecraft.world.item.ItemStack)'](item));
        return 1;
    }));
    event.register(event.commands.literal('asyncoutputprobe').requires(source => source.hasPermission(4)).executes(ctx => {
        var Pos = Java.loadClass('net.minecraft.core.BlockPos');
        var Probe = Java.loadClass('org.gtlcore.test.AsyncOutputProbe');
        var LongIngredient = Java.loadClass('org.gtlcore.gtlcore.api.recipe.ingredient.LongIngredient');
        var Key = Java.loadClass('appeng.api.stacks.AEItemKey');
        var item = Item.of('minecraft:diamond', {async_output_probe: 'preserve'});
        for(var i=0;i<2;i++) {
            ctx.source.server.runCommandSilent('setblock '+(80+i*2)+' 65 0 air');
            ctx.source.server.runCommandSilent('setblock '+(80+i*2)+' 65 0 gtceu:me_extended_async_export_buffer');
            var ingredient=LongIngredient['create(net.minecraft.world.item.ItemStack)'](item);
            ingredient.setActualAmount(3000000000);
            Probe.save(ctx.source.level, new Pos(80+i*2,65,0), ingredient, Key['of(net.minecraft.world.item.ItemStack)'](item), i===1);
        }
        return 1;
    }));
});
