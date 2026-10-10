package vn.edu.multigame.questionbank.model;
import java.util.*;
/** Immutable question content validation, shared by authoring, Room and game snapshots. */
public final class ClueTimeline {
    private ClueTimeline() {}
    public static List<Long> offsets(Map<String,Object> payload) {
        if(payload==null || !(payload.get("hints") instanceof List<?> hints) || hints.isEmpty() || hints.size()>20)
            throw new IllegalArgumentException("Invalid hints");
        var offsets=new ArrayList<Long>(); long previous=-1;
        for(var value:hints) {
            if(!(value instanceof Map<?,?> hint) || !(hint.get("offsetMs") instanceof Number n)
                    || !(hint.get("text") instanceof String text) || text.isBlank() || text.length()>2000)
                throw new IllegalArgumentException("Invalid hint");
            long offset=n.longValue();
            if(offset<0 || offset<=previous || n.doubleValue()!=offset) throw new IllegalArgumentException("Invalid offset");
            offsets.add(offset); previous=offset;
        }
        return List.copyOf(offsets);
    }
    public static boolean fits(Map<String,Object> payload,long durationMs) {
        try {var offsets=offsets(payload);return durationMs>0 && offsets.getLast()<durationMs;}
        catch(IllegalArgumentException failure) {return false;}
    }
}
