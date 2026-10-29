-- 인계·반납 기록 (docs/erd.md item_inspections, REQ-017·018)
-- ai_result(2순위)는 REQ-040에서 컬럼 추가 마이그레이션으로 넣는다
CREATE TABLE item_inspections (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    rental_id         BIGINT       NOT NULL,
    phase             VARCHAR(30)  NOT NULL COMMENT 'InspectionPhase',
    image_urls        JSON         NOT NULL COMMENT '사진 URL 배열. 미반납 신고(lost_reported)면 빈 배열',
    checklist         JSON         NULL COMMENT 'ReturnChecklistItem별 true/false. RETURN만',
    note              VARCHAR(300) NULL COMMENT '인계 메모(inspection.handover-memo-max) 또는 반납 메모(return.note-max)',
    lost_reported     BOOLEAN      NOT NULL DEFAULT FALSE COMMENT '미반납 신고(사진 없음, REQ-018 2순위)',
    result            VARCHAR(30)  NULL COMMENT 'InspectionResult. 제공자 판정(RETURN만), 1회 기록',
    result_note       VARCHAR(500) NULL COMMENT 'inspection.abnormal-note-max. ABNORMAL이면 필수',
    result_image_urls JSON         NULL COMMENT '판정 사진 URL 배열. ABNORMAL이면 필수',
    written_by        BIGINT       NOT NULL COMMENT '작성자. 반납 대리 접수면 제공자',
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_item_inspections_rental_id_phase UNIQUE (rental_id, phase),
    CONSTRAINT fk_item_inspections_rentals FOREIGN KEY (rental_id) REFERENCES rentals (id),
    CONSTRAINT fk_item_inspections_members FOREIGN KEY (written_by) REFERENCES members (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
