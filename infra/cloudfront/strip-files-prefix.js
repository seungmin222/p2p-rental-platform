// CloudFront Function (viewer request, 런타임 cloudfront-js-2.0). 동작 `/files/*`에 연결한다.
// 앱이 만드는 이미지 URL은 AWS_S3_PUBLIC_URL(= https://<배포>.cloudfront.net/files) + "/" + S3 키다.
// S3 키(items/…, profiles/…, inspections/…, deductions/…)에는 /files가 없으므로 오리진(S3)으로 보내기 전에 뗀다.
// /files를 쓰는 이유: 키 디렉터리를 루트에 두면 SPA 경로(/items/:id)와 겹친다. 런북: infra/README.md
function handler(event) {
	var request = event.request;
	request.uri = request.uri.replace(/^\/files(?=\/)/, '');
	return request;
}
