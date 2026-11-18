import {importProject,exportProject,keyValue} from './bridge.mjs';

const id='display_custom_bridge';
const nodeData='display_custom_node';
const clipData='display_custom_clip';
const projectData='display_custom_project';
const channelNames=['position','rotation','scale'];
const vec=value=>Array.from(value).slice(0,3);
let format,actions=[],properties=[];

function read(holder,key){return holder[key]?JSON.parse(holder[key]):{};}
function encode(holder,key,value){holder[key]=JSON.stringify(value);}
function notify(error){Blockbench.showMessageBox({title:'Display Custom',message:String(error?.message??error)});}
function guarded(operation){try{return operation();}catch(error){notify(error);}}
function isBridge(){return Format?.id===id;}

function colorTexture(name,hex){
  const canvas=document.createElement('canvas');
  canvas.width=canvas.height=16;
  const context=canvas.getContext('2d');
  context.fillStyle=hex;context.fillRect(0,0,16,16);
  return new Texture({name,uv_width:16,uv_height:16}).fromDataURL(canvas.toDataURL()).add(false);
}

function openModel(content){
  const model=importProject(JSON.parse(content.replace(/^\uFEFF/,'')));
  setupProject(format);
  Project.name=model.name;
  encode(Project,projectData,{name:model.name,version:model.version,info:model.info,playback:model.playback});
  const groups=new Map(),textures=new Map();
  const byId=new Map(model.nodes.map(node=>[node.id,node]));
  function addNode(node){
    if(groups.has(node.id))return groups.get(node.id);
    const parent=node.parent?addNode(byId.get(node.parent)):'root';
    const group=new Group({name:node.id,origin:node.origin,rotation:node.rotation});
    group.addTo(parent).init();
    groups.set(node.id,group);
    encode(group,nodeData,{meta:node.meta,part:!!node.box});
    if(node.box){
      const faces=Object.fromEntries(['north','south','east','west','up','down'].map(face=>[face,{uv:[0,0,16,16]}]));
      const cube=new Cube({name:node.id+'_shape',...node.box,origin:node.origin,faces,box_uv:false});
      cube.addTo(group).init();
      if(!textures.has(node.color))textures.set(node.color,colorTexture('DC '+node.color,node.color));
      cube.applyTexture(textures.get(node.color),true);
    }
    return group;
  }
  model.nodes.forEach(addNode);
  for(const clip of model.animations){
    const animation=new BBAnimation({name:clip.name,length:clip.duration,loop:clip.loop?'loop':'hold',snapping:20}).add(false);
    const events={};
    for(const [target,track] of Object.entries(clip.tracks)){
      const group=groups.get(target),animator=animation.getBoneAnimator(group);
      if(track.events.length)events[group.uuid]=track.events;
      for(const channel of channelNames)for(const key of track.keys[channel]){
        const [x,y,z]=key.value;
        animator.addKeyframe({channel,time:key.time,interpolation:key.interpolation,data_points:[{x,y,z}]});
      }
    }
    encode(animation,clipData,{meta:clip.meta,events});
  }
  if(typeof Canvas.updateAll==='function')Canvas.updateAll();
  Blockbench.showQuickMessage('已导入 '+model.nodes.filter(node=>node.box).length+' 个 Display Custom 部件');
}

function readScene(){
  const nodes=[],targetIds=new Map();
  const parentName=node=>node.parent instanceof Group?node.parent.name:undefined;
  function validateCube(cube){
    if(cube.inflate||cube.rescale||cube.stretch?.some(x=>x!==1))
      throw Error('请先应用方盒的膨胀、拉伸或缩放修饰：'+cube.name);
  }
  for(const group of Group.all){
    const data=read(group,nodeData);
    if(data.part){
      const cube=group.children[0];
      if(group.children.length!==1||!(cube instanceof Cube)||cube.rotation.some(x=>x!==0))
        throw Error('部件骨骼 '+group.name+' 需要一个无独立旋转的预览方盒。');
      validateCube(cube);
      nodes.push({id:group.name,parent:parentName(group),meta:data.meta,origin:vec(group.origin),rotation:vec(group.rotation),
        wrapped:true,box:{from:vec(cube.from),to:vec(cube.to)}});
    }else nodes.push({id:group.name,parent:parentName(group),meta:data.meta,origin:vec(group.origin),rotation:vec(group.rotation)});
    targetIds.set(group.uuid,group.name);
  }
  for(const cube of Cube.all){
    if(cube.parent instanceof Group&&read(cube.parent,nodeData).part)continue;
    validateCube(cube);
    nodes.push({id:cube.name,parent:parentName(cube),meta:read(cube,nodeData).meta,
      origin:vec(cube.origin),rotation:vec(cube.rotation),box:{from:vec(cube.from),to:vec(cube.to)}});
  }
  if(Outliner.elements.some(element=>!(element instanceof Cube)))throw Error('导出仅支持组和方盒。');
  const animations=Animator.animations.map(animation=>{
    const {meta,events}=read(animation,clipData);
    if(animation.anim_time_update||animation.start_delay||animation.loop_delay||animation.blend_weight)
      throw Error('请先烘焙动画表达式：'+animation.name);
    const tracks={};
    for(const [uuid,animator] of Object.entries(animation.animators)){
      const target=targetIds.get(uuid);
      if(!target){
        if(animator.keyframes.length)throw Error('请在骨骼组上制作数值姿态，声音用 Display Custom 动画属性保存。');
        continue;
      }
      if(animator.rotation_global||animator.quaternion_interpolation||channelNames.some(channel=>animator.muted?.[channel]))
        throw Error('导出需要局部、逐轴欧拉动画，且轨道未静音：'+target);
      const keys=Object.fromEntries(channelNames.map(channel=>[channel,(animator[channel]??[]).map(key=>({
        time:key.time,value:keyValue(key),interpolation:key.interpolation
      }))]));
      if(Object.values(keys).some(keys=>keys.length)||events?.[uuid]?.length)
        tracks[target]={keys,events:events?.[uuid]??[]};
    }
    for(const [uuid,values] of Object.entries(events??{})){
      const target=targetIds.get(uuid);
      if(target&&!Object.hasOwn(tracks,target))tracks[target]={keys:{},events:values};
    }
    const last=Math.max(.1,...Object.values(tracks).flatMap(track=>Object.values(track.keys).flatMap(keys=>keys.map(key=>key.time))));
    return {name:animation.name,duration:animation.length||last,loop:animation.loop==='loop',tracks,meta};
  });
  const data=read(Project,projectData);
  return {...data,name:data.name??Project.name,nodes,animations};
}

function exportModel(){
  const model=exportProject(readScene());
  const content=JSON.stringify(model);
  Blockbench.export({type:'Display Custom JSON',extensions:['json'],name:model.name,content});
}

function editProperties(){
  const cube=Cube.selected?.[0];
  const parent=cube?.parent;
  const node=cube?(parent instanceof Group&&read(parent,nodeData).part?parent:cube):Group.first_selected;
  const animation=BBAnimation.selected;
  const data=node&&read(node,nodeData);
  const clip=animation&&read(animation,clipData);
  const {name,version,playback,info}=read(Project,projectData);
  const {id:unusedId,group:unusedGroup,position:unusedP,rotation:unusedR,scale:unusedS,...extra}=data?.meta??{};
  const {duration,loop,...clipMeta}=clip?.meta??{};
  new Dialog({
    id:'display_custom_properties',title:'Display Custom 属性',
    form:{
      name:{label:'模型 ID',type:'text',value:name??Project.name},
      info:{label:'模型信息 JSON',type:'textarea',value:JSON.stringify(info??{},null,2)},
      ...(node?{node:{label:node.name+' 部件或组属性',type:'textarea',value:JSON.stringify(extra,null,2)}}:{}),
      ...(animation?{animation:{label:animation.name+' 动画规则',type:'textarea',value:JSON.stringify(clipMeta,null,2)}}:{})
    },
    onConfirm(form){
      try{
        const nextInfo=JSON.parse(form.info);
        const nextNode=node?JSON.parse(form.node):null;
        const nextClip=animation?JSON.parse(form.animation):null;
        const scene=readScene();
        scene.name=form.name.trim();
        scene.info=nextInfo;
        if(node)scene.nodes.find(candidate=>candidate.id===node.name).meta=nextNode;
        if(animation)scene.animations.find(clip=>clip.name===animation.name).meta=nextClip;
        const checked=exportProject(scene);
        encode(Project,projectData,{name:checked.name,version:checked.version??version,playback,info:checked.info});
        if(node)encode(node,nodeData,{...data,meta:nextNode});
        if(animation)encode(animation,clipData,{...clip,meta:nextClip});
        Project.saved=false;
      }catch(error){notify(error);return false;}
    }
  }).show();
}

Plugin.register(id,{
  title:'Display Custom Bridge',author:'Display Custom',description:'在 Blockbench 编辑 Display Custom 模型、骨骼和动画。',
  version:'0.1.0',min_version:'5.0.0',variant:'both',icon:'view_in_ar',
  contributes:{formats:[id]},
  onload(){
    properties=[
      new Property(ModelProject,'string',projectData,{default:'',condition:()=>isBridge()}),
      new Property(Group,'string',nodeData,{default:'',condition:()=>isBridge()}),
      new Property(Cube,'string',nodeData,{default:'',condition:()=>isBridge()}),
      new Property(BBAnimation,'string',clipData,{default:'',condition:()=>isBridge()})
    ];
    format=new ModelFormat(id,{name:'Display Custom',description:'Display Custom JSON v1–v9',
      icon:'view_in_ar',category:'minecraft',bone_rig:true,animation_mode:true,rotate_cubes:true,
      centered_grid:true,forward_direction:'+z',euler_order:'XYZ',quaternion_interpolation:false,
      molang:false,paint_mode:false,integer_size:false});
    const add=(suffix,name,menu,click,condition)=>{const action=new Action(id+'_'+suffix,{name,icon:'view_in_ar',click,condition});actions.push(action);MenuBar.addAction(action,menu);};
    add('import','导入 Display Custom JSON…','file.import',()=>Blockbench.import({extensions:['json'],type:'Display Custom JSON',readtype:'text'},files=>{
      if(files[0])guarded(()=>openModel(files[0].content));
    }));
    add('export','导出 Display Custom JSON…','file.export',()=>guarded(exportModel),isBridge);
    add('properties','Display Custom 属性…','edit',()=>guarded(editProperties),isBridge);
  },
  onunload(){
    actions.forEach(action=>action.delete());actions=[];
    properties.forEach(property=>property.delete());properties=[];
    format?.delete();
  }
});
