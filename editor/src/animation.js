import {textStyle,validateTextStyle} from './text-style.js';
import {validateTags,STATES} from './model-info.js';
export const trs=part=>({position:[...part.position],rotation:[...part.rotation],scale:[...part.scale]});
export const ITEM_USE_STATES={hold:'握持',charge:'蓄力',use:'使用'};
export const isHurt=clip=>Array.isArray(clip?.tags)&&clip.tags.includes('hurt');
export const runsInParallel=clip=>clip?.parallel??(!!clip?.itemUse||isHurt(clip));
const ITEM_FAMILIES=new Set('sword spear axe trident mace shield pickaxe fishing_rod bow elytra crossbow other'.split(' '));
const itemSelector=id=>typeof id==='string'&&(id==='*'||/^[a-z0-9_.-]+:[a-z0-9_./-]+$/.test(id)||id.startsWith('#')&&ITEM_FAMILIES.has(id.slice(1)));
export function vector(v,min,max,label){
  if(!Array.isArray(v)||v.length!==3||v.some(n=>typeof n!=='number'||!Number.isFinite(n)||n<min||n>max))throw Error(`${label} 需为三个 ${min} 至 ${max} 的有限数字`);
  return [...v];
}
export function validateAnimations(input,parts){
  if(input==null)return undefined;
  if(typeof input!=='object'||Array.isArray(input)||Object.values(input).filter(clip=>!isHurt(clip)).length>32)throw Error('普通动画最多 32 段，可另设一段受伤动画');
  const ids=new Set(parts.map(p=>p.id)),out={},itemRules=new Set();let count=0;
  for(const [name,clip] of Object.entries(input)){
    if(!/^[a-z][a-z0-9_-]{0,47}$/.test(name)||['constructor','prototype','__proto__'].includes(name))throw Error('动画名称需为小写字母开头的标识符');
    if(!clip||typeof clip.duration!=='number'||!Number.isFinite(clip.duration)||clip.duration<.1||clip.duration>120||typeof clip.loop!=='boolean')throw Error('动画时长需为 0.1–120 秒，并指定是否循环');
    if(!clip.tracks||typeof clip.tracks!=='object'||Array.isArray(clip.tracks))throw Error('动画需要 tracks 对象');
    if(clip.interpolation!=null&&!['linear','step'].includes(clip.interpolation))throw Error('动画过渡需为 linear 或 step');
    const tracks={};
    for(const [id,frames] of Object.entries(clip.tracks)){
      if(!ids.has(id))throw Error('动画引用了不存在的部件：'+id);
      if(!Array.isArray(frames)||frames.length<1||frames.length>128)throw Error('每条轨道需为 1–128 个关键帧');
      let previous=-1;
      tracks[id]=frames.map(f=>{
        if(!f||typeof f.time!=='number'||!Number.isFinite(f.time)||f.time<0||f.time>clip.duration||f.time<=previous)throw Error('关键帧时间必须递增且在动画时长内');
        previous=f.time;if(++count>4096)throw Error('单个模型最多 4096 个关键帧');
        const target=parts.find(p=>p.id===id);if((f.text!=null||f.textStyle!=null)&&target.type!=='text')throw Error('文字关键帧只能用于文字元素');
        if(f.text!=null&&(typeof f.text!=='string'||f.text.length>1024||f.text.split('\n').length>16))throw Error('关键帧文字无效');
        if(f.visible!=null&&typeof f.visible!=='boolean')throw Error('关键帧显示状态需为布尔值');
        if(f.interpolation!=null&&!['linear','step'].includes(f.interpolation))throw Error('帧过渡需为 linear 或 step');
        const style=validateTextStyle(f.textStyle);
        return {...(f.visible!=null?{visible:f.visible}:{}),...(f.interpolation?{interpolation:f.interpolation}:{}),...(f.text!=null?{text:f.text}:{}),...(style?{textStyle:style}:{}),time:f.time,position:vector(f.position,-32,32,'关键帧位置'),rotation:vector(f.rotation,-360,360,'关键帧旋转'),scale:vector(f.scale,.01,16,'关键帧尺寸')};
      });
    }
    const sounds=(clip.sounds??[]).map(s=>{
      if(!s||typeof s.sound!=='string'||!/^[a-z0-9_.-]+:[a-z0-9_./-]+$/.test(s.sound)||!Number.isFinite(s.time)||s.time<0||s.time>clip.duration||!Number.isFinite(s.volume??1)||(s.volume??1)<0||(s.volume??1)>4||!Number.isFinite(s.pitch??1)||(s.pitch??1)<.5||(s.pitch??1)>2)throw Error('声音需要资源名、有效时间、0–4 音量和 0.5–2 音高');
      return {time:s.time,sound:s.sound,volume:s.volume??1,pitch:s.pitch??1};
    });
    if(sounds.length>128)throw Error('每个动画最多 128 个声音事件');
    sounds.sort((a,b)=>a.time-b.time);
    const tags=validateTags(clip.tags,STATES);
    const itemUse=clip.itemUse;
    if(clip.parallel!=null&&typeof clip.parallel!=='boolean')throw Error('并行动画需要布尔值');
    if(isHurt(clip)&&(clip.loop||clip.parallel===false||tags.length!==1||itemUse!=null))
      throw Error('受伤动画必须为非循环并行动画，且只能绑定受伤事件');
    if(itemUse!=null){
      if(!Object.hasOwn(ITEM_USE_STATES,itemUse.state)||!Array.isArray(itemUse.items)||!itemUse.items.length||itemUse.items.length>64)throw Error('物品动画需要状态和 1–64 个物品 ID');
      for(const id of itemUse.items){
        const key=itemUse.state+':'+id;
        if(!itemSelector(id)||itemRules.has(key))throw Error('无效或重复的物品动画规则：'+id);
        itemRules.add(key);
      }
      if(itemUse.timing!=null&&(itemUse.timing!=='kinetic'||itemUse.state!=='charge'||clip.loop))
        throw Error('长矛分段计时需要非循环蓄力动画');
    }
    out[name]={duration:clip.duration,loop:clip.loop,tracks,...(tags?{tags}:{}),...(clip.interpolation?{interpolation:clip.interpolation}:{}),...(sounds.length?{sounds}:{}),...(clip.parallel!=null?{parallel:clip.parallel}:{}),...(itemUse?{itemUse:{state:itemUse.state,items:[...itemUse.items],...(itemUse.timing?{timing:itemUse.timing}:{})}}:{})};
  }
  return out;
}
export function clipTime(clip,seconds,wrap=true){
  return clip.loop&&wrap?((seconds%clip.duration)+clip.duration)%clip.duration:Math.max(0,Math.min(clip.duration,seconds));
}
export function samplePart(part,clip,seconds,wrap=true){
  const frames=clip?.tracks?.[part.id];if(!frames?.length)return trs(part);
  const time=clipTime(clip,seconds,wrap);
  if(time<=frames[0].time)return trs(frames[0]);
  if(time>=frames.at(-1).time)return trs(frames.at(-1));
  const end=frames.findIndex(f=>f.time>time),a=frames[end-1],b=frames[end],t=(time-a.time)/(b.time-a.time);
  if((a.interpolation??clip.interpolation)==='step')return trs(a);
  const lerp=key=>a[key].map((v,i)=>v+(b[key][i]-v)*t);
  return {position:lerp('position'),rotation:lerp('rotation'),scale:lerp('scale')};
}
export function putFrame(clip,id,time,pose){
  const existing=(clip.tracks[id]||[]).find(f=>Math.abs(f.time-time)<.0001);
  const t=existing?.time??Math.max(0,Math.min(clip.duration,Math.round(time*1000)/1000));
  const frames=clip.tracks[id]||[];
  clip.tracks[id]=[...frames.filter(f=>Math.abs(f.time-t)>.0001),{...(existing?.text!=null?{text:existing.text}:{}),...(existing?.textStyle?{textStyle:structuredClone(existing.textStyle)}:{}),...(existing?.visible!=null?{visible:existing.visible}:{}),...(existing?.interpolation?{interpolation:existing.interpolation}:{}),time:t,...trs(pose),...(pose.visible!=null?{visible:pose.visible}:{}),...(pose.interpolation?{interpolation:pose.interpolation}:{}),...(pose.text!=null?{text:pose.text}:{}),...(pose.textStyle?{textStyle:structuredClone(pose.textStyle)}:{})}].sort((a,b)=>a.time-b.time);
}
export function newClip(parts=[],duration=2){return {duration,loop:true,tracks:Object.fromEntries(parts.map(p=>[p.id,[{time:0,...trs(p)},{time:duration,...trs(p)}]]))};}

// A later clip overrides only targets for which it actually has a track.
export function sampleLayers(target,clips,seconds,wrap=true){
  let value=trs(target);
  for(const clip of clips)if(clip?.tracks?.[target.id])value=samplePart(target,clip,seconds,wrap);
  return value;
}
export function moveFrame(clip,id,from,to){
  const frames=clip.tracks[id]??[],frame=frames.find(f=>Math.abs(f.time-from)<.0001);
  if(!frame)throw Error('关键帧不存在');
  to=Math.round(Math.max(0,Math.min(clip.duration,to))*1000)/1000;
  if(frames.some(f=>f!==frame&&Math.abs(f.time-to)<.0001))throw Error('目标时间已有关键帧');
  frame.time=to;frames.sort((a,b)=>a.time-b.time);return to;
}

// Half-open playback window includes t=0 exactly once at start and each loop.
export function soundEventsBetween(clip,previous,current){
  const out=[];if(current<previous)return out;
  for(const event of clip.sounds??[]){
    if(clip.loop){
      const first=Math.max(0,Math.floor((previous-event.time+1e-7)/clip.duration)+1);
      const last=Math.floor((current-event.time+1e-7)/clip.duration);
      for(let cycle=first;cycle<=last;cycle++)out.push({...event,at:cycle*clip.duration+event.time});
    }else if(event.time>previous+1e-7&&event.time<=current+1e-7)out.push({...event,at:event.time});
  }
  return out.sort((a,b)=>a.at-b.at);
}

export function sampleText(part,clip,seconds,wrap=true){
  const time=clip?clipTime(clip,seconds,wrap):0,frames=clip?.tracks?.[part.id]??[];let text=part.text,style=textStyle(part),previous={time:0,textStyle:style};
  for(const frame of frames){if(frame.text!=null&&frame.time<=time+1e-7)text=frame.text;}
  for(const frame of frames.filter(f=>f.textStyle)){
    if(frame.time<=time+1e-7){style={...frame.textStyle};previous=frame;continue;}
    const factor=sampleFrameState(part,clip,seconds,wrap).interpolation==='step'?0:frame.time===previous.time?1:Math.max(0,(time-previous.time)/(frame.time-previous.time)),end=frame.textStyle;
    for(const key of ['opacity','backgroundOpacity'])style[key]=Math.round(previous.textStyle[key]+(end[key]-previous.textStyle[key])*factor);
    for(const key of ['color','backgroundColor']){const a=previous.textStyle[key],b=end[key];style[key]='#'+[1,3,5].map(i=>Math.round(parseInt(a.slice(i,i+2),16)+(parseInt(b.slice(i,i+2),16)-parseInt(a.slice(i,i+2),16))*factor).toString(16).padStart(2,'0')).join('');}
    break;
  }
  return {text,textStyle:style};
}
export const frameValue=value=>({...trs(value),...(value.text!=null?{text:value.text}:{}),...(value.textStyle?{textStyle:structuredClone(value.textStyle)}:{})});

// Visibility is an event: defaults to shown until the first explicit event.
// Interpolation belongs to the outgoing segment of the current frame.
export function sampleFrameState(target,clip,seconds,wrap=true){
 const time=clip?clipTime(clip,seconds,wrap):0;let visible=true,interpolation=clip?.interpolation??'linear';
 for(const f of clip?.tracks?.[target.id]??[]){if(f.time>time+1e-7)break;if(f.visible!=null)visible=f.visible;interpolation=f.interpolation??clip.interpolation??'linear';}
 return {visible,interpolation};
}
