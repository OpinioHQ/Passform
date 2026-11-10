package com.opinio.passform.service;

// S3에 올린 응답 파일. 판별한 형식과 서버가 만든 키를 담는다. DB 저장이 실패하면 호출자가 원본을 지운다.
public record StoredFile(String questionId, String filename, String contentType, long sizeBytes, String key) {
}
