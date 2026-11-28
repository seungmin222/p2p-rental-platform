-- 관리자 문의 처리 답변 (docs/erd.md inquiries, REQ-057)
-- 처리 완료(RESOLVED)에는 답변이 필수다(서비스 검사). 처리 중(IN_PROGRESS)에도 남길 수 있다
ALTER TABLE inquiries
    ADD COLUMN answer      VARCHAR(1000) NULL COMMENT 'inquiry.answer-max' AFTER status,
    ADD COLUMN answered_at DATETIME(6)   NULL COMMENT '답변을 마지막으로 쓴 시각' AFTER answer;
