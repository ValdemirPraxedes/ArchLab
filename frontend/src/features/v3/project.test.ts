import { describe, expect, it } from 'vitest';
import { newProjectV3, projectReducer, toRequestV3 } from './project';
describe('v3 project boundary', () => {
  it('keeps drafts independent and never serializes canvas metadata', () => { const first = newProjectV3(), second = newProjectV3(); first.positions['a'] = { x: 10, y: 20 }; first.request.architecture.id = 'changed'; expect(second.request.architecture.id).toBe('architecture-v3'); const wire = toRequestV3(first); expect(wire).not.toHaveProperty('positions'); expect(wire).not.toHaveProperty('nextId'); wire.architecture.id = 'wire'; expect(first.request.architecture.id).toBe('changed'); });
  it('renames immutably and clones replacements', () => { const project = newProjectV3(); const renamed = projectReducer(project, { type: 'rename', name: 'new' }); expect(project.request.architecture.id).toBe('architecture-v3'); expect(renamed.request.architecture.id).toBe('new'); expect(projectReducer(project, { type: 'replace', project: renamed })).toEqual(renamed); });
});
