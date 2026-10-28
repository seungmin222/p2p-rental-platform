import { create } from 'zustand';

const DURATION_MS = 3000;
const MAX_COUNT = 3;

let nextId = 1;

export const useToastStore = create((set) => ({
  toasts: [],
  add: (toastItem) =>
    // 최대 개수를 넘으면 가장 오래된 것부터 버린다
    set((state) => ({ toasts: [...state.toasts, toastItem].slice(-MAX_COUNT) })),
  remove: (id) => set((state) => ({ toasts: state.toasts.filter((t) => t.id !== id) })),
}));

/** 어디서든 호출: toast.show({ message: '저장했어요' }), toast.show({ message, tone: 'danger' }) */
export const toast = {
  show({ message, tone = 'default' }) {
    const id = nextId;
    nextId += 1;
    useToastStore.getState().add({ id, message, tone });
    setTimeout(() => useToastStore.getState().remove(id), DURATION_MS);
    return id;
  },
  dismiss(id) {
    useToastStore.getState().remove(id);
  },
};
