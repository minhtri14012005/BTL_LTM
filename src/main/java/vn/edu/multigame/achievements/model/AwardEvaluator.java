package vn.edu.multigame.achievements.model;

import java.math.BigInteger;
import java.util.*;
import vn.edu.multigame.game.engine.RankingEngine;

/** Pure policy evaluation over committed facts, without persistence, network, clock or scoring mutations. */
public final class AwardEvaluator {
    public record Metric(String mode,int participated,int correct,long correctTimeMs) {}
    public record Player(long userId,int score,long correctTimeMs,int historyRank,boolean eligible,List<Metric> modes) {
        public int participated(){return modes.stream().mapToInt(Metric::participated).sum();}
        public int correct(){return modes.stream().mapToInt(Metric::correct).sum();}
    }
    public record Award(long userId,String type,String modeKey,Map<String,Object> evidence) {
        public Award(long userId,String type,String modeKey){this(userId,type,modeKey,Map.of());}
        public Award { evidence=Map.copyOf(evidence); }
    }
    public record Result(boolean qualified,List<String> reasons,Map<Long,Integer> ranks,List<Award> awards,int eligibleCount,int eligibleCorrect) {}
    public Result evaluate(ApprovedAwardPolicy policy,int configured,int scored,boolean completed,List<Player> players){
        policy.requireSupported();var eligible=players.stream().filter(Player::eligible).toList();
        int correct=eligible.stream().mapToInt(Player::correct).sum();var reasons=new ArrayList<String>();
        if(!completed)reasons.add("NOT_COMPLETED");
        if(players.size()<policy.minInitialPlayers())reasons.add("INSUFFICIENT_INITIAL_PLAYERS");
        if(configured<policy.minConfiguredQuestions() || scored<policy.minScoredQuestions())reasons.add("INSUFFICIENT_QUESTIONS");
        if(eligible.size()<policy.minEligiblePlayers())reasons.add("INSUFFICIENT_ELIGIBLE_PLAYERS");
        if(correct<policy.minEligibleCorrect())reasons.add("NO_ELIGIBLE_CORRECT");
        if(!reasons.isEmpty())return new Result(false,List.copyOf(reasons),Map.of(),List.of(),eligible.size(),correct);
        var ranks=new LinkedHashMap<Long,Integer>();var awards=new ArrayList<Award>();
        new RankingEngine().calculate(eligible.stream().map(p->new RankingEngine.Entry(p.userId(),p.score(),p.correctTimeMs())).toList())
                .forEach(r->{ranks.put(r.player().userId(),r.rank());if(r.rank()==1)awards.add(new Award(r.player().userId(),"CUP","ALL"));});
        for(var p:eligible)if(p.participated()>=policy.perfectMinQuestions() && p.correct()==p.participated())awards.add(new Award(p.userId(),"PERFECT","ALL"));
        var modes=new TreeSet<String>();eligible.forEach(p->p.modes().forEach(m->modes.add(m.mode())));
        for(String mode:modes){
            var candidates=eligible.stream().filter(p->p.modes().stream().anyMatch(m->m.mode().equals(mode) && m.correct()>=policy.fastMinCorrect()
                    && 100L*m.correct()>=(long)policy.fastAccuracyPercent()*m.participated())).toList();
            Metric best=null;
            for(var p:candidates){var m=metric(p,mode);if(best==null || compare(m,best)<0)best=m;}
            if(best!=null)for(var p:candidates)if(compare(metric(p,mode),best)==0)awards.add(new Award(p.userId(),"FAST",mode));
        }
        return new Result(true,List.of(),Map.copyOf(ranks),List.copyOf(awards),eligible.size(),correct);
    }
    private Metric metric(Player p,String mode){return p.modes().stream().filter(m->m.mode().equals(mode)).findFirst().orElseThrow();}
    private int compare(Metric a,Metric b){return BigInteger.valueOf(a.correctTimeMs()).multiply(BigInteger.valueOf(b.correct()))
            .compareTo(BigInteger.valueOf(b.correctTimeMs()).multiply(BigInteger.valueOf(a.correct())));}
}
