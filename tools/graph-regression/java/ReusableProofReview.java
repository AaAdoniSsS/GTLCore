package org.gtlcore.gtlcore.integration.ae2.graph.core;

import java.math.BigInteger;
import java.util.*;
import static org.gtlcore.gtlcore.integration.ae2.graph.core.GeneralSearchReview.*;

public final class ReusableProofReview {
    static GraphRecipe<String> read(GraphRecipe<String> recipe, String token, long amount) {
        var slots=new ArrayList<>(recipe.slots());
        slots.add(new GraphRecipe.Slot<>(token,amount,-1,true,true));
        var out=new LinkedHashMap<>(recipe.outputs());out.merge(token,amount,Long::sum);
        return new GraphRecipe<>(recipe.id(),recipe.binding(),slots,out);
    }
    public static void main(String[]args) {
        var random=new Random(2609261645L);int reached=0,closed=0,scheduled=0,dead=0;
        for(int test=0;test<600;test++) {
            var recipes=new ArrayList<GraphRecipe<String>>();
            var stock=new LinkedHashMap<String,Long>();
            for(int i=0;i<3;i++) stock.put("k"+i,(long)random.nextInt(3));
            stock.put("token",(long)random.nextInt(3));
            int[] counts=new int[3+random.nextInt(4)];
            for(int i=0;i<counts.length;i++) {
                var in=new LinkedHashMap<String,Long>();var out=new LinkedHashMap<String,Long>();
                for(int j=0,n=1+random.nextInt(2);j<n;j++) {in.merge("k"+random.nextInt(3),1L,Long::sum);out.merge("k"+random.nextInt(3),1L,Long::sum);}
                var recipe=r("r"+i,in,out);
                recipes.add(random.nextBoolean()?read(recipe,"token",1+random.nextInt(2)):recipe);
                counts[i]=random.nextInt(3);
            }
            for(int i=0;i<3;i++) recipes.add(r("id"+i,Map.of("k"+i,1L),Map.of("k"+i,1L)));
            recipes.add(r("tokenId",Map.of("token",1L),Map.of("token",1L)));
            counts=Arrays.copyOf(counts,recipes.size());
            var initial=exact(stock);var goal=Map.of("k2",z(1+random.nextInt(3)));
            boolean ref=bfs(recipes,initial,goal);var budget=budget();
            try(var back=new BackwardCoverability<>(recipes,initial,goal,Set.of(),List.of(),budget,500_000)) {
                while(!back.step()) {}
                if(back.result()==BackwardCoverability.Result.WITNESS) {
                    reached++;check(ref,"false read-arc witness");
                    var by=new LinkedHashMap<String,GraphRecipe<String>>();recipes.forEach(r->by.put(r.id(),r));
                    var held=new LinkedHashMap<>(initial);replay(back.witness(),by,held);check(covers(held,goal),"read-arc target");
                } else {closed++;check(back.result()==BackwardCoverability.Result.CLOSED&&!ref,"false read-arc closed");}
            }
            boolean sched=schedule(recipes,initial,counts,new HashSet<>());
            try(var model=RecipeCountModel.region(recipes,Map.of(),stock,Set.of(),budget);
                var order=new CountSchedule<>(model,Arrays.stream(counts).mapToObj(GeneralSearchReview::z).toArray(BigInteger[]::new),budget)) {
                forceExact(order);while(!order.step()){}
                check((order.result()==CountSchedule.Result.WITNESS)==sched,"read-arc order mismatch");
                if(sched){scheduled++;var by=new LinkedHashMap<String,GraphRecipe<String>>();recipes.forEach(r->by.put(r.id(),r));replay(order.witness(),by,new LinkedHashMap<>(initial));}
                else{dead++;check(order.result()==CountSchedule.Result.DEAD,"read-arc dead not proved");}
            }
            check(budget.reservedBytes()==0,"read-arc memory");
        }
        System.out.println("READ_ARCS cases=600 reach="+reached+" closed="+closed+" scheduled="+scheduled+" dead="+dead+" wrong_proofs=0");
        var recipes=List.of(read(r("a",Map.of("f",1L),Map.of("x",1L)),"a",1),
            read(r("b",Map.of("f",1L),Map.of("y",1L)),"b",1),
            r("p",Map.of("x",1L,"y",1L),Map.of("p",1L)),
            read(r("alt",Map.of("f",2L),Map.of("p",1L)),"c",1));
        var stock=Map.of("a",1L,"b",1L,"c",1L,"f",2L);
        var incumbent=plan(recipes,List.of("a","b","p"),Map.of("a",1L,"b",1L),stock,1);var budget=budget();
        try(var opt=new SeedOptimization<>(new GraphCompiler<>(recipes),incumbent,stock,Map.of(),Set.of(),Set.of(),true,budget)) {
            while(!opt.step()){}
            var p=opt.result();check(p.seeds().equals(Map.of("c",1L)),"reusable global minimum "+p.seeds());
            check(opt.cardinalityProven()&&opt.amountsProven(),"reusable optimality not proved "+budget.diagnostics());PlanVerifier.verify(p);
        }
        var once=read(r("long",Map.of("f",1L),Map.of("p",1L)),"token",1);budget=budget();
        try(var search=new BackwardCoverability<>(List.of(once),Map.of("f",z(Long.MAX_VALUE),"token",z(1)),Map.of("p",z(Long.MAX_VALUE)),Set.of(),List.of(),budget,10000)) {
            while(!search.step()){}check(search.result()==BackwardCoverability.Result.WITNESS,"long read token");
            var sum=SequenceSummary.of(search.witness(),Map.of("long",once));check(sum.required("token").equals(z(1)),"long token scaled");check(sum.delta("token").signum()==0,"long token consumed");
        }
        var config=new GraphRecipe<>("config","config",List.of(new GraphRecipe.Slot<>("card",1,-1,true),new GraphRecipe.Slot<>("f",1)),Map.of("p",1L));
        budget=budget();try(var s=new BackwardCoverability<>(List.of(config),Map.of("card",z(1),"f",z(8)),Map.of("p",z(8)),Set.of(),List.of(),budget,10000)) {
            while(!s.step()){}check(s.result()==BackwardCoverability.Result.UNKNOWN,"per-push input incorrectly proved impossible");
        }
        System.out.println("REUSABLE_SEEDS 2->1 certified; LONG token=1; consumed configuration remains UNKNOWN");
        budget=budget();try(var proofs=new OrderProofs<String>(null,budget)){
            proofs.learnSource(Map.of("x",0,"y",2));
            check(proofs.sourceConflict(Map.of("y",2))==null,"partial assignment inferred absent choice");
            check(proofs.sourceConflict(Map.of("y",2),true)!=null,"default source not recognized before compile");
            check(proofs.sourceConflict(Map.of("x",1,"y",2),true)==null,"different source forbidden");
            check(proofs.sourceConflict(Map.of("y",1),true)==null,"different co-choice forbidden");
        }
        System.out.println("SOURCE precompile matching distinguishes omitted defaults from partial assignments");
    }
}
