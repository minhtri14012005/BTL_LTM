package vn.edu.multigame.lesson.service;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static vn.edu.multigame.questionbank.enums.TextMatchPolicy.*;

class FillBlankMatcherTest {
    @Test void exactJavaSyntaxAndEveryDeclaredAliasArePreserved() {
        var accepted=List.of("ClassLoader","java.lang.ClassLoader");
        assertThat(FillBlankMatcher.matches("ClassLoader",accepted,EXACT)).isTrue();
        assertThat(FillBlankMatcher.matches("java.lang.ClassLoader",accepted,EXACT)).isTrue();
        for(String wrong:List.of("classloader","CLASSLOADER"," ClassLoader","ClassLoader ","Class Loader"))
            assertThat(FillBlankMatcher.matches(wrong,accepted,EXACT)).isFalse();
        assertThat(accepted).containsExactly("ClassLoader","java.lang.ClassLoader");
    }
    @Test void vietnameseCasePolicyPreservesAccentsAndWhitespace() {
        var accepted=List.of("Đảng Cộng sản Việt Nam","ĐCSVN");
        assertThat(FillBlankMatcher.matches("đảng cộng sản việt nam",accepted,CASE_INSENSITIVE)).isTrue();
        assertThat(FillBlankMatcher.matches("đcsvn",accepted,CASE_INSENSITIVE)).isTrue();
        assertThat(FillBlankMatcher.matches("đcsvn",accepted,EXACT)).isFalse();
        assertThat(FillBlankMatcher.matches("Dang Cong san Viet Nam",accepted,CASE_INSENSITIVE)).isFalse();
        assertThat(FillBlankMatcher.matches(" ĐCSVN",accepted,CASE_INSENSITIVE)).isFalse();
        assertThat(FillBlankMatcher.matches("",accepted,CASE_INSENSITIVE)).isFalse();
    }
    @Test void exactUnicodeDoesNotInventEquivalentSourceAnswers() {
        assertThat(FillBlankMatcher.matches("Hồ Chí Minh",List.of("Hồ Chí Minh"),EXACT)).isTrue();
        assertThat(FillBlankMatcher.matches("Hồ Chí minh",List.of("Hồ Chí Minh"),EXACT)).isFalse();
        assertThat(FillBlankMatcher.matches("Ho\u0302\u0300 Chi\u0301 Minh",List.of("Hồ Chí Minh"),EXACT)).isFalse();
        assertThat(FillBlankMatcher.matches(null,List.of("Word"),EXACT)).isFalse();
        assertThat(FillBlankMatcher.matches("Word",List.of("Word"),null)).isFalse();
    }
}
