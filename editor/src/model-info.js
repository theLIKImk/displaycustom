export const ROLES=['body','head','arm','leg','foot'];
export const STATES=['stand','sit','walk','run','sneak','sneak_walk','climb','jump','fall','swim','fly','sleep','hurt'];
export const INFO_FIELDS={name:['显示名称',128],author:['作者',128],description:['模型信息',2048],contact:['联系方式',256],copyright:['版权信息',1024]};
export function validateInfo(info){
 if(info==null)return undefined;
 if(typeof info!=='object'||Array.isArray(info))throw Error('info 必须为对象');
 const result={};for(const [key,value] of Object.entries(info)){
  if(!Object.hasOwn(INFO_FIELDS,key)||typeof value!=='string'||value.length>INFO_FIELDS[key][1]||/[\u0000-\u0008\u000b-\u001f\u007f\u00a7]/u.test(value))throw Error('模型附加信息无效：'+key);
  result[key]=value;
 }return result;
}
export function validateTags(tags,allowed,max=allowed.length){
 if(tags==null)return undefined;
 if(!Array.isArray(tags)||tags.length>max||new Set(tags).size!==tags.length||tags.some(t=>typeof t!=='string'||!allowed.includes(t)))throw Error('标签无效或重复；允许：'+allowed.join(', '));
 return [...tags];
}
