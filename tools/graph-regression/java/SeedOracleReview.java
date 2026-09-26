package org.gtlcore.gtlcore.integration.ae2.graph.core;
import java.util.*;
import java.math.BigInteger;
import static org.gtlcore.gtlcore.integration.ae2.graph.core.GeneralSearchReview.*;

public class SeedOracleReview {
 public static void main(String[]args){var rng=new Random(261426);int proven=0,amounts=0,improved=0;
  for(int test=0;test<400;test++){
   int[]unit={1+rng.nextInt(3),1+rng.nextInt(3),1+rng.nextInt(3)};var rs=new ArrayList<GraphRecipe<String>>();var stock=new LinkedHashMap<String,Long>();var loans=new LinkedHashMap<String,Long>();
   for(int i=0;i<3;i++){stock.put("c"+i,(long)(unit[i]+rng.nextInt(3)));loans.put("c"+i,stock.get("c"+i));rs.add(r("r"+i,Map.of("c"+i,(long)unit[i],"f",1L),Map.of("c"+((i+1)%3),(long)unit[(i+1)%3],"p",1L)));}
   stock.put("f",3L+rng.nextInt(4));
   for(int i=0;i<3;i++)if(rng.nextBoolean())rs.add(r("boot"+i,Map.of("f",1L+rng.nextInt(3)),Map.of("c"+i,(long)unit[i])));
   // Extra sources join resource choices, but each firing uses finite fuel.
   if(rng.nextBoolean())rs.add(r("both",Map.of("c0",(long)unit[0],"c1",(long)unit[1],"f",1L),Map.of("c0",(long)unit[0],"c1",(long)unit[1],"p",2L)));
   var inc=plan(rs,List.of("r0","r1","r2"),loans,stock,3);int ref=4;
   for(int mask=0;mask<8;mask++){var initial=new HashMap<String,BigInteger>();var goal=new HashMap<String,BigInteger>();initial.put("f",z(stock.get("f")));goal.put("p",z(3));for(int i=0;i<3;i++)if((mask&(1<<i))!=0){initial.put("c"+i,z(stock.get("c"+i)));goal.put("c"+i,z(stock.get("c"+i)));}if(bfs(rs,initial,goal))ref=Math.min(ref,Integer.bitCount(mask));}
   var b=budget();try(var opt=new SeedOptimization<>(new GraphCompiler<>(rs),inc,stock,Map.of(),Set.of(),Set.of(),true,b)){
    while(!opt.step()){}var got=opt.result();PlanVerifier.verify(got);check(got.seeds().size()>=ref,"false seed objective "+test);
    if(opt.cardinalityProven()){proven++;check(got.seeds().size()==ref,"FALSE minimum "+test+" ref="+ref+" got="+got.seeds()+" "+b.diagnostics());}
    if(got.seeds().size()<3)improved++;
    if(opt.amountsProven()){
     amounts++;for(var e:got.seeds().entrySet())if(e.getValue()>1){var initial=new HashMap<String,BigInteger>();var goal=new HashMap<String,BigInteger>();initial.put("f",z(stock.get("f")));goal.put("p",z(3));got.seeds().forEach((k,v)->{long count=v-(k.equals(e.getKey())?1:0);initial.put(k,z(count));goal.put(k,z(count));});check(!bfs(rs,initial,goal),"FALSE quantity minimum "+test+" "+got.seeds());}
    }
   }check(b.reservedBytes()==0,"seed oracle memory");
  }System.out.println("SEED_ORACLE cases=400 improved="+improved+" cardinality_proven="+proven+" amounts_proven="+amounts+" wrong_proofs=0");
  long n=Long.MAX_VALUE;var rs=List.of(r("out",Map.of("a",1L,"f",1L),Map.of("b",1L,"p",1L)),r("back",Map.of("b",1L),Map.of("a",1L)));var by=new LinkedHashMap<String,GraphRecipe<String>>();rs.forEach(r->by.put(r.id(),r));var body=new PlanStep.Sequence(List.of(new PlanStep.Batch("out",1),new PlanStep.Batch("back",1)));var steps=PlanStep.repeat(body,z(n));var stock=Map.of("a",1L,"b",1L,"f",n);var inc=new GraphPlan<>("p",n,true,steps,by,stock,Map.of("a",1L,"b",1L),Map.of(),GraphPlan.Result.FEASIBLE,0,0);PlanVerifier.verify(inc);var b=budget();
  try(var opt=new SeedOptimization<>(new GraphCompiler<>(rs),inc,stock,Map.of(),Set.of(),Set.of(),true,b)){while(!opt.step()){}var got=opt.result();check(got.seeds().size()==1,"long seed count");check(opt.cardinalityProven(),"long cardinality proof "+b.diagnostics());PlanVerifier.verify(got);check(got.patternTimesExact().values().stream().anyMatch(x->x.compareTo(z(n))>=0),"long compressed work");System.out.println("LONG_SEEDS "+got.seeds()+" "+b.diagnostics());}
 }
}
