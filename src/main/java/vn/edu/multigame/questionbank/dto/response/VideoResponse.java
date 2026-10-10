package vn.edu.multigame.questionbank.dto.response;
public record VideoResponse(String mediaRef,String url,String contentType,long sizeBytes,long durationMs,int width,int height,String videoCodec,String audioCodec) {}
