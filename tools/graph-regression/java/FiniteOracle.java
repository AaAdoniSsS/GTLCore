package org.gtlcore.gtlcore.integration.ae2.graph.core;
import java.math.BigInteger;
import java.util.*;

public class FiniteOracle {
    static BigInteger b(long x){return BigInteger.valueOf(x);}
    static boolean valid(List<ExactLinearProgram.Constraint> rows,BigInteger[] x){
        for(var row:rows){BigInteger s=BigInteger.ZERO;for(var e:row.terms().entrySet())s=s.add(e.getValue().multiply(x[e.getKey()]));if(s.compareTo(row.upper())>0)return false;}return true;
    }
    public static void main(String[] args){
        Random random=new Random(261703);int feasible=0,closed=0;long completions=0;
        for(int test=0;test<3000;test++){
            int n=2+random.nextInt(5),m=1+random.nextInt(12),states=1;
            BigInteger[] low=new BigInteger[n],high=new BigInteger[n];int[] size=new int[n];
            for(int i=0;i<n;i++){low[i]=b(random.nextInt(3));if(test%7==0)low[i]=low[i].add(b(Long.MAX_VALUE));size[i]=1+random.nextInt(4);high[i]=low[i].add(b(size[i]-1));states*=size[i];}
            List<ExactLinearProgram.Constraint> rows=new ArrayList<>();
            for(int j=0;j<m;j++){
                Map<Integer,BigInteger> terms=new LinkedHashMap<>();BigInteger bound=b(random.nextInt(17)-8);
                for(int i=0;i<n;i++){int k=random.nextInt(11)-5;if(k!=0){terms.put(i,b(k));bound=bound.add(low[i].multiply(b(k)));}}
                rows.add(new ExactLinearProgram.Constraint(terms,bound));
                if(test%3==0) {Map<Integer,BigInteger> opposite=new LinkedHashMap<>();terms.forEach((k,v)->opposite.put(k,v.negate()));rows.add(new ExactLinearProgram.Constraint(opposite,bound.negate()));}
            }
            var bounded=new ArrayList<>(rows);
            for(int i=0;i<n;i++){bounded.add(new ExactLinearProgram.Constraint(Map.of(i,BigInteger.ONE),high[i]));bounded.add(new ExactLinearProgram.Constraint(Map.of(i,BigInteger.ONE.negate()),low[i].negate()));}
            PlanningBudget budget=new PlanningBudget(0,20_000_000,256L<<20,()->false,System::nanoTime);
            try(var bounds=new CountBounds(n,bounded,budget);var match=new CountMeetInMiddle(rows,low,high,budget)){
                while(!bounds.step()){}while(!match.step()){}
                boolean possible=false;BigInteger[] lo=bounds.lowerBounds(),hi=bounds.upperBounds();
                for(int code=0;code<states;code++){
                    BigInteger[] x=low.clone();int c=code;for(int i=0;i<n;i++){x[i]=x[i].add(b(c%size[i]));c/=size[i];}
                    if(!valid(rows,x))continue;possible=true;completions++;
                    if(bounds.blocked())throw new AssertionError("False bound proof "+test);
                    for(int i=0;i<n;i++)if(x[i].compareTo(lo[i])<0||hi[i]!=null&&x[i].compareTo(hi[i])>0)throw new AssertionError("False bound "+test);
                }
                if(match.infeasible()&&possible)throw new AssertionError("False match proof "+test);
                if(match.counts()!=null&&!valid(bounded,match.counts()))throw new AssertionError("Bad counts "+test);
                if(possible&&match.counts()==null)throw new AssertionError("Unsolved tiny domain "+test);
                if(possible)feasible++;else closed++;
            }
        }
        System.out.println("PASS finite=3000 feasible="+feasible+" infeasible="+closed+" checked_completions="+completions);
    }
}
