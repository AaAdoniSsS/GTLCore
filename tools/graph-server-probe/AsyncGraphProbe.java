package org.gtlcore.test;

import org.gtlcore.gtlcore.api.recipe.ingredient.LongIngredient;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEExtendedAsyncOutputPartMachine;
import org.gtlcore.gtlcore.integration.ae2.graph.GraphCpuAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import appeng.api.config.Actionable;
import appeng.api.crafting.*;
import appeng.api.networking.*;
import appeng.api.networking.crafting.*;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.*;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.service.CraftingService;
import appeng.me.service.helpers.NetworkCraftingProviders;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.Future;

/** Actual CPU / ME routing / async output hatch; deterministic external processing and bounded storage. */
public final class AsyncGraphProbe {
    private static IGrid grid;
    private static IGridNode node;
    private static appeng.api.storage.IStorageProvider storageProvider;
    private static NetworkCraftingProviders providers;
    private static MEExtendedAsyncOutputPartMachine hatch;
    private static CraftingCPUCluster cpu;
    private static AEItemKey raw, output;
    private static IActionSource source;
    private static Future<ICraftingPlan> planning;
    private static long rawStock, outputStock, processing, dispatched;
    private static boolean allowStorage;
    private static int state, ticks;
    private static boolean cancel;

    public static void start(IGrid network, CraftingCPUCluster cluster, MEExtendedAsyncOutputPartMachine machine,
                             Level level, IActionSource actionSource, AEItemKey input, AEItemKey product, boolean cancelling) throws Exception {
        if (state != 0) throw new IllegalStateException("Already testing");
        grid=network; cpu=cluster; hatch=machine; source=actionSource; raw=input; output=product; cancel=cancelling;
        rawStock=1000; outputStock=processing=dispatched=0; ticks=0; allowStorage=false;
        var storage = new appeng.api.storage.MEStorage() {
            public net.minecraft.network.chat.Component getDescription() { return raw.getDisplayName(); }
            public void getAvailableStacks(KeyCounter out) { out.add(raw,rawStock); out.add(output,outputStock); }
            public long extract(AEKey key,long amount,Actionable mode,IActionSource src) {
                long held=key.equals(raw)?rawStock:key.equals(output)?outputStock:0;
                long taken=Math.min(amount,held);
                if(mode==Actionable.MODULATE) { if(key.equals(raw)) rawStock-=taken; else if(key.equals(output)) outputStock-=taken; }
                return taken;
            }
            public long insert(AEKey key,long amount,Actionable mode,IActionSource src) {
                if(!allowStorage || (!key.equals(raw)&&!key.equals(output))) return 0;
                long accepted=Math.min(7,amount);
                if(mode==Actionable.MODULATE) { if(key.equals(raw)) rawStock+=accepted; else outputStock+=accepted; }
                return accepted;
            }
        };
        storageProvider=mounts->mounts.mount(storage);
        grid.getStorageService().addGlobalStorageProvider(storageProvider);
        var pattern=PatternDetailsHelper.decodePattern(PatternDetailsHelper.encodeProcessingPattern(
                new GenericStack[]{new GenericStack(raw,1)},new GenericStack[]{new GenericStack(output,1)}),level);
        var provider=new ICraftingProvider() {
            public List<IPatternDetails> getAvailablePatterns() { return List.of(pattern); }
            public boolean isBusy() { return false; }
            public boolean pushPattern(IPatternDetails p, KeyCounter[] inputs) {
                long received=0;
                for(var slot:inputs) for(var entry:slot) {
                    if(!entry.getKey().equals(raw)) throw new AssertionError("Unexpected input identity");
                    received=Math.addExact(received,entry.getLongValue());
                }
                if(received<=0) throw new AssertionError("Unfunded dispatch");
                processing+=received; dispatched+=received;
                return true;
            }
        };
        node=(IGridNode)Proxy.newProxyInstance(AsyncGraphProbe.class.getClassLoader(),new Class[]{IGridNode.class},(p,m,a)->switch(m.getName()) {
            case "getService" -> a[0]==ICraftingProvider.class?provider:null;
            case "getGrid" -> grid;
            case "hashCode" -> System.identityHashCode(p);
            case "equals" -> p==a[0];
            default -> null;
        });
        var field=CraftingService.class.getDeclaredField("craftingProviders"); field.setAccessible(true);
        providers=(NetworkCraftingProviders)field.get(grid.getCraftingService()); providers.addProvider(node);
        planning=grid.getCraftingService().beginCraftingCalculation(level,()->source,output,100,CalculationStrategy.REPORT_MISSING_ITEMS);
        state=1;
    }

    public static void tick() throws Exception {
        if(state==0) return;
        if(++ticks>2400) throw new AssertionError("Async/graph timeout state="+state+" cpu="+cpu.isBusy()+" hatch="+hatch.getBuffer()+" raw="+rawStock+" output="+outputStock);
        if(state==1) {
            if(!planning.isDone()) return;
            var plan=planning.get();
            if(plan.simulation()) throw new AssertionError("Missing material");
            var submitted=grid.getCraftingService().submitJob(plan,null,cpu,false,source);
            if(!submitted.successful()) throw new AssertionError("Submit "+submitted.errorCode());
            state=2;
        } else if(state==2) {
            if(processing==0) return;
            if(cancel) ((GraphCpuAccess)cpu.craftingLogic).gtlcore$graphController().cancel();
            deliver();
            // Actual machine NBT roundtrip while output has not entered AE yet.
            var saved=new CompoundTag(); hatch.saveCustomPersistedData(saved,false);
            long before=hatch.getBuffer().getLong(output);
            hatch.loadCustomPersistedData(saved);
            if(before!=hatch.getBuffer().getLong(output)||before==0) throw new AssertionError("Hatch save lost output");
            state=3;
        } else {
            deliver();
            if(ticks>100) allowStorage=true;
            if(!cpu.isBusy()&&processing==0&&hatch.getBuffer().isEmpty()) {
                var saved=new CompoundTag(); hatch.saveCustomPersistedData(saved,false);
                if(!hatch.getBuffer().isEmpty()) return;
                if(rawStock+outputStock!=1000||outputStock!=dispatched||(!cancel&&outputStock!=100))
                    throw new AssertionError("Conservation failed raw="+rawStock+" output="+outputStock+" dispatched="+dispatched);
                System.out.println("[Async Graph] PASS cancel="+cancel+" raw="+rawStock+" output="+outputStock+" dispatched="+dispatched+" ticks="+ticks+" full_storage_then_partial=7 nbt_roundtrip=true");
                providers.removeProvider(node); grid.getStorageService().removeGlobalStorageProvider(storageProvider); state=0;
            }
        }
    }

    private static void deliver() {
        if(processing==0) return;
        var ingredient=LongIngredient.create(output.toStack(1));
        ingredient.setActualAmount(processing);
        var left=hatch.getItemOutputHandler().meHandleRecipeOutput(List.of(ingredient),false);
        if(!left.isEmpty()) throw new AssertionError("Fixture hatch rejected material");
        processing=0;
    }
}
