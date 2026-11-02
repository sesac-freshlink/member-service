package com.freshlink.member.domain.admin;

/** 점포 이미지 업로드 주소 요청. contentType은 image/jpeg 또는 image/png (없으면 image/jpeg) */
public record StoreImageUploadUrlRequest(String contentType) {
}
