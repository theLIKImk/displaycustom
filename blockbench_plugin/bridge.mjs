import {validateModel,MATERIALS,displayType} from '../editor/src/model.js';
import {keyColor} from '../editor/src/palette.js';

const channels=['position','rotation','scale'];
const axes=['x','y','z'];
const zero=[0,0,0],one=[1,1,1];
const add=(a,b)=>a.map((x,i)=>x+b[i]);
const subtract=(a,b)=>a.map((x,i)=>x-b[i]);
const multiply=(a,b)=>a.map((x,i)=>x*b[i]);
const units=(a,n)=>a.map(x=>x*n);
const unit=a=>a.every(x=>Math.abs(x-1)<1e-7);
const near=(a,b)=>a.every((x,i)=>Math.abs(x-b[i])<1e-7);
const timeKey=t=>Math.round(t*1e6)/1e6;
const extent=2*1024*1024;
function rotated(offset,rotation){
  const [x,y,z]=rotation.map(value=>value*Math.PI/180);
  const [a,b,c]=offset;
  const zx=a*Math.cos(z)-b*Math.sin(z),zy=a*Math.sin(z)+b*Math.cos(z);
  const yx=zx*Math.cos(y)+c*Math.sin(y),yz=c*Math.cos(y)-zx*Math.sin(y);
  return [yx,zy*Math.cos(x)-yz*Math.sin(x),zy*Math.sin(x)+yz*Math.cos(x)];
}

// Blockbench origins are expressed in unposed model coordinates, in 1/16 blocks.
export function importProject(input){
  const model=validateModel(input),groups=new Map((model.groups??[]).map(group=>[group.id,group]));
  const origins=new Map();
  function origin(node){
    if(origins.has(node.id))return origins.get(node.id);
    const parent=node.group&&groups.get(node.group);
    const parentOrigin=parent?subtract(origin(parent),units(parent.pivot??zero,16)):zero;
    const result=add(parentOrigin,units(add(node.position,node.pivot??zero),16));
    origins.set(node.id,result);
    return result;
  }
  const nodes=[...groups.values(),...model.parts].map(node=>{
    const part=!groups.has(node.id);
    if(!part&&!unit(node.scale))throw Error('组 '+node.id+' 的默认缩放必须为 [1,1,1]，Blockbench 骨骼只能用关键帧改变缩放。');
    const center=origin(node);
    const box=part?{from:subtract(center,units(node.scale,8)),to:add(center,units(node.scale,8))}:null;
    return {id:node.id,parent:node.group,origin:center,rotation:node.rotation,box,meta:node,
      ...(part?{color:keyColor(node.itemModel)??(displayType(node)==='block'?MATERIALS[node.material]?.[1]:'#809db5')??'#d3d8da'}:{})};
  });
  const byId=new Map(nodes.map(node=>[node.id,node]));
  const animations=Object.entries(model.animations??{}).map(([name,clip])=>{
    const {tracks,...meta}=clip;
    return {name,duration:clip.duration,loop:clip.loop,meta,tracks:Object.fromEntries(Object.entries(tracks).map(([id,frames])=>{
      const target=byId.get(id).meta;
      const keys=Object.fromEntries(channels.map(channel=>[channel,frames.map(frame=>{
        let value=frame[channel];
        if(channel==='position')value=units(subtract(value,target.position),16);
        if(channel==='rotation')value=subtract(value,target.rotation);
        if(channel==='scale')value=value.map((x,i)=>x/target.scale[i]);
        return {time:frame.time,value,interpolation:frame.interpolation??clip.interpolation??'linear'};
      })]));
      const events=frames.flatMap(({time,visible,text,textStyle})=>{
        const payload={...(visible!=null?{visible}:{}),...(text!=null?{text}:{}),...(textStyle?{textStyle}:{})};
        return Object.keys(payload).length?[{time,...payload}]:[];
      });
      return [id,{keys,events}];
    }))};
  });
  return {name:model.name,info:model.info,version:model.version,playback:model.playback,nodes,animations};
}

function sample(keys,time,fallback){
  if(!keys.length)return fallback;
  const next=keys.findIndex(key=>key.time>time);
  if(next===0)return keys[0].value;
  if(next===-1)return keys.at(-1).value;
  const a=keys[next-1],b=keys[next];
  if(a.interpolation==='step')return a.value;
  const t=(time-a.time)/(b.time-a.time);
  return a.value.map((x,i)=>x+(b.value[i]-x)*t);
}

function compileTrack(node,track,duration,clipMode){
  const keys=Object.fromEntries(channels.map(channel=>[channel,(track.keys[channel]??[]).map(key=>({...key,time:timeKey(key.time)})).sort((a,b)=>a.time-b.time)]));
  const times=new Set();
  for(const channel of channels)for(const [i,key] of keys[channel].entries()){
    if(!Number.isFinite(key.time)||key.time<0||key.time>duration||keys[channel][i-1]?.time===key.time||!key.value.every(Number.isFinite))
      throw Error('关键帧时间或数值无效：'+node.id);
    if(!['linear','step'].includes(key.interpolation))throw Error('请先将反曲线或表达式烘焙为线性／定格关键帧：'+node.id);
    times.add(key.time);
  }
  const events=new Map((track.events??[]).map(({time,...event})=>[timeKey(time),event]));
  for(const time of events.keys()){
    if(time>duration)throw Error('缩短动画前请先移除结束时间之后的显示或文字事件：'+node.id);
    times.add(time);
  }
  const sorted=[...times].sort((a,b)=>a-b);
  return sorted.map((time,index)=>{
    const position=add(node.position,units(sample(keys.position,time,zero),1/16));
    const rotation=add(node.rotation,sample(keys.rotation,time,zero));
    const scale=multiply(node.scale,sample(keys.scale,time,one));
    let mode=keys.position.findLast(key=>key.time<=time)?.interpolation??clipMode??'linear';
    if(index+1<sorted.length){
      const end=sorted[index+1],middle=(time+end)/2;
      const moving=new Set();
      for(const channel of channels){
        const a=sample(keys[channel],time,zero),b=sample(keys[channel],end,zero);
        if(near(a,b))continue;
        moving.add(near(a,sample(keys[channel],middle,zero))?'step':'linear');
      }
      if(moving.size>1){
        // DC v6+ has one interpolation mode per node. A held channel can jump
        // only at its endpoint, so a synchronous mixed interval is ambiguous.
        throw Error('同一节点的位移、旋转与缩放混用了线性和定格：'+node.id+' @ '+time+'s');
      }
      if(moving.size)mode=moving.values().next().value;
    }
    return {time,position,rotation,scale,...(mode!==(clipMode??'linear')?{interpolation:mode}:{}),...events.get(time)};
  });
}

export function exportProject(project){
  const nodes=new Map(project.nodes.map(node=>[node.id,node]));
  if(nodes.size!==project.nodes.length)throw Error('组与部件必须使用不同的名称。');
  const parts=[],groups=[],transforms=new Map();
  for(const node of project.nodes){
    const parent=node.parent&&nodes.get(node.parent);
    if(node.parent&&(!parent||parent.box))throw Error('部件须挂在组下：'+node.id);
    const meta=node.meta??{};
    const pivot=node.box?zero:meta.pivot??zero;
    const relative=parent?subtract(node.origin,parent.origin):node.origin;
    let position=subtract(add(units(relative,1/16),parent?.meta?.pivot??zero),pivot);
    let scale=one,rotation=node.rotation;
    if(node.box){
      const {from,to}=node.box;
      const center=units(add(from,to),.5);
      const offset=subtract(center,node.origin);
      if(node.wrapped&&!near(offset,zero))
        throw Error('部件 '+node.id+' 的预览方盒中心必须与部件骨骼重合。请移动骨骼，或对称调整方盒尺寸。');
      position=add(position,units(rotated(offset,node.rotation),1/16));
      scale=units(subtract(to,from),1/16);
    }
    const {group:oldParent,...data}=meta;
    const geometry={...data,id:node.id,position,rotation,scale,...(node.parent?{group:node.parent}:{})};
    if(node.box)parts.push({material:'WHITE_CONCRETE',type:displayType(meta),...geometry});
    else groups.push({kind:'bone',...geometry});
    transforms.set(node.id,geometry);
  }
  const animations={};
  for(const animation of project.animations){
    const {name,meta={}}=animation,duration=timeKey(animation.duration);
    if(!/^[a-z][a-z0-9_-]{0,47}$/.test(name)||name==='constructor'||name==='prototype'||Object.hasOwn(animations,name))
      throw Error('动画名称无效或重复：'+name);
    const tracks={};
    for(const [id,track] of Object.entries(animation.tracks)){
      const node=transforms.get(id);
      if(!node)throw Error('动画目标不存在：'+id);
      const frames=compileTrack(node,track,duration,meta.interpolation);
      if(frames.length)tracks[id]=frames;
    }
    animations[name]={...meta,duration,loop:animation.loop,tracks};
  }
  const version=Math.max(project.version??7,5);
  const playback=project.playback?.animations.filter(name=>Object.hasOwn(animations,name));
  const result=validateModel({version,name:project.name,info:project.info,parts,groups,animations,
    ...(playback?.length?{playback:{animations:playback}}:{})});
  if(new TextEncoder().encode(JSON.stringify(result)).length>extent)throw Error('导出的模型超过 2 MiB。');
  return result;
}

export function keyValue(key){
  if(key.data_points?.length!==1)throw Error('请先烘焙包含多个数据点的关键帧。');
  return axes.map(axis=>{
    const value=key.data_points[0][axis];
    if(typeof value!=='number'&&(typeof value!=='string'||!value.trim()||!Number.isFinite(Number(value))))
      throw Error('请先把表达式烘焙为数值关键帧。');
    return Number(value);
  });
}
