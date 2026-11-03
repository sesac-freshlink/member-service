-- 점포 상세 (10/30). 지도 좌표·설명·이미지 칸. 값은 관리자 점포 수정(AD-03)에서 넣고, 비어 있어도 된다.
-- 이미지는 프론트 버킷의 images/stores/{storeId}/{uuid}.jpg 키만 저장하고, 화면은 같은 주소 /images/...로 읽는다
ALTER TABLE store
    ADD COLUMN latitude    DECIMAL(9,6) NULL COMMENT '지도 위도' AFTER region,
    ADD COLUMN longitude   DECIMAL(9,6) NULL COMMENT '지도 경도' AFTER latitude,
    ADD COLUMN description VARCHAR(500) NULL COMMENT '점포 설명' AFTER longitude,
    ADD COLUMN image_key   VARCHAR(255) NULL COMMENT '점포 이미지 S3 키 (images/stores/{storeId}/{uuid}.jpg)' AFTER description;
