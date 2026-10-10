package vn.edu.multigame.common.util;
import java.text.Normalizer;
import java.util.Locale;
/** Shared authoring/evaluation canonicalization; no database, clock or network. */
public final class TextNormalizer {
    public static final String POLICY = "NFC_CASE_INSENSITIVE_WHITESPACE";
    private TextNormalizer() {}
    public static String normalize(String input) {
        if(input==null) throw new IllegalArgumentException("Text cannot be null");
        String nfc=Normalizer.normalize(input,Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
        StringBuilder output=new StringBuilder(); boolean space=false;
        for(int cp:nfc.codePoints().toArray()) {
            if(Character.isWhitespace(cp) || Character.isSpaceChar(cp)) { space=output.length()>0; }
            else { if(space) output.append(' '); output.appendCodePoint(cp); space=false; }
        }
        return Normalizer.normalize(output,Normalizer.Form.NFC);
    }
}
