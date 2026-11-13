// 목 STOMP 서버용 프레임 읽기·쓰기(순수 함수). STOMP 1.2: COMMAND\nkey:value\n...\n\nbody\0
// 헤더 값 이스케이프(\n \c \\)는 목이 쓰는 값에 없어 다루지 않는다.

/** 웹소켓 메시지 하나(프레임 여러 개일 수 있음)를 프레임 배열로. 하트비트(빈 줄)는 건너뛴다 */
export function parseFrames(data) {
  return String(data)
    .split('\0')
    .map((chunk) => chunk.replace(/^[\r\n]+/, ''))
    .filter((chunk) => chunk !== '')
    .map((chunk) => {
      const separator = chunk.indexOf('\n\n');
      const head = separator < 0 ? chunk : chunk.slice(0, separator);
      const body = separator < 0 ? '' : chunk.slice(separator + 2);
      const [command, ...lines] = head.split(/\r?\n/);
      const headers = {};
      for (const line of lines) {
        const colon = line.indexOf(':');
        // 같은 헤더가 여러 번이면 처음 것(1.2 규칙)
        if (colon > 0 && !(line.slice(0, colon) in headers)) {
          headers[line.slice(0, colon)] = line.slice(colon + 1);
        }
      }
      return { command, headers, body };
    });
}

export function serializeFrame(command, headers = {}, body = '') {
  const lines = Object.entries(headers).map(([key, value]) => `${key}:${value}`);
  return `${[command, ...lines].join('\n')}\n\n${body}\0`;
}
