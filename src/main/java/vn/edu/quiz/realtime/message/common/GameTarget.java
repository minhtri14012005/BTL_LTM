package vn.edu.quiz.realtime.message.common;
public record GameTarget(String kind,long id) { public static GameTarget of(long id) { return new GameTarget("GAME",id); } }
