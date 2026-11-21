import { useState } from 'react';
import { useWatch } from 'react-hook-form';

import { AGREEMENT_REQUIRED_MESSAGE, agreementFieldName, REQUIRED_AGREEMENTS } from '../constants';
import TermsDocumentSheet from './TermsDocumentSheet';

const CHECKBOX = 'size-5 shrink-0 accent-primary';
const ROW = 'flex min-h-11 flex-1 cursor-pointer items-center gap-3';

/**
 * 가입 약관 동의(REQ-037): 전체 동의 + 필수 세 항목. 문서가 있는 항목은 "보기"로 바텀시트를 연다.
 * 검증은 항목마다 true여야 통과(서버도 하나라도 false면 400 details agreements.{필드}).
 */
export default function TermsAgreementField({ control, register, setValue, errors }) {
  const values = useWatch({ control, name: 'agreements' });
  const [openDocumentType, setOpenDocumentType] = useState(null);
  const allChecked = REQUIRED_AGREEMENTS.every(({ name }) => values?.[name] === true);
  const error = REQUIRED_AGREEMENTS.map(({ name }) => errors.agreements?.[name]?.message).find(
    Boolean,
  );

  function toggleAll(event) {
    for (const { name } of REQUIRED_AGREEMENTS) {
      setValue(agreementFieldName(name), event.target.checked, {
        shouldValidate: errors.agreements != null,
      });
    }
  }

  return (
    <fieldset className="flex flex-col" aria-describedby={error ? 'agreements-error' : undefined}>
      <legend className="mb-2 text-caption font-semibold">약관 동의</legend>
      <div className="rounded-xl border border-line px-4 py-1">
        <label className={`${ROW} border-b border-divider py-1 font-semibold`}>
          <input type="checkbox" className={CHECKBOX} checked={allChecked} onChange={toggleAll} />
          전체 동의
        </label>
        <ul className="py-1">
          {REQUIRED_AGREEMENTS.map(({ name, label, documentType }) => (
            <li key={name} className="flex items-center gap-2">
              <label className={`${ROW} text-body`}>
                <input
                  type="checkbox"
                  className={CHECKBOX}
                  {...register(agreementFieldName(name), {
                    validate: (value) => value === true || AGREEMENT_REQUIRED_MESSAGE,
                  })}
                />
                <span>
                  <span className="text-primary">[필수]</span> {label}
                </span>
              </label>
              {documentType && (
                <button
                  type="button"
                  onClick={() => setOpenDocumentType(documentType)}
                  className="min-h-11 shrink-0 px-1 text-caption text-ink-muted underline focus-visible:outline-2 focus-visible:outline-primary"
                >
                  보기
                </button>
              )}
            </li>
          ))}
        </ul>
      </div>
      {error && (
        <p id="agreements-error" role="alert" className="mt-1.5 text-xs text-danger">
          {error}
        </p>
      )}
      <TermsDocumentSheet
        documentType={openDocumentType}
        onClose={() => setOpenDocumentType(null)}
      />
    </fieldset>
  );
}
