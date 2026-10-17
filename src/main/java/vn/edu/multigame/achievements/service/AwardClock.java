package vn.edu.multigame.achievements.service;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;
/** Injectable Server wall clock for eligibility; never client time or a gameplay timer. */
@Component @Profile("mysql")
public class AwardClock { public long now(){return System.currentTimeMillis();} }
