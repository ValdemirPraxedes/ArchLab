import type { ProjectV3 } from './project';
export interface HistoryV3 { present: ProjectV3; past: ProjectV3[]; future: ProjectV3[]; highWater: number }
export function historyV3(project: ProjectV3): HistoryV3 { return { present: project, past: [], future: [], highWater: project.nextId }; }
export type HistoryAction = { type: 'commit'; project: ProjectV3 } | { type: 'undo' | 'redo' };
export function historyReducerV3(state: HistoryV3, action: HistoryAction): HistoryV3 {
  if (action.type === 'commit') { if (JSON.stringify(action.project) === JSON.stringify(state.present)) return state; const highWater = Math.max(state.highWater, action.project.nextId); return { present: { ...structuredClone(action.project), nextId: highWater }, highWater, past: [...state.past, state.present].slice(-50), future: [] }; }
  const source = action.type === 'undo' ? state.past : state.future; if (!source.length) return state;
  const project = { ...structuredClone(source.at(-1)!), nextId: state.highWater };
  return action.type === 'undo' ? { ...state, present: project, past: state.past.slice(0,-1), future: [...state.future, state.present] } : { ...state, present: project, past: [...state.past, state.present].slice(-50), future: state.future.slice(0,-1) };
}
