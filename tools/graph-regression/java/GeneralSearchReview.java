package org.gtlcore.gtlcore.integration.ae2.graph.core;
import java.math.BigInteger;
import java.util.*;

public class GeneralSearchReview {
 static BigInteger z(long n){return BigInteger.valueOf(n);}
 static PlanningBudget budget(){return new PlanningBudget(0,10_000_000,256L<<20,()->false,System::nanoTime);}
 static GraphRecipe<String> r(String id,Map<String,Long> in,Map<String,Long> out){return new GraphRecipe<>(id,id,in.entrySet().stream().map(e->new GraphRecipe.Slot<>(e.getKey(),e.getValue())).toList(),out);}
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 static Map<String,BigInteger> exact(Map<String,Long> x){var y=new HashMap<String,BigInteger>();x.forEach((k,v)->y.put(k,z(v)));return y;}
 static Map<String,BigInteger> fire(Map<String,BigInteger> s,GraphRecipe<String> r){
  for(var e:r.inputs().entrySet())if(s.getOrDefault(e.getKey(),z(0)).compareTo(z(e.getValue()))<0)return null;
  var n=new HashMap<>(s);r.inputs().forEach((k,v)->n.merge(k,z(-v),BigInteger::add));r.outputs().forEach((k,v)->n.merge(k,z(v),BigInteger::add));n.values().removeIf(v->v.signum()==0);return n;
 }
 static boolean covers(Map<String,BigInteger>s,Map<String,BigInteger>g){return g.entrySet().stream().allMatch(e->s.getOrDefault(e.getKey(),z(0)).compareTo(e.getValue())>=0);}
 static boolean bfs(List<GraphRecipe<String>> rs,Map<String,BigInteger> initial,Map<String,BigInteger> goal){
  var seen=new HashSet<Map<String,BigInteger>>();var todo=new ArrayDeque<Map<String,BigInteger>>();var first=new HashMap<>(initial);first.values().removeIf(n->n.signum()==0);seen.add(first);todo.add(first);
  while(!todo.isEmpty()){var s=todo.removeFirst();if(covers(s,goal))return true;for(var r:rs){var n=fire(s,r);if(n!=null&&seen.add(n))todo.add(n);}check(seen.size()<100_000,"oracle must remain finite");}return false;
 }
 static boolean schedule(List<GraphRecipe<String>>rs,Map<String,BigInteger>s,int[]left,Set<String> seen){
  if(Arrays.stream(left).allMatch(n->n==0))return true;String key=Arrays.toString(left);if(!seen.add(key))return false;
  for(int i=0;i<left.length;i++)if(left[i]>0){var n=fire(s,rs.get(i));if(n==null)continue;left[i]--;if(schedule(rs,n,left,seen)){left[i]++;return true;}left[i]++;}return false;
 }
 static void replay(PlanStep p,Map<String,GraphRecipe<String>>rs,Map<String,BigInteger>held){
  if(p instanceof PlanStep.Batch b){for(long j=0;j<b.runs();j++){var n=fire(held,rs.get(b.recipe()));check(n!=null,"bad execution "+b);held.clear();held.putAll(n);}}
  else if(p instanceof PlanStep.Repeat r){for(long j=0;j<r.times();j++)replay(r.body(),rs,held);}
  else for(var c:((PlanStep.Sequence)p).children())replay(c,rs,held);
 }
 static void forceExact(CountSchedule<?> schedule){try{var f=CountSchedule.class.getDeclaredField("attempt");f.setAccessible(true);f.setInt(schedule,100);var m=CountSchedule.class.getDeclaredMethod("nextAttempt");m.setAccessible(true);m.invoke(schedule);}catch(Exception e){throw new RuntimeException(e);}}
 static void fixedSchedules(){var random=new Random(26112026);int dead=0,found=0;
  for(int test=0;test<3000;test++){
   var rs=new ArrayList<GraphRecipe<String>>();int[]counts=new int[3+random.nextInt(5)];
   var stock=new HashMap<String,Long>();for(int i=0;i<4;i++)stock.put("k"+i,(long)random.nextInt(4));
   for(int i=0;i<counts.length;i++){var in=new HashMap<String,Long>();var out=new HashMap<String,Long>();for(int j=0,n=1+random.nextInt(3);j<n;j++){in.merge("k"+random.nextInt(4),1L,Long::sum);out.merge("k"+random.nextInt(4),1L,Long::sum);}rs.add(r("r"+i,in,out));counts[i]=random.nextInt(3);}
   // Identity recipes keep every resource internal in the region model.
   for(int i=0;i<4;i++)rs.add(r("id"+i,Map.of("k"+i,1L),Map.of("k"+i,1L)));
   counts=Arrays.copyOf(counts,rs.size());boolean ref=schedule(rs,exact(stock),counts,new HashSet<>());var b=budget();
   try(var model=RecipeCountModel.region(rs,Map.of(),stock,Set.of(),b);var s=new CountSchedule<>(model,Arrays.stream(counts).mapToObj(GeneralSearchReview::z).toArray(BigInteger[]::new),b)){
    forceExact(s);
    while(!s.step()){}check((s.result()==CountSchedule.Result.WITNESS)==ref,"schedule mismatch "+test+" ref="+ref+" got="+s.result());
    if(ref){found++;var by=new HashMap<String,GraphRecipe<String>>();rs.forEach(r->by.put(r.id(),r));replay(s.witness(),by,exact(stock));}else{dead++;check(s.result()==CountSchedule.Result.DEAD,"not proved dead");}
   }check(b.reservedBytes()==0,"schedule memory");
  }System.out.println("SCHEDULE cases=3000 found="+found+" dead="+dead+" mismatches=0");
  var rs=new ArrayList<GraphRecipe<String>>();for(int i=0;i<40;i++)rs.add(r("read"+i,Map.of("c",1L),Map.of("c",1L,"o"+i,1L)));var b=budget();var counts=new BigInteger[40];Arrays.fill(counts,z(1));
  try(var model=RecipeCountModel.region(rs,Map.of(),Map.of("c",1L),Set.of(),b);var s=new CountSchedule<>(model,counts,b)){forceExact(s);while(!s.step()){}check(s.result()==CountSchedule.Result.WITNESS,"connected 40 actions must solve");System.out.println("CONNECTED40 "+b.diagnostics());}
 }
 static void backward(){var random=new Random(26120926);int found=0,closed=0,unknown=0;
  for(int test=0;test<1000;test++){
   var rs=new ArrayList<GraphRecipe<String>>();var stock=new HashMap<String,Long>();for(int i=0,n=2+random.nextInt(5);i<n;i++)stock.merge("k"+random.nextInt(4),1L,Long::sum);
   for(int i=0,n=3+random.nextInt(6);i<n;i++){var in=new HashMap<String,Long>();var out=new HashMap<String,Long>();for(int j=0,m=1+random.nextInt(3);j<m;j++){in.merge("k"+random.nextInt(4),1L,Long::sum);out.merge("k"+random.nextInt(4),1L,Long::sum);}rs.add(r("r"+i,in,out));}
   var goal=Map.of("k3",z(1+random.nextInt(4)));boolean ref=bfs(rs,exact(stock),goal);var b=budget();
   try(var search=new BackwardCoverability<>(rs,exact(stock),goal,Set.of(),List.of(),b,1_000_000)){
    while(!search.step()){}if(search.result()==BackwardCoverability.Result.WITNESS){found++;check(ref,"false reachable");var by=new HashMap<String,GraphRecipe<String>>();rs.forEach(r->by.put(r.id(),r));var held=exact(stock);replay(search.witness(),by,held);check(covers(held,goal),"witness goal");}
    else if(search.result()==BackwardCoverability.Result.CLOSED){closed++;check(!ref,"false CLOSED case="+test);}else unknown++;
   }check(b.reservedBytes()==0,"backward memory");
  }
  var b=budget();var rs=List.of(r("grow",Map.of("a",1L),Map.of("a",2L)));
  try(var s=new BackwardCoverability<>(rs,Map.of("a",z(1)),Map.of("a",z(Long.MAX_VALUE).multiply(z(5))),Set.of(),List.of(),b,10000)){while(!s.step()){}check(s.result()==BackwardCoverability.Result.WITNESS,"long acceleration");var summary=SequenceSummary.of(s.witness(),Map.of("grow",rs.get(0)));check(summary.required("a").equals(z(1)),"long seed");check(summary.delta("a").add(z(1)).compareTo(z(Long.MAX_VALUE).multiply(z(5)))>=0,"long goal");}
  System.out.println("COVERABILITY cases=1000 witness="+found+" closed="+closed+" unknown="+unknown+" unsound=0; long*5 acceleration passed");
 }
 static GraphPlan<String> plan(List<GraphRecipe<String>>rs,List<String>path,Map<String,Long>seeds,Map<String,Long>stock,long amount){
  var by=new LinkedHashMap<String,GraphRecipe<String>>();rs.forEach(r->by.put(r.id(),r));var steps=new PlanStep.Sequence(path.stream().map(id->(PlanStep)new PlanStep.Batch(id,1)).toList());var sum=SequenceSummary.of(steps,by);var initial=new LinkedHashMap<>(sum.required());seeds.forEach((k,v)->initial.merge(k,z(v),BigInteger::max));var p=new GraphPlan<>("p",amount,true,steps,by,initial,seeds,Map.of(),GraphPlan.Result.FEASIBLE,0,0);PlanVerifier.verify(p);return p;
 }
 static void seeds(){
  var rs=List.of(r("a",Map.of("a",1L,"f",1L),Map.of("a",1L,"x",1L)),r("b",Map.of("b",1L,"f",1L),Map.of("b",1L,"y",1L)),r("p",Map.of("x",1L,"y",1L),Map.of("p",1L)),r("alt",Map.of("c",1L,"f",2L),Map.of("c",1L,"p",1L)));
  var stock=Map.of("a",1L,"b",1L,"c",1L,"f",2L);var incumbent=plan(rs,List.of("a","b","p"),Map.of("a",1L,"b",1L),stock,1);var b=budget();
  try(var opt=new SeedOptimization<>(new GraphCompiler<>(rs),incumbent,stock,Map.of(),Set.of(),Set.of(),true,b)){while(!opt.step()){}var p=opt.result();check(p.seeds().equals(Map.of("c",1L)),"global alternate source "+p.seeds());check(opt.cardinalityProven()&&opt.amountsProven(),"minimality proof "+b.diagnostics());PlanVerifier.verify(p);}
  check(b.reservedBytes()==0,"seed memory");System.out.println("SEED alternate sources 2->1, certified");
  rs=List.of(r("p",Map.of("a",4L,"f",1L),Map.of("a",4L,"p",1L)));stock=Map.of("a",16L,"f",1L);incumbent=plan(rs,List.of("p"),Map.of("a",16L),stock,1);b=budget();
  try(var opt=new SeedOptimization<>(new GraphCompiler<>(rs),incumbent,stock,Map.of(),Set.of(),Set.of(),true,b)){while(!opt.step()){}check(opt.result().seeds().equals(Map.of("a",4L)),"minimum amount "+opt.result().seeds());check(opt.cardinalityProven()&&opt.amountsProven(),"quantity proof "+b.diagnostics());}
  System.out.println("SEED amount 16->4, certified");
 }
 static void sourceProofs(){
  var rs=List.of(r("target",Map.of("x",1L,"y",1L),Map.of("p",1L)),r("bad",Map.of("absent",1L),Map.of("x",1L)),r("good",Map.of("raw",1L),Map.of("x",1L)),r("y0",Map.of("f",1L),Map.of("y",1L)),r("y1",Map.of("g",1L),Map.of("y",1L)));
  var compiler=new GraphCompiler<>(rs);var stock=Map.of("raw",1L,"f",1L,"g",1L);var b=budget();var compilation=compiler.begin("p",Set.of(),Map.of(),Set.of(),b);while(!compilation.step()){}var graph=compilation.result();
  try(var proofs=new OrderProofs<>(RecipeCountModel.forProofs(compiler,"p",1,stock,Map.of(),Set.of(),Set.of(),true,b),b);var explanation=new SourceExplanation<>(proofs,compiler,"p",Set.of(),Set.of(),graph,Map.of(),b)){
   while(!explanation.step()){}check(explanation.result()!=null&&explanation.result().keySet().equals(Set.of("x")),"core must skip unrelated y "+explanation.result());
   check(proofs.sourceConflict(Map.of("p",0,"x",0,"y",1))!=null,"source core reusable");check(proofs.sourceConflict(Map.of("p",0,"x",1,"y",0))==null,"valid source retained");
   try(var changed=RecipeCountModel.forProofs(compiler,"p",1,Map.of("absent",1L,"f",1L),Map.of(),Set.of(),Set.of(),true,b)){check(proofs.forModel(changed).isEmpty(),"inventory scope");}
   try(var reversed=RecipeCountModel.create(new GraphCompiler<>(List.of(rs.get(4),rs.get(3),rs.get(2),rs.get(1),rs.get(0))),"p",1,stock,Map.of(),Set.of(),Set.of(),true,b)){
    check(!proofs.forModel(reversed).isEmpty(),"identity translation");
    for(var clause:proofs.forModel(reversed)){var low=new BigInteger[reversed.recipes.size()];var hi=new BigInteger[low.length];Arrays.fill(low,z(0));for(int i=0;i<low.length;i++)if(reversed.recipes.get(i).id().equals("good"))hi[i]=z(0);check(clause.impliedBy(low,hi,b),"translated source absence");}
   }
  }System.out.println("SOURCE proofs independent replay, core minimization, recipe remapping, inventory isolation passed");
 }
 public static void main(String[]args){fixedSchedules();backward();seeds();sourceProofs();}
}
