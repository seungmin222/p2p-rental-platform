-- AI 물품 상태 비교 결과 (docs/erd.md item_inspections.ai_result, REQ-040)
-- RETURN 기록에만 쓴다. NULL = 비교 전(적재 전·적재 실패·미반납 신고). 형식은 rental/entity/AiCompareRecord
ALTER TABLE item_inspections
    ADD COLUMN ai_result JSON NULL AFTER result_image_urls;
