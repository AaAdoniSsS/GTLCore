package org.gtlcore.gtlcore.integration.ae2.graph.core;
import java.math.BigInteger;
import java.util.*;
import static org.gtlcore.gtlcore.integration.ae2.graph.core.GeneralSearchReview.*;

public final class SharedConflictReview {
    static CountConflict forbidden(Map<Integer,BigInteger> terms,BigInteger rhs){return new CountConflict(List.of(new ExactLinearProgram.Constraint(terms,rhs)));}
    public static void main(String[]args){
        int comparisons=0;var random=new Random(2609261712L);
        for(int test=0;test<180;test++){
            int[] weights={1+random.nextInt(4),1+random.nextInt(4),1+random.nextInt(4)};
            int stock=1+random.nextInt(15);var recipes=new ArrayList<GraphRecipe<String>>();
            for(int i=0;i<3;i++)recipes.add(r("r"+i,Map.of("raw",(long)weights[i]),Map.of("p"+i,1L)));
            recipes.add(r("rawId",Map.of("raw",1L),Map.of("raw",1L)));
            var budget=budget();
            var model=RecipeCountModel.region(recipes,Map.of(),Map.of("raw",(long)stock),Set.of(),budget);
            try(var proofs=new OrderProofs<>(model,budget)){
                var terms=new LinkedHashMap<Integer,BigInteger>();for(int i=0;i<3;i++)terms.put(i,z(-weights[i]));
                proofs.publish(model,List.of(forbidden(terms,z(-stock-1))));
                for(int a=0;a<4;a++)for(int b=0;b<4;b++)for(int c=0;c<4;c++){
                    var prefix=Map.of("r0",z(a),"r1",z(b),"r2",z(c));int[] counts={a,b,c};
                    for(int selected=0;selected<3;selected++){
                        var cap=proofs.maximumAdditional(prefix,"r"+selected,z(20));
                        for(int x=a;x<=15;x++)for(int y=b;y<=15;y++)for(int q=c;q<=15;q++){
                            if(x*weights[0]+y*weights[1]+q*weights[2]>stock)continue;
                            int[] totals={x,y,q};check(cap.compareTo(z(totals[selected]-counts[selected]))>=0,"valid completion clipped");comparisons++;
                        }
                    }
                }
            }
            check(budget.reservedBytes()==0,"propagation memory");
        }
        var recipes=List.of(r("r0",Map.of("raw",1L),Map.of("p0",1L)),r("r1",Map.of("raw",1L),Map.of("p1",1L)),r("rawId",Map.of("raw",1L),Map.of("raw",1L)));
        var budget=budget();var model=RecipeCountModel.region(recipes,Map.of(),Map.of("raw",7L),Set.of(),budget);
        try(var proofs=new OrderProofs<>(model,budget)){
            var conditional=new CountConflict(List.of(new ExactLinearProgram.Constraint(Map.of(0,z(-1)),z(-4)),new ExactLinearProgram.Constraint(Map.of(1,z(-1)),z(-4))));
            proofs.publish(model,List.of(conditional));
            check(proofs.maximumAdditional(Map.of("r0",z(4)),"r1",z(99)).equals(z(3)),"conditional unit propagation");
            check(proofs.maximumAdditional(Map.of("r0",z(3)),"r1",z(99)).equals(z(99)),"unmet antecedent pruned");
            check(proofs.maximumAdditional(Map.of("r0",z(4),"r1",z(2)),"r1",z(99)).equals(z(1)),"cumulative count not subtracted");
        }
        budget=budget();model=RecipeCountModel.region(recipes,Map.of(),Map.of("raw",Long.MAX_VALUE),Set.of(),budget);
        try(var proofs=new OrderProofs<>(model,budget)){
            proofs.publish(model,List.of(forbidden(Map.of(0,z(-1),1,z(-1)),z(Long.MAX_VALUE).negate().subtract(z(1)))));
            check(proofs.maximumAdditional(Map.of("r0",z(Long.MAX_VALUE-1)),"r1",z(Long.MAX_VALUE)).equals(z(1)),"long remainder");
        }
        recipes=List.of(r("spend",Map.of("raw",1L),Map.of("p",1L)),r("restore",Map.of("s",1L),Map.of("raw",1L)));
        budget=budget();model=RecipeCountModel.region(recipes,Map.of(),Map.of("raw",1L,"s",10L),Set.of(),budget);
        try(var proofs=new OrderProofs<>(model,budget)){
            proofs.publish(model,List.of(forbidden(Map.of(0,z(-1),1,z(1)),z(-2))));
            check(proofs.maximumAdditional(Map.of(),"spend",z(10)).equals(z(10)),"later refill incorrectly excluded by prefix");
        }
        System.out.println("SHARED_PROPAGATION cases=180 independent_completions="+comparisons+" wrong_pruning=0; conditional, long and later refill passed");
    }
}
