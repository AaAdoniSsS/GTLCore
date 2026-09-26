package org.gtlcore.gtlcore.integration.ae2.graph.core;
import java.util.*;
import java.util.concurrent.*;
import java.math.BigInteger;
import static org.gtlcore.gtlcore.integration.ae2.graph.core.GeneralSearchReview.*;
public class SeedThreadReview {
 public static void main(String[]args)throws Exception{for(int width:new int[]{1,4,8,16}){
  var jobs=new ArrayList<CompletableFuture<GraphPlan<String>>>();var budgets=new ArrayList<PlanningBudget>();var threads=ConcurrentHashMap.<String>newKeySet();
  try(var scheduler=new PlanningScheduler(width,32,1,1_000_000)){
   for(int id=0;id<12;id++){long n=id%2==0?Long.MAX_VALUE:123456789L;var rs=List.of(r("out",Map.of("a",1L,"f",1L),Map.of("b",1L,"p",1L)),r("back",Map.of("b",1L),Map.of("a",1L)));var by=new LinkedHashMap<String,GraphRecipe<String>>();rs.forEach(r->by.put(r.id(),r));var body=new PlanStep.Sequence(List.of(new PlanStep.Batch("out",1),new PlanStep.Batch("back",1)));var stock=Map.of("a",1L,"b",1L,"f",n);var inc=new GraphPlan<>("p",n,true,PlanStep.repeat(body,z(n)),by,stock,Map.of("a",1L,"b",1L),Map.of(),GraphPlan.Result.FEASIBLE,0,0);var b=budget();budgets.add(b);var opt=new SeedOptimization<>(new GraphCompiler<>(rs),inc,stock,Map.of(),Set.of(),Set.of(),true,b);
    jobs.add(scheduler.submit(new PlanningScheduler.Work<GraphPlan<String>>(){public boolean advance(PlanningScheduler.Slice slice){threads.add(Thread.currentThread().getName());while(slice.next())if(opt.step())return true;return false;}public GraphPlan<String> result(){return opt.result();}public void close(){opt.close();}},b));
   }
   for(var job:jobs){var p=job.get(15,TimeUnit.SECONDS);check(p.seeds().size()==1,"threaded long seeds");check(p.seedOptimality()!=null&&p.seedOptimality().cardinalityProven()&&p.seedOptimality().quantitiesParetoProven(),"lost thread migration proof");PlanVerifier.verify(p);}
  }
  for(var b:budgets)check(b.reservedBytes()==0,"thread seed memory");System.out.println("SEED THREAD workers="+width+" orders=12 used_threads="+threads.size()+" long counts, proof flags, memory release passed");
 }}
}
