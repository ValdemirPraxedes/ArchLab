import { describe, expect, it } from 'vitest';
import { activityAt, nextBoundary, previousBoundary } from './playbackTimeline';
import type { SimulationResponse } from '../../api/archlabClient';

const activities = [
 { id:'a', kind:'COMMUNICATION', messageId:'m1', connectionId:'c1', sourceId:'client',targetId:'api',startMs:0,endMs:20,durationMs:20 },
 { id:'b', kind:'PROCESSING',componentId:'api',requestId:'r1',phase:'API_PRE_PROCESSING',startMs:20,endMs:50,durationMs:30 },
 { id:'c', kind:'COMMUNICATION',messageId:'m2',connectionId:'c2',sourceId:'api',targetId:'db',startMs:50,endMs:60,durationMs:10 },
 { id:'d', kind:'PROCESSING',componentId:'db',requestId:'r1',phase:'DATABASE_PROCESSING',startMs:60,endMs:210,durationMs:150 },
 { id:'e', kind:'COMMUNICATION',messageId:'m3',connectionId:'c2',sourceId:'db',targetId:'api',startMs:210,endMs:220,durationMs:10 },
 { id:'f', kind:'PROCESSING',componentId:'api',requestId:'r1',phase:'API_POST_PROCESSING',startMs:220,endMs:240,durationMs:20 },
 { id:'g', kind:'COMMUNICATION',messageId:'m4',connectionId:'c1',sourceId:'api',targetId:'client',startMs:240,endMs:260,durationMs:20 },
] as SimulationResponse['timeline'];

describe('reprodução virtual', () => {
 it('identifica as sete atividades no cenário de 260 ms', () => {
   expect(activityAt(activities, 0)).toBe(0);
   expect(activityAt(activities, 60)).toBe(3);
   expect(activityAt(activities, 220)).toBe(5);
   expect(activityAt(activities, 260)).toBe(6);
 });
 it('avança e volta pelos limites das etapas', () => {
   expect(nextBoundary(activities,0)).toBe(20);
   expect(nextBoundary(activities,20)).toBe(50);
   expect(previousBoundary(activities,50)).toBe(20);
   expect(previousBoundary(activities,0)).toBe(0);
 });
});
