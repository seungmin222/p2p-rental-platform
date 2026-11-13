/** 상대 프로필 사진(없거나 탈퇴면 연한 원). 이름이 옆에 있으므로 장식 */
export default function ChatAvatar({ url, className = 'size-12' }) {
  if (url) {
    return (
      <img
        src={url}
        alt=""
        loading="lazy"
        className={`shrink-0 rounded-full bg-placeholder object-cover ${className}`}
      />
    );
  }
  return (
    <div aria-hidden="true" className={`shrink-0 rounded-full bg-primary-soft ${className}`} />
  );
}
