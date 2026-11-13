import { create } from 'zustand';

/** 연결 상태(UI 표시용). 서버 데이터가 아니라 Zustand에 둔다 */
export const STOMP_STATUS = {
  IDLE: 'idle', // 로그아웃 상태(연결하지 않음)
  CONNECTING: 'connecting',
  CONNECTED: 'connected',
  DISCONNECTED: 'disconnected', // 끊겨서 다시 연결을 기다리는 중
};

export const useStompStatusStore = create((set) => ({
  status: STOMP_STATUS.IDLE,
  setIdle: () => set({ status: STOMP_STATUS.IDLE }),
  setConnecting: () => set({ status: STOMP_STATUS.CONNECTING }),
  setConnected: () => set({ status: STOMP_STATUS.CONNECTED }),
  setDisconnected: () => set({ status: STOMP_STATUS.DISCONNECTED }),
}));

export function useStompStatus() {
  return useStompStatusStore((state) => state.status);
}
