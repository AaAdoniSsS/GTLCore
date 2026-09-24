package org.gtlcore.test;

import org.gtlcore.gtlcore.integration.ae2.graph.AeGraphPlan;
import org.gtlcore.gtlcore.integration.ae2.graph.GraphCpuAccess;
import org.gtlcore.gtlcore.integration.ae2.graph.GraphPlanningRequest;
import org.gtlcore.gtlcore.integration.ae2.graph.core.PlanVerifier;
import net.minecraft.world.level.Level;
import appeng.api.config.Actionable;
import appeng.api.crafting.*;
import appeng.api.networking.*;
import appeng.api.networking.crafting.*;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.*;
import appeng.api.storage.MEStorage;
import appeng.api.storage.IStorageProvider;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.service.CraftingService;
import appeng.me.service.helpers.NetworkCraftingProviders;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Opt-in real ExtendedAE water cell / AE capture / CPU test with deterministic processing. */
public final class WaterByproductProbe {
    private record Case(String name, long returned, long amount, boolean waterFirst, boolean execute, boolean unnecessary) {}
    private static final ArrayDeque<Case> cases = new ArrayDeque<>();
    private static final Map<AEKey,Long> held = new LinkedHashMap<>(), pending = new LinkedHashMap<>();
    private static final Map<IPatternDetails,Long> dispatched = new LinkedHashMap<>();
    private record Delayed(AEKey key, long amount, long due) {}
    private static final List<Delayed> delayed = new ArrayList<>();
    private static boolean pipelineOverlap;
    private static IGrid grid;
    private static CraftingCPUCluster cpu;
    private static Level level;
    private static IActionSource source;
    private static AEKey raw, intermediate, product, unused, water;
    private static IGridNode node;
    private static NetworkCraftingProviders providers;
    private static IStorageProvider storageProvider;
    private static GraphPlanningRequest request;
    private static java.util.concurrent.CompletableFuture<GraphStressProbe.Sample> baseline;
    private static Case current;
    private static List<IPatternDetails> patterns;
    private static int state, ticks, passed, failed, oldPassed, oldFailed;
    private static long started, waterReceived, executionStarted;
    private static volatile long finished;
    private static boolean legacyExecuting;
    private static boolean providerChanged;
    private static long legacyCallsAtSubmit;

    public static void start(IGrid network, CraftingCPUCluster cluster, Level world, IActionSource action,
                             AEKey r, AEKey i, AEKey p, AEKey u, AEKey w) throws Exception {
        if(state != 0 || cluster.isBusy()) throw new IllegalStateException("Fixture busy");
        grid=network; cpu=cluster; level=world; source=action;
        raw=r; intermediate=i; product=p; unused=u; water=w;
        if(grid.getStorageService().getCachedInventory().get(water) != Long.MAX_VALUE)
            throw new AssertionError("Real ExtendedAE water disk is not mounted: " + grid.getStorageService().getCachedInventory().get(water));
        if(grid.getStorageService().getInventory().extract(water, 100_000_000_000L, Actionable.SIMULATE, source) != 100_000_000_000L)
            throw new AssertionError("Water is advertised but inaccessible");
        cases.clear(); passed=failed=oldPassed=oldFailed=0;
        for(long amount:new long[]{9,97,100_000_000,100_000_017,1_000_000_000_000L})
            for(long returned:new long[]{500,1000,2000})
                for(boolean first:new boolean[]{false,true})
                    cases.add(new Case("chain",returned,amount,first,amount==9,false));
        for(long amount:new long[]{9,100_000_000}) cases.add(new Case("unneeded-water-recipe",1000,amount,true,amount==9,true));
        cases.add(new Case("replan",2000,9,false,true,false));
        cases.add(new Case("pipeline",1000,9,false,true,false));
        var field=CraftingService.class.getDeclaredField("craftingProviders"); field.setAccessible(true);
        providers=(NetworkCraftingProviders)field.get(grid.getCraftingService());
        var storage=new MEStorage() {
            public net.minecraft.network.chat.Component getDescription() { return raw.getDisplayName(); }
            public void getAvailableStacks(KeyCounter out) { held.forEach(out::add); }
            public long extract(AEKey key,long amount,Actionable mode,IActionSource src) {
                long taken=Math.min(amount,held.getOrDefault(key,0L));
                if(mode==Actionable.MODULATE && taken>0) held.merge(key,-taken,Long::sum);
                return taken;
            }
            public long insert(AEKey key,long amount,Actionable mode,IActionSource src) {
                if(key.equals(water)) return 0;
                if(mode==Actionable.MODULATE) held.merge(key,amount,Math::addExact);
                return amount;
            }
        };
        storageProvider=mounts->mounts.mount(storage);
        grid.getStorageService().addGlobalStorageProvider(storageProvider);
        next();
    }

    private static IPatternDetails pattern(GenericStack[] inputs, GenericStack[] outputs) {
        return PatternDetailsHelper.decodePattern(PatternDetailsHelper.encodeProcessingPattern(inputs,outputs),level);
    }

    private static void next() {
        if(node!=null) { providers.removeProvider(node); node=null; }
        current=cases.poll();
        if(current==null) {
            grid.getStorageService().removeGlobalStorageProvider(storageProvider);
            state=0;
            System.out.println("[Water Graph] SUITE COMPLETE passed="+passed+" failed="+failed+" max_fast_passed="+oldPassed+" max_fast_failed="+oldFailed);
            return;
        }
        held.clear(); pending.clear(); dispatched.clear(); waterReceived=0;
        delayed.clear(); pipelineOverlap=false;
        legacyExecuting=false;
        providerChanged=false;
        held.put(raw,current.amount());
        var outputs=new GenericStack[]{new GenericStack(product,1),new GenericStack(water,current.returned())};
        if(current.unnecessary()) patterns=List.of(
                pattern(new GenericStack[]{new GenericStack(raw,1),new GenericStack(water,1000)},new GenericStack[]{new GenericStack(product,1)}),
                pattern(new GenericStack[]{new GenericStack(product,1),new GenericStack(unused,1)},new GenericStack[]{new GenericStack(water,1000),new GenericStack(intermediate,1)}));
        else patterns=List.of(pattern(new GenericStack[]{new GenericStack(raw,1),new GenericStack(water,1000)},new GenericStack[]{new GenericStack(intermediate,1)}),
                pattern(new GenericStack[]{new GenericStack(intermediate,1)},outputs));
        if(current.waterFirst()&&!current.unnecessary()) {
            // A normal AE provider advertises only the first output. A second
            // equivalent pattern makes water independently craftable, as with
            // parallel patterns exposing both outputs as primary products.
            patterns=new ArrayList<>(patterns);
            patterns.add(pattern(new GenericStack[]{new GenericStack(intermediate,1)},
                    new GenericStack[]{new GenericStack(water,current.returned()),new GenericStack(product,1)}));
        }
        var provider=new ICraftingProvider() {
            public List<IPatternDetails> getAvailablePatterns() { return patterns; }
            public boolean isBusy() { return false; }
            public boolean pushPattern(IPatternDetails pattern,KeyCounter[] inputs) {
                if(!current.execute()) throw new AssertionError("Planning-only case dispatched");
                var actual=new KeyCounter();
                for(var slot:inputs) actual.addAll(slot);
                var required=new KeyCounter();
                for(var input:pattern.getInputs()) {
                    var first=input.getPossibleInputs()[0];
                    required.add(first.what(),Math.multiplyExact(first.amount(),input.getMultiplier()));
                }
                long runs=0;
                for(var entry:required) {
                    long count=actual.get(entry.getKey());
                    if(count<=0||count%entry.getLongValue()!=0) throw new AssertionError("Incomplete real dispatch");
                    long n=count/entry.getLongValue();
                    if(runs!=0 && runs!=n) throw new AssertionError("Input batch ratio mismatch");
                    runs=n;
                }
                if(actual.size()!=required.size()) throw new AssertionError("Unexpected real input");
                waterReceived=Math.addExact(waterReceived,actual.get(water));
                long ordinal=dispatched.merge(pattern,runs,Math::addExact);
                boolean upstream=actual.get(raw)>0;
                if(!upstream&&delayed.stream().anyMatch(d->d.key().equals(intermediate))) pipelineOverlap=true;
                for(var output:pattern.getOutputs()) {
                    long amount=Math.multiplyExact(output.amount(),runs);
                    if((current.name().equals("pipeline")||current.name().equals("replan"))&&upstream) delayed.add(new Delayed(output.what(),amount,ticks+(ordinal%2==0?60:1)));
                    else pending.merge(output.what(),amount,Math::addExact);
                }
                return true;
            }
        };
        node=(IGridNode)Proxy.newProxyInstance(WaterByproductProbe.class.getClassLoader(),new Class[]{IGridNode.class},(p,m,a)->switch(m.getName()) {
            case "getService" -> a[0]==ICraftingProvider.class?provider:null;
            case "getGrid" -> grid;
            case "hashCode" -> System.identityHashCode(p);
            case "equals" -> p==a[0];
            default -> null;
        });
        providers.addProvider(node);
        // Re-mount finite storage to publish this case's fresh stock through AE's cache.
        grid.getStorageService().refreshGlobalStorageProvider(storageProvider);
        state=1; ticks=0;
    }

    public static void tick() throws Exception {
        if(state==0) return;
        if(++ticks>2400) { fail("execution timeout raw="+held+" pending="+pending+" water_received="+waterReceived+" dispatched="+dispatched); return; }
        if(state==5) {
            if(ticks<3) return;
            if(grid.getStorageService().getCachedInventory().get(raw)!=current.amount())
                throw new AssertionError("Baseline did not receive the same refreshed starting stock");
            baseline=GraphStressProbe.baseline(grid,level,source,product,current.amount());
            state=4; return;
        }
        if(state==4) {
            if(!baseline.isDone()) return;
            var sample=baseline.get();
            var plan=sample.plan();
            boolean valid=plan!=null&&!plan.simulation()&&plan.missingItems().isEmpty()&&plan.emittedItems().isEmpty()&&
                    plan.usedItems().get(raw)==current.amount()&&plan.usedItems().get(unused)==0&&
                    plan.patternTimes().values().stream().mapToLong(Long::longValue).sum()==current.amount()*(current.unnecessary()?1:2);
            if(valid) oldPassed++; else oldFailed++;
            System.out.printf(Locale.ROOT,"[Water Graph] MAX_FAST %s %s wall_ms=%.3f work_ms=%.3f water_used=%d failure=%s max_fast_calls=%d%n",
                    valid?"PASS":"FAIL",label(),sample.wall()/1e6,sample.work()/1e6,plan==null?-1:plan.usedItems().get(water),sample.failure(),LegacyCalls.maxFast.get());
            if(valid&&current.execute()) {
                legacyCallsAtSubmit=LegacyCalls.executor.get();
                var submitted=grid.getCraftingService().submitJob(plan,null,cpu,false,source);
                if(!submitted.successful()) { oldFailed++; System.out.println("[Water Graph] MAX_FAST EXEC FAIL submit="+submitted.errorCode()); next(); return; }
                if(((GraphCpuAccess)cpu.craftingLogic).gtlcore$graphController().ownsTask()) throw new AssertionError("Legacy job entered graph executor");
                legacyExecuting=true; state=3; executionStarted=System.nanoTime();
                return;
            }
            next(); return;
        }
        if(state==1) {
            if(ticks<3) return;
            started=System.nanoTime(); finished=0;
            request=(GraphPlanningRequest)grid.getCraftingService().beginCraftingCalculation(level,()->source,product,current.amount(),CalculationStrategy.REPORT_MISSING_ITEMS);
            request.whenComplete((p,e)->finished=System.nanoTime());
            var exact=request;
            java.util.concurrent.CompletableFuture.delayedExecutor(15,TimeUnit.SECONDS).execute(()->{ if(!exact.isDone()) exact.cancel(true); });
            state=2;
        } else if(state==2) {
            if(!request.isDone()) return;
            ICraftingPlan plan;
            try { plan=request.get(); } catch(Exception ex) { fail("planning_exception="+ex); return; }
            if(!(plan instanceof AeGraphPlan graph)||plan.simulation()) {
                fail("plan="+(plan instanceof AeGraphPlan g?g.graph().result():plan)+" missing="+plan.missingItems()); return;
            }
            PlanVerifier.verify(graph.graph());
            if(plan.usedItems().get(raw)!=current.amount()||plan.usedItems().get(unused)!=0)
                throw new AssertionError("Wrong materials "+plan.usedItems());
            long runs=plan.patternTimes().values().stream().mapToLong(Long::longValue).sum();
            if(runs!=(current.unnecessary()?current.amount():Math.multiplyExact(2,current.amount())))
                throw new AssertionError("Unnecessary processing runs "+runs);
            System.out.printf(Locale.ROOT,"[Water Graph] PLAN PASS %s wall_ms=%.3f solver_ms=%.3f nodes=%d initial_water=%d recipes=%d result=%s%n",
                    label(),(finished-started)/1e6,graph.graph().planningNanos()/1e6,graph.graph().searchNodes(),plan.usedItems().get(water),plan.patternTimes().size(),graph.graph().result());
            if(!current.execute()) { passed++; compareOld(); return; }
            var submitted=grid.getCraftingService().submitJob(plan,null,cpu,false,source);
            if(!submitted.successful()) { fail("submit="+submitted.errorCode()); return; }
            state=3; executionStarted=System.nanoTime();
        } else {
            for(var iterator=delayed.iterator();iterator.hasNext();) {
                var entry=iterator.next();
                if(entry.due()>ticks) continue;
                pending.merge(entry.key(),entry.amount(),Math::addExact);
                iterator.remove();
            }
            if(current.name().equals("replan")&&!providerChanged&&dispatched.size()>1) {
                providers.removeProvider(node);
                patterns=List.of(patterns.get(0),pattern(new GenericStack[]{new GenericStack(intermediate,1)},
                        new GenericStack[]{new GenericStack(product,1),new GenericStack(water,3000)}));
                providers.addProvider(node);
                providerChanged=true;
                System.out.println("[Water Graph] changed effective downstream output after upstream and downstream work was accepted; replan required");
            }
            for(var iterator=pending.entrySet().iterator();iterator.hasNext();) {
                var entry=iterator.next();
                long accepted=grid.getStorageService().getInventory().insert(entry.getKey(),entry.getValue(),Actionable.MODULATE,source);
                if(accepted==entry.getValue()) iterator.remove(); else entry.setValue(entry.getValue()-accepted);
            }
            if(cpu.isBusy()||!pending.isEmpty()||!delayed.isEmpty()) {
                if(ticks%200==0) {
                    System.out.println("[Water Graph] EXEC PROGRESS engine="+(legacyExecuting?"MAX_FAST":"GRAPH")+" "+label()+
                            " ticks="+ticks+" dispatched_runs="+dispatched.values().stream().mapToLong(Long::longValue).sum()+" water_received="+waterReceived+" pending="+pending+" held="+held+" status="+cpu.getJobStatus());
                }
                return;
            }
            if(held.getOrDefault(raw,0L)!=0 || held.getOrDefault(product,0L)!=current.amount() || held.getOrDefault(intermediate,0L)!=0 ||
                    waterReceived!=current.amount()*1000 || grid.getStorageService().getCachedInventory().get(water)!=Long.MAX_VALUE)
                throw new AssertionError("Execution conservation failed "+held+" dispatched_water="+waterReceived);
            long downstream=dispatched.entrySet().stream().filter(e->!e.getKey().equals(patterns.get(0))).mapToLong(Map.Entry::getValue).sum();
            if(dispatched.getOrDefault(patterns.get(0),0L)!=current.amount()||downstream!=(current.unnecessary()?0:current.amount()))
                throw new AssertionError("Unexpected dispatched recipes "+dispatched);
            System.out.println("[Water Graph] EXEC PASS engine="+(legacyExecuting?"MAX_FAST":"GRAPH")+" "+label()+" product="+held.get(product)+
                    " raw_left=0 water_received="+waterReceived+" ticks="+ticks+" execute_ms="+(System.nanoTime()-executionStarted)/1e6+
                    " legacy_executor_calls="+(legacyExecuting?LegacyCalls.executor.get()-legacyCallsAtSubmit:0)+" pipeline_overlap="+pipelineOverlap);
            if(legacyExecuting&&LegacyCalls.executor.get()==legacyCallsAtSubmit) throw new AssertionError("Old executor was not exercised");
            if(legacyExecuting) next(); else { passed++; compareOld(); }
        }
    }

    private static String label() { return "case="+current.name()+" return_mB="+current.returned()+" amount="+current.amount()+" water_craftable="+current.waterFirst()+" cpu_lanes="+(cpu.getCoProcessors()+1); }
    private static void compareOld() {
        if(current.name().equals("replan")) { next(); return; }
        // Restore exactly the same finite starting stock, excluding runtime product.
        held.clear(); held.put(raw,current.amount()); pending.clear();
        delayed.clear(); pipelineOverlap=false;
        dispatched.clear(); waterReceived=0;
        grid.getStorageService().refreshGlobalStorageProvider(storageProvider);
        // AE posts inventory-cache updates at end tick. Give the old planner
        // exactly the same settled stock that the graph capture saw.
        baseline=null; state=5; ticks=0;
    }
    private static void fail(String reason) {
        if(legacyExecuting) {
            oldFailed++;
            System.out.println("[Water Graph] MAX_FAST EXEC FAIL "+label()+" "+reason);
            cpu.cancelJob(); next(); return;
        }
        failed++;
        System.out.println("[Water Graph] FAIL "+label()+" "+reason);
        if(request!=null&&!request.isDone()) request.cancel(true);
        if(cpu.isBusy()) ((GraphCpuAccess)cpu.craftingLogic).gtlcore$graphController().cancel();
        compareOld();
    }
}
