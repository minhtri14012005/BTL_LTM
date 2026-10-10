package vn.edu.multigame.common.util;
import java.util.HashSet;
import java.util.List;
/** Stable IDs form a complete permutation; repeated display values do not merge IDs. */
public final class ArrangementIds {
    private ArrangementIds() {}
    public static boolean permutation(List<String> expected,List<String> submitted) {
        if(expected==null || submitted==null || expected.size()<2 || expected.size()>100 || expected.size()!=submitted.size()
                || expected.stream().anyMatch(id->id==null || !id.matches("[A-Za-z0-9_-]{1,64}"))
                || submitted.stream().anyMatch(id->id==null || !id.matches("[A-Za-z0-9_-]{1,64}"))) return false;
        var ids=new HashSet<>(expected);
        return ids.size()==expected.size() && new HashSet<>(submitted).size()==submitted.size() && ids.equals(new HashSet<>(submitted));
    }
}
