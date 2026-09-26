package org.gtlcore.gtlcore.integration.ae2.graph.core;

import java.math.BigInteger;
import java.util.*;
import static org.gtlcore.gtlcore.integration.ae2.graph.core.GeneralSearchReview.*;

public class CompletionReview {
    static void supports() {
        Random random=new Random(26092645);int cuts=0,checks=0;
        for(int test=0;test<500;test++) {
            List<GraphRecipe<String>> recipes=new ArrayList<>();
            for(int i=0;i<5;i++) {
                Map<String,Long> in=new HashMap<>(),out=new HashMap<>();
                in.put("f",1L);in.put("c"+random.nextInt(3),1L+random.nextInt(2));
                out.put("c"+random.nextInt(3),1L);if(random.nextBoolean())out.put("p",1L);
                recipes.add(r("r"+i,in,out));
            }
            var b=budget();var choice=List.of("c0","c1","c2");var base=Map.of("f",z(4));var loans=Map.of("c0",z(2),"c1",z(2),"c2",z(2));
            var support=new SeedSupport<>(recipes,choice,base,loans,Set.of(),Set.of(),"p",1,true,b);
            boolean[] feasible=new boolean[8];
            for(int mask=0;mask<8;mask++) {
                var initial=new HashMap<>(base);var goal=new HashMap<String,BigInteger>();goal.put("p",z(1));
                for(int i=0;i<3;i++)if((mask&(1<<i))!=0){initial.put(choice.get(i),z(2));goal.put(choice.get(i),z(2));}
                feasible[mask]=bfs(recipes,initial,goal);
            }
            for(int mask=0;mask<8;mask++) {
                BitSet selected=BitSet.valueOf(new long[]{mask});BitSet cut=support.conflict(selected);
                if(cut==null)continue;cuts++;check(support.verified(cut),"cut replay failed");
                for(int other=0;other<8;other++)if(!cut.intersects(BitSet.valueOf(new long[]{other}))){checks++;check(!feasible[other],"startup clause removed feasible seed set");}
            }
        }
        System.out.println("SUPPORT_CUTS models=500 cuts="+cuts+" independent_completions="+checks);
    }
    static void globalSeeds() {
        for(int groups:new int[]{4,8,12}) {
            var recipes=new ArrayList<GraphRecipe<String>>();var inputs=new LinkedHashMap<String,Long>();
            var stock=new LinkedHashMap<String,Long>();var loans=new LinkedHashMap<String,Long>();var steps=new ArrayList<String>();
            stock.put("f",(long)groups);
            for(int i=0;i<groups;i++) {
                recipes.add(r("out"+i,Map.of("a"+i,1L,"f",1L),Map.of("b"+i,1L,"x"+i,1L)));
                recipes.add(r("back"+i,Map.of("b"+i,1L),Map.of("a"+i,1L)));
                stock.put("a"+i,1L);stock.put("b"+i,1L);loans.put("a"+i,1L);loans.put("b"+i,1L);
                inputs.put("x"+i,1L);steps.add("out"+i);steps.add("back"+i);
            }
            recipes.add(r("finish",inputs,Map.of("p",1L)));steps.add("finish");
            var inc=plan(recipes,steps,loans,stock,1);var b=budget();
            try(var search=new SeedOptimization<>(new GraphCompiler<>(recipes),inc,stock,Map.of(),Set.of(),Set.of(),true,b)) {
                while(!search.step()){}var p=search.result();PlanVerifier.verify(p);
                check(p.seeds().size()==groups,"seed pairs: "+p.seeds()+" "+b.diagnostics());
                check(search.cardinalityProven(),"pair minimum not certified "+b.diagnostics());
                System.out.println("GLOBAL_SEEDS "+(2*groups)+"->"+p.seeds().size()+" proven="+search.cardinalityProven()+" work="+b.nodes());
            }
            check(b.reservedBytes()==0,"seed resources retained");
        }
    }
    static void recurrence() {
        for(long n:new long[]{1,1000000,Long.MAX_VALUE/14}) {
            var recipes=List.of(r("base",Map.of("c",1L,"f",1L),Map.of("b",1L)),r("middle",Map.of("b",2L),Map.of("c",7L)),r("finish",Map.of("b",10L),Map.of("p",1L)));
            var b=budget();var stock=Map.of("c",2L,"f",14*n);
            try(var model=RecipeCountModel.region(recipes,Map.of("p",z(n)),stock,Set.of(),b);
                var search=new CountSchedule<>(model,new BigInteger[]{z(n).multiply(z(14)),z(n).multiply(z(2)),z(n)},b)) {
                while(!search.step()){}check(search.result()==CountSchedule.Result.WITNESS,"interior recurrence missing");
                var by=new LinkedHashMap<String,GraphRecipe<String>>();recipes.forEach(r->by.put(r.id(),r));
                var p=new GraphPlan<>("p",n,true,search.witness(),by,stock,Map.of("c",2L),Map.of(),GraphPlan.Result.FEASIBLE,0,0);
                PlanVerifier.verify(p);check(b.diagnostics().toString().contains("count_recurrence"),"not compiled interior cut");
                check(p.patternTimesExact().get("base").equals(z(n).multiply(z(14))),"counts changed");
                System.out.println("INTERIOR_CUT n="+n+" work="+b.nodes());
            }
            check(b.reservedBytes()==0,"schedule resources retained");
        }
    }
    static void allocation() {
        Random random=new Random(261545);int reached=0;
        for(int test=0;test<500;test++) {
            var rs=new ArrayList<GraphRecipe<String>>();var stock=new LinkedHashMap<String,Long>();
            for(int i=0;i<5;i++)stock.merge("k"+random.nextInt(4),1L,Long::sum);
            for(int i=0;i<6;i++) {
                var in=new HashMap<String,Long>();var out=new HashMap<String,Long>();
                for(int k=0;k<1+random.nextInt(2);k++){in.merge("k"+random.nextInt(4),1L,Long::sum);out.merge("k"+random.nextInt(4),1L,Long::sum);}
                rs.add(r("r"+i,in,out));
            }
            long amount=1+random.nextInt(4);boolean ref=bfs(rs,exact(stock),Map.of("k3",z(amount)));var b=budget();
            var search=new AllocationSearch<>(new GraphCompiler<>(rs),"k3",amount,stock,Set.of(),Map.of(),false,false,Set.of(),b,System.nanoTime());
            while(!search.step()){}var p=search.result();search.discard();
            check(!ref||p!=null,"allocation missed finite witness "+test);
            if(p!=null){reached++;check(ref,"allocation false witness");PlanVerifier.verify(p);}
            check(b.reservedBytes()==0,"allocation resources retained "+b.reservedBytes());
        }
        System.out.println("ALLOCATION_SLEEP models=500 reached="+reached+" misses=0");
    }
    static void continuations() throws Exception {
        var rs=List.of(r("out",Map.of("a",1L,"f",1L),Map.of("b",1L,"p",1L)),r("back",Map.of("b",1L),Map.of("a",1L)));
        var b=budget();int pauses=0;long previous=0;
        var field=IntegerCountSearch.class.getDeclaredField("allowance");field.setAccessible(true);
        var work=IntegerCountSearch.class.getDeclaredField("work");work.setAccessible(true);
        try(var search=new IntegerCountSearch<>(new GraphCompiler<>(rs),"p",1000000000L,Map.of("a",1L,"f",1000000000L),Map.of("a",1L),Set.of(),Set.of(),true,true,b,System.nanoTime())) {
            field.setLong(search,1);
            while(true) {
                while(!search.step()){}
                check(b.nodes()>=previous,"work refunded");previous=b.nodes();
                if(!search.paused())break;
                pauses++;check(!search.infeasible(),"paused search called infeasible");
                search.resume();field.setLong(search,work.getLong(search)+1);
                check(pauses<10000,"continuation did not progress");
            }
            check(search.result()!=null,"lost retained count witness");PlanVerifier.verify(search.result());
        }
        check(pauses>3,"no forced suspensions");check(b.reservedBytes()==0,"paused workspace leak "+b.reservedBytes());
        System.out.println("CONTINUATIONS forced_pauses="+pauses+" cumulative_work="+b.nodes()+" long_order_verified=true");
    }
    public static void main(String[]args)throws Exception{supports();globalSeeds();recurrence();allocation();continuations();}
}
