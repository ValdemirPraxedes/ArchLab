import {describe,expect,it} from 'vitest';
import {http,HttpResponse} from 'msw';
import {server} from '../../test/server';
import {ArchlabV1Client} from '../../api/archlabV1Client';
import type {ArchitectureNode,ArchitectureEdge} from './model';
import {toArchitectureV1} from './architectureV1';
import {toUnifiedRequestV41} from './unifiedScenarioV41';

describe('REST → Broker → WebSocket no contrato frontend',()=>{
  it('preserva comportamento, sessão e correlação na resposta',async()=>{
    const nodes=[
      {id:'browser',data:{componentType:'WEB_BROWSER',label:'Browser'}},
      {id:'orders',data:{componentType:'API',label:'Orders',behaviors:[{id:'receive',trigger:'REQUEST_RECEIVED',match:{operation:'POST /orders'},actions:[
        {id:'publish',kind:'PUBLISH',connectionId:'orders-broker',destinationName:'orders'},
        {id:'respond',kind:'RESPOND',statusCode:201,dependsOn:['publish']}]}]}},
      {id:'broker',data:{componentType:'BROKER',label:'Broker',destinations:[{name:'orders',kind:'QUEUE'}],subscriptions:[{destinationName:'orders',consumerComponentId:'notifications',connectionId:'delivery'}]}},
      {id:'notifications',data:{componentType:'API',label:'Notifications',behaviors:[{id:'consume',trigger:'MESSAGE_CONSUMED',match:{destinationName:'orders'},actions:[{id:'send',kind:'WS_SEND',sessionAlias:'notifications-session',sender:'TARGET'}]}]}},
    ] as ArchitectureNode[];
    const edges=[
      {id:'entry',source:'browser',target:'orders',data:{protocol:'REST',latencyMs:1,integration:{kind:'HTTP_REST',request:{method:'POST',path:'/orders'},response:{statusCode:201}}}},
      {id:'orders-broker',source:'orders',target:'broker',data:{protocol:'QUEUE',latencyMs:1,integration:{kind:'QUEUE',queueName:'orders'}}},
      {id:'delivery',source:'broker',target:'notifications',data:{protocol:'QUEUE',latencyMs:1,integration:{kind:'QUEUE',queueName:'orders'}}},
      {id:'socket',source:'browser',target:'notifications',data:{protocol:'WEBSOCKET',latencyMs:1,integration:{kind:'WEBSOCKET',path:'/notifications',connectTimeoutMs:5000,idleTimeoutMs:null,closeMode:'AUTOMATIC',messages:[]}}},
    ] as ArchitectureEdge[];
    const request=toUnifiedRequestV41(toArchitectureV1(nodes,edges),{componentId:'browser',connectionId:'entry',operation:'POST /orders'},
      [{connectionId:'socket',sessionAlias:'notifications-session',autoCloseAtEnd:true}],nodes,edges);
    server.use(http.post('/api/v1/simulations',async({request:incoming})=>{
      const body=await incoming.json() as typeof request;
      expect(body.scenario).not.toHaveProperty('kind');
      expect(body.scenario).toMatchObject({entry:{connectionId:'entry'},sessionSetup:[{connectionId:'socket'}]});
      expect(body.architecture.components.find(component=>component.id==='orders')).toMatchObject({behaviors:[{actions:[{kind:'PUBLISH'},{kind:'RESPOND'}]}]});
      return HttpResponse.json({execution:{id:'exec-1',status:'COMPLETED',mainStatus:'COMPLETED',mode:'UNIFIED',requestId:'request-1',entryComponentId:'browser',entryConnectionId:'entry',totalDurationMs:8,responseStatusCode:201},
        initialState:{components:{},requests:{},exchanges:{}},trace:[{type:'ACTION_EVENT',sequence:1,timestampMs:3,componentId:'orders',actionId:'respond',action:'COMPLETED',correlation:{operationId:'exchange-1',profile:'HTTP_REST'}}],timeline:[],metrics:{totalDurationMs:8,processingByComponent:[],communicationByConnection:[],waitingByComponent:[],exchanges:[]},messageRepresentations:[]});
    }));
    const response=await new ArchlabV1Client().simulate(request);
    expect(response.execution).toMatchObject({mainStatus:'COMPLETED',status:'COMPLETED',responseStatusCode:201});
    expect(response.trace[0].correlation?.operationId).toBe('exchange-1');
  });
});
