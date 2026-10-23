package vn.edu.multigame.lesson.service;

import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import vn.edu.multigame.lesson.dto.LessonRequests.*;
import vn.edu.multigame.lesson.domain.LessonMode;
import static org.assertj.core.api.Assertions.*;

class LessonPlanTest {
    private Slot slot(LessonMode mode,String budget) {return new Slot(mode,"Title",new BigDecimal(budget));}
    private Plan plan(Slot... slots) {return new Plan(UUID.randomUUID().toString(),"Lesson",List.of(slots));}
    @Test void acceptsExactlyOneWheelAndDistinctFollowingModesAt100Points() {
        assertThatCode(()->LessonService.validatePlan(plan(slot(LessonMode.LUCKY_WHEEL,"20"),slot(LessonMode.QUIZ,"40.00"),slot(LessonMode.FILL_BLANK,"40")))).doesNotThrowAnyException();
    }
    @Test void rejectsWrongOrderDuplicateModeAndMissingSlot() {
        assertThatThrownBy(()->LessonService.validatePlan(plan(slot(LessonMode.QUIZ,"20"),slot(LessonMode.LUCKY_WHEEL,"40"),slot(LessonMode.FILL_BLANK,"40")))).isInstanceOf(LessonFailure.class);
        assertThatThrownBy(()->LessonService.validatePlan(plan(slot(LessonMode.LUCKY_WHEEL,"20"),slot(LessonMode.QUIZ,"40"),slot(LessonMode.QUIZ,"40")))).isInstanceOf(LessonFailure.class);
        assertThatThrownBy(()->LessonService.validatePlan(plan(slot(LessonMode.LUCKY_WHEEL,"20"),slot(LessonMode.QUIZ,"40")))).isInstanceOf(LessonFailure.class);
    }
    @Test void rejectsOverflowNegativeAndMoreThanTwoDecimalPlaces() {
        for(String budget:List.of("20.01","-0.01","0.001"))
            assertThatThrownBy(()->LessonService.validatePlan(plan(slot(LessonMode.LUCKY_WHEEL,budget),slot(LessonMode.QUIZ,"40"),slot(LessonMode.ASSIGNMENT,"40")))).isInstanceOf(LessonFailure.class);
    }
}
