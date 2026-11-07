import type { BehaviorActionV41 } from '../../api/archlabV1Client';

type Kind = BehaviorActionV41['kind'];

/**
 * Component types each v0.6 data Action may target. Mirrors the normative
 * docs/api/examples/v0.6/capability-matrix-v6.json, which the backend tests also read;
 * dataCapabilitiesV6.test.ts fails if the two diverge.
 */
export const DATA_ACTION_TARGETS_V6:Partial<Record<Kind,readonly string[]>>={
  DB_SELECT:['RELATIONAL_DATABASE'], DB_INSERT:['RELATIONAL_DATABASE'],
  DB_UPDATE:['RELATIONAL_DATABASE','DOCUMENT_DATABASE'], DB_DELETE:['RELATIONAL_DATABASE','DOCUMENT_DATABASE','KEY_VALUE_STORE'],
  DB_GET:['DOCUMENT_DATABASE','KEY_VALUE_STORE'], DB_PUT:['DOCUMENT_DATABASE','KEY_VALUE_STORE'],
  DB_FIND:['DOCUMENT_DATABASE'], DB_BULK_INSERT:['RELATIONAL_DATABASE','DOCUMENT_DATABASE'],
  ETL_EXTRACT:['RELATIONAL_DATABASE','DOCUMENT_DATABASE','KEY_VALUE_STORE'],
  ETL_TRANSFORM:['DATA_TRANSFORMER'],
  ETL_LOAD:['RELATIONAL_DATABASE','DOCUMENT_DATABASE','KEY_VALUE_STORE'],
};
