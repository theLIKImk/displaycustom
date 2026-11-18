import {Matrix4,Euler,Quaternion,Vector3} from 'three';
import {sampleLayers,trs,sampleFrameState} from './animation.js';
export function trsMatrix(value){return new Matrix4().compose(new Vector3(...value.position),new Quaternion().setFromEuler(new Euler(...value.rotation.map(n=>n*Math.PI/180),'XYZ')),new Vector3(...value.scale));}
export function nodeMatrix(model,node,clips,time,wrap=true,seen=new Set()){
  if(seen.has(node.id))throw Error('循环层级');seen.add(node.id);
  const pose=sampleLayers(node,clips,time,wrap),pivot=node.pivot??[0,0,0];
  const local=new Matrix4().makeTranslation(...pose.position).multiply(new Matrix4().makeTranslation(...pivot));
  const rs=trsMatrix({...pose,position:[0,0,0]});local.multiply(rs).multiply(new Matrix4().makeTranslation(...pivot.map(n=>-n)));
  const parent=model.groups?.find(g=>g.id===node.group);return parent?nodeMatrix(model,parent,clips,time,wrap,seen).multiply(local):local;
}
export function partMatrix(model,part,clips,time,wrap=true){return nodeMatrix(model,part,clips,time,wrap);}
export function ancestors(model,node){const result=[];let id=node.group;while(id){const g=model.groups?.find(g=>g.id===id);if(!g||result.some(a=>a.id===id))throw Error('无效层级');result.push(g);id=g.group;}return result;}
export function groupParts(model,ids,id){
  const members=[...(model.groups??[]),...model.parts].filter(p=>ids.includes(p.id));
  if(!members.length||members.length!==new Set(ids).size)throw Error('请选择有效元素或节点');
  // Identity group preserves every base pose and existing animation, without rebaking.
  model.groups??=[];model.groups.push({id,position:[0,0,0],rotation:[0,0,0],scale:[1,1,1]});
  members.filter(p=>!ancestors(model,p).some(a=>ids.includes(a.id))).forEach(p=>p.group=id);
}
export function moveToGroup(model,ids,groupId){
  if(groupId&&!model.groups?.some(g=>g.id===groupId))throw Error('目标组不存在');
  const members=[...(model.groups??[]),...model.parts].filter(p=>ids.includes(p.id));
  if(!members.length||members.length!==new Set(ids).size)throw Error('请选择有效元素或节点');
  for(const member of members)if(member.id===groupId||model.groups?.find(g=>g.id===groupId)&&ancestors(model,model.groups.find(g=>g.id===groupId)).some(g=>g.id===member.id))throw Error('不能移入自身或后代节点');
  const roots=members.filter(m=>!ancestors(model,m).some(a=>ids.includes(a.id)));
  for(const part of roots){if(groupId)part.group=groupId;else delete part.group;}
}
export function dissolveGroup(model,id){
  const group=model.groups?.find(g=>g.id===id);if(!group)return;
  if(JSON.stringify(trs(group))!==JSON.stringify({position:[0,0,0],rotation:[0,0,0],scale:[1,1,1]})||Object.values(model.animations??{}).some(c=>c.tracks[id]))throw Error('组已有变换或动画；请先恢复组基础变换并删除组轨道，再解组，以免丢失动画');
  [...(model.groups??[]),...model.parts].forEach(p=>{if(p.group===id){if(group.group)p.group=group.group;else delete p.group;}});model.groups=model.groups.filter(g=>g.id!==id);
}

export function nodeVisible(model,node,clips,time,wrap=true){
 return [node,...ancestors(model,node)].every(target=>{let visible=true;for(const clip of clips)if(clip?.tracks?.[target.id])visible=sampleFrameState(target,clip,time,wrap).visible;return visible;});
}
