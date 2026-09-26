package org.gtlcore.gtlcore.integration.ae2.graph.core;

import java.math.BigInteger;
import java.util.*;

public final class FusedOracle {
    static BigInteger b(long n) { return BigInteger.valueOf(n); }
    static PlanningBudget budget() { return new PlanningBudget(0, 10_000_000, 128L<<20, ()->false, System::nanoTime); }
    static boolean valid(List<ExactLinearProgram.Constraint> rows, BigInteger[] x) {
        for (var row:rows) {
            BigInteger sum=BigInteger.ZERO;
            for(var e:row.terms().entrySet()) sum=sum.add(e.getValue().multiply(x[e.getKey()]));
            if(sum.compareTo(row.upper())>0)return false;
        }
        return true;
    }
    static List<ExactLinearProgram.Constraint> bounded(List<ExactLinearProgram.Constraint> rows,BigInteger[] lo,BigInteger[] hi){
        var all=new ArrayList<>(rows);
        for(int i=0;i<lo.length;i++){
            all.add(new ExactLinearProgram.Constraint(Map.of(i,b(-1)),lo[i].negate()));
            if(hi[i]!=null)all.add(new ExactLinearProgram.Constraint(Map.of(i,b(1)),hi[i]));
        }return all;
    }
    static void integerModels(){
        var random=new Random(26092641); int feasible=0,components=0,conditioned=0,proofs=0;long solutions=0;
        for(int test=0;test<2400;test++){
            int n=4+random.nextInt(3),width=test%4==0?1:2,states=1;
            BigInteger[] lo=new BigInteger[n],hi=new BigInteger[n];
            for(int i=0;i<n;i++){lo[i]=test%9==0?b(Long.MAX_VALUE).add(b(i%2)):b(test%4==0?0:random.nextInt(2));hi[i]=lo[i].add(b(width));states*=width+1;}
            var rows=new ArrayList<ExactLinearProgram.Constraint>();
            for(int r=0;r<2+random.nextInt(7);r++){
                Map<Integer,BigInteger> terms=new LinkedHashMap<>();BigInteger rhs=b(random.nextInt(15)-5);
                for(int i=0;i<n;i++){
                    if(test%3==0 && i/2!=r%(n/2))continue;
                    int coefficient=random.nextInt(7)-3;
                    if(test%3==1 && i%2==1)coefficient=terms.getOrDefault(i-1,BigInteger.ZERO).intValue();
                    if(coefficient!=0){terms.put(i,b(coefficient));rhs=rhs.add(b(coefficient).multiply(lo[i]));}
                }
                rows.add(new ExactLinearProgram.Constraint(terms,rhs));
            }
            if(test%4==0)for(int i=0;i+1<n;i+=2)rows.add(new ExactLinearProgram.Constraint(Map.of(i,b(1),i+1,b(1)),b(1)));
            var all=bounded(rows,lo,hi);var budget=budget();
            try(var red=new CountReduction(all,lo,hi,budget);var comp=new CountComponents(rows,lo,hi,budget);
                var cond=new CountConditioning(rows,lo,hi,null,budget);var quick=new CountQuickSolve(rows,lo,hi,budget,test%2==0)){
                while(!red.step()){}while(!comp.step()){}while(!cond.step()){}while(!quick.step()){}
                boolean possible=false;
                for(int code=0;code<states;code++){
                    int c=code;var x=lo.clone();for(int i=0;i<n;i++){x[i]=x[i].add(b(c%(width+1)));c/=width+1;}
                    if(!valid(all,x))continue;possible=true;solutions++;
                    var projected=Arrays.stream(red.representatives()).mapToObj(i->x[i]).toArray(BigInteger[]::new);
                    if(!valid(red.rows(),projected)||!Arrays.equals(x,red.expand(projected)))throw new AssertionError("Invalid reduction test="+test+" "+rows);
                }
                if((comp.infeasible()||quick.infeasible())&&possible)throw new AssertionError("False proof "+test);
                for(var answer:new BigInteger[][]{comp.counts(),cond.counts(),quick.counts()})if(answer!=null&&!valid(all,answer))throw new AssertionError("Invalid lifting "+test);
                if(possible)feasible++;if(comp.counts()!=null)components++;if(cond.counts()!=null)conditioned++;if(comp.infeasible()||quick.infeasible())proofs++;
            }
            if(budget.reservedBytes()!=0)throw new AssertionError("Leak "+test+" "+budget.reservedBytes());
        }
        System.out.println("PASS integer_models=2400 feasible="+feasible+" checked_solutions="+solutions+" component_witnesses="+components+" conditioned_witnesses="+conditioned+" checked_proofs="+proofs);
    }
    static GraphRecipe<String> r(String name,Map<String,Long> in,Map<String,Long> out){return new GraphRecipe<>(name,name,in.entrySet().stream().map(e->new GraphRecipe.Slot<>(e.getKey(),e.getValue())).toList(),out);}
    static boolean reachable(List<GraphRecipe<String>> recipes,Map<String,Long> stock,long goal){
        Set<String> all=new TreeSet<>(stock.keySet());all.add("G");for(var r:recipes){all.addAll(r.inputs().keySet());all.addAll(r.outputs().keySet());}
        List<String> keys=new ArrayList<>(all);int g=keys.indexOf("G");var todo=new ArrayDeque<List<Long>>();var seen=new HashSet<List<Long>>();
        var initial=keys.stream().map(k->stock.getOrDefault(k,0L)).toList();todo.add(initial);seen.add(initial);
        while(!todo.isEmpty()){
            var state=todo.removeFirst();if(state.get(g)>=goal)return true;
            for(var r:recipes){var next=new ArrayList<Long>();boolean enabled=true;
                for(int i=0;i<keys.size();i++){var k=keys.get(i);long need=r.inputs().getOrDefault(k,0L);if(state.get(i)<need){enabled=false;break;}next.add(state.get(i)-need+r.outputs().getOrDefault(k,0L));}
                if(enabled&&seen.add(next))todo.addLast(next);
            }
            if(seen.size()>200000)throw new AssertionError("Oracle unexpectedly large");
        }return false;
    }
    static void recovery(){
        var rng=new Random(26092651);int yes=0,miss=0,macros=0;
        for(int sample=0;sample<400;sample++){
            int n=2+rng.nextInt(2);long cat=1+rng.nextInt(2),cost=1+rng.nextInt(3);
            var recipes=new ArrayList<GraphRecipe<String>>();Map<String,Long> stock=new LinkedHashMap<>();
            stock.put("CAT",(long)rng.nextInt(4));stock.put("F",(long)rng.nextInt(10));
            for(int i=0;i<n;i++){
                String raw=sample%7==0?"U":"U"+i,mid="P"+i;stock.put(raw,1L+rng.nextInt(3));long yield=1+rng.nextInt(2);
                recipes.add(r("s"+i,Map.of(raw,1L,"CAT",cat),Map.of(mid,1L)));
                recipes.add(r("r"+i,Map.of(mid,1L,"F",cost),Map.of("G",yield,"CAT",cat)));
                recipes.add(r("b"+i,Map.of(mid,1L),Map.of("G",yield)));
            }
            if(sample%11==0)stock.put("P0",1L);
            if(sample%13==0){stock.put("BONUS",1L);recipes.add(r("extra",Map.of("BONUS",1L),Map.of("CAT",cat)));}
            if(sample%17==0){stock.put("Z",1L);recipes.add(r("pending_source",Map.of("Z",1L),Map.of("P0",1L)));}
            long goal=1+rng.nextInt(8);boolean oracle=reachable(recipes,stock,goal);if(oracle)yes++;
            var budget=budget();GraphPlan<String> plan;
            try(var s=new IntegerCountSearch<>(new GraphCompiler<>(recipes),"G",goal,stock,Map.of(),Set.of(),Set.of(),false,true,budget,System.nanoTime())){
                while(!s.step()){ }plan=s.result();if(s.infeasible()&&oracle)throw new AssertionError("False recovery proof "+sample);
                if(plan!=null&&plan.feasible()){
                    if(!oracle)throw new AssertionError("False recovery acceptance "+sample);
                    var inventory=new HashMap<>(stock);
                    // The tiny oracle independently replays every original leaf.
                    replay(plan.steps(),plan.recipes(),inventory);
                    if(inventory.getOrDefault("G",0L)<goal)throw new AssertionError("Goal "+sample);
                }else if(oracle)miss++;
            }
            if(budget.diagnostics().toString().contains("lifted_witness"))macros++;
            if(budget.reservedBytes()!=0)throw new AssertionError("Recovery leak "+sample+" "+budget.reservedBytes());
        }
        System.out.println("PASS recovery_bfs=400 feasible="+yes+" unresolved="+miss+" macro_witnesses="+macros);
    }
    static void replay(PlanStep s,Map<String,GraphRecipe<String>> recipes,Map<String,Long> held){
        if(s instanceof PlanStep.Batch batch){var r=recipes.get(batch.recipe());if(r==null)throw new AssertionError("Synthetic recipe escaped");for(long t=0;t<batch.runs();t++){for(var e:r.inputs().entrySet()){if(held.getOrDefault(e.getKey(),0L)<e.getValue())throw new AssertionError("Prefix "+r);held.merge(e.getKey(),-e.getValue(),Long::sum);}r.outputs().forEach((k,v)->held.merge(k,v,Long::sum));}}
        else if(s instanceof PlanStep.Repeat repeat){if(repeat.times()>1000)throw new AssertionError("Tiny program exploded");for(long t=0;t<repeat.times();t++)replay(repeat.body(),recipes,held);}
        else for(var child:((PlanStep.Sequence)s).children())replay(child,recipes,held);
    }
    static void cancellation(){
        var recipes=List.of(r("s0",Map.of("U",1L,"CAT",1L),Map.of("P0",1L)),r("r0",Map.of("P0",1L),Map.of("G",1L,"CAT",1L)),r("s1",Map.of("V",1L,"CAT",1L),Map.of("P1",1L)),r("r1",Map.of("P1",1L),Map.of("G",2L,"CAT",1L)));
        int tries=0;
        for(long work:new long[]{1,10,50,100,200,400,800,1200,2000,4000,8000})for(long memory:new long[]{65536,1L<<20,16L<<20,128L<<20}){
            var b=new PlanningBudget(0,work,memory,()->false,System::nanoTime);
            try(var s=new IntegerCountSearch<>(new GraphCompiler<>(recipes),"G",99,Map.of("U",100L,"V",100L,"CAT",1L),Map.of(),Set.of(),Set.of(),false,true,b,System.nanoTime())){while(!s.step()){} }
            catch(PlanningBudget.Exhausted expected){}
            if(b.reservedBytes()!=0)throw new AssertionError("Cancellation leak work="+work+" memory="+memory+" reserved="+b.reservedBytes());tries++;
        }
        System.out.println("PASS cancellation="+tries);
    }
    static void groupedModels(){
        Random rng=new Random(26092671);int witnesses=0,proofs=0;long checked=0;
        for(int test=0;test<500;test++){
            int groups=2,options=test%2==0?3:2,n=groups*options,states=1;
            BigInteger[] low=new BigInteger[n],high=new BigInteger[n];int[] capacities={1+rng.nextInt(3),1+rng.nextInt(3)};
            Arrays.fill(low,b(0));List<ExactLinearProgram.Constraint> rows=new ArrayList<>();
            boolean exact=test%3!=0;
            for(int g=0;g<groups;g++){
                Map<Integer,BigInteger> pos=new LinkedHashMap<>(),neg=new LinkedHashMap<>();
                for(int o=0;o<options;o++){int id=g*options+o;pos.put(id,b(1));neg.put(id,b(-1));high[id]=b(capacities[g]);states*=capacities[g]+1;}
                rows.add(new ExactLinearProgram.Constraint(pos,b(capacities[g])));
                if(exact)rows.add(new ExactLinearProgram.Constraint(neg,b(-capacities[g])));
            }
            for(int r=0;r<2;r++){
                Map<Integer,BigInteger> terms=new LinkedHashMap<>();
                for(int o=0;o<options;o++){int coefficient=rng.nextInt(9)-4;for(int g=0;g<groups;g++)if(coefficient!=0)terms.put(g*options+o,b(coefficient));}
                rows.add(new ExactLinearProgram.Constraint(terms,b(rng.nextInt(21)-7)));
            }
            if(test%11==0)rows.add(new ExactLinearProgram.Constraint(Map.of(0,b(1)),b(0)));
            var all=bounded(rows,low,high);boolean possible=false;
            for(int code=0;code<states;code++){
                int c=code;var x=low.clone();for(int i=0;i<n;i++){int size=high[i].intValue()+1;x[i]=b(c%size);c/=size;}
                if(valid(all,x)){possible=true;checked++;}
            }
            var budget=budget();
            try(var grouped=new CountGroups(rows,low,high,budget);var match=new CountMeetInMiddle(rows,low,high,budget)){
                while(!grouped.step()){}while(!match.step()){}
                if((grouped.infeasible()||match.infeasible())&&possible)throw new AssertionError("False group proof "+test);
                if(grouped.infeasible())proofs++;
                if(grouped.counts()!=null){if(!valid(all,grouped.counts()))throw new AssertionError("Invalid group witness "+test);witnesses++;}
                if(match.counts()!=null&&!valid(all,match.counts()))throw new AssertionError("Invalid simplex witness "+test);
                if(possible&&match.counts()==null)throw new AssertionError("Unsolved tiny simplex "+test);
            }
            if(budget.reservedBytes()!=0)throw new AssertionError("Group leak "+test);
        }
        // Mathematical aggregate exceeds long; restoring two per-group longs must stay exact.
        BigInteger cap=b(Long.MAX_VALUE),twice=cap.multiply(b(2));
        var rows=List.of(new ExactLinearProgram.Constraint(Map.of(0,b(1),1,b(1)),cap),new ExactLinearProgram.Constraint(Map.of(2,b(1),3,b(1)),cap),
            new ExactLinearProgram.Constraint(Map.of(0,b(-1),1,b(-1)),cap.negate()),new ExactLinearProgram.Constraint(Map.of(2,b(-1),3,b(-1)),cap.negate()),
            new ExactLinearProgram.Constraint(Map.of(0,b(-1),2,b(-1)),twice.negate()));
        var budget=budget();try(var grouped=new CountGroups(rows,new BigInteger[]{b(0),b(0),b(0),b(0)},new BigInteger[]{cap,cap,cap,cap},budget)){
            while(!grouped.step()){}if(grouped.counts()==null||!valid(rows,grouped.counts()))throw new AssertionError("Wide aggregate");
        }
        if(budget.reservedBytes()!=0)throw new AssertionError("Wide group leak");
        System.out.println("PASS grouped_models=500 witnesses="+witnesses+" checked_infeasible="+proofs+" exact_solutions="+checked+" above_long=1");
    }
    public static void main(String[] args){integerModels();groupedModels();recovery();cancellation();}
}
