import {colorKey,keyColor} from './palette.js';
import {validateTextStyle} from './text-style.js';
import {vector,validateAnimations} from './animation.js';
import {validateInfo,validateTags,ROLES} from './model-info.js';
export const MATERIALS = {
  WHITE_CONCRETE: ['白色混凝土', '#d9dbe0'], ORANGE_CONCRETE: ['橙色混凝土', '#e76516'],
  MAGENTA_CONCRETE: ['品红色混凝土', '#ab32a5'], LIGHT_BLUE_CONCRETE: ['淡蓝色混凝土', '#2995c5'],
  YELLOW_CONCRETE: ['黄色混凝土', '#e9b32b'], LIME_CONCRETE: ['黄绿色混凝土', '#63a51c'],
  PINK_CONCRETE: ['粉红色混凝土', '#d5658e'], GRAY_CONCRETE: ['灰色混凝土', '#363a3e'],
  LIGHT_GRAY_CONCRETE: ['淡灰色混凝土', '#899082'], CYAN_CONCRETE: ['青色混凝土', '#15868c'],
  PURPLE_CONCRETE: ['紫色混凝土', '#65269b'], BLUE_CONCRETE: ['蓝色混凝土', '#30338b'],
  BROWN_CONCRETE: ['棕色混凝土', '#603c24'], GREEN_CONCRETE: ['绿色混凝土', '#485b24'],
  RED_CONCRETE: ['红色混凝土', '#a62823'], BLACK_CONCRETE: ['黑色混凝土', '#111519'],
  IRON_BLOCK: ['铁块', '#d3d8da'], GOLD_BLOCK: ['金块', '#f4d44c'],
  SEA_LANTERN: ['海晶灯', '#b0ded6'], GLASS: ['玻璃', '#bedee4'],
};
export const displayType=part=>part.type??(part.itemModel!=null?'item':'block');
export const MAX_PARTS = 1024;

export function validateModel(value) {
  if (!value || ![1,2,3,4,5,6,7,8,9].includes(value.version)) throw Error('只支持 version 为 1–9 的模型文件');
  const info=validateInfo(value.info);
  if (typeof value.name !== 'string' || !/^[a-z0-9_-]{1,48}$/.test(value.name)) throw Error('模型名需为 1–48 位小写字母、数字、下划线或短横线');
  if (!Array.isArray(value.parts) || value.parts.length < 1 || value.parts.length > MAX_PARTS) throw Error(`部件数量必须为 1–${MAX_PARTS}`);
  const ids = new Set(),heldHands=new Set();
  const parts = value.parts.map(p => {
    if (!p || typeof p.id !== 'string' || !/^[a-zA-Z0-9_-]{1,48}$/.test(p.id) || ['__proto__','prototype','constructor'].includes(p.id) || ids.has(p.id)) throw Error('部件 ID 无效或重复');
    ids.add(p.id);
    const migratedModel=p.color!=null?colorKey(p.color):p.itemModel;
    const type=p.type??(migratedModel!=null?'item':'block'),material=p.material??'IRON_BLOCK';
    if(!['block','item','text'].includes(type))throw Error('部件类型必须为 block、item 或 text');
    if (!Object.hasOwn(MATERIALS, material)) throw Error(`编辑器暂不支持材质 ${material}，请使用内置材质列表`);
    if (p.itemModel != null && (typeof p.itemModel !== 'string' || !/^[a-z0-9_-]+:[a-z0-9_-]+(?:\/[a-z0-9_-]+)*$/.test(p.itemModel))) throw Error('自定义模型资源路径无效');
    const phase=p.phase??0;
    if (typeof phase !== 'number' || !Number.isFinite(phase) || Math.abs(phase) > 360) throw Error('动画相位需在 -360 至 360 之间');
    // Import 0.2 files once, then keep only the shared resource reference.
    const itemModel=migratedModel;
    if(itemModel!=null&&type!=='item')throw Error('只有物品部件可以设置模型资源引用');
    if(p.heldItem!=null){
      if(type!=='item'||itemModel!=null||!['left','right'].includes(p.heldItem)||heldHands.has(p.heldItem))
        throw Error('手持占位符需要物品类型，每只手最多一个，且不能设置 itemModel');
      heldHands.add(p.heldItem);
    }
    if(p.itemMaterial!=null&&(type!=='item'||typeof p.itemMaterial!=='string'||!/^[A-Z0-9_]+$/.test(p.itemMaterial)))throw Error('itemMaterial 需要有效的物品标识符');
    if(type==='text'){
      if(typeof p.text!=='string'||p.text.length>1024||p.text.split('\n').length>16)throw Error('文字需为不超过 1024 字符、16 行的字符串');
    }else if(p.text!=null)throw Error('text 字段只能用于文字部件');
    const textStyle=validateTextStyle(p.textStyle);if(textStyle&&type!=='text')throw Error('textStyle 只能用于文字部件');
    if(itemModel?.startsWith('display_custom:colors/')&&!keyColor(itemModel))throw Error('颜色资源不存在，请使用颜色选择器或 16 色命名别名');
    const tags=validateTags(p.tags,ROLES,1);
    return {id:p.id, material,...(tags?{tags}:{}), position:vector(p.position,-32,32,'位置'), rotation:vector(p.rotation,-360,360,'旋转'), scale:vector(p.scale,.01,16,'尺寸'), swing:vector(p.swing??[0,0,0],-180,180,'摆动幅度'), phase,
      ...(p.group!=null?{group:p.group}:{}),...(p.type!=null?{type}:{}),...(itemModel != null ? {itemModel}:{} ),
      ...(p.itemMaterial!=null?{itemMaterial:p.itemMaterial}:{}),...(type==='text'?{text:p.text}:{}),...(textStyle?{textStyle}:{}),...(p.heldItem?{heldItem:p.heldItem}:{})};
  });
  if(value.groups!=null&&(!Array.isArray(value.groups)||value.groups.length>32))throw Error('最多 32 个元素组');
  const groups=(value.groups??[]).map(g=>{
    if(!g||typeof g.id!=='string'||!/^[a-zA-Z0-9_-]{1,48}$/.test(g.id)||['__proto__','prototype','constructor'].includes(g.id)||ids.has(g.id))throw Error('组 ID 无效或与元素重复');
    ids.add(g.id);if(g.kind!=null&&!['group','bone'].includes(g.kind))throw Error('节点类型需为 group 或 bone');
    if(g.length!=null&&(!Number.isFinite(g.length)||g.length<.01||g.length>32))throw Error('骨骼长度需为 0.01–32');
    const tags=validateTags(g.tags,ROLES,1);
    return {...(tags?{tags}:{}),...(g.group!=null?{group:g.group}:{}),...(g.pivot?{pivot:vector(g.pivot,-32,32,'支点')}:{}),...(g.kind?{kind:g.kind}:{}),...(g.length!=null?{length:g.length}:{}),id:g.id,position:vector(g.position,-32,32,'组位置'),rotation:vector(g.rotation,-360,360,'组旋转'),scale:vector(g.scale,.01,16,'组尺寸')};
  });
  for(const g of groups){const seen=new Set([g.id]);let parent=g.group;while(parent){if(seen.has(parent))throw Error('组或骨骼不能循环嵌套');seen.add(parent);const ancestor=groups.find(n=>n.id===parent);if(!ancestor)throw Error('父组不存在');parent=ancestor.group;if(seen.size>32)throw Error('嵌套层级过深');}}
  for(const p of parts)if(p.group!=null&&!groups.some(g=>g.id===p.group))throw Error('元素引用不存在的组');
  const animations=validateAnimations(value.animations,[...parts,...groups]);
  const stateTags=Object.values(animations??{}).flatMap(c=>c.tags??[]);if(new Set(stateTags).size!==stateTags.length)throw Error('每个状态标签只能分配给一个动画');
  let playback;
  if(value.playback!=null){
    const names=value.playback.animations;
    if(!Array.isArray(names)||names.length>16||new Set(names).size!==names.length||names.some(n=>!Object.hasOwn(animations??{},n)))throw Error('播放列表引用无效或重复动画');
    playback={animations:[...names]};
  }
  const extended=groups.length||parts.some(p=>p.group!=null)||playback||Object.values(animations??{}).some(c=>c.sounds?.length);
  const modern=groups.some(g=>g.group||g.pivot||g.kind||g.length!=null)||Object.values(animations??{}).some(c=>Object.values(c.tracks).some(fs=>fs.some(f=>f.text!=null||f.textStyle)));
  const motion=Object.values(animations??{}).some(c=>c.interpolation||Object.values(c.tracks).some(fs=>fs.some(f=>f.visible!=null||f.interpolation)));
  const tagged=info||[...parts,...groups,...Object.values(animations??{})].some(p=>p.tags);
  const itemBinding=heldHands.size||Object.values(animations??{}).some(clip=>clip.itemUse);
  const parallel=Object.values(animations??{}).some(clip=>clip.parallel!=null||clip.itemUse?.timing||clip.tags?.includes('hurt'));
  const version=parallel?9:itemBinding?Math.max(8,value.version):tagged?Math.max(7,value.version):motion?Math.max(6,value.version):modern?Math.max(5,value.version):parts.some(p=>p.textStyle)?Math.max(4,value.version):extended?Math.max(3,value.version):parts.some(p=>p.type!=null||p.itemMaterial!=null||p.text!=null)?Math.max(2,value.version):value.version;
  return {version, name:value.name,...(info?{info}:{}), parts,...(groups.length?{groups}:{}),...(animations&&Object.keys(animations).length?{animations}:{}),...(playback?{playback}:{})};
}

export function pose(part, seconds, playing) {
  const wave = playing ? Math.sin(seconds * Math.PI * 2 * .8 + part.phase * Math.PI / 180) : 0;
  const bob = playing ? Math.sin(seconds * Math.PI * 2 * .8) * .03 : 0;
  return {
    position:part.position.map((n,i) => n + (i === 1 ? bob : 0)),
    rotation:part.rotation.map((n,i) => (n + wave * part.swing[i]) * Math.PI / 180),
    scale:[...part.scale],
  };
}
