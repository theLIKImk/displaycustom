import {strToU8, zipSync, zlibSync} from 'fflate';
import {MATERIALS, validateModel,displayType} from './model.js';
import {PALETTE,NAMED_COLORS,colorKey,keyColor,allColorCodes} from './palette.js';
export {PALETTE,allColorCodes} from './palette.js';
export const PACK_FORMAT=[97,1];
export function toResourceModel(input){
  const model=validateModel(input);
  return {...model,parts:model.parts.map(p=>displayType(p)==='block'?({...p,...(p.type!=null?{type:'item'}:{}),itemModel:colorKey(MATERIALS[p.material][1])}):p)};
}
export function colorDemo(input,key='cyan'){
  if(!Object.hasOwn(PALETTE,key))throw Error('未知配色');
  const model=structuredClone(input);model.name='resource_'+key;
  for(const p of model.parts){
    const hex=p.id==='body'||p.id.startsWith('arm_')?PALETTE[key][1]
      :p.id==='head'?'#eeeeee':p.id.startsWith('eye_')||p.id==='chest_light'?'#bbffdd':p.id.startsWith('leg_')?'#334455':MATERIALS[p.material][1];
    delete p.color;p.itemModel=colorKey(hex);
  }
  return toResourceModel(model);
}
export function texturePixels(hex){const rgba=[...([1,3,5].map(i=>parseInt(hex.slice(i,i+2),16))),255];return new Uint8Array(Array.from({length:256},()=>rgba).flat());}
const encoder=new TextEncoder();
function concat(...arrays){const out=new Uint8Array(arrays.reduce((s,a)=>s+a.length,0));let o=0;for(const a of arrays){out.set(a,o);o+=a.length;}return out;}
function uint32(n){const a=new Uint8Array(4);new DataView(a.buffer).setUint32(0,n>>>0);return a;}
function crc32(bytes){let crc=0xffffffff;for(const byte of bytes){crc^=byte;for(let i=0;i<8;i++)crc=(crc>>>1)^((crc&1)?0xedb88320:0);}return (crc^0xffffffff)>>>0;}
function chunk(type,data){const body=concat(encoder.encode(type),data);return concat(uint32(data.length),body,uint32(crc32(body)));}
export function texturePng(hex) {
  const header=concat(uint32(16),uint32(16),new Uint8Array([8,6,0,0,0]));
  const pixels=texturePixels(hex), rows=new Uint8Array(16*(1+16*4));
  for(let y=0;y<16;y++)rows.set(pixels.subarray(y*64,(y+1)*64),y*65+1);
  return concat(new Uint8Array([137,80,78,71,13,10,26,10]),chunk('IHDR',header),chunk('IDAT',zlibSync(rows)),chunk('IEND',new Uint8Array()));
}
const json=value=>strToU8(JSON.stringify(value,null,2)+'\n');

export function colorLibraryFiles(codes=allColorCodes()){
  const files={'pack.mcmeta':json({pack:{description:'Display Custom · 4096 RGB colors · Java 26.3',min_format:PACK_FORMAT,max_format:PACK_FORMAT}})};
  const ref='display_custom:colors/unit_cube',texture='display_custom:item/colors/white';
  const faces=Object.fromEntries(['north','south','east','west','up','down'].map(side=>[side,{uv:[0,0,16,16],texture:'#all',tintindex:0}]));
  files['assets/display_custom/models/colors/unit_cube.json']=json({textures:{all:texture,particle:texture},elements:[{from:[0,0,0],to:[16,16,16],faces}],display:{gui:{rotation:[30,225,0],scale:[.65,.65,.65]}}});
  files['assets/display_custom/textures/item/colors/white.png']=texturePng('#ffffff');
  const entries=new Map(codes.map(code=>{
    if(!/^[0-9a-f]{6}$/.test(code)||!keyColor('display_custom:colors/'+code))throw Error('颜色库编号无效：'+code);
    return [code,'#'+code];
  }));
  for(const [name,hex] of Object.entries(NAMED_COLORS))entries.set(name,hex);
  for(const [code,hex] of entries)files[`assets/display_custom/items/colors/${code}.json`]=json({model:{type:'minecraft:model',model:ref,tints:[{type:'minecraft:constant',value:parseInt(hex.slice(1),16)}]}});
  return files;
}
/** Small per-model kit reuses the same public keys; no per-model texture files. */
export function resourcePackFiles(inputs,extraFiles={}){
  const models=inputs.map(toResourceModel),names=new Set(),codes=new Set();
  for(const model of models){
    if(names.has(model.name))throw Error('重复模型名称：'+model.name);names.add(model.name);
    for(const p of model.parts){
      if(displayType(p)!=='item'||!p.itemModel)continue;
      const hex=keyColor(p.itemModel);
      if(!hex&&extraFiles['assets/'+p.itemModel.replace(':','/items/')+'.json'])continue;
      if(!hex)throw Error('测试包只能打包公共颜色库引用，外部模型请使用原资源包：'+p.itemModel);
      codes.add(hex.slice(1));
    }
  }
  return {files:{...colorLibraryFiles([...codes]),...extraFiles},models};
}
export function resourcePackZip(inputs){return zipSync(resourcePackFiles(inputs).files,{level:6});}
export function fullColorLibraryZip(){return zipSync(colorLibraryFiles(),{level:6});}
export function resourceTestKit(input,extraFiles={}){
  const {files,models:[model]}=resourcePackFiles([input],extraFiles);
  return zipSync({'resourcepack.zip':zipSync(files,{level:6}),[`models/${model.name}.json`]:json(model),
    'README.txt':strToU8(`Display Custom 0.0.1 / Paper 26.3\n\n模型不含 color 参数，使用 display_custom:colors/rrggbb 公共颜色库。\n本包包含当前模型用到的颜色及已导入的物品模型/PNG 资源。\n客户端启用本测试包内的 resourcepack.zip。仅使用公共颜色且已有完整版 4096 色包时，只需更新模型 JSON。\nJSON 放入 plugins/DisplayCustom/models/，执行 /dc reload，再 /dc spawn ${model.name}。\n动画使用 /dc play <实例ID> <动画名> <倍速>，例如 /dc play <实例ID> demo 1；/dc modelinfo ${model.name} 查看动画列表。\n`)},{level:6});
}
